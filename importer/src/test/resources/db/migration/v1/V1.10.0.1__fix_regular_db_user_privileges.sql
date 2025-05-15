-- SPDX-License-Identifier: Apache-2.0

revoke all on all tables in schema public from ${db-user};
alter default privileges in schema public revoke all on tables from ${db-user};
