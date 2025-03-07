// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.subscribe;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hedera.mirror.monitor.ScenarioProperties;
import com.hedera.mirror.monitor.ScenarioProtocol;
import com.hedera.mirror.monitor.ScenarioStatus;
import com.hedera.mirror.monitor.converter.DurationToStringSerializer;
import com.hedera.mirror.monitor.converter.StringToDurationDeserializer;
import java.time.Duration;
import java.util.Map;

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
