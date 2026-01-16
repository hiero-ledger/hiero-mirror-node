-- Add alias index for entity_history table

create index if not exists entity_history__alias on entity_history (alias) where alias is not null;
