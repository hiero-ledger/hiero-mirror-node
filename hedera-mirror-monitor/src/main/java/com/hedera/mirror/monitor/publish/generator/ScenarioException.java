// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.publish.generator;

import com.hedera.mirror.monitor.subscribe.Scenario;
import lombok.Getter;

public class ScenarioException extends RuntimeException {

    private static final long serialVersionUID = 1690349494197296387L;

    @Getter
    private final transient Scenario<?, ?> scenario;

    public ScenarioException(Scenario<?, ?> scenario, String message) {
        super(message);
        this.scenario = scenario;
    }
}
