// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;
import static org.assertj.core.api.Assertions.withinPercentage;

import com.google.common.base.Stopwatch;
import com.google.common.base.Suppliers;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.math3.util.Pair;
import org.hiero.mirror.monitor.MonitorProperties;
import org.hiero.mirror.monitor.ScenarioStatus;
import org.hiero.mirror.monitor.publish.PublishProperties;
import org.hiero.mirror.monitor.publish.PublishRequest;
import org.hiero.mirror.monitor.publish.PublishScenario;
import org.hiero.mirror.monitor.publish.PublishScenarioProperties;
import org.hiero.mirror.monitor.publish.transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CompositeTransactionGeneratorTest {

    private PublishScenarioProperties publishScenarioProperties1;
    private PublishScenarioProperties publishScenarioProperties2;
    private PublishProperties properties;
    private Supplier<CompositeTransactionGenerator> supplier;
    private double totalTps;

    @BeforeEach
    void init() {
        publishScenarioProperties1 = new PublishScenarioProperties();
        publishScenarioProperties1.setName("test1");
        publishScenarioProperties1.setProperties(Map.of("topicId", "0.0.1000"));
        publishScenarioProperties1.setTps(750);
        publishScenarioProperties1.setType(TransactionType.CONSENSUS_SUBMIT_MESSAGE);
        totalTps = publishScenarioProperties1.getTps();

        publishScenarioProperties2 = new PublishScenarioProperties();
        publishScenarioProperties2.setName("test2");
        publishScenarioProperties2.setTps(250);
        publishScenarioProperties2.setType(TransactionType.ACCOUNT_CREATE);
        totalTps += publishScenarioProperties2.getTps();

        properties = new PublishProperties();
        properties.getScenarios().put(publishScenarioProperties1.getName(), publishScenarioProperties1);
        properties.getScenarios().put(publishScenarioProperties2.getName(), publishScenarioProperties2);
        supplier = Suppliers.memoize(() -> new CompositeTransactionGenerator(
                p -> p,
                new MonitorProperties(),
                p -> p.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
                properties));

        prepare();
    }

    @ParameterizedTest(name = "batch request count {0}")
    @ValueSource(ints = {0, -1})
    void batchRequestDefault(int count) {
        CompositeTransactionGenerator generator = supplier.get();

        List<PublishRequest> publishRequests = generator.next(count);
        assertThat(publishRequests).hasSize(generator.batchSize.get());
    }

    @Test
    void batchRequestTwo() {
        CompositeTransactionGenerator generator = supplier.get();

        List<PublishRequest> publishRequests = generator.next(2);
        assertThat(publishRequests).hasSize(2);
    }

    @Test
    void batchRequestGreaterThanDefault() {
        CompositeTransactionGenerator generator = supplier.get();
        int count = generator.batchSize.get() + 1;

        List<PublishRequest> publishRequests = generator.next(count);
        assertThat(publishRequests).hasSize(count);
    }

    @Test
    void distribution() {
        properties.setWarmupPeriod(Duration.ZERO);
        CompositeTransactionGenerator generator = supplier.get();
        assertThat(generator.distribution.get().getPmf())
                .hasSize(properties.getScenarios().size())
                .extracting(Pair::getValue)
                .containsExactly(0.75, 0.25);

        Multiset<TransactionType> types = HashMultiset.create();
        double seconds = 5;
        for (int i = 0; i < totalTps * seconds; ) {
            List<PublishRequest> requests = generator.next();
            requests.stream()
                    .map(r -> r.getScenario().getProperties().getType())
                    .forEach(types::add);
            i += requests.size();
        }

        for (PublishScenarioProperties publishScenarioProperties :
                properties.getScenarios().values()) {
            assertThat(types.count(publishScenarioProperties.getType()))
                    .isNotNegative()
                    .isNotZero()
                    .isCloseTo((int) (publishScenarioProperties.getTps() * seconds), withinPercentage(10));
        }
    }

    @Test
    void disabledScenario() {
        publishScenarioProperties1.setEnabled(false);
        CompositeTransactionGenerator generator = supplier.get();
        assertThat(generator.distribution.get().getPmf())
                .hasSize(1)
                .extracting(Pair::getValue)
                .containsExactly(1.0);
    }

    @Test
    void noScenario() {
        properties.getScenarios().clear();
        assertInactive();
    }

    @Test
    void noWarmup() {
        properties.setWarmupPeriod(Duration.ZERO);
        CompositeTransactionGenerator generator = supplier.get();
        Stopwatch stopwatch = Stopwatch.createStarted();
        double seconds = 4.0;
        int total = (int) (totalTps * seconds);
        for (int i = 0; i < total; i++) {
            generator.next();
        }

        assertThat(stopwatch.elapsed().toMillis() * 1.0 / 1000).isCloseTo(seconds, withinPercentage(5));
    }

    @Test
    void publishDisabled() {
        properties.setEnabled(false);
        assertInactive();
    }

    @Test
    void scenariosComplete() {
        properties.getScenarios().remove(publishScenarioProperties2.getName());
        publishScenarioProperties1.setLimit(1L);
        CompositeTransactionGenerator generator = supplier.get();
        List<PublishScenario> scenarios = generator.scenarios().collectList().block();
        assertThat(generator.next()).hasSize(1);
        assertThat(generator.next()).isEmpty();
        assertInactive();
        assertThat(properties.getScenarios().values())
                .extracting(PublishScenarioProperties::isEnabled)
                .containsExactly(false);
        assertThat(scenarios).extracting(PublishScenario::getStatus).containsOnly(ScenarioStatus.COMPLETED);
        assertThat(generator.scenarios().count().block()).isZero();
    }

    @Test
    void warmupBatchRequest() {
        int warmUpSeconds = 4;
        properties.setWarmupPeriod(Duration.ofSeconds(warmUpSeconds));
        CompositeTransactionGenerator generator = supplier.get();

        long begin = System.currentTimeMillis();
        long elapsed = 0;
        long lastElapsed = 0;
        int count = 0;
        List<Integer> counts = new ArrayList<>();
        while (elapsed < (warmUpSeconds + 3) * 1000) {
            List<PublishRequest> publishRequests = generator.next(0);
            count += publishRequests.size();

            elapsed = System.currentTimeMillis() - begin;
            if (elapsed - lastElapsed >= 1000) {
                counts.add(count);
                count = 0;
                lastElapsed = elapsed;
            }
        }

        List<Integer> warmupCounts = counts.subList(0, warmUpSeconds);
        List<Integer> stableCounts = counts.subList(warmUpSeconds, counts.size());
        assertThat(warmupCounts).isSorted().allSatisfy(n -> assertThat(n * 1.0).isLessThan(totalTps));
        assertThat(stableCounts).isNotEmpty().allSatisfy(n -> assertThat(n * 1.0)
                .isCloseTo(totalTps, withinPercentage(5)));
    }

    @Test
    @Timeout(10)
    void scenariosDurationAfterFirstFinish() {
        publishScenarioProperties1.setDuration(Duration.ofSeconds(3));
        publishScenarioProperties2.setDuration(Duration.ofSeconds(5));
        properties.setWarmupPeriod(Duration.ZERO);
        CompositeTransactionGenerator generator = supplier.get();

        long begin = System.currentTimeMillis();
        do {
            generator.next(0);
        } while (System.currentTimeMillis() - begin <= 3100);

        assertThat(generator.transactionGenerators)
                .hasSize(1)
                .first()
                .returns(publishScenarioProperties2, from(ConfigurableTransactionGenerator::getProperties));
    }

    @Test
    @Timeout(10)
    void scenariosDurationAfterBothFinish() {
        publishScenarioProperties1.setDuration(Duration.ofSeconds(3));
        publishScenarioProperties2.setDuration(Duration.ofSeconds(5));
        properties.setWarmupPeriod(Duration.ZERO);
        CompositeTransactionGenerator generator = supplier.get();

        long begin = System.currentTimeMillis();
        while (true) {
            List<PublishRequest> publishRequests = generator.next(1);
            if (publishRequests.isEmpty()) {
                break;
            }
        }

        long elapsed = System.currentTimeMillis() - begin;
        assertThat(generator.transactionGenerators).isEmpty();
        assertThat(elapsed).isBetween(4950L, 5100L);
    }

    private void assertInactive() {
        assertThat(supplier.get().rateLimiter.get()).isEqualTo(CompositeTransactionGenerator.INACTIVE_RATE_LIMITER);
    }

    private void prepare() {
        // warmup so in tests the timing will be accurate
        TransactionGenerator generator = Suppliers.synchronizedSupplier(() -> new CompositeTransactionGenerator(
                        p -> p,
                        new MonitorProperties(),
                        p -> p.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
                        properties))
                .get();
        generator.next(0);
    }
}
