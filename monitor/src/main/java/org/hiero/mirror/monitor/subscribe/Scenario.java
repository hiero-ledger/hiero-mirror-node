// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.subscribe;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Duration;
import java.util.Map;
import org.hiero.mirror.monitor.ScenarioProperties;
import org.hiero.mirror.monitor.ScenarioProtocol;
import org.hiero.mirror.monitor.ScenarioStatus;
import org.hiero.mirror.monitor.converter.DurationToStringSerializer;
import org.hiero.mirror.monitor.converter.StringToDurationDeserializer;

@JsonSerialize(as = Scenario.class)
public interface Scenario<P extends ScenarioProperties, T> {

    long getCount();

    @JsonDeserialize(using = StringToDurationDeserializer.class)
    @JsonSerialize(using = DurationToStringSerializer.class)
    Duration getElapsed();

    Map<String, Integer> getErrors();

    int getId();

    default String getName() {
        return getProperties().getName();
    }

    @JsonIgnore
    P getProperties();

    ScenarioProtocol getProtocol();

    double getRate();

    ScenarioStatus getStatus();

    boolean isRunning();

    void onComplete();

    void onError(Throwable t);

    void onNext(T response);
}
