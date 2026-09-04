// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.hedera.node.app.service.contract.impl.hevm.HederaOperationsRegistry;
import org.hiero.mirror.web3.viewmodel.ContractCallResponse;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

final class RuntimeHintsConfigurationTest {

    @Test
    void registersMainnetEvmsDeclaredMethods() {
        final var hints = new RuntimeHints();
        new RuntimeHintsConfiguration.CustomRuntimeHints()
                .registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onType(MainnetEVMs.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(TypeReference.of("com.esaulpaugh.headlong.abi.Single[]")))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(ContractCallResponse.class))
                .accepts(hints);
    }

    @ParameterizedTest
    @EnumSource(
            value = EvmSpecVersion.class,
            names = {"LONDON", "PARIS", "SHANGHAI", "CANCUN", "PRAGUE"})
    void operationsAreReflectivelyResolvable(EvmSpecVersion version) {
        assertThatNoException().isThrownBy(() -> HederaOperationsRegistry.forVersion(version));
    }
}
