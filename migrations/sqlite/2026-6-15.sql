create table migrations (
       id          int primary key,
       label       text not null,
       migrated_at text default strftime('%Y-%m-%d %H:%M:%f', 'now'),
       doc         blob not null check (json_valid(doc))
);

insert into migrations (label, doc) values
('social.mushin.alternative', '{}');

create table documents (
       id                text default (uuidv7_text()),
       version           blob unique not null default (uuidv7_blob()),
       system_from       text not null default (strftime('%Y-%m-%d %H:%M:%f', 'now')),
       doc               blob not null check (json_valid(doc)),
       creator           blob not null,
       owner             blob not null,
       doc_type          text not null,
       primary key       (id, doc_type)
);

create table documents_history (
       id                text not null,
       version           blob unique not null,
       system_from       text not null,
       system_to         text not null default (strftime('%Y-%m-%d %H:%M:%f', 'now')),
       doc               blob not null,
       creator           blob not null,
       owner             blob not null,
       doc_type          text not null,
       primary key       (id, doc_type, version)
);

create index idx_docs_history on documents_history (id, system_to desc);

create index idx_users
       on documents (json_extract(doc, '$.nickname'))
       where doc_type = ':social.mushin.alternative/user';

create trigger check_version_validity_on_documents_insert
       before insert on documents
       for each row
       when exists (select 1 from documents_history where version = NEW.version)
begin
    select raise(ABORT, 'Error: version must be unique');
end;

create trigger check_version_validity_on_documents_update
       before update on documents
       for each row
       when exists (select 1 from documents_history where version = NEW.version)
begin
    select raise(ABORT, 'Error: version must be unique');
end;

create trigger apply_unitemporal_updates_to_documents
       after update on documents
       when NEW.version != OLD.version
begin
    insert into documents_history (id, version, system_from, doc, creator, owner, doc_type)
    values (OLD.id, OLD.version, OLD.system_from, OLD.doc, OLD.creator, OLD.owner, OLD.doc_type);
    update documents set system_from = strftime('%Y-%m-%d %H:%M:%f', 'now'),
                         version = uuidv7_blob()
                         where id = OLD.id;
end;

create trigger apply_unitemporal_deletes_to_documents
       after delete on documents
begin
    insert into documents_history (id, version, system_from, doc, creator, owner, doc_type)
    values (OLD.id, OLD.version, OLD.system_from, OLD.doc, OLD.creator, OLD.owner, OLD.doc_type);
end;
