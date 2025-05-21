// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.batch;

import static org.hiero.mirror.common.domain.transaction.TransactionHash.V1_SHARD_COUNT;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import lombok.CustomLog;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.transaction.TransactionHash;
import org.hiero.mirror.importer.exception.ParserException;
import org.hiero.mirror.importer.parser.CommonParserProperties;
import org.springframework.context.annotation.Profile;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@CustomLog
@Named
@Profile("!v2")
public class TransactionHashBatchInserter implements BatchPersister {
    private final Map<Integer, BatchInserter> shardBatchInserters;
    private final String tableName;
    private final Scheduler scheduler;
    private final TransactionHashTxManager transactionManager;

    public TransactionHashBatchInserter(
            DataSource dataSource,
            MeterRegistry meterRegistry,
            CommonParserProperties commonParserProperties,
            TransactionHashTxManager transactionHashTxManager) {
        this.tableName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, TransactionHash.class.getSimpleName());
        this.scheduler = Schedulers.newParallel(this.tableName, 8);
        this.transactionManager = transactionHashTxManager;

        this.shardBatchInserters = IntStream.range(0, V1_SHARD_COUNT)
                .boxed()
                .collect(ImmutableMap.toImmutableMap(
                        Function.identity(),
                        shard -> new BatchInserter(
                                TransactionHash.class,
                                dataSource,
                                meterRegistry,
                                commonParserProperties,
                                String.format("%s_%02d", tableName, shard))));
    }

    @Override
    public void persist(Collection<?> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        try {
            Stopwatch stopwatch = Stopwatch.createStarted();

            // After parser transaction completes, process all transactions for shards
            transactionManager.initialize(items, this.tableName);

            Map<Integer, List<TransactionHash>> shardedItems = items.stream()
                    .map(TransactionHash.class::cast)
                    .filter(TransactionHash::hashIsValid)
                    .collect(Collectors.groupingBy(TransactionHash::calculateV1Shard));

            Mono.when(shardedItems.entrySet().stream().map(this::processShard).toList())
                    .block();

            log.info(
                    "Copied {} rows from {} shards to {} table in {}",
                    items.size(),
                    shardedItems.size(),
                    this.tableName,
                    stopwatch);
        } catch (Exception e) {
            throw new ParserException(
                    String.format("Error copying %d items to table %s", items.size(), this.tableName));
        }
    }

    private Mono<Void> processShard(Map.Entry<Integer, List<TransactionHash>> shardData) {
        return Mono.just(shardData)
                .doOnNext(this::persist)
                .subscribeOn(scheduler)
                .then();
    }

    @SneakyThrows
    private void persist(Map.Entry<Integer, List<TransactionHash>> data) {
        var threadState = transactionManager.updateAndGetThreadState(data.getKey());
        shardBatchInserters.get(data.getKey()).persistItems(data.getValue(), threadState.getConnection());
    }

    @VisibleForTesting
    Map<String, TransactionHashTxManager.ThreadState> getThreadConnections() {
        return transactionManager.getThreadConnections();
    }
}
