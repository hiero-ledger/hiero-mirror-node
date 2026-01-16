// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.subscribe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.hiero.mirror.monitor.subscribe.grpc.GrpcSubscriberProperties;
import org.hiero.mirror.monitor.subscribe.rest.RestSubscriberProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SubscribePropertiesTest {

    private GrpcSubscriberProperties grpcSubscriberProperties;
    private RestSubscriberProperties restSubscriberProperties;
    private SubscribeProperties subscribeProperties;

    @BeforeEach
    void setup() {
        grpcSubscriberProperties = new GrpcSubscriberProperties();
        restSubscriberProperties = new RestSubscriberProperties();
        subscribeProperties = new SubscribeProperties();
        subscribeProperties.getGrpc().put("grpc1", grpcSubscriberProperties);
        subscribeProperties.getRest().put("rest1", restSubscriberProperties);
    }

    @Test
    void validate() {
        subscribeProperties.validate();
        assertThat(grpcSubscriberProperties.getName()).isEqualTo("grpc1");
        assertThat(restSubscriberProperties.getName()).isEqualTo("rest1");
    }

    @Test
    void duplicateName() {
        subscribeProperties.getGrpc().put("rest1", grpcSubscriberProperties);
        assertThrows(IllegalArgumentException.class, subscribeProperties::validate);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void emptyName(String name) {
        subscribeProperties.getGrpc().put(name, grpcSubscriberProperties);
        assertThrows(IllegalArgumentException.class, subscribeProperties::validate);
    }

    @Test
    void nullName() {
        subscribeProperties.getGrpc().put(null, grpcSubscriberProperties);
        assertThrows(IllegalArgumentException.class, subscribeProperties::validate);
    }

    @Test
    void noScenarios() {
        subscribeProperties.getGrpc().clear();
        subscribeProperties.getRest().clear();
        assertThrows(IllegalArgumentException.class, subscribeProperties::validate);
    }

    @Test
    void noScenariosDisabled() {
        subscribeProperties.setEnabled(false);
        subscribeProperties.getGrpc().clear();
        subscribeProperties.getRest().clear();
        subscribeProperties.validate();
    }
}
