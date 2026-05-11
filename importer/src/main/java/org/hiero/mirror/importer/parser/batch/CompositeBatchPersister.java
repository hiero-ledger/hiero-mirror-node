// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.batch;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.domain.transaction.TransactionHash;
import org.hiero.mirror.importer.parser.CommonParserProperties;
import org.hiero.mirror.importer.repository.upsert.UpsertQueryGenerator;
import org.hiero.mirror.importer.repository.upsert.UpsertQueryGeneratorFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.relational.core.mapping.Table;

@Named
@Primary
public class CompositeBatchPersister implements BatchPersister {

    private final Map<Class<?>, BatchPersister> batchPersisters = new ConcurrentHashMap<>();
    private final DataSource dataSource;
    private final MeterRegistry meterRegistry;
    private final CommonParserProperties properties;
    private final UpsertQueryGeneratorFactory upsertQueryGeneratorFactory;

    public CompositeBatchPersister(
            DataSource dataSource,
            MeterRegistry meterRegistry,
            CommonParserProperties properties,
            UpsertQueryGeneratorFactory upsertQueryGeneratorFactory,
            Optional<TransactionHashBatchInserter> transactionHashV1BatchPersister) {
        this.dataSource = dataSource;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
        this.upsertQueryGeneratorFactory = upsertQueryGeneratorFactory;

        transactionHashV1BatchPersister.ifPresent(
                batchPersister -> batchPersisters.put(TransactionHash.class, batchPersister));
    }

    @Override
    public void persist(Collection<? extends Object> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Object item = items.iterator().next();
        if (item == null) {
            throw new UnsupportedOperationException("Object does not support batch insertion: " + item);
        }

        BatchPersister batchPersister = batchPersisters.computeIfAbsent(item.getClass(), this::create);
        batchPersister.persist(items);
    }

    private BatchPersister create(Class<?> domainClass) {
        Table table = AnnotationUtils.findAnnotation(domainClass, Table.class);

        if (table == null) {
            throw new UnsupportedOperationException("Object does not support batch insertion: " + domainClass);
        }

        var entityClass = getEntityClass(domainClass);
        Upsertable upsertable = AnnotationUtils.findAnnotation(domainClass, Upsertable.class);

        if (upsertable != null) {
            UpsertQueryGenerator generator = upsertQueryGeneratorFactory.get(domainClass);
            return new BatchUpserter(entityClass, dataSource, meterRegistry, properties, generator);
        } else {
            return new BatchInserter(entityClass, dataSource, meterRegistry, properties);
        }
    }

    // Finds which parent class has the Table annotation to get an accurate table name
    private Class<?> getEntityClass(Class<?> domainClass) {
        if (domainClass == null || domainClass == Object.class) {
            throw new IllegalStateException("Unable to find Table annotation");
        }

        var table = AnnotationUtils.getAnnotation(domainClass, Table.class);
        if (table != null) {
            return domainClass;
        }

        return getEntityClass(domainClass.getSuperclass());
    }
}
