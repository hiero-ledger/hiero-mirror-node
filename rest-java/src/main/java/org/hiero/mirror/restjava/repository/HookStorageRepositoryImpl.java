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
class HookStorageRepositoryImpl implements HookStorageRepositoryCustom {

    private final NamedParameterJdbcOperations jdbcOperations;
    private final JdbcConverter jdbcConverter;
    private final RelationalMappingContext mappingContext;

    @Override
    public List<HookStorage> findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
            long ownerId, long hookId, Collection<byte[]> keys, Pageable pageable) {
        var sql = """
                select created_timestamp,
                       deleted,
                       owner_id,
                       hook_id,
                       key,
                       modified_timestamp,
                       value
                from hook_storage
                where owner_id = :ownerId
                  and hook_id = :hookId
                  and key in (:keys)
                  and deleted = false""" + sortAndLimit(pageable);
        var params = new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("hookId", hookId)
                .addValue("keys", keys);
        return jdbcOperations.query(sql, params, rowMapper());
    }

    @Override
    public List<HookStorage> findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
            long ownerId, long hookId, byte[] fromKey, byte[] toKey, Pageable pageable) {
        var sql = """
                select created_timestamp,
                       deleted,
                       owner_id,
                       hook_id,
                       key,
                       modified_timestamp,
                       value
                from hook_storage
                where owner_id = :ownerId
                  and hook_id = :hookId
                  and key between :fromKey and :toKey
                  and deleted = false""" + sortAndLimit(pageable);
        var params = new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("hookId", hookId)
                .addValue("fromKey", fromKey)
                .addValue("toKey", toKey);
        return jdbcOperations.query(sql, params, rowMapper());
    }

    @SuppressWarnings("unchecked")
    private RowMapper<HookStorage> rowMapper() {
        RelationalPersistentEntity<HookStorage> entity =
                (RelationalPersistentEntity<HookStorage>) mappingContext.getRequiredPersistentEntity(HookStorage.class);
        return new EntityRowMapper<>(entity, jdbcConverter);
    }

    private static String sortAndLimit(Pageable pageable) {
        var sb = new StringBuilder();
        var sort = pageable.getSort();
        if (sort.isSorted()) {
            sb.append(" order by ");
            sb.append(sort.stream().map(HookStorageRepositoryImpl::orderClause).collect(Collectors.joining(", ")));
        }
        sb.append(" limit ").append(pageable.getPageSize()).append(" offset ").append(pageable.getOffset());
        return sb.toString();
    }

    private static String orderClause(Sort.Order order) {
        if (!Constants.KEY.equals(order.getProperty())) {
            throw new UnsupportedOperationException("Unsupported sort property: " + order.getProperty());
        }
        return "key " + order.getDirection().name();
    }
}
