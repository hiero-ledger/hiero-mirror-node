-- HAPI changed the max symbol size to 100 characters, apply the same to db table
alter table if exists token alter column symbol type character varying(100);
