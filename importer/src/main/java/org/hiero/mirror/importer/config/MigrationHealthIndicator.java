// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import jakarta.inject.Named;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

@Named
@RequiredArgsConstructor
public class MigrationHealthIndicator extends BaseCallback implements HealthIndicator {

    private static final Health DOWN = Health.down().build();
    private static final Health UP = Health.up().build();

    private final AtomicBoolean complete = new AtomicBoolean(false);

    @Override
    public Health health() {
        return complete.get() ? UP : DOWN;
    }

    @Override
    public void handle(Event event, Context context) {
        complete.set(true);
    }

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.AFTER_MIGRATE;
    }
}
