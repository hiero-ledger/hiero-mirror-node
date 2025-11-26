// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.hiero.mirror.restjava.jooq.domain.Tables.ACCOUNT_BALANCE;
import static org.hiero.mirror.restjava.jooq.domain.Tables.ENTITY;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.sum;
import static org.jooq.impl.DSL.val;

import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.restjava.config.NetworkProperties;
import org.hiero.mirror.restjava.dto.NetworkSupply;
import org.hiero.mirror.restjava.service.Bound;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;

@Named
@RequiredArgsConstructor
final class NetworkSupplyRepositoryCustomImpl implements NetworkSupplyRepositoryCustom {

    private final CommonProperties commonProperties;
    private final DSLContext dslContext;
    private final NetworkProperties networkProperties;

    @Override
    public NetworkSupply getSupply(Bound timestamp) {
        if (timestamp.isEmpty()) {
            return getSupplyFromEntity();
        }
        return getSupplyFromAccountBalance(timestamp);
    }

    private NetworkSupply getSupplyFromEntity() {
        var condition = buildUnreleasedSupplyAccountCondition(
                ENTITY.ID, commonProperties.getShard(), commonProperties.getRealm());
        var unreleasedSupplyField = coalesce(sum(ENTITY.BALANCE), val(0L));
        var consensusTimestampField = coalesce(max(ENTITY.BALANCE_TIMESTAMP), val(0L));

        var result = dslContext
                .select(unreleasedSupplyField, consensusTimestampField)
                .from(ENTITY)
                .where(condition)
                .fetchOne();

        return toNetworkSupply(Objects.requireNonNull(result), unreleasedSupplyField, consensusTimestampField);
    }

    private NetworkSupply getSupplyFromAccountBalance(Bound timestamp) {
        var minTimestamp = timestamp.getAdjustedLowerRangeValue();
        var maxTimestamp = timestamp.adjustUpperBound();

        // Optimize the query by limiting to at most two most recent partitions
        var optimalLowerBound = getFirstDayOfMonth(maxTimestamp, -1);
        minTimestamp = Math.max(minTimestamp, optimalLowerBound);

        var accountCondition = buildUnreleasedSupplyAccountCondition(
                ACCOUNT_BALANCE.ACCOUNT_ID, commonProperties.getShard(), commonProperties.getRealm());
        var timestampCondition = ACCOUNT_BALANCE
                .CONSENSUS_TIMESTAMP
                .between(minTimestamp, maxTimestamp)
                .and(accountCondition);

        // Use PostgreSQL DISTINCT ON to get latest balance per account
        var accountBalances = DSL.name("account_balances");
        var unreleasedSupplyField = coalesce(sum(DSL.field("balance", Long.class)), val(0L));
        var consensusTimestampField = max(DSL.field("consensus_timestamp", Long.class));

        var result = dslContext
                .with(accountBalances)
                .as(select(ACCOUNT_BALANCE.BALANCE, ACCOUNT_BALANCE.CONSENSUS_TIMESTAMP)
                        .distinctOn(ACCOUNT_BALANCE.ACCOUNT_ID)
                        .from(ACCOUNT_BALANCE)
                        .where(timestampCondition)
                        .orderBy(ACCOUNT_BALANCE.ACCOUNT_ID.asc(), ACCOUNT_BALANCE.CONSENSUS_TIMESTAMP.desc()))
                .select(unreleasedSupplyField, consensusTimestampField)
                .from(accountBalances)
                .fetchOne();

        return toNetworkSupply(Objects.requireNonNull(result), unreleasedSupplyField, consensusTimestampField);
    }

    private Condition buildUnreleasedSupplyAccountCondition(Field<Long> accountField, long shard, long realm) {
        Condition condition = DSL.noCondition();

        for (var range : networkProperties.getUnreleasedSupplyAccounts()) {
            var from = EntityId.of(shard, realm, range.getFrom()).getId();
            var to = EntityId.of(shard, realm, range.getTo()).getId();

            if (from == to) {
                condition = condition.or(accountField.eq(from));
            } else {
                condition = condition.or(accountField.between(from, to));
            }
        }

        return condition;
    }

    private NetworkSupply toNetworkSupply(
            Record result, Field<?> unreleasedSupplyField, Field<?> consensusTimestampField) {
        var unreleasedSupply = new BigInteger(String.valueOf(result.get(unreleasedSupplyField)));
        var consensusTimestamp = result.get(consensusTimestampField);

        // With aggregate queries, result is never null, but consensusTimestamp can be:
        // - null when max() returns null (empty result set without coalesce)
        // - 0 when coalesce defaults to 0 (empty result set with coalesce)
        if (consensusTimestamp == null || consensusTimestamp.equals(0L)) {
            throw new EntityNotFoundException("Network supply not found");
        }

        return NetworkSupply.from(unreleasedSupply, (Long) consensusTimestamp);
    }

    private long getFirstDayOfMonth(long timestamp, int monthOffset) {
        // Convert nanoseconds to epoch second
        var instant = Instant.ofEpochSecond(timestamp / 1_000_000_000);
        var dateTime = instant.atZone(ZoneOffset.UTC);

        // Get first day of the target month (matches JS: Date.UTC(year, month + offset))
        var firstDay = dateTime.plusMonths(monthOffset).withDayOfMonth(1);
        return firstDay.toLocalDate().atStartOfDay(ZoneOffset.UTC).toEpochSecond() * 1_000_000_000L;
    }
}
