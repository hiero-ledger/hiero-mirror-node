// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.monitor.validator.AccountIdValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MonitorPropertiesTest {

    private MonitorProperties monitorProperties;

    @BeforeEach
    void setup() {
        monitorProperties = new MonitorProperties();
        monitorProperties.setAccountIdValidator(new AccountIdValidator(new CommonProperties()));
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
            0.0.2, 0.0.2, 0, 0
            0.0.10, 0.0.10, 0, 0
            1.5.2, 0.0.2, 5, 1
            """)
    void init(String expected, String operatorId, long realm, long shard) {
        // given
        monitorProperties.getOperator().setAccountId(operatorId);
        monitorProperties.getAccountIdValidator().commonProperties().setRealm(realm);
        monitorProperties.getAccountIdValidator().commonProperties().setShard(shard);

        // when
        monitorProperties.init();

        // then
        assertThat(monitorProperties.getOperator().getAccountId()).isEqualTo(expected);
    }

    @Test
    void initThrows() {
        // given
        monitorProperties.getOperator().setAccountId("0.0.5");
        monitorProperties.getAccountIdValidator().commonProperties().setRealm(2);
        monitorProperties.getAccountIdValidator().commonProperties().setShard(1);

        // when, then
        assertThatThrownBy(monitorProperties::init).isInstanceOf(IllegalArgumentException.class);
    }
}
