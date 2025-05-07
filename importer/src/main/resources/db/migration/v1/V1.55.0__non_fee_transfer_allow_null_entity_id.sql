-- allow entity_id to be null

alter table if exists non_fee_transfer alter column entity_id drop not null;
