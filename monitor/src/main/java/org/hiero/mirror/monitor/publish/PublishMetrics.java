// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish;

import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.hiero.mirror.monitor.NodeProperties;
import org.hiero.mirror.monitor.converter.DurationToStringSerializer;
import org.springframework.scheduling.annotation.Scheduled;

@CustomLog
@Named
@RequiredArgsConstructor
public class PublishMetrics {

    static final String METRIC_DURATION = "hiero.mirror.monitor.publish.duration";
    static final String METRIC_HANDLE = "hiero.mirror.monitor.publish.handle";
    static final String METRIC_SUBMIT = "hiero.mirror.monitor.publish.submit";
    static final String SUCCESS = "SUCCESS";

    private final Map<Tags, TimeGauge> durationGauges = new ConcurrentHashMap<>();
    private final Map<Tags, Timer> handleTimers = new ConcurrentHashMap<>();
    private final Map<Tags, Timer> submitTimers = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final PublishProperties publishProperties;

    public void onSuccess(PublishResponse response) {
        recordMetric(response.getRequest(), response, SUCCESS);
    }

    public void onError(PublishException publishException) {
        PublishRequest request = publishException.getPublishRequest();
        String status = publishException.getStatus();
        recordMetric(request, null, status);
    }

    private void recordMetric(PublishRequest request, PublishResponse response, String status) {
        try {
            var node = request.getNode();
            long startTime = request.getTimestamp().toEpochMilli();
            long endTime = response != null ? response.getTimestamp().toEpochMilli() : System.currentTimeMillis();
            Tags tags = new Tags(node, request.getScenario(), status);

            Timer submitTimer = submitTimers.computeIfAbsent(tags, this::newSubmitMetric);
            submitTimer.record(endTime - startTime, TimeUnit.MILLISECONDS);

            durationGauges.computeIfAbsent(tags, this::newDurationMetric);

            if (response != null && response.getReceipt() != null) {
                long elapsed = System.currentTimeMillis() - startTime;
                Timer handleTimer = handleTimers.computeIfAbsent(tags, this::newHandleMetric);
                handleTimer.record(elapsed, TimeUnit.MILLISECONDS);
            }
        } catch (Exception ex) {
            log.error("Unexpected error when recording metric for {}", request, ex);
        }
    }

    private TimeGauge newDurationMetric(Tags tags) {
        TimeUnit unit = TimeUnit.NANOSECONDS;
        return TimeGauge.builder(METRIC_DURATION, tags.getScenario(), unit, s -> s.getElapsed()
                        .toNanos())
                .description("The amount of time this scenario has been publishing transactions")
                .tags(tags.common())
                .register(meterRegistry);
    }

    private Timer newHandleMetric(Tags tags) {
        return Timer.builder(METRIC_HANDLE)
                .description("The time it takes from submit to being handled by the main nodes")
                .tags(tags.common())
                .tag(Tags.TAG_STATUS, tags.getStatus())
                .register(meterRegistry);
    }

    private Timer newSubmitMetric(Tags tags) {
        return Timer.builder(METRIC_SUBMIT)
                .description("The time it takes to submit a transaction")
                .tags(tags.common())
                .tag(Tags.TAG_STATUS, tags.getStatus())
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${hiero.mirror.monitor.publish.statusFrequency:10000}")
    public void status() {
        if (publishProperties.isEnabled()) {
            var running = new AtomicBoolean(false);
            durationGauges.keySet().stream()
                    .map(Tags::getScenario)
                    .distinct()
                    .filter(PublishScenario::isRunning)
                    .peek(s -> running.set(true))
                    .forEach(this::status);

            if (!running.get()) {
                log.info("No publishers");
            }
        }
    }

    private void status(PublishScenario scenario) {
        String elapsed = DurationToStringSerializer.convert(scenario.getElapsed());
        log.info(
                "Scenario {} published {} transactions in {} at {}/s. Errors: {}",
                scenario,
                scenario.getCount(),
                elapsed,
                scenario.getRate(),
                scenario.getErrors());
    }

    @Value
    class Tags {
        static final String TAG_HOST = "host";
        static final String TAG_NODE = "node";
        static final String TAG_PORT = "port";
        static final String TAG_SCENARIO = "scenario";
        static final String TAG_STATUS = "status";
        static final String TAG_TYPE = "type";

        private final NodeProperties node;
        private final PublishScenario scenario;
        private final String status;

        private String getType() {
            return scenario.getProperties().getType().toString();
        }

        private List<Tag> common() {
            var tags = new ArrayList<Tag>(5);
            tags.add(new ImmutableTag(Tags.TAG_SCENARIO, scenario.getName()));
            tags.add(new ImmutableTag(Tags.TAG_TYPE, getType()));

            if (node != null) {
                tags.add(new ImmutableTag(Tags.TAG_HOST, String.valueOf(node.getHost())));
                tags.add(new ImmutableTag(Tags.TAG_NODE, String.valueOf(node.getNodeId())));
                tags.add(new ImmutableTag(Tags.TAG_PORT, String.valueOf(node.getPort())));
            }

            return tags;
        }
    }
}
