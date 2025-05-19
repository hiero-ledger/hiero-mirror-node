// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.StreamType;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;

/**
 * Intercepts requests to the S3 API and records relevant metrics before continuing.
 */
@CustomLog
@Named
@RequiredArgsConstructor
public class MetricsExecutionInterceptor implements ExecutionInterceptor {

    static final ExecutionAttribute<ResponseSizeSubscriber> SIZE = new ExecutionAttribute<>("size");
    static final ExecutionAttribute<Instant> START_TIME = new ExecutionAttribute<>("start-time");
    static final String METRIC_DOWNLOAD_REQUEST = "hiero.mirror.importer.stream.request";
    static final String METRIC_DOWNLOAD_RESPONSE = "hiero.mirror.importer.stream.response";
    static final String ACTION_LIST = "list";
    static final String ACTION_SIDECAR = "sidecar";
    static final String ACTION_SIGNATURE = "signature";
    static final String ACTION_SIGNED = "signed";
    static final String TAG_ACTION = "action";
    static final String TAG_METHOD = "method";
    static final String TAG_NODE = "node";
    static final String TAG_SHARD = "shard";
    static final String TAG_STATUS = "status";
    static final String TAG_TYPE = "type";
    static final String QUERY_START_AFTER = "start-after";

    private static final Pattern ENTITY_ID_PATTERN =
            Pattern.compile("/(balance|record)(s_)?(\\d{1,10})\\.\\d{1,10}\\.(\\d{1,10})");
    private static final Pattern SIDECAR_PATTERN = Pattern.compile("Z_\\d{1,2}\\.rcd");
    private static final Pattern NODE_ID_PATTERN =
            Pattern.compile("[^/]/+(\\d{1,10})/(\\d{1,10})/((balance|record)/)?");

    private final MeterRegistry meterRegistry;

    private final Timer.Builder requestMetric = Timer.builder(METRIC_DOWNLOAD_REQUEST)
            .description("The time in seconds it took to receive the response from S3");

    private final DistributionSummary.Builder responseSizeMetric = DistributionSummary.builder(METRIC_DOWNLOAD_RESPONSE)
            .description("The size of the response in bytes returned from S3")
            .baseUnit("bytes");

    @Override
    public void beforeTransmission(Context.BeforeTransmission context, ExecutionAttributes executionAttributes) {
        executionAttributes.putAttributeIfAbsent(START_TIME, Instant.now());
    }

    // Wrap the response to count the number of bytes read
    @Override
    public Optional<Publisher<ByteBuffer>> modifyAsyncHttpResponseContent(
            Context.ModifyHttpResponse context, ExecutionAttributes executionAttributes) {
        return context.responsePublisher().map(publisher -> subscriber -> {
            ResponseSizeSubscriber responseSizeSubscriber = new ResponseSizeSubscriber(subscriber);
            executionAttributes.putAttributeIfAbsent(SIZE, responseSizeSubscriber);
            publisher.subscribe(responseSizeSubscriber);
        });
    }

    @Override
    public void afterExecution(Context.AfterExecution context, ExecutionAttributes executionAttributes) {
        try {
            var uriAttributes = getUriAttributes(context.httpRequest().getUri());
            var startTime = executionAttributes.getAttribute(START_TIME);
            var responseSizeSubscriber = executionAttributes.getAttribute(SIZE);

            String[] tags = {
                TAG_ACTION, uriAttributes.action(),
                TAG_METHOD, context.httpRequest().method().name(),
                TAG_NODE, String.valueOf(uriAttributes.node()),
                TAG_SHARD, String.valueOf(uriAttributes.shard()),
                TAG_STATUS, String.valueOf(context.httpResponse().statusCode()),
                TAG_TYPE, uriAttributes.type()
            };

            if (startTime != null) {
                requestMetric.tags(tags).register(meterRegistry).record(Duration.between(startTime, Instant.now()));
            }

            if (responseSizeSubscriber != null) {
                responseSizeMetric
                        .tags(tags)
                        .register(meterRegistry)
                        .record(Double.valueOf(responseSizeSubscriber.getSize()));
            }
        } catch (Exception e) {
            log.warn("Unable to collect S3 metrics", e);
        }
    }

    private UriAttributes getUriAttributes(URI uri) {
        var query = uri.getQuery();
        var uriComponent = query != null && query.contains(QUERY_START_AFTER) ? query : uri.getPath();
        var action = getAction(uriComponent);

        Matcher accountIdMatcher = ENTITY_ID_PATTERN.matcher(uriComponent);
        if (accountIdMatcher.find()) {
            var shard = accountIdMatcher.group(3);
            var nodeId = String.valueOf(Long.parseLong(accountIdMatcher.group(4)) - 3L);
            var streamType = accountIdMatcher.group(1);
            return new UriAttributes(action, nodeId, shard, streamType.toUpperCase());
        }

        Matcher nodeIdMatcher = NODE_ID_PATTERN.matcher(uriComponent);
        if (nodeIdMatcher.find()) {
            var shard = nodeIdMatcher.group(1);
            var nodeId = nodeIdMatcher.group(2);
            var streamType = nodeIdMatcher.group(4) != null ? nodeIdMatcher.group(4) : StreamType.BLOCK.name();
            return new UriAttributes(action, nodeId, shard, streamType.toUpperCase());
        }

        throw new IllegalStateException("Could not detect a node ID or account ID in URI: " + uri);
    }

    // Instead of tagging the URI path, simplify it to the 3 actions we use from the S3 API
    private String getAction(String uri) {
        if (uri.contains(QUERY_START_AFTER)) {
            return ACTION_LIST;
        } else if (uri.contains(StreamType.SIGNATURE_SUFFIX)) {
            return ACTION_SIGNATURE;
        } else if (SIDECAR_PATTERN.matcher(uri).find()) {
            return ACTION_SIDECAR;
        } else {
            return ACTION_SIGNED;
        }
    }

    private record UriAttributes(String action, String node, String shard, String type) {}

    @Getter
    @RequiredArgsConstructor
    private class ResponseSizeSubscriber implements Subscriber<ByteBuffer> {

        private final Subscriber<? super ByteBuffer> wrapped;
        private long size = 0L;

        @Override
        public void onSubscribe(Subscription s) {
            wrapped.onSubscribe(s);
        }

        @Override
        public void onNext(ByteBuffer byteBuffer) {
            wrapped.onNext(byteBuffer);
            if (byteBuffer.hasRemaining()) {
                size += byteBuffer.remaining();
            }
        }

        @Override
        public void onError(Throwable t) {
            wrapped.onError(t);
        }

        @Override
        public void onComplete() {
            wrapped.onComplete();
        }
    }
}
