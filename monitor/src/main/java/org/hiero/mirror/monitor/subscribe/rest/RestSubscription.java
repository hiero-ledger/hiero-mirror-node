// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.subscribe.rest;

import lombok.Getter;
import lombok.Value;
import org.hiero.mirror.monitor.AbstractScenario;
import org.hiero.mirror.monitor.ScenarioProtocol;
import org.hiero.mirror.monitor.publish.PublishResponse;
import org.hiero.mirror.rest.model.TransactionByIdResponse;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Sinks;

@Getter
@Value
class RestSubscription extends AbstractScenario<RestSubscriberProperties, TransactionByIdResponse> {

    private final Sinks.Many<PublishResponse> sink;

    RestSubscription(int id, RestSubscriberProperties properties) {
        super(id, properties);
        sink = Sinks.many().multicast().directBestEffort();
    }

    @Override
    public ScenarioProtocol getProtocol() {
        return ScenarioProtocol.REST;
    }

    @Override
    public void onError(Throwable t) {
        String message = t.getMessage();

        if (Exceptions.isRetryExhausted(t) && t.getCause() != null) {
            t = t.getCause();
            message += " " + t.getMessage();
        }

        String error = t.getClass().getSimpleName();

        if (t instanceof WebClientResponseException webClientResponseException) {
            error = String.valueOf(webClientResponseException.getStatusCode().value());
        }

        log.warn("Subscription {} failed: {}", this, message);
        errors.add(error);
    }

    @Override
    public String toString() {
        String name = getName();
        return getProperties().getSubscribers() <= 1 ? name : name + " #" + getId();
    }
}
