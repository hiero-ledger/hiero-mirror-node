-- Performing a trigger for each row is slower than directly batching NOTIFY via code.
-- We can revisit with PostgreSQL 10 since it has transition tables allowing for each statement triggers.
drop trigger if exists topic_message_trigger on topic_message;
drop function if exists topic_message_notifier();
