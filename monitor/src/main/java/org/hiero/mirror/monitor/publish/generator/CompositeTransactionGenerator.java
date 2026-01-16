// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.generator;

import com.google.common.util.concurrent.RateLimiter;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.CustomLog;
import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.util.Pair;
import org.hiero.mirror.monitor.MonitorProperties;
import org.hiero.mirror.monitor.expression.ExpressionConverter;
import org.hiero.mirror.monitor.properties.ScenarioPropertiesAggregator;
import org.hiero.mirror.monitor.publish.PublishProperties;
import org.hiero.mirror.monitor.publish.PublishRequest;
import org.hiero.mirror.monitor.publish.PublishScenario;
import org.hiero.mirror.monitor.publish.PublishScenarioProperties;
import reactor.core.publisher.Flux;

@CustomLog
@Named
public class CompositeTransactionGenerator implements TransactionGenerator {

    static final RateLimiter INACTIVE_RATE_LIMITER;

    static {
        INACTIVE_RATE_LIMITER = RateLimiter.create(Double.MIN_NORMAL);
        // the first acquire always succeeds, so do this so tps=Double.MIN_NORMAL won't acquire
        INACTIVE_RATE_LIMITER.acquire();
    }

    private final PublishProperties properties;
    final AtomicReference<EnumeratedDistribution<TransactionGenerator>> distribution = new AtomicReference<>();
    final AtomicReference<RateLimiter> rateLimiter = new AtomicReference<>();
    final List<ConfigurableTransactionGenerator> transactionGenerators;
    final AtomicInteger batchSize = new AtomicInteger(1);

    public CompositeTransactionGenerator(
            ExpressionConverter expressionConverter,
            MonitorProperties monitorProperties,
            ScenarioPropertiesAggregator scenarioPropertiesAggregator,
            PublishProperties properties) {
        this.properties = properties;
        this.transactionGenerators = properties.getScenarios().values().stream()
                .filter(PublishScenarioProperties::isEnabled)
                .map(scenarioProperties -> new ConfigurableTransactionGenerator(
                        expressionConverter, monitorProperties, scenarioPropertiesAggregator, scenarioProperties))
                .collect(Collectors.toList());
        rebuild();
    }

    @Override
    public List<PublishRequest> next(int count) {
        int permits = count > 0 ? count : batchSize.get();
        rateLimiter.get().acquire(permits);

        List<PublishRequest> publishRequests = new ArrayList<>();
        int i = 0;
        while (i < permits) {
            try {
                TransactionGenerator transactionGenerator = distribution.get().sample();
                publishRequests.addAll(transactionGenerator.next());
                i++;
            } catch (ScenarioException e) {
                log.warn(e.getMessage());
                e.getScenario().getProperties().setEnabled(false);
                e.getScenario().onComplete();
                rebuild();
                if (rateLimiter.get().equals(INACTIVE_RATE_LIMITER)) {
                    break;
                }
            } catch (Exception e) {
                log.error("Unable to generate a transaction", e);
                throw e;
            }
        }

        return publishRequests;
    }

    @Override
    public Flux<PublishScenario> scenarios() {
        return Flux.fromIterable(transactionGenerators).flatMap(TransactionGenerator::scenarios);
    }

    private synchronized void rebuild() {
        double total = 0.0;
        List<Pair<TransactionGenerator, Double>> pairs = new ArrayList<>();
        for (Iterator<ConfigurableTransactionGenerator> iter = transactionGenerators.iterator(); iter.hasNext(); ) {
            ConfigurableTransactionGenerator transactionGenerator = iter.next();
            PublishScenarioProperties publishScenarioProperties = transactionGenerator.getProperties();
            if (publishScenarioProperties.isEnabled()) {
                total += publishScenarioProperties.getTps();
                pairs.add(Pair.create(transactionGenerator, publishScenarioProperties.getTps()));
            } else {
                iter.remove();
            }
        }

        if (!properties.isEnabled() || pairs.isEmpty() || total == 0.0) {
            batchSize.set(1);
            distribution.set(null);
            rateLimiter.set(INACTIVE_RATE_LIMITER);
            log.info("Publishing is disabled");
            return;
        }

        for (ConfigurableTransactionGenerator transactionGenerator : transactionGenerators) {
            log.info("Activated scenario: {}", transactionGenerator.getProperties());
        }

        batchSize.set(Math.max(1, (int) Math.ceil(total / properties.getBatchDivisor())));
        distribution.set(new EnumeratedDistribution<>(pairs));

        RateLimiter current = rateLimiter.get();
        if (current != null) {
            current.setRate(total);
        } else {
            rateLimiter.set(getRateLimiter(total, properties.getWarmupPeriod()));
        }
    }

    private RateLimiter getRateLimiter(double tps, Duration warmupPeriod) {
        if (warmupPeriod.equals(Duration.ZERO)) {
            return RateLimiter.create(tps);
        }

        return RateLimiter.create(tps, properties.getWarmupPeriod());
    }
}
