// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.subscribe.controller;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.monitor.ScenarioProperties;
import org.hiero.mirror.monitor.ScenarioProtocol;
import org.hiero.mirror.monitor.ScenarioStatus;
import org.hiero.mirror.monitor.subscribe.MirrorSubscriber;
import org.hiero.mirror.monitor.subscribe.Scenario;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@CustomLog
@RequestMapping("/api/v1/subscriber")
@RequiredArgsConstructor
@RestController
class SubscriberController {

    private final MirrorSubscriber mirrorSubscriber;

    @GetMapping
    public <T extends ScenarioProperties> Flux<Scenario<T, Object>> subscriptions(
            @RequestParam("protocol") Optional<ScenarioProtocol> protocol,
            @RequestParam("status") Optional<List<ScenarioStatus>> status) {
        return mirrorSubscriber
                .<Scenario<T, Object>>getSubscriptions()
                .filter(s -> !protocol.isPresent() || protocol.get() == s.getProtocol())
                .filter(s -> !status.isPresent() || status.get().contains(s.getStatus()))
                .switchIfEmpty(Mono.error(new NoSuchElementException()));
    }

    @GetMapping("/{name}")
    public <T extends ScenarioProperties> Flux<Scenario<T, Object>> subscriptions(
            @PathVariable("name") String name, @RequestParam("status") Optional<List<ScenarioStatus>> status) {
        Flux<Scenario<T, Object>> subscriptions = subscriptions(Optional.empty(), status);
        return subscriptions
                .filter(subscription -> subscription.getName().equals(name))
                .switchIfEmpty(Mono.error(new NoSuchElementException()));
    }

    @GetMapping("/{name}/{id}")
    public <T extends ScenarioProperties> Mono<Scenario<T, Object>> subscription(
            @PathVariable("name") String name, @PathVariable("id") int id) {
        Flux<Scenario<T, Object>> subscriptions = subscriptions(name, Optional.empty());
        return subscriptions.filter(s -> s.getId() == id).last();
    }

    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Not found")
    @ExceptionHandler(NoSuchElementException.class)
    void notFound() {
        // Error logging is done generically in LoggingFilter
    }
}
