// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.repository;

import java.util.List;
import java.util.Objects;
import org.springframework.data.jdbc.core.convert.DataAccessStrategy;
import org.springframework.data.jdbc.core.convert.DelegatingDataAccessStrategy;
import org.springframework.data.jdbc.core.convert.Identifier;
import org.springframework.data.jdbc.core.convert.InsertSubject;
import org.springframework.data.relational.core.conversion.IdValueSource;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;

/**
 * Preserves the JPA assigned-id insert semantics the code base was written against: Spring Data JDBC treats a
 * primitive id of zero as absent, omits the id column from the insert and expects the database to generate it. No
 * mirror node table generates ids, so any present id value (including zero) is inserted as provided.
 */
public class AssignedIdDataAccessStrategy extends DelegatingDataAccessStrategy {

    private final RelationalMappingContext mappingContext;

    public AssignedIdDataAccessStrategy(DataAccessStrategy delegate, RelationalMappingContext mappingContext) {
        super(delegate);
        this.mappingContext = mappingContext;
    }

    @Override
    public <T> Object insert(T objectToSave, Class<T> domainType, Identifier identifier, IdValueSource idValueSource) {
        var id = assignedId(objectToSave, domainType, idValueSource);
        if (id == null) {
            return super.insert(objectToSave, domainType, identifier, idValueSource);
        }

        super.insert(objectToSave, domainType, identifier, IdValueSource.PROVIDED);
        return id;
    }

    @Override
    public <T> Object[] insert(
            List<InsertSubject<T>> insertSubjects, Class<T> domainType, IdValueSource idValueSource) {
        var ids = insertSubjects.stream()
                .map(subject -> assignedId(subject.getInstance(), domainType, idValueSource))
                .toList();
        if (ids.isEmpty() || ids.stream().anyMatch(Objects::isNull)) {
            return super.insert(insertSubjects, domainType, idValueSource);
        }

        super.insert(insertSubjects, domainType, IdValueSource.PROVIDED);
        return ids.toArray();
    }

    private Object assignedId(Object instance, Class<?> domainType, IdValueSource idValueSource) {
        if (idValueSource != IdValueSource.GENERATED) {
            return null;
        }

        var entity = mappingContext.getRequiredPersistentEntity(domainType);
        if (!entity.hasIdProperty()) {
            return null;
        }

        return entity.getIdentifierAccessor(instance).getIdentifier();
    }
}
