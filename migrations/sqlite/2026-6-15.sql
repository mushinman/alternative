PRAGMA foreign_keys = ON;

create table migrations (
       id          int primary key,
       label       text not null,
       migrated_at text default strftime('%Y-%m-%d %H:%M:%f', 'now'),
       doc         blob not null check (json_valid(doc))
);

insert into migrations (label, doc) values
('social.mushin.alternative', '{}');

create table documents (
       doc_id            blob primary key not null,
       version           blob unique not null,
       system_from       text not null default (strftime('%Y-%m-%d %H:%M:%f', 'now')),
       doc               blob not null check (json_valid(doc)),
       creator           blob not null,
       owner             blob not null,
       doc_type          text not null
);

create table documents_history (
       doc_id            blob not null,
       version           blob unique not null,
       system_from       text not null,
       system_to         text not null default (strftime('%Y-%m-%d %H:%M:%f', 'now')),
       doc               blob not null,
       creator           blob not null,
       owner             blob not null,
       doc_type          text not null,
       primary key       (doc_id, version)
);

create index idx_docs_history on documents_history (doc_id, system_to desc);

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
    insert into documents_history (doc_id, version, system_from, doc, creator, owner, doc_type)
    values (OLD.doc_id, OLD.version, OLD.system_from, OLD.doc, OLD.creator, OLD.owner, OLD.doc_type);
    update documents set system_from = strftime('%Y-%m-%d %H:%M:%f', 'now') where doc_id = OLD.doc_id;
end;

create trigger apply_unitemporal_deletes_to_documents
       after delete on documents
begin
    insert into documents_history (doc_id, version, system_from, doc, creator, owner, doc_type)
    values (OLD.doc_id, OLD.version, OLD.system_from, OLD.doc, OLD.creator, OLD.owner, OLD.doc_type);
end;
