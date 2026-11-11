// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.hedera.node.app.service.contract.impl.hevm.HederaOperationsRegistry;
import java.util.List;
import org.hiero.mirror.web3.viewmodel.AccessListEntry;
import org.hiero.mirror.web3.viewmodel.AuthorizationListEntry;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.hiero.mirror.web3.viewmodel.ContractCallResponse;
import org.hiero.mirror.web3.viewmodel.SimulateBlockStateCall;
import org.hiero.mirror.web3.viewmodel.SimulateCallError;
import org.hiero.mirror.web3.viewmodel.SimulateCallResult;
import org.hiero.mirror.web3.viewmodel.SimulateLog;
import org.hiero.mirror.web3.viewmodel.StateOverride;
import org.hiero.mirror.web3.viewmodel.StorageEntry;
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
        assertThat(RuntimeHintsPredicates.reflection().onType(StateOverride.class))
                .accepts(hints);
    }

    @Test
    void registersNestedSimulateTypes() {
        final var hints = new RuntimeHints();
        new RuntimeHintsConfiguration.CustomRuntimeHints()
                .registerHints(hints, getClass().getClassLoader());

        for (final var type : List.of(
                AccessListEntry.class,
                AuthorizationListEntry.class,
                BlockOverride.class,
                SimulateBlockStateCall.class,
                SimulateCallError.class,
                SimulateCallResult.class,
                SimulateLog.class,
                StorageEntry.class)) {
            assertThat(RuntimeHintsPredicates.reflection().onType(type)).accepts(hints);
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = EvmSpecVersion.class,
            names = {"LONDON", "PARIS", "SHANGHAI", "CANCUN", "PRAGUE"})
    void operationsAreReflectivelyResolvable(EvmSpecVersion version) {
        assertThatNoException().isThrownBy(() -> HederaOperationsRegistry.forVersion(version));
    }
}
