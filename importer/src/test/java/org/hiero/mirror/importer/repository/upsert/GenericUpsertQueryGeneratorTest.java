// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository.upsert;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.vertical_blank.sqlformatter.SqlFormatter;
import com.github.vertical_blank.sqlformatter.languages.Dialect;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.schedule.Schedule;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class GenericUpsertQueryGeneratorTest extends ImporterIntegrationTest {

    private static final SqlFormatter.Formatter SQL_FORMATTER = SqlFormatter.of(Dialect.PostgreSql);
    private final UpsertQueryGeneratorFactory factory;

    @Test
    void getFinalTableName() {
        UpsertQueryGenerator generator = factory.get(Entity.class);
        assertThat(generator.getFinalTableName()).isEqualTo("entity");
    }

    @Test
    void getTemporaryTableName() {
        UpsertQueryGenerator generator = factory.get(Entity.class);
        assertThat(generator.getTemporaryTableName()).isEqualTo("entity_temp");
    }

    @Test
    void customCoalesceColumn() {
        var sql = "case when total_supply >= 0 then total_supply else e_total_supply + coalesce(total_supply, 0) end";
        UpsertQueryGenerator generator = factory.get(Token.class);
        assertThat(generator).isInstanceOf(GenericUpsertQueryGenerator.class);
        assertThat(format(generator.getUpsertQuery())).containsIgnoringWhitespaces(sql);
    }

    @Test
    void getCustomFeeUpsertQueryHistory() {
        var sql = """
                with non_history as (
                  select
                    e.entity_id as e_entity_id,
                    e.fixed_fees as e_fixed_fees,
                    e.fractional_fees as e_fractional_fees,
                    e.royalty_fees as e_royalty_fees,
                    e.timestamp_range as e_timestamp_range,
                    t.*
                  from
                    custom_fee e
                    join custom_fee_temp t on e.entity_id = t.entity_id
                  where
                    t.timestamp_range is null
                )
                insert into
                  custom_fee (
                    entity_id,
                    fixed_fees,
                    fractional_fees,
                    royalty_fees,
                    timestamp_range
                  )
                select
                  coalesce(entity_id, e_entity_id, null),
                  fixed_fees,
                  fractional_fees,
                  royalty_fees,
                  coalesce(timestamp_range, e_timestamp_range, null)
                from
                  non_history on conflict (entity_id) do
                update
                set
                  fixed_fees = excluded.fixed_fees,
                  fractional_fees = excluded.fractional_fees,
                  royalty_fees = excluded.royalty_fees,
                  timestamp_range = excluded.timestamp_range;
                with existing as (
                  select
                    e.entity_id as e_entity_id,
                    e.fixed_fees as e_fixed_fees,
                    e.fractional_fees as e_fractional_fees,
                    e.royalty_fees as e_royalty_fees,
                    e.timestamp_range as e_timestamp_range,
                    t.*
                  from
                    custom_fee_temp t
                    left join custom_fee e on e.entity_id = t.entity_id
                  where
                    t.timestamp_range is not null
                ),
                existing_history as (
                  insert into
                    custom_fee_history (
                      entity_id,
                      fixed_fees,
                      fractional_fees,
                      royalty_fees,
                      timestamp_range
                    )
                  select
                    distinct on (entity_id) e_entity_id,
                    e_fixed_fees,
                    e_fractional_fees,
                    e_royalty_fees,
                    int8range(lower(e_timestamp_range), lower(timestamp_range)) as timestamp_range
                  from
                    existing
                  where
                    e_timestamp_range is not null
                    and timestamp_range is not null
                  order by
                    entity_id,
                    timestamp_range asc
                ),
                temp_history as (
                  insert into
                    custom_fee_history (
                      entity_id,
                      fixed_fees,
                      fractional_fees,
                      royalty_fees,
                      timestamp_range
                    )
                  select
                    distinct coalesce(e_entity_id, entity_id, null),
                    fixed_fees,
                    fractional_fees,
                    royalty_fees,
                    coalesce(timestamp_range, e_timestamp_range, null)
                  from
                    existing
                  where
                    upper(timestamp_range) is not null
                )
                insert into
                  custom_fee (
                    entity_id,
                    fixed_fees,
                    fractional_fees,
                    royalty_fees,
                    timestamp_range
                  )
                select
                  coalesce(entity_id, e_entity_id, null),
                  fixed_fees,
                  fractional_fees,
                  royalty_fees,
                  coalesce(timestamp_range, e_timestamp_range, null)
                from
                  existing
                where
                  timestamp_range is not null
                  and upper(timestamp_range) is null on conflict (entity_id) do
                update
                set
                  fixed_fees = excluded.fixed_fees,
                  fractional_fees = excluded.fractional_fees,
                  royalty_fees = excluded.royalty_fees,
                  timestamp_range = excluded.timestamp_range;
                """;
        var generator = factory.get(CustomFee.class);
        assertThat(generator).isInstanceOf(GenericUpsertQueryGenerator.class);
        assertThat(format(generator.getUpsertQuery())).isEqualTo(format(sql));
    }

    @Test
    void getUpsertQueryHistory() {
        var sql = """
                        with non_history as (
                          select
                            e.alias as e_alias,
                            e.auto_renew_account_id as e_auto_renew_account_id,
                            e.auto_renew_period as e_auto_renew_period,
                            e.balance as e_balance,
                            e.balance_timestamp as e_balance_timestamp,
                            e.created_timestamp as e_created_timestamp,
                            e.decline_reward as e_decline_reward,
                            e.deleted as e_deleted,
                            e.ethereum_nonce as e_ethereum_nonce,
                            e.evm_address as e_evm_address,
                            e.expiration_timestamp as e_expiration_timestamp,
                            e.id as e_id,
                            e.key as e_key,
                            e.max_automatic_token_associations as e_max_automatic_token_associations,
                            e.memo as e_memo,
                            e.num as e_num,
                            e.obtainer_id as e_obtainer_id,
                            e.permanent_removal as e_permanent_removal,
                            e.proxy_account_id as e_proxy_account_id,
                            e.public_key as e_public_key,
                            e.realm as e_realm,
                            e.receiver_sig_required as e_receiver_sig_required,
                            e.shard as e_shard,
                            e.stake_period_start as e_stake_period_start,
                            e.staked_account_id as e_staked_account_id,
                            e.staked_node_id as e_staked_node_id,
                            e.timestamp_range as e_timestamp_range,
                            e.type as e_type,
                            t.*
                          from
                            entity e
                            join entity_temp t on e.id = t.id
                          where
                            t.timestamp_range is null
                        )
                        insert into
                          entity (
                            alias,
                            auto_renew_account_id,
                            auto_renew_period,
                            balance,
                            balance_timestamp,
                            created_timestamp,
                            decline_reward,
                            deleted,
                            ethereum_nonce,
                            evm_address,
                            expiration_timestamp,
                            id,
                            key,
                            max_automatic_token_associations,
                            memo,
                            num,
                            obtainer_id,
                            permanent_removal,
                            proxy_account_id,
                            public_key,
                            realm,
                            receiver_sig_required,
                            shard,
                            stake_period_start,
                            staked_account_id,
                            staked_node_id,
                            timestamp_range,
                            type
                          )
                        select
                          coalesce(alias, e_alias, null),
                          coalesce(
                            auto_renew_account_id,
                            e_auto_renew_account_id,
                            null
                          ),
                          coalesce(auto_renew_period, e_auto_renew_period, null),
                          case
                            when coalesce(e_type, type) in ('ACCOUNT', 'CONTRACT') then coalesce(e_balance, 0) + coalesce(balance, 0)
                            when e_balance is not null then e_balance + coalesce(balance, 0)
                          end,
                          coalesce(balance_timestamp, e_balance_timestamp, null),
                          coalesce(created_timestamp, e_created_timestamp, null),
                          coalesce(decline_reward, e_decline_reward, false),
                          coalesce(deleted, e_deleted, null),
                          case
                            when coalesce(e_type, type) = 'ACCOUNT' then coalesce(ethereum_nonce, e_ethereum_nonce, 0)
                            else coalesce(ethereum_nonce, e_ethereum_nonce)
                          end,
                          coalesce(evm_address, e_evm_address, null),
                          coalesce(expiration_timestamp, e_expiration_timestamp, null),
                          coalesce(id, e_id, null),
                          coalesce(key, e_key, null),
                          coalesce(
                            max_automatic_token_associations,
                            e_max_automatic_token_associations,
                            null
                          ),
                          coalesce(memo, e_memo, ''),
                          coalesce(num, e_num, null),
                          coalesce(obtainer_id, e_obtainer_id, null),
                          coalesce(permanent_removal, e_permanent_removal, null),
                          coalesce(proxy_account_id, e_proxy_account_id, null),
                          case when public_key is not null and length(public_key) = 0 then null
                            else coalesce(public_key, e_public_key, null)
                          end,
                          coalesce(realm, e_realm, null),
                          coalesce(
                            receiver_sig_required,
                            e_receiver_sig_required,
                            null
                          ),
                          coalesce(shard, e_shard, null),
                          coalesce(stake_period_start, e_stake_period_start, '-1'),
                          coalesce(staked_account_id, e_staked_account_id, null),
                          coalesce(staked_node_id, e_staked_node_id, '-1'),
                          coalesce(timestamp_range, e_timestamp_range, null),
                          coalesce(type, e_type, 'UNKNOWN')
                          from
                            non_history on conflict (id) do
                          update
                          set
                            auto_renew_account_id = excluded.auto_renew_account_id,
                            auto_renew_period = excluded.auto_renew_period,
                            balance = excluded.balance,
                            balance_timestamp = excluded.balance_timestamp,
                            decline_reward = excluded.decline_reward,
                            deleted = excluded.deleted,
                            ethereum_nonce = excluded.ethereum_nonce,
                            expiration_timestamp = excluded.expiration_timestamp,
                            key = excluded.key,
                            max_automatic_token_associations = excluded.max_automatic_token_associations,
                            memo = excluded.memo,
                            obtainer_id = excluded.obtainer_id,
                            permanent_removal = excluded.permanent_removal,
                            proxy_account_id = excluded.proxy_account_id,
                            public_key = excluded.public_key,
                            receiver_sig_required = excluded.receiver_sig_required,
                            stake_period_start = excluded.stake_period_start,
                            staked_account_id = excluded.staked_account_id,
                            staked_node_id = excluded.staked_node_id,
                            timestamp_range = excluded.timestamp_range,
                            type = excluded.type;
                        with existing as (
                          select
                            e.alias as e_alias,
                            e.auto_renew_account_id as e_auto_renew_account_id,
                            e.auto_renew_period as e_auto_renew_period,
                            e.balance as e_balance,
                            e.balance_timestamp as e_balance_timestamp,
                            e.created_timestamp as e_created_timestamp,
                            e.decline_reward as e_decline_reward,
                            e.deleted as e_deleted,
                            e.ethereum_nonce as e_ethereum_nonce,
                            e.evm_address as e_evm_address,
                            e.expiration_timestamp as e_expiration_timestamp,
                            e.id as e_id,
                            e.key as e_key,
                            e.max_automatic_token_associations as e_max_automatic_token_associations,
                            e.memo as e_memo,
                            e.num as e_num,
                            e.obtainer_id as e_obtainer_id,
                            e.permanent_removal as e_permanent_removal,
                            e.proxy_account_id as e_proxy_account_id,
                            e.public_key as e_public_key,
                            e.realm as e_realm,
                            e.receiver_sig_required as e_receiver_sig_required,
                            e.shard as e_shard,
                            e.stake_period_start as e_stake_period_start,
                            e.staked_account_id as e_staked_account_id,
                            e.staked_node_id as e_staked_node_id,
                            e.timestamp_range as e_timestamp_range,
                            e.type as e_type,
                            t.*
                          from
                            entity_temp t
                            left join entity e on e.id = t.id
                          where
                            t.timestamp_range is not null
                        ),
                        existing_history as (
                          insert into
                            entity_history (
                              alias,
                              auto_renew_account_id,
                              auto_renew_period,
                              balance,
                              balance_timestamp,
                              created_timestamp,
                              decline_reward,
                              deleted,
                              ethereum_nonce,
                              evm_address,
                              expiration_timestamp,
                              id,
                              key,
                              max_automatic_token_associations,
                              memo,
                              num,
                              obtainer_id,
                              permanent_removal,
                              proxy_account_id,
                              public_key,
                              realm,
                              receiver_sig_required,
                              shard,
                              stake_period_start,
                              staked_account_id,
                              staked_node_id,
                              timestamp_range,
                              type
                            )
                          select
                            distinct on (id) e_alias,
                            e_auto_renew_account_id,
                            e_auto_renew_period,
                            e_balance,
                            e_balance_timestamp,
                            e_created_timestamp,
                            e_decline_reward,
                            e_deleted,
                            e_ethereum_nonce,
                            e_evm_address,
                            e_expiration_timestamp,
                            e_id,
                            e_key,
                            e_max_automatic_token_associations,
                            e_memo,
                            e_num,
                            e_obtainer_id,
                            e_permanent_removal,
                            e_proxy_account_id,
                            e_public_key,
                            e_realm,
                            e_receiver_sig_required,
                            e_shard,
                            e_stake_period_start,
                            e_staked_account_id,
                            e_staked_node_id,
                            int8range(lower(e_timestamp_range), lower(timestamp_range)) as timestamp_range,
                            e_type
                          from
                            existing
                          where
                            e_timestamp_range is not null
                            and timestamp_range is not null
                          order by
                            id,
                            timestamp_range asc
                        ),
                        temp_history as (
                          insert into
                            entity_history (
                              alias,
                              auto_renew_account_id,
                              auto_renew_period,
                              balance,
                              balance_timestamp,
                              created_timestamp,
                              decline_reward,
                              deleted,
                              ethereum_nonce,
                              evm_address,
                              expiration_timestamp,
                              id,
                              key,
                              max_automatic_token_associations,
                              memo,
                              num,
                              obtainer_id,
                              permanent_removal,
                              proxy_account_id,
                              public_key,
                              realm,
                              receiver_sig_required,
                              shard,
                              stake_period_start,
                              staked_account_id,
                              staked_node_id,
                              timestamp_range,
                              type
                            )
                          select
                            distinct coalesce(e_alias, alias, null),
                            coalesce(
                              auto_renew_account_id,
                              e_auto_renew_account_id,
                              null
                            ),
                            coalesce(auto_renew_period, e_auto_renew_period, null),
                            case
                              when coalesce(e_type, type) in ('ACCOUNT', 'CONTRACT') then coalesce(e_balance, 0) + coalesce(balance, 0)
                              when e_balance is not null then e_balance + coalesce(balance, 0)
                            end,
                            coalesce(balance_timestamp, e_balance_timestamp, null),
                            coalesce(e_created_timestamp, created_timestamp, null),
                            coalesce(decline_reward, e_decline_reward, false),
                            coalesce(deleted, e_deleted, null),
                            case
                              when coalesce(e_type, type) = 'ACCOUNT' then coalesce(ethereum_nonce, e_ethereum_nonce, 0)
                              else coalesce(ethereum_nonce, e_ethereum_nonce)
                            end,
                            coalesce(e_evm_address, evm_address, null),
                            coalesce(expiration_timestamp, e_expiration_timestamp, null),
                            coalesce(e_id, id, null),
                            coalesce(key, e_key, null),
                            coalesce(
                              max_automatic_token_associations,
                              e_max_automatic_token_associations,
                              null
                            ),
                            coalesce(memo, e_memo, ''),
                            coalesce(e_num, num, null),
                            coalesce(obtainer_id, e_obtainer_id, null),
                            coalesce(permanent_removal, e_permanent_removal, null),
                            coalesce(proxy_account_id, e_proxy_account_id, null),
                            case when public_key is not null and length(public_key) = 0 then null
                              else coalesce(public_key, e_public_key, null)
                            end,
                            coalesce(e_realm, realm, null),
                            coalesce(
                              receiver_sig_required,
                              e_receiver_sig_required,
                              null
                            ),
                            coalesce(e_shard, shard, null),
                            coalesce(stake_period_start, e_stake_period_start, '-1'),
                            coalesce(staked_account_id, e_staked_account_id, null),
                            coalesce(staked_node_id, e_staked_node_id, '-1'),
                            coalesce(timestamp_range, e_timestamp_range, null),
                            coalesce(type, e_type, 'UNKNOWN')
                          from
                            existing
                          where
                            upper(timestamp_range) is not null
                        )
                        insert into
                          entity (
                            alias,
                            auto_renew_account_id,
                            auto_renew_period,
                            balance,
                            balance_timestamp,
                            created_timestamp,
                            decline_reward,
                            deleted,
                            ethereum_nonce,
                            evm_address,
                            expiration_timestamp,
                            id,
                            key,
                            max_automatic_token_associations,
                            memo,
                            num,
                            obtainer_id,
                            permanent_removal,
                            proxy_account_id,
                            public_key,
                            realm,
                            receiver_sig_required,
                            shard,
                            stake_period_start,
                            staked_account_id,
                            staked_node_id,
                            timestamp_range,
                            type
                          )
                        select
                          coalesce(alias, e_alias, null),
                          coalesce(
                            auto_renew_account_id,
                            e_auto_renew_account_id,
                            null
                          ),
                          coalesce(auto_renew_period, e_auto_renew_period, null),
                          case
                            when coalesce(e_type, type) in ('ACCOUNT', 'CONTRACT') then coalesce(e_balance, 0) + coalesce(balance, 0)
                            when e_balance is not null then e_balance + coalesce(balance, 0)
                          end,
                          coalesce(balance_timestamp, e_balance_timestamp, null),
                          coalesce(created_timestamp, e_created_timestamp, null),
                          coalesce(decline_reward, e_decline_reward, false),
                          coalesce(deleted, e_deleted, null),
                          case
                            when coalesce(e_type, type) = 'ACCOUNT' then coalesce(ethereum_nonce, e_ethereum_nonce, 0)
                            else coalesce(ethereum_nonce, e_ethereum_nonce)
                          end,
                          coalesce(evm_address, e_evm_address, null),
                          coalesce(expiration_timestamp, e_expiration_timestamp, null),
                          coalesce(id, e_id, null),
                          coalesce(key, e_key, null),
                          coalesce(
                            max_automatic_token_associations,
                            e_max_automatic_token_associations,
                            null
                          ),
                          coalesce(memo, e_memo, ''),
                          coalesce(num, e_num, null),
                          coalesce(obtainer_id, e_obtainer_id, null),
                          coalesce(permanent_removal, e_permanent_removal, null),
                          coalesce(proxy_account_id, e_proxy_account_id, null),
                          case when public_key is not null and length(public_key) = 0 then null
                            else coalesce(public_key, e_public_key, null)
                          end,
                          coalesce(realm, e_realm, null),
                          coalesce(
                            receiver_sig_required,
                            e_receiver_sig_required,
                            null
                          ),
                          coalesce(shard, e_shard, null),
                          coalesce(stake_period_start, e_stake_period_start, '-1'),
                          coalesce(staked_account_id, e_staked_account_id, null),
                          coalesce(staked_node_id, e_staked_node_id, '-1'),
                          coalesce(timestamp_range, e_timestamp_range, null),
                          coalesce(type, e_type, 'UNKNOWN')
                        from
                          existing
                        where
                          timestamp_range is not null
                          and upper(timestamp_range) is null on conflict (id) do
                        update
                        set
                          auto_renew_account_id = excluded.auto_renew_account_id,
                          auto_renew_period = excluded.auto_renew_period,
                          balance = excluded.balance,
                          balance_timestamp = excluded.balance_timestamp,
                          decline_reward = excluded.decline_reward,
                          deleted = excluded.deleted,
                          ethereum_nonce = excluded.ethereum_nonce,
                          expiration_timestamp = excluded.expiration_timestamp,
                          key = excluded.key,
                          max_automatic_token_associations = excluded.max_automatic_token_associations,
                          memo = excluded.memo,
                          obtainer_id = excluded.obtainer_id,
                          permanent_removal = excluded.permanent_removal,
                          proxy_account_id = excluded.proxy_account_id,
                          public_key = excluded.public_key,
                          receiver_sig_required = excluded.receiver_sig_required,
                          stake_period_start = excluded.stake_period_start,
                          staked_account_id = excluded.staked_account_id,
                          staked_node_id = excluded.staked_node_id,
                          timestamp_range = excluded.timestamp_range,
                          type = excluded.type;
                        """;

        var generator = factory.get(Entity.class);
        assertThat(generator).isInstanceOf(GenericUpsertQueryGenerator.class);
        assertThat(format(generator.getUpsertQuery()))
                .isEqualTo(format(sql))
                .doesNotContain(" and coalesce(e_created_timestamp, created_timestamp) is not null");
    }

    @Test
    void getUpsertQueryNoHistory() {
        var sql = """
                with existing as (
                  select
                    e.consensus_timestamp as e_consensus_timestamp,
                    e.creator_account_id as e_creator_account_id,
                    e.executed_timestamp as e_executed_timestamp,
                    e.expiration_time as e_expiration_time,
                    e.payer_account_id as e_payer_account_id,
                    e.schedule_id as e_schedule_id,
                    e.transaction_body as e_transaction_body,
                    e.wait_for_expiry as e_wait_for_expiry,
                    t.*
                  from
                    schedule_temp t
                    left join schedule e on e.schedule_id = t.schedule_id
                )
                insert into
                  schedule (
                    consensus_timestamp,
                    creator_account_id,
                    executed_timestamp,
                    expiration_time,
                    payer_account_id,
                    schedule_id,
                    transaction_body,
                    wait_for_expiry
                  )
                select
                  coalesce(consensus_timestamp, e_consensus_timestamp, null),
                  coalesce(creator_account_id, e_creator_account_id, null),
                  coalesce(executed_timestamp, e_executed_timestamp, null),
                  coalesce(expiration_time, e_expiration_time, null),
                  coalesce(payer_account_id, e_payer_account_id, null),
                  coalesce(schedule_id, e_schedule_id, null),
                  coalesce(transaction_body, e_transaction_body, null),
                  coalesce(wait_for_expiry, e_wait_for_expiry, false)
                from
                  existing
                where
                  coalesce(consensus_timestamp, e_consensus_timestamp) is not null on conflict (schedule_id) do
                update
                set
                  executed_timestamp = excluded.executed_timestamp""";

        var generator = factory.get(Schedule.class);
        assertThat(generator).isInstanceOf(GenericUpsertQueryGenerator.class);
        assertThat(format(generator.getUpsertQuery())).isEqualTo(format(sql));
    }

    @Test
    void skipPartialUpdate() {
        var generator = factory.get(Token.class);
        assertThat(generator).isInstanceOf(GenericUpsertQueryGenerator.class);
        assertThat(format(generator.getUpsertQuery()))
                .contains("and coalesce(e_created_timestamp, created_timestamp) is not null");
    }

    private String format(String sql) {
        return SQL_FORMATTER.format(sql);
    }
}
