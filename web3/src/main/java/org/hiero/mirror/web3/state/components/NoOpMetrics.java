// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.components;

import com.swirlds.common.metrics.PlatformMetrics;
import com.swirlds.common.metrics.noop.internal.NoOpMetricsFactory;
import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.MetricConfig;
import com.swirlds.metrics.api.Metrics;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.hiero.consensus.model.node.NodeId;

/**
 * A no-op {@link Metrics} implementation.
 */
@SuppressWarnings("deprecation")
public class NoOpMetrics implements PlatformMetrics {

    private final Map<String /* category */, Map<String /* name */, Metric>> metrics = new HashMap<>();

    private static final NoOpMetricsFactory FACTORY = new NoOpMetricsFactory();

    @Override
    public NodeId getNodeId() {
        return NodeId.of(42L);
    }

    @Override
    public boolean isGlobalMetrics() {
        return false;
    }

    @Override
    public boolean isPlatformMetrics() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized @Nullable Metric getMetric(@Nonnull final String category, @Nonnull final String name) {
        final Map<String, Metric> metricsInCategory = metrics.get(category);
        if (metricsInCategory == null) {
            return null;
        }
        return metricsInCategory.get(name);
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public synchronized Collection<Metric> findMetricsByCategory(@Nonnull final String category) {
        final Map<String, Metric> metricsInCategory = metrics.get(category);
        if (metricsInCategory == null) {
            return List.of();
        }
        return metricsInCategory.values();
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public synchronized Collection<Metric> getAll() {
        // Not very efficient, but the no-op metrics doesn't do snapshotting, so this should rarely (if ever) be called.
        final List<Metric> allMetrics = new ArrayList<>();
        for (final Map<String, Metric> metricsInCategory : metrics.values()) {
            allMetrics.addAll(metricsInCategory.values());
        }
        return allMetrics;
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @SuppressWarnings("unchecked")
    @Override
    public synchronized <T extends Metric> T getOrCreate(@Nonnull final MetricConfig<T, ?> config) {
        Objects.requireNonNull(config, "config must not be null");
        final String category = config.getCategory();
        final String name = config.getName();

        final Map<String, Metric> metricsInCategory = metrics.computeIfAbsent(category, k -> new HashMap<>());
        return (T) metricsInCategory.computeIfAbsent(name, k -> FACTORY.createMetric(config));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void remove(@Nonnull final String category, @Nonnull final String name) {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(name, "name must not be null");
        final Map<String, Metric> metricsInCategory = metrics.get(category);

        if (metricsInCategory == null) {
            return;
        }

        metricsInCategory.remove(name);

        if (metricsInCategory.isEmpty()) {
            metrics.remove(category);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(@Nonnull final Metric metric) {
        Objects.requireNonNull(metric, "metric must not be null");
        remove(metric.getCategory(), metric.getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(@Nonnull final MetricConfig<?, ?> config) {
        Objects.requireNonNull(config, "config must not be null");
        remove(config.getCategory(), config.getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addUpdater(@Nonnull final Runnable updater) {
        Objects.requireNonNull(updater, "updater must not be null");
        // Intentional no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeUpdater(@Nonnull final Runnable updater) {
        Objects.requireNonNull(updater, "updater must not be null");
        // Intentional no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        // Intentional no-op
    }
}
