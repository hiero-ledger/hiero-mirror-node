-------------------
-- Support HIP-573 custom fee enhancement: Blanket exemptions for custom fee collectors
-------------------

alter table if exists custom_fee
    add column all_collectors_are_exempt boolean not null default false;

