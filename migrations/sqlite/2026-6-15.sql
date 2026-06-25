PRAGMA foreign_keys = ON;

create table migrations (
       id          int primary key,
       label       text not null,
       migrated_at text default current_timestamp,
       doc         jsonb
);

insert into migrations (label, doc) values
('social.mushin.alternative', '{}');

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

create table documents (
       doc_id            blob not null references document_identities(doc_id) on delete cascade,
       version           blob not null, -- UUID v7
       created_at        text not null default current_timestamp,
       doc               jsonb not null,
       creator           blob not null references actor_identities(actor_id) on delete set null,
       doc_type          text not null,
       primary key       (doc_id, version desc)
);

create index idx_docs on documents (doc_id, version desc);
