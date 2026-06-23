PRAGMA foreign_keys = ON;

create table actor_identities (
    actor_id       blob not null primary key,
    created_at     text not null default current_timestamp,
    actor_status   text check (actor_status in ('valid', 'invalid', 'timeout'))
);

create table actor_metadata_deltas (
    actor_id       blob not null references actor_identities(actor_id) on delete cascade,
    created_at     text default current_timestamp,
    creator        blob not null references actor_identities(actor_id) on delete set null,
    version        blob not null,
    doc_status     text check (doc_status in ('valid', 'locked', 'invalid')),
    primary key    (actor_id, version)
);

create trigger apply_actor_metadata_delta
    after insert on actor_metadata_deltas
begin
    update actor_identities
    set actor_status = coalesce(NEW.actor_status, actor_status)
    where actor_id = NEW.actor_id;
end;

create index idx_actor_metadata on actor_metadata_deltas (actor_id, version desc);

create table document_identities (
    doc_id          blob not null primary key,
    created_at      text default current_timestamp,
    owner           blob not null references actor_identities(actor_id) on delete cascade,
    doc_status      text not null check (doc_status in ('valid', 'locked', 'invalid')) default 'valid'
);

create table document_metadata_deltas (
       doc_id          blob not null references document_identities(doc_id) on delete cascade,
       created_at      text default current_timestamp,
       owner           blob default null,
       creator         blob not null references actor_identities(actor_id) on delete set null,
       version         blob not null,
       doc_status      text check (doc_status in ('valid', 'locked', 'invalid')),
       primary key     (doc_id, version)
);

create trigger apply_document_metadata_delta
    after insert on document_metadata_deltas
begin
    update document_identities
    set owner = coalesce(NEW.owner, owner),
        doc_status = coalesce(NEW.doc_status, doc_status)
    where doc_id = NEW.doc_id;
end;

create index idx_doc_metadata on document_metadata_deltas (doc_id, version desc);

create table authentication_documents (
       doc_id            blob not null references document_identities(doc_id) on delete cascade,
       authn_type        text not null check (authn_type in ('remember-me', 'password', 'email')),
       created_at        text not null default current_timestamp,
       payload           jsonb not null,
       creator           blob not null references actor_identities(actor_id) on delete set null,
       primary key       (doc_id, authn_type)
);


-- Authorization.
create table role_documents (
       doc_id     blob not null references document_identities(doc_id) on delete cascade,
       version    blob not null,
       created_at text default current_timestamp,
       creator    blob references actor_identities(actor_id) on delete set null,
       doc        jsonb,
       primary key (doc_id, version)
);

create index idx_roles_docs on role_documents (doc_id, version desc);

create table role_assignment_documents (
       doc_id     blob not null references document_identities(doc_id) on delete cascade,
       creator    blob references actor_identities(actor_id) on delete set null,
       created_at text default current_timestamp,
       version    blob not null,
       doc        jsonb
);

create index idx_role_assignment_docs on role_assignment_documents (doc_id, version desc);

create table user_documents (
    doc_id     blob not null references document_identities(doc_id) on delete cascade,
    version    blob not null,
    created_at text default current_timestamp,
    creator    blob references actor_identities(actor_id) on delete set null,
    doc        jsonb,
    primary key (doc_id, version)
);

create index idx_user_docs on user_documents (doc_id, version desc);

create table relationship_documents (
       relationship_type text not null check (relationship_type in ('follow', 'mute', 'block')),
       doc_id            blob not null references document_identities(doc_id) on delete cascade,
       source_actor      blob not null references actor_identities(actor_id) on delete cascade,
       target_actor      blob not null references actor_identities(actor_id) on delete cascade,
       version           blob not null,
       created_at        text default current_timestamp,
       doc               jsonb default null,
       primary key       (doc_id, version),
       check (source_actor != target_actor)
);

create index idx_relationship_docs on relationship_documents(source_actor, relationship_type, target_actor, version desc);

create trigger enforce_unique_active_relationship
    before insert on relationship_documents
begin
    select raise(ABORT, 'active relationship already exists')
    where exists (
        select 1
        from relationship_documents r
        join document_identities di on di.doc_id = r.doc_id
        where r.source_actor = NEW.source_actor
          and r.relationship_type = NEW.relationship_type
          and r.target_actor = NEW.target_actor
          and di.doc_status = 'valid'
    );
end;

create table status_documents (
    doc_id     blob not null references document_identities(doc_id) on delete cascade,
    version    blob not null,
    created_at text default current_timestamp,
    creator               blob references actor_identities(actor_id) on delete set null,
    reply_to              blob references document_identities(doc_id) on delete set null,
    reply_to_hard_deleted boolean not null default false,
    doc        jsonb,
    primary key (doc_id, version)
);

create index idx_status_docs on status_documents (doc_id, version desc);

create table status_reaction_documents (
    doc_id     blob not null references document_identities(doc_id) on delete cascade,
    version    blob not null,
    created_at text default current_timestamp,
    creator               blob references actor_identities(actor_id) on delete set null,
    reply_to              blob references document_identities(doc_id) on delete set null,
    reply_to_hard_deleted boolean not null default false,
    doc                   jsonb,
    primary key (doc_id, version)
);

create index idx_status_reaction_docs on status_reaction_documents (doc_id, version desc);

create table custom_documents (
       doc_id       blob not null references document_identities(doc_id) on delete cascade,
       version      blob not null,
       created_at   text default current_timestamp,
       creator      blob references actor_identities(actor_id) on delete set null,
       category     text,
       label        text,
       doc          jsonb,
       primary key  (doc_id, version)
);

create index idx_custom_docs_by_selector on custom_documents (category, label, version desc);

create table audit_logs (
       log_id      blob not null primary key,
       created_at  text not null default current_timestamp,
       doc         jsonb
);

create trigger cascade_actor_soft_delete
    after update of invalidated_at on actor_identities
    when NEW.invalidated_at is not null and OLD.invalidated_at is null
begin
    update document_identities
    set doc_status = 'invalid'
    where doc_id in (
        select r.doc_id
        from relationship_documents r
        where (r.source_actor = NEW.id or r.target_actor = NEW.id)
    )
    and doc_status = 'valid';
end;

-- Fires before the ON DELETE SET NULL FK cascade nullifies reply_to
create trigger mark_reply_to_hard_deleted
    before delete on document_identities
begin
    update status_documents
    set reply_to_hard_deleted = true
    where reply_to = OLD.doc_id;

    update status_reaction_documents
    set reply_to_hard_deleted = true
    where reply_to = OLD.doc_id;
end;
