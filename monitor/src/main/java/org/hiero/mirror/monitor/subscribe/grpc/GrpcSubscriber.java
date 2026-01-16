// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.subscribe.grpc;

import static io.grpc.Status.Code.INVALID_ARGUMENT;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collection;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.monitor.ScenarioProperties;
import org.hiero.mirror.monitor.expression.ExpressionConverter;
import org.hiero.mirror.monitor.subscribe.MirrorSubscriber;
import org.hiero.mirror.monitor.subscribe.SubscribeProperties;
import org.hiero.mirror.monitor.subscribe.SubscribeResponse;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

@CustomLog
@Named
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
class GrpcSubscriber implements MirrorSubscriber {

    private final ExpressionConverter expressionConverter;
    private final GrpcClient grpcClient;
    private final SubscribeProperties subscribeProperties;
    private final Flux<GrpcSubscription> subscriptions =
            Flux.defer(this::createSubscriptions).cache();

    @Override
    public Flux<SubscribeResponse> subscribe() {
        return subscriptions.flatMap(this::clientSubscribe);
    }

    @Override
    public Flux<GrpcSubscription> getSubscriptions() {
        return subscriptions;
    }

    private Flux<GrpcSubscription> createSubscriptions() {
        Collection<GrpcSubscription> subscriptionList = new ArrayList<>();

        for (GrpcSubscriberProperties properties : subscribeProperties.getGrpc().values()) {
            if (subscribeProperties.isEnabled() && properties.isEnabled()) {
                String topicId = expressionConverter.convert(properties.getTopicId());
                properties.setTopicId(topicId);

                for (int i = 1; i <= properties.getSubscribers(); ++i) {
                    subscriptionList.add(new GrpcSubscription(i, properties));
                }
            }
        }

        return Flux.fromIterable(subscriptionList);
    }

    private Flux<SubscribeResponse> clientSubscribe(GrpcSubscription subscription) {
        GrpcSubscriberProperties subscriberProperties = subscription.getProperties();
        ScenarioProperties.RetryProperties retry = subscriberProperties.getRetry();

        return Flux.defer(() -> grpcClient.subscribe(subscription))
                .retryWhen(Retry.backoff(retry.getMaxAttempts(), retry.getMinBackoff())
                        .maxBackoff(retry.getMaxBackoff())
                        .filter(this::shouldRetry)
                        .doBeforeRetry(r -> log.warn(
                                "Retry attempt #{} after failure: {}",
                                r.totalRetries() + 1,
                                StringUtils.substringBefore(r.failure().getMessage(), "\n"))))
                .doOnError(t -> log.error("Error subscribing {}: ", subscription, t))
                .doOnSubscribe(s -> log.info("Starting subscriber {}: {}", subscription, subscriberProperties));
    }

    // Don't retry client errors
    private boolean shouldRetry(Throwable t) {
        return getStatusCode(t) != INVALID_ARGUMENT;
    }

    private Status.Code getStatusCode(Throwable t) {
        if (t instanceof StatusRuntimeException statusRuntimeException) {
            return statusRuntimeException.getStatus().getCode();
        }
        return Status.Code.UNKNOWN;
    }
}
