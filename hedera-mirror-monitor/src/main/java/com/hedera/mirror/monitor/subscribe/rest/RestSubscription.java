// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.subscribe.rest;

import com.hedera.mirror.monitor.AbstractScenario;
import com.hedera.mirror.monitor.ScenarioProtocol;
import com.hedera.mirror.monitor.publish.PublishResponse;
import com.hedera.mirror.rest.model.TransactionByIdResponse;
import lombok.Getter;
import lombok.Value;
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
