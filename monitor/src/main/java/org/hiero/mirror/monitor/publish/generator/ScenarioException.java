// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish.generator;

import lombok.Getter;
import org.hiero.mirror.monitor.subscribe.Scenario;

public class ScenarioException extends RuntimeException {

    private static final long serialVersionUID = 1690349494197296387L;

    @Getter
    private final transient Scenario<?, ?> scenario;

    public ScenarioException(Scenario<?, ?> scenario, String message) {
        super(message);
        this.scenario = scenario;
    }
}
