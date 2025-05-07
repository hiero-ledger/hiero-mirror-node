-------------------
-- Revert V1.43.2 which limits changes to token_account table
-------------------

-- change access privilege for db owner, if different than the owner of the temp resources, so later we can drop
-- the temp resources
create or replace function change_owner_access_privilege(grant_or_revoke boolean) returns void as
$$
begin
    if current_user <> '${db-user}' then
        if grant_or_revoke then
            execute 'grant ${db-user} to ' || current_user;
        else
            execute 'revoke ${db-user} from ' || current_user;
        end if;
    end if;
end
$$ language plpgsql;

select change_owner_access_privilege(true);

drop function if exists add_missing_token_account_association() cascade;
drop table if exists last_transaction;

select change_owner_access_privilege(false);
drop function if exists change_owner_access_privilege(boolean);
