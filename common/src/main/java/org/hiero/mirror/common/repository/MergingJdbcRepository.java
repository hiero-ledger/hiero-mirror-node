// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.repository;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.repository.support.SimpleJdbcRepository;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.transaction.annotation.Transactional;

/**
 * Preserves the JPA save() semantics the code base was written against: new entities (Persistable.isNew() or a null
 * id) are inserted, everything else is merged - updated when the row already exists in the database and inserted
 * otherwise. Spring Data JDBC's default save() decides between insert and update purely from in-memory state, which
 * breaks aggregates with manually assigned ids.
 */
@Transactional(readOnly = true)
public class MergingJdbcRepository<T, ID> extends SimpleJdbcRepository<T, ID> {

    private final JdbcAggregateOperations entityOperations;
    private final PersistentEntity<T, ?> entity;

    public MergingJdbcRepository(
            JdbcAggregateOperations entityOperations, PersistentEntity<T, ?> entity, JdbcConverter converter) {
        super(entityOperations, entity, converter);
        this.entityOperations = entityOperations;
        this.entity = entity;
    }

    @Transactional
    @Override
    public <S extends T> S save(S instance) {
        if (isNew(instance)) {
            return entityOperations.insert(instance);
        }

        var id = entity.getIdentifierAccessor(instance).getRequiredIdentifier();
        return entityOperations.existsById(id, entity.getType())
                ? entityOperations.update(instance)
                : entityOperations.insert(instance);
    }

    @Transactional
    @Override
    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        var result = new ArrayList<S>();
        entities.forEach(instance -> result.add(save(instance)));
        return result;
    }

    private boolean isNew(T instance) {
        if (instance instanceof Persistable<?> persistable) {
            return persistable.isNew();
        }

        return entity.getIdentifierAccessor(instance).getIdentifier() == null;
    }
}
