// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.restjava.common.Constants;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jdbc.core.convert.EntityRowMapper;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class HookStorageChangeRepositoryImpl implements HookStorageChangeRepositoryCustom {

    private final NamedParameterJdbcOperations jdbcOperations;
    private final JdbcConverter jdbcConverter;
    private final RelationalMappingContext mappingContext;

    @Override
    public List<HookStorage> findByKeyBetweenAndTimestampBetween(
            long ownerId,
            long hookId,
            byte[] keyLowerBound,
            byte[] keyUpperBound,
            long timestampLowerBound,
            long timestampUpperBound,
            Pageable pageable) {
        var keyDirection = pageable.getSort().stream()
                .filter(o -> Constants.KEY.equals(o.getProperty()))
                .findFirst()
                .map(Sort.Order::getDirection)
                .orElse(Sort.Direction.ASC);
        var inner = "select distinct on (key) owner_id, hook_id, key, value_written as \"value\", "
                + "consensus_timestamp as \"modified_timestamp\", consensus_timestamp as \"consensus_timestamp\", "
                + "0 as \"created_timestamp\", "
                + "(value_written is null or length(value_written) = 0) as \"deleted\" "
                + "from hook_storage_change "
                + "where owner_id = :ownerId and hook_id = :hookId "
                + "and key >= :keyLowerBound and key <= :keyUpperBound "
                + "and consensus_timestamp between :timestampLowerBound and :timestampUpperBound "
                + "order by key "
                + keyDirection.name()
                + ", consensus_timestamp desc";
        var sql = "select * from (" + inner + ") r" + outerSortAndLimit(pageable);
        var params = new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("hookId", hookId)
                .addValue("keyLowerBound", keyLowerBound)
                .addValue("keyUpperBound", keyUpperBound)
                .addValue("timestampLowerBound", timestampLowerBound)
                .addValue("timestampUpperBound", timestampUpperBound);
        return jdbcOperations.query(sql, params, rowMapper());
    }

    @Override
    public List<HookStorage> findByKeyInAndTimestampBetween(
            long ownerId,
            long hookId,
            Collection<byte[]> keys,
            long timestampLowerBound,
            long timestampUpperBound,
            Pageable pageable) {
        var keyDirection = pageable.getSort().stream()
                .filter(o -> Constants.KEY.equals(o.getProperty()))
                .findFirst()
                .map(Sort.Order::getDirection)
                .orElse(Sort.Direction.ASC);
        var inner = "select distinct on (key) owner_id, hook_id, key, value_written as \"value\", "
                + "consensus_timestamp as \"modified_timestamp\", consensus_timestamp as \"consensus_timestamp\", "
                + "0 as \"created_timestamp\", "
                + "(value_written is null or length(value_written) = 0) as \"deleted\" "
                + "from hook_storage_change "
                + "where owner_id = :ownerId and hook_id = :hookId "
                + "and key in (:keys) "
                + "and consensus_timestamp between :timestampLowerBound and :timestampUpperBound "
                + "order by key "
                + keyDirection.name()
                + ", consensus_timestamp desc";
        var sql = "select * from (" + inner + ") r" + outerSortAndLimit(pageable);
        var params = new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("hookId", hookId)
                .addValue("keys", keys)
                .addValue("timestampLowerBound", timestampLowerBound)
                .addValue("timestampUpperBound", timestampUpperBound);
        return jdbcOperations.query(sql, params, rowMapper());
    }

    @SuppressWarnings("unchecked")
    private RowMapper<HookStorage> rowMapper() {
        RelationalPersistentEntity<HookStorage> entity =
                (RelationalPersistentEntity<HookStorage>) mappingContext.getRequiredPersistentEntity(HookStorage.class);
        return new EntityRowMapper<>(entity, jdbcConverter);
    }

    private static String outerSortAndLimit(Pageable pageable) {
        var sb = new StringBuilder();
        var sort = pageable.getSort();
        if (sort.isSorted()) {
            sb.append(" order by ");
            sb.append(sort.stream()
                    .map(HookStorageChangeRepositoryImpl::outerOrderClause)
                    .collect(Collectors.joining(", ")));
        }
        sb.append(" limit ").append(pageable.getPageSize()).append(" offset ").append(pageable.getOffset());
        return sb.toString();
    }

    private static String outerOrderClause(Sort.Order order) {
        return switch (order.getProperty()) {
            case Constants.KEY -> "r.key " + order.getDirection().name();
            case Constants.CONSENSUS_TIMESTAMP ->
                "r.consensus_timestamp " + order.getDirection().name();
            default -> throw new UnsupportedOperationException("Unsupported sort property: " + order.getProperty());
        };
    }
}
