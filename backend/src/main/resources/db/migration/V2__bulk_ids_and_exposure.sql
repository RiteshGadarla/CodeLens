-- file/entity ids are reserved in bulk by the analysis writer
alter sequence source_file_seq increment by 1;
alter sequence code_entity_seq increment by 1;

alter table entity_metric add column exposed boolean not null default false;
