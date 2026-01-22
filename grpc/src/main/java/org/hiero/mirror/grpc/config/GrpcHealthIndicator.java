// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.config;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.event.EventListener;
import org.springframework.grpc.autoconfigure.server.GrpcServerProperties;
import org.springframework.grpc.server.lifecycle.GrpcServerStartedEvent;

@CustomLog
@Named
@RequiredArgsConstructor
public class GrpcHealthIndicator implements HealthIndicator {

    private final AtomicReference<Status> status = new AtomicReference<>(Status.UNKNOWN);
    private final GrpcServerProperties grpcServerProperties;

    @Override
    public Health health() {
        return Health.status(status.get()).build();
    }

    @EventListener(GrpcServerStartedEvent.class)
    public void onStart() {
        log.info("Started gRPC server on {}", grpcServerProperties.getAddress());
        status.set(Status.UP);
    }

    @PreDestroy
    public void onStop() {
        log.info("Stopping gRPC server");
        status.set(Status.OUT_OF_SERVICE);
    }
}
