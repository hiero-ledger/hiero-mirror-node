// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish;

import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.monitor.AbstractScenario;
import org.hiero.mirror.monitor.ScenarioProtocol;

@SuppressWarnings("java:S2160")
public class PublishScenario extends AbstractScenario<PublishScenarioProperties, PublishResponse> {

    private final String memo;

    public PublishScenario(PublishScenarioProperties properties) {
        super(1, properties);
        String hostname = Objects.requireNonNullElse(System.getenv("HOSTNAME"), "unknown");
        this.memo = String.format("Monitor %s on %s", properties.getName(), hostname);
    }

    public String getMemo() {
        var memoMessage = System.currentTimeMillis() + " " + this.memo;
        return StringUtils.truncate(memoMessage, properties.getMaxMemoLength());
    }

    @Override
    public ScenarioProtocol getProtocol() {
        return ScenarioProtocol.GRPC;
    }

    @Override
    public void onError(Throwable throwable) {
        if (throwable instanceof PublishException publishException) {
            errors.add(publishException.getStatus());
        } else {
            super.onError(throwable);
        }
    }
}
