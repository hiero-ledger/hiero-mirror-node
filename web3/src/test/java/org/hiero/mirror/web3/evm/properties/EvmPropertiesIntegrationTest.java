// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.ConfigData;
import java.lang.reflect.Field;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class EvmPropertiesIntegrationTest extends Web3IntegrationTest {

    private static final String DOT_SEPARATOR = ".";
    private static final String CONTRACTS_CONFIG = "contracts";
    private static final String MAX_GAS_REFUND_PERCENTAGE = "maxRefundPercentOfGasLimit";
    private static final String MAX_GAS_REFUND_PERCENTAGE_KEY_CONFIG =
            CONTRACTS_CONFIG + DOT_SEPARATOR + MAX_GAS_REFUND_PERCENTAGE;
    private final EvmProperties properties;

    private static String getConfigKey(final Class<?> configClass, final String fieldName) {
        try {
            Field field = configClass.getDeclaredField(fieldName);
            ConfigData configProperty = configClass.getAnnotation(ConfigData.class);
            return configProperty.value() + DOT_SEPARATOR + field.getName();
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("Field " + fieldName + " not found in " + configClass.getSimpleName());
        }
    }

    private static String getContractsConfigKey(final String configKey) {
        var fieldName = configKey.replace(CONTRACTS_CONFIG + DOT_SEPARATOR, "");
        return getConfigKey(ContractsConfig.class, fieldName);
    }

    @Test
    void getProperties() {
        assertThat(properties.getProperties())
                .isNotEmpty()
                // override from yaml
                .containsEntry(MAX_GAS_REFUND_PERCENTAGE_KEY_CONFIG, "100");
    }

    @Test
    void verifyUpstreamPropertiesExist() {
        Set<String> propertyKeys = properties.getProperties().keySet();
        propertyKeys.stream().forEach(configKey -> assertThat(getContractsConfigKey(configKey))
                .isEqualTo(configKey));
    }

    @Test
    void verifyUpstreamNonExistentProperty() {
        assertThrows(IllegalArgumentException.class, () -> getConfigKey(ContractsConfig.class, "nonExistentField"));
    }
}
