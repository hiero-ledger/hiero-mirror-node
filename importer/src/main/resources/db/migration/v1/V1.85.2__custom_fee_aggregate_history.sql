create table custom_fee_temp (
  fixed_fees        jsonb,
  fractional_fees   jsonb,
  royalty_fees      jsonb,
  timestamp_range   int8range not null,
  token_id          bigint    not null
);
create table custom_fee_history
(
    like custom_fee_temp including constraints
);

with custom_fee_jsonb as (
  select
     case when collector_account_id is not null and amount_denominator is null and royalty_denominator is null then
       jsonb_build_object(
         'all_collectors_are_exempt', all_collectors_are_exempt,
         'amount', amount,
         'collector_account_id', collector_account_id,
         'denominating_token_id', denominating_token_id)
     end as fixed_fees,
     case when amount_denominator is not null then
       jsonb_build_object(
         'all_collectors_are_exempt', all_collectors_are_exempt,
         'collector_account_id', collector_account_id,
         'denominator', amount_denominator,
         'maximum_amount', maximum_amount,
         'minimum_amount', minimum_amount,
         'net_of_transfers', net_of_transfers,
         'numerator', amount)
     end as fractional_fees,
     case when royalty_denominator is not null then
       jsonb_build_object(
         'all_collectors_are_exempt', all_collectors_are_exempt,
         'collector_account_id', collector_account_id,
         'denominator', royalty_denominator,
         'fallback_fee',
             case when amount is not null then
               jsonb_build_object(
                 'amount', amount,
                 'denominating_token_id', denominating_token_id)
             end,
         'numerator', royalty_numerator)
     end as royalty_fees,
     int8range(created_timestamp, (
        select c.created_timestamp
        from custom_fee c
        where c.token_id = p.token_id
          and c.created_timestamp > p.created_timestamp
        order by c.created_timestamp
        limit 1)) as timestamp_range,
     token_id
   from custom_fee p
 ), aggregated_custom_fee as (
  select
    jsonb_agg(fixed_fees) filter (where fixed_fees is not null) as fixed_fees,
    jsonb_agg(fractional_fees) filter (where fractional_fees is not null) as fractional_fees,
    jsonb_agg(royalty_fees) filter (where royalty_fees is not null) as royalty_fees,
    timestamp_range,
    token_id
  from custom_fee_jsonb
  group by token_id, timestamp_range
), history as (
  insert into custom_fee_history (fixed_fees, fractional_fees, royalty_fees, timestamp_range, token_id)
  select * from aggregated_custom_fee where upper(timestamp_range) is not null
) insert into custom_fee_temp (fixed_fees, fractional_fees, royalty_fees, timestamp_range, token_id)
select * from aggregated_custom_fee where upper(timestamp_range) is null;

drop table custom_fee;
alter table custom_fee_temp
  rename to custom_fee;
alter table custom_fee
  add primary key (token_id);

create index if not exists custom_fee_history__token_id_timestamp_range
  on custom_fee_history (token_id, lower(timestamp_range));
create index if not exists custom_fee_history__timestamp_range on custom_fee_history using gist (timestamp_range);