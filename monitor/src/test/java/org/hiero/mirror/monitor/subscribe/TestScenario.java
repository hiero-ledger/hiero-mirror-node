// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.subscribe;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.hiero.mirror.monitor.ScenarioProperties;
import org.hiero.mirror.monitor.ScenarioProtocol;
import org.hiero.mirror.monitor.ScenarioStatus;

@Data
public class TestScenario implements Scenario<ScenarioProperties, Object> {

    private long count = 1;
    private Duration elapsed = Duration.ofSeconds(1L);
    private Map<String, Integer> errors = new HashMap<>();
    private int id = 1;
    private String name = "Test";
    private AbstractSubscriberProperties properties;
    private ScenarioProtocol protocol = ScenarioProtocol.GRPC;
    private double rate = 1.0;
    private ScenarioStatus status = ScenarioStatus.RUNNING;

    @Override
    public boolean isRunning() {
        return status == ScenarioStatus.RUNNING;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public void onComplete() {
        // Ignore
    }

    @Override
    public void onError(Throwable t) {
        // Ignore
    }

    @Override
    public void onNext(Object response) {
        // Ignore
    }
}
