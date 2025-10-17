create index if not exists token_account__token_id_account_id
    on public.token_account(token_id, account_id);
