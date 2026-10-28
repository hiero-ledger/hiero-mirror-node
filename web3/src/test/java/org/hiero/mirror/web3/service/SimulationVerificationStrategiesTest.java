// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy.Decision.INVALID;
import static com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy.Decision.VALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.entityid.impl.AppEntityIdFactory;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.stream.Stream;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SimulationVerificationStrategiesTest {

    private static final long ACTIVE_CONTRACT_NUM = 1001L;
    private static final long OTHER_CONTRACT_NUM = 2002L;
    private static final Address ACTIVE_ADDRESS = Address.fromHexString("0x00000000000000000000000000000000000003e9");
    private static final Address OTHER_ADDRESS = Address.fromHexString("0x00000000000000000000000000000000000007d2");
    private static final Key ACTIVE_CONTRACT_KEY = Key.newBuilder()
            .contractID(ContractID.newBuilder().contractNum(ACTIVE_CONTRACT_NUM))
            .build();
    private static final Key OTHER_CONTRACT_KEY = Key.newBuilder()
            .contractID(ContractID.newBuilder().contractNum(OTHER_CONTRACT_NUM))
            .build();
    private static final Key DELEGATABLE_KEY = Key.newBuilder()
            .delegatableContractId(ContractID.newBuilder().contractNum(ACTIVE_CONTRACT_NUM))
            .build();
    private static final Key ED25519_KEY =
            Key.newBuilder().ed25519(Bytes.wrap(new byte[32])).build();
    private static final Key ECDSA_KEY =
            Key.newBuilder().ecdsaSecp256k1(Bytes.wrap(new byte[33])).build();

    private final SimulationVerificationStrategies verificationStrategies = new SimulationVerificationStrategies();

    @Mock
    private HederaNativeOperations nativeOperations;

    @Mock
    private HandleContext handleContext;

    @BeforeEach
    void setup() {
        when(nativeOperations.entityIdFactory())
                .thenReturn(new AppEntityIdFactory(new EvmProperties().getVersionedConfiguration()));
    }

    @ParameterizedTest(name = "delegatable={0}, delegate permission={1}, EVM address={2}, active={3}")
    @CsvSource({
        "false, false, false, false, false",
        "false, false, false, true,  true",
        "false, false, true,  false, false",
        "false, false, true,  true,  true",
        "false, true,  false, false, false",
        "false, true,  false, true,  false",
        "false, true,  true,  false, false",
        "false, true,  true,  true,  false",
        "true,  false, false, false, false",
        "true,  false, false, true,  true",
        "true,  false, true,  false, false",
        "true,  false, true,  true,  true",
        "true,  true,  false, false, false",
        "true,  true,  false, true,  true",
        "true,  true,  true,  false, false",
        "true,  true,  true,  true,  true"
    })
    void verifiesContractKeys(
            final boolean delegatable,
            final boolean requiresDelegatePermission,
            final boolean evmAddress,
            final boolean activeContract,
            final boolean expected) {
        final var contractId = evmAddress
                ? ContractID.newBuilder()
                        .evmAddress(Bytes.wrap((activeContract ? ACTIVE_ADDRESS : OTHER_ADDRESS)
                                .getBytes()
                                .toArray()))
                        .build()
                : ContractID.newBuilder()
                        .contractNum(activeContract ? ACTIVE_CONTRACT_NUM : OTHER_CONTRACT_NUM)
                        .build();
        final var key = delegatable
                ? Key.newBuilder().delegatableContractId(contractId).build()
                : Key.newBuilder().contractID(contractId).build();
        final var strategy = verificationStrategies.activatingOnlyContractKeysFor(
                ACTIVE_ADDRESS, requiresDelegatePermission, nativeOperations);

        assertThat(strategy.decideForPrimitive(key)).isEqualTo(expected ? VALID : INVALID);
        assertThat(strategy.asSignatureTestIn(handleContext, null).test(key)).isEqualTo(expected);
        assertThat(strategy.asSignatureTestIn(handleContext, key).test(key)).isEqualTo(expected);
        verifyNoInteractions(handleContext);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acceptsCryptographicKeysWithoutSignatureVerification(final boolean requiresDelegatePermission) {
        final var strategy = verificationStrategies.activatingOnlyContractKeysFor(
                ACTIVE_ADDRESS, requiresDelegatePermission, nativeOperations);
        final var signatureTest = strategy.asSignatureTestIn(handleContext, null);

        assertThat(strategy.decideForPrimitive(ED25519_KEY)).isEqualTo(VALID);
        assertThat(strategy.decideForPrimitive(ECDSA_KEY)).isEqualTo(VALID);
        assertThat(signatureTest.test(ED25519_KEY)).isTrue();
        assertThat(signatureTest.test(ECDSA_KEY)).isTrue();
        verifyNoInteractions(handleContext);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("keyLists")
    void evaluatesKeyLists(final String description, final Key key, final boolean expected) {
        final var strategy =
                verificationStrategies.activatingOnlyContractKeysFor(ACTIVE_ADDRESS, false, nativeOperations);

        assertThat(strategy.asSignatureTestIn(handleContext, null).test(key)).isEqualTo(expected);
        verifyNoInteractions(handleContext);
    }

    private static Stream<Arguments> keyLists() {
        return Stream.of(
                Arguments.of("empty list", keyList(), false),
                Arguments.of("active contract", keyList(ACTIVE_CONTRACT_KEY), true),
                Arguments.of("inactive contract", keyList(OTHER_CONTRACT_KEY), false),
                Arguments.of("all valid", keyList(ACTIVE_CONTRACT_KEY, ED25519_KEY, ECDSA_KEY), true),
                Arguments.of("inactive contract first", keyList(OTHER_CONTRACT_KEY, ED25519_KEY), false),
                Arguments.of("inactive contract last", keyList(ED25519_KEY, OTHER_CONTRACT_KEY), false),
                Arguments.of("nested valid list", keyList(keyList(ACTIVE_CONTRACT_KEY), ED25519_KEY), true),
                Arguments.of("nested invalid list", keyList(ED25519_KEY, keyList(OTHER_CONTRACT_KEY)), false),
                Arguments.of("nested empty list", keyList(ED25519_KEY, keyList()), false));
    }

    @ParameterizedTest
    @CsvSource({
        "-1, 0, false",
        "-1, 1, true",
        " 0, 0, false",
        " 0, 1, true",
        " 1, 0, false",
        " 1, 1, true",
        " 1, 2, true",
        " 2, 1, false",
        " 2, 2, true",
        " 3, 1, false",
        " 3, 2, true"
    })
    void evaluatesThresholds(final int threshold, final int validKeys, final boolean expected) {
        final var key = thresholdKey(
                threshold,
                validKeys > 0 ? ED25519_KEY : OTHER_CONTRACT_KEY,
                validKeys > 1 ? ACTIVE_CONTRACT_KEY : OTHER_CONTRACT_KEY);
        final var strategy =
                verificationStrategies.activatingOnlyContractKeysFor(ACTIVE_ADDRESS, false, nativeOperations);

        assertThat(strategy.asSignatureTestIn(handleContext, null).test(key)).isEqualTo(expected);
        verifyNoInteractions(handleContext);
    }

    @Test
    void rejectsEmptyAndMissingThresholdKeyLists() {
        final var strategy =
                verificationStrategies.activatingOnlyContractKeysFor(ACTIVE_ADDRESS, false, nativeOperations);
        final var signatureTest = strategy.asSignatureTestIn(handleContext, null);
        final var missingKeys = Key.newBuilder()
                .thresholdKey(ThresholdKey.newBuilder().threshold(1))
                .build();

        assertThat(signatureTest.test(missingKeys)).isFalse();
        assertThat(signatureTest.test(thresholdKey(0))).isFalse();
        assertThat(signatureTest.test(thresholdKey(1))).isFalse();
        verifyNoInteractions(handleContext);
    }

    @Test
    void evaluatesMixedNestedKeyListsAndThresholds() {
        final var strategy =
                verificationStrategies.activatingOnlyContractKeysFor(ACTIVE_ADDRESS, false, nativeOperations);
        final var signatureTest = strategy.asSignatureTestIn(handleContext, null);
        final var validList = keyList(ACTIVE_CONTRACT_KEY, ED25519_KEY);
        final var invalidList = keyList(ACTIVE_CONTRACT_KEY, OTHER_CONTRACT_KEY);
        final var validThreshold = thresholdKey(1, OTHER_CONTRACT_KEY, validList);
        final var invalidThreshold = thresholdKey(2, ED25519_KEY, invalidList);

        assertThat(signatureTest.test(validThreshold)).isTrue();
        assertThat(signatureTest.test(invalidThreshold)).isFalse();
        assertThat(signatureTest.test(keyList(ECDSA_KEY, validThreshold))).isTrue();
        assertThat(signatureTest.test(keyList(ECDSA_KEY, invalidThreshold))).isFalse();
        assertThat(signatureTest.test(thresholdKey(2, validThreshold, invalidThreshold)))
                .isFalse();
        verifyNoInteractions(handleContext);
    }

    @Test
    void enforcesDelegatePermissionsInsideCompositeKeys() {
        final var strategy =
                verificationStrategies.activatingOnlyContractKeysFor(ACTIVE_ADDRESS, true, nativeOperations);
        final var signatureTest = strategy.asSignatureTestIn(handleContext, null);

        assertThat(signatureTest.test(keyList(ED25519_KEY, DELEGATABLE_KEY))).isTrue();
        assertThat(signatureTest.test(keyList(ED25519_KEY, ACTIVE_CONTRACT_KEY)))
                .isFalse();
        assertThat(signatureTest.test(thresholdKey(2, ACTIVE_CONTRACT_KEY, DELEGATABLE_KEY, ED25519_KEY)))
                .isTrue();
        assertThat(signatureTest.test(thresholdKey(3, ACTIVE_CONTRACT_KEY, DELEGATABLE_KEY, ED25519_KEY)))
                .isFalse();
        verifyNoInteractions(handleContext);
    }

    private static Key keyList(final Key... keys) {
        return Key.newBuilder().keyList(KeyList.newBuilder().keys(keys)).build();
    }

    private static Key thresholdKey(final int threshold, final Key... keys) {
        return Key.newBuilder()
                .thresholdKey(ThresholdKey.newBuilder()
                        .threshold(threshold)
                        .keys(KeyList.newBuilder().keys(keys)))
                .build();
    }
}
