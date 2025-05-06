-------------------
-- Add missing receiver_sig_required
-------------------

alter table if exists entity
    add column if not exists receiver_sig_required boolean null;
