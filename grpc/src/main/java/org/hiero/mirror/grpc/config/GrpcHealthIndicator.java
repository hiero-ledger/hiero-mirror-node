// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.config;

import jakarta.inject.Named;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.event.GrpcServerShutdownEvent;
import net.devh.boot.grpc.server.event.GrpcServerStartedEvent;
import net.devh.boot.grpc.server.event.GrpcServerTerminatedEvent;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.event.EventListener;

@CustomLog
@Named
@RequiredArgsConstructor
public class GrpcHealthIndicator implements HealthIndicator {

    private final AtomicReference<Status> status = new AtomicReference<>(Status.UNKNOWN);

    @Override
    public Health health() {
        return Health.status(status.get()).build();
    }

    @EventListener
    public void onStart(GrpcServerStartedEvent event) {
        log.info("Started gRPC server on {}:{}", event.getAddress(), event.getPort());
        status.set(Status.UP);
    }

    @EventListener
    public void onStop(GrpcServerShutdownEvent event) {
        log.info("Stopping gRPC server");
        status.set(Status.OUT_OF_SERVICE);
    }

    @EventListener
    public void onTermination(GrpcServerTerminatedEvent event) {
        log.info("Stopped gRPC server");
        status.set(Status.DOWN);
    }
}
