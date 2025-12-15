// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_30;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_34;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_38;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_46;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_50;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_51;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;

import com.hedera.hapi.node.base.SemanticVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Version;

@ExtendWith(MockitoExtension.class)
class MirrorNodeEvmPropertiesTest {
    private static final int MAX_REFUND_PERCENT = 100;
    private static final Bytes32 CHAIN_ID = Bytes32.fromHexString("0x0128");

    private final CommonProperties commonProperties = new CommonProperties();
    private final SystemEntity systemEntity = new SystemEntity(commonProperties);
    private final MirrorNodeEvmProperties properties = new MirrorNodeEvmProperties(commonProperties, systemEntity);

    @AutoClose
    private final MockedStatic<ContractCallContext> staticMock = mockStatic(ContractCallContext.class);

    @Mock
    private ContractCallContext contractCallContext;

    @Mock
    private RecordFile recordFile;

    private static List<SemanticVersion> createEvmVersionsList() {
        return List.of(
                EVM_VERSION_0_30,
                EVM_VERSION_0_34,
                EVM_VERSION_0_38,
                EVM_VERSION_0_46,
                EVM_VERSION_0_50,
                EVM_VERSION_0_51);
    }

    private static Stream<Arguments> hapiVersionToEvmVersionProviderCustom() {
        return hapiVersionToEvmVersionProvider(createEvmVersionsList());
    }

    private static Stream<Arguments> hapiVersionToEvmVersionProvider(List<SemanticVersion> evmVersions) {
        Stream.Builder<Arguments> argumentsBuilder = Stream.builder();

        evmVersions.forEach(evmVersion -> {
            argumentsBuilder.add(
                    Arguments.of(new Version(evmVersion.major(), evmVersion.minor(), evmVersion.patch()), evmVersion));
        });
        return argumentsBuilder.build();
    }

    @BeforeEach
    void setup() {
        properties.setEvmVersions(new ArrayList<>());
    }

    @Test
    void correctPropertiesEvaluation() {
        staticMock.when(ContractCallContext::get).thenReturn(contractCallContext);
        assertThat(properties.maxGasRefundPercentage()).isEqualTo(MAX_REFUND_PERCENT);
        assertThat(properties.chainIdBytes32()).isEqualTo(CHAIN_ID);
    }

    @ParameterizedTest
    @MethodSource("hapiVersionToEvmVersionProviderCustom")
    void correctHistoricalEvmVersion(Version hapiVersion, SemanticVersion expectedEvmVersion) {
        staticMock.when(ContractCallContext::get).thenReturn(contractCallContext);
        given(contractCallContext.useHistorical()).willReturn(true);
        given(contractCallContext.getRecordFile()).willReturn(recordFile);
        given(recordFile.getHapiVersion()).willReturn(hapiVersion);
        properties.setEvmVersions(createEvmVersionsList());
        assertThat(properties.getSemanticEvmVersion()).isEqualTo(expectedEvmVersion);
    }
}
