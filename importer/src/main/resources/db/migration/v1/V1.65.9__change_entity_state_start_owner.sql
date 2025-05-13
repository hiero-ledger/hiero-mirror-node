create or replace function change_access_privilege(grant_or_revoke boolean) returns void as
$$
begin
    if current_user <> '${db-user}' then
        if grant_or_revoke then
            grant create on schema public to ${db-user};
            grant ${db-user} to current_user;
        else
            revoke ${db-user} from current_user;
            revoke create on schema public from ${db-user};
        end if;
    end if;
end
$$ language plpgsql;

select change_access_privilege(true);
alter materialized view if exists entity_state_start owner to ${db-user};
select change_access_privilege(false);

drop function if exists change_access_privilege(grant_or_revoke boolean);
