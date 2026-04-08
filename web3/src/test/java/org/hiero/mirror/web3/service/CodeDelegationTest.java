// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.esaulpaugh.headlong.abi.Function;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.web3j.generated.BLSPrecompileCall;
import org.hiero.mirror.web3.web3j.generated.EthCall;
import org.hiero.mirror.web3.web3j.generated.EvmCodes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.web3j.abi.TypeDecoder;

@RequiredArgsConstructor
final class CodeDelegationTest extends AbstractContractCallServiceHistoricalTest {

    @Test
    void callToAccountWithCodeDelegationExecutesTargetContract() {
        // Given - deploy a contract that has a pure function multiplySimpleNumbers() returning 4
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());

        // Create an account entity with delegation address pointing to the deployed contract
        final var account = accountEntityPersistWithCodeDelegation(contractAddress.toArrayUnsafe());
        final var accountAddress = toAddress(account.toEntityId());

        // Encode the call data for multiplySimpleNumbers()
        final var functionCall = contract.call_multiplySimpleNumbers();
        final var callData = Bytes.fromHexString(functionCall.encodeFunctionCall());

        final var serviceParameters =
                getContractExecutionParameters(callData, accountAddress, Address.ZERO, 0L, ETH_CALL);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);

        // Then - multiplySimpleNumbers returns 2 * 2 = 4
        assertThat(result).isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000004");
    }

    @Test
    void callToAccountWithCodeDelegationReturnsStorageData() {
        // Given - deploy a contract with a view function returnStorageData() returning "test"
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());

        final var account = accountEntityPersistWithCodeDelegation(contractAddress.toArrayUnsafe());
        final var accountAddress = toAddress(account.toEntityId());

        final var functionCall = contract.call_returnStorageData();
        final var callData = Bytes.fromHexString(functionCall.encodeFunctionCall());

        final var serviceParameters =
                getContractExecutionParameters(callData, accountAddress, Address.ZERO, 0L, ETH_CALL);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);
        final var decodedResult =
                TypeDecoder.decodeUtf8String(result, 66).getValue(); // offset 66 - 2 for "0x" prefix and 64 for offset
        // Then - returnStorageData returns the string "test", ABI-encoded
        assertThat(decodedResult).isEqualTo("test");
    }

    @Test
    void callToAccountWithCodeDelegationAndStorageSlotsModification() {
        // Given
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());

        final var account = accountEntityPersistWithCodeDelegation(contractAddress.toArrayUnsafe());
        final var accountAddress = toAddress(account.toEntityId());

        final var functionCall = contract.call_writeToStorageSlot("123");
        final var callData = Bytes.fromHexString(functionCall.encodeFunctionCall());

        final var serviceParameters =
                getContractExecutionParameters(callData, accountAddress, Address.ZERO, 0L, ETH_CALL);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);
        final var decodedResult =
                TypeDecoder.decodeUtf8String(result, 66).getValue(); // offset 66 - 2 for "0x" prefix and 64 for offset
        // Then
        assertThat(decodedResult).isEqualTo("123");
    }

    @Test
    void getAccountCodeDelegation() {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());

        final var account = accountEntityPersistWithCodeDelegation(contractAddress.toArrayUnsafe());
        final var accountAddress = toAddress(account.toEntityId());

        final var functionCall = contract.call_getExternalBytecode(accountAddress.toHexString());
        final var callData = Bytes.fromHexString(functionCall.encodeFunctionCall());

        final var serviceParameters =
                getContractExecutionParameters(callData, accountAddress, Address.ZERO, 0L, ETH_CALL);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);

        // Then
        final int prefixForOffsetAndLength = 128;
        final var delegatePrefix = "ef0100";
        final var contractAddressWithoutPrefix = contract.getContractAddress().substring(2);
        final int prefix = HEX_PREFIX.length() + prefixForOffsetAndLength;
        final var decodedResult =
                result.substring(prefix, prefix + delegatePrefix.length() + contractAddressWithoutPrefix.length());
        assertThat(decodedResult).isEqualTo(delegatePrefix + contractAddressWithoutPrefix);
    }

    @Test
    void callToAccountWithoutCodeDelegationReturnsEmpty() {
        // Given - an account with no delegation address
        final var account = accountEntityPersistWithCodeDelegation(null);
        final var accountAddress = toAddress(account.toEntityId());
        final var serviceParameters =
                getContractExecutionParameters(Bytes.EMPTY, accountAddress, Address.ZERO, 0L, ETH_CALL);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);

        // Then
        assertThat(result).isEqualTo(HEX_PREFIX);
    }

    @Test
    void callToAccountWithZeroDelegationAddressReturnsEmpty() {
        // Given - an account with the zero delegation address (deletion marker)
        final var zeroDelegation = new byte[20];
        final var account = accountEntityPersistWithCodeDelegation(zeroDelegation);
        final var accountAddress = toAddress(account.toEntityId());
        final var serviceParameters =
                getContractExecutionParameters(Bytes.EMPTY, accountAddress, Address.ZERO, 0L, ETH_CALL);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);

        // Then
        assertThat(result).isEqualTo(HEX_PREFIX);
    }

    @Test
    void callToNonExistingAccountWithCodeDelegationReturnsEmpty() {
        // Given - an existing account with code delegation to non-existing account
        final var randomAddress = Hex.decode("a94f5374fce5edbc8e2a8697c15331677e6ebf0b");

        // Create an account entity with delegation address pointing to the non-existing account
        final var account = accountEntityPersistWithCodeDelegation(randomAddress);
        final var accountAddress = toAddress(account.toEntityId());

        final var senderAccount = accountEntityPersist();
        final var senderAddress = toAddress(senderAccount.toEntityId());

        final var serviceParameters =
                getContractExecutionParameters(Bytes.EMPTY, accountAddress, senderAddress, 1L, ETH_CALL);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);

        // Then - no-op
        assertThat(result).isEqualTo(HEX_PREFIX);
    }

    @ParameterizedTest
    @CsvSource({
        "0x0000000000000000000000000000000000000004",
        "0x0000000000000000000000000000000000000167",
        "0x0000000000000000000000000000000000000168",
        "0x0000000000000000000000000000000000000169"
    })
    void callToAccountWithDelegationToPrecompileIsNoOp(final String precompileAddress) {
        // Given - an account with delegation address pointing to a system contract / precompile
        final var account = accountEntityPersistWithCodeDelegation(
                Address.fromHexString(precompileAddress).toArrayUnsafe());
        final var accountAddress = toAddress(account.toEntityId());
        final var serviceParameters =
                getContractExecutionParameters(Bytes.EMPTY, accountAddress, Address.ZERO, 0L, ETH_CALL);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);

        // Then - delegation to a precompile/system contract should result in a no-op
        assertThat(result).isEqualTo(HEX_PREFIX);
    }

    @Test
    void callToSystemAccountWithCodeDelegationReturnsEmpty() {
        // Given - an existing account with code delegation to system account
        final var systemAccountAddress = toAddress(systemEntity.nodeRewardAccount());

        // Create an account entity with delegation address pointing to the system account
        final var account = accountEntityPersistWithCodeDelegation(systemAccountAddress.toArrayUnsafe());
        final var accountAddress = toAddress(account.toEntityId());

        final var serviceParameters =
                getContractExecutionParameters(Bytes.EMPTY, accountAddress, Address.ZERO, 0L, ETH_CALL);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);

        // Then - no-op
        assertThat(result).isEqualTo(HEX_PREFIX);
    }

    @Test
    void callToAccountWithCodeDelegationToAnotherAccountWithCodeDelegationFails() {
        // Given - deploy a contract that has a pure function multiplySimpleNumbers()
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());

        // Create an account entity with delegation address pointing to the deployed contract
        final var accountSecondary = accountEntityPersistWithCodeDelegation(contractAddress.toArrayUnsafe());
        final var accountAddressSecondary = toAddress(accountSecondary.toEntityId());

        // Encode the call data for multiplySimpleNumbers()
        final var functionCall = contract.call_multiplySimpleNumbers();
        final var callData = Bytes.fromHexString(functionCall.encodeFunctionCall());

        // Create an account entity with delegation address pointing to the first account
        final var accountPrimary = accountEntityPersistWithCodeDelegation(accountAddressSecondary.toArrayUnsafe());
        final var accountAddressPrimary = toAddress(accountPrimary.toEntityId());

        final var serviceParameters =
                getContractExecutionParameters(callData, accountAddressPrimary, Address.ZERO, 0L, ETH_CALL);

        // Then - results in literal execution of the delegation indicator (which will fail since 0xef is a banned op
        // code)
        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessageContaining(CONTRACT_EXECUTION_EXCEPTION.name());
    }

    @Test
    void callToTokenWithCodeDelegationFails() {
        // Given - a fungible token
        final var tokenEntity = tokenEntityPersist();
        final var treasuryAccount = accountEntityPersist();
        final var token = fungibleTokenPersist(tokenEntity, treasuryAccount);
        final var tokenAddress = toAddress(tokenEntity.toEntityId());

        // Create an account entity with delegation address pointing to the token
        final var account = accountEntityPersistWithCodeDelegation(tokenAddress.toArrayUnsafe());
        final var accountAddress = toAddress(account.toEntityId());

        final var callData = Bytes.of(new Function("getTokenInfo(address)")
                .encodeCallWithArgs(asHeadlongAddress(tokenAddress.toArrayUnsafe()))
                .array());
        final var serviceParameters =
                getContractExecutionParameters(callData, accountAddress, Address.ZERO, 0L, ETH_CALL);

        // Then
        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessageContaining(CONTRACT_EXECUTION_EXCEPTION.name());
    }

    @Test
    void callToScheduleWithCodeDelegationFails() {
        // Given - a schedule entity
        final var scheduleEntity = scheduleEntityPersist();
        final var payerAccount = accountEntityPersist();
        final var schedule = schedulePersist(scheduleEntity, payerAccount, new byte[] {});
        final var scheduleAddress = toAddress(scheduleEntity.toEntityId());

        // Create an account entity with delegation address pointing to the schedule
        final var account = accountEntityPersistWithCodeDelegation(scheduleAddress.toArrayUnsafe());
        final var accountAddress = toAddress(account.toEntityId());

        final var callData = Bytes.of(new Function("authorizeSchedule(address)")
                .encodeCallWithArgs(asHeadlongAddress(scheduleAddress.toArrayUnsafe()))
                .array());
        final var serviceParameters =
                getContractExecutionParameters(callData, accountAddress, Address.ZERO, 0L, ETH_CALL);

        // Then
        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessageContaining(CONTRACT_EXECUTION_EXCEPTION.name());
    }

    private Entity accountEntityPersistWithCodeDelegation(final byte[] delegationAddress) {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT)
                        .evmAddress(null)
                        .alias(null)
                        .balance(DEFAULT_ACCOUNT_BALANCE)
                        .delegationAddress(delegationAddress))
                .persist();
    }

    // BLS precompiles are available in EVM 0.70.0. Inputs and expected outputs for EVM 0.70.0 are from
    // evm-spec-testing.
    @Nested
    class BLSPrecompileTest {

        private static final byte[] EMPTY_BYTES = new byte[0];

        private enum EvmVersion {
            // EVM version where BLS precompiles become available.
            EVM_0_70,
            // Historical EVM version before BLS precompiles become available.
            EVM_0_67
        }

        @ParameterizedTest
        @CsvSource({"EVM_0_70, true", "EVM_0_70, false", "EVM_0_67, true", "EVM_0_67, false"})
        void bls12_g1Add(EvmVersion evmVersion, boolean isStaticCall) throws Exception {
            runTest(
                    evmVersion,
                    isStaticCall,
                    Address.BLS12_G1ADD,
                    "00000000000000000000000000000000112b98340eee2777cc3c14163dea3ec97977ac3dc5c70da32e6e87578f44912e902ccef9efe28d4a78b8999dfbca942600000000000000000000000000000000186b28d92356c4dfec4b5201ad099dbdede3781f8998ddf929b4cd7756192185ca7b8f4ef7088f813270ac3d48868a210000000000000000000000000000000017f1d3a73197d7942695638c4fa9ac0fc3688c4f9774b905a14e3a3f171bac586c55e83ff97a1aeffb3af00adb22c6bb0000000000000000000000000000000008b3f481e3aaa0f1a09e30ed741d8ae4fcf5e095d5d00af600db18cb2c04b3edd03cc744a2888ae40caa232946c5e7e1",
                    "000000000000000000000000000000000a40300ce2dec9888b60690e9a41d3004fda4886854573974fab73b046d3147ba5b7a5bde85279ffede1b45b3918d82d0000000000000000000000000000000006d3d887e9f53b9ec4eb6cedf5607226754b07c01ace7834f57f3e7315faefb739e59018e22c492006190fba4a870025");
        }

        @ParameterizedTest
        @CsvSource({"EVM_0_70, true", "EVM_0_70, false", "EVM_0_67, true", "EVM_0_67, false"})
        void bls12_g1Msm(EvmVersion evmVersion, boolean isStaticCall) throws Exception {
            runTest(
                    evmVersion,
                    isStaticCall,
                    Address.BLS12_G1MULTIEXP,
                    "00000000000000000000000000000000112b98340eee2777cc3c14163dea3ec97977ac3dc5c70da32e6e87578f44912e902ccef9efe28d4a78b8999dfbca942600000000000000000000000000000000186b28d92356c4dfec4b5201ad099dbdede3781f8998ddf929b4cd7756192185ca7b8f4ef7088f813270ac3d48868a210000000000000000000000000000000000000000000000000000000000000002",
                    "0000000000000000000000000000000015222cddbabdd764c4bee0b3720322a65ff4712c86fc4b1588d0c209210a0884fa9468e855d261c483091b2bf7de6a630000000000000000000000000000000009f9edb99bc3b75d7489735c98b16ab78b9386c5f7a1f76c7e96ac6eb5bbde30dbca31a74ec6e0f0b12229eecea33c39");
        }

        @ParameterizedTest
        @CsvSource({"EVM_0_70, true", "EVM_0_70, false", "EVM_0_67, true", "EVM_0_67, false"})
        void bls12_g2Add(EvmVersion evmVersion, boolean isStaticCall) throws Exception {
            runTest(
                    evmVersion,
                    isStaticCall,
                    Address.BLS12_G2ADD,
                    "00000000000000000000000000000000103121a2ceaae586d240843a398967325f8eb5a93e8fea99b62b9f88d8556c80dd726a4b30e84a36eeabaf3592937f2700000000000000000000000000000000086b990f3da2aeac0a36143b7d7c824428215140db1bb859338764cb58458f081d92664f9053b50b3fbd2e4723121b68000000000000000000000000000000000f9e7ba9a86a8f7624aa2b42dcc8772e1af4ae115685e60abc2c9b90242167acef3d0be4050bf935eed7c3b6fc7ba77e000000000000000000000000000000000d22c3652d0dc6f0fc9316e14268477c2049ef772e852108d269d9c38dba1d4802e8dae479818184c08f9a569d87845100000000000000000000000000000000024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb80000000000000000000000000000000013e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e000000000000000000000000000000000ce5d527727d6e118cc9cdc6da2e351aadfd9baa8cbdd3a76d429a695160d12c923ac9cc3baca289e193548608b82801000000000000000000000000000000000606c4a02ea734cc32acd2b02bc28b99cb3e287e85a763af267492ab572e99ab3f370d275cec1da1aaa9075ff05f79be",
                    "000000000000000000000000000000000b54a8a7b08bd6827ed9a797de216b8c9057b3a9ca93e2f88e7f04f19accc42da90d883632b9ca4dc38d013f71ede4db00000000000000000000000000000000077eba4eecf0bd764dce8ed5f45040dd8f3b3427cb35230509482c14651713282946306247866dfe39a8e33016fcbe520000000000000000000000000000000014e60a76a29ef85cbd69f251b9f29147b67cfe3ed2823d3f9776b3a0efd2731941d47436dc6d2b58d9e65f8438bad073000000000000000000000000000000001586c3c910d95754fef7a732df78e279c3d37431c6a2b77e67a00c7c130a8fcd4d19f159cbeb997a178108fffffcbd20");
        }

        @ParameterizedTest
        @CsvSource({"EVM_0_70, true", "EVM_0_70, false", "EVM_0_67, true", "EVM_0_67, false"})
        void bls12_g2Msm(EvmVersion evmVersion, boolean isStaticCall) throws Exception {
            runTest(
                    evmVersion,
                    isStaticCall,
                    Address.BLS12_G2MULTIEXP,
                    "00000000000000000000000000000000024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb80000000000000000000000000000000013e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e000000000000000000000000000000000ce5d527727d6e118cc9cdc6da2e351aadfd9baa8cbdd3a76d429a695160d12c923ac9cc3baca289e193548608b82801000000000000000000000000000000000606c4a02ea734cc32acd2b02bc28b99cb3e287e85a763af267492ab572e99ab3f370d275cec1da1aaa9075ff05f79be0000000000000000000000000000000000000000000000000000000000000001",
                    "00000000000000000000000000000000024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb80000000000000000000000000000000013e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e000000000000000000000000000000000ce5d527727d6e118cc9cdc6da2e351aadfd9baa8cbdd3a76d429a695160d12c923ac9cc3baca289e193548608b82801000000000000000000000000000000000606c4a02ea734cc32acd2b02bc28b99cb3e287e85a763af267492ab572e99ab3f370d275cec1da1aaa9075ff05f79be");
        }

        @ParameterizedTest
        @CsvSource({"EVM_0_70, true", "EVM_0_70, false", "EVM_0_67, true", "EVM_0_67, false"})
        void bls12_pairingCheck(EvmVersion evmVersion, boolean isStaticCall) throws Exception {
            runTest(
                    evmVersion,
                    isStaticCall,
                    Address.BLS12_PAIRING,
                    "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
                    "0000000000000000000000000000000000000000000000000000000000000001");
        }

        @ParameterizedTest
        @CsvSource({"EVM_0_70, true", "EVM_0_70, false", "EVM_0_67, true", "EVM_0_67, false"})
        void bls12_mapFpToG1(EvmVersion evmVersion, boolean isStaticCall) throws Exception {
            runTest(
                    evmVersion,
                    isStaticCall,
                    Address.BLS12_MAP_FP_TO_G1,
                    "0000000000000000000000000000000004090815ad598a06897dd89bcda860f25837d54e897298ce31e6947378134d3761dc59a572154963e8c954919ecfa82d",
                    "000000000000000000000000000000001974dbb8e6b5d20b84df7e625e2fbfecb2cdb5f77d5eae5fb2955e5ce7313cae8364bc2fff520a6c25619739c6bdcb6a0000000000000000000000000000000015f9897e11c6441eaa676de141c8d83c37aab8667173cbe1dfd6de74d11861b961dccebcd9d289ac633455dfcc7013a3");
        }

        @ParameterizedTest
        @CsvSource({"EVM_0_70, true", "EVM_0_70, false", "EVM_0_67, true", "EVM_0_67, false"})
        void bls12_mapFp2ToG2(EvmVersion evmVersion, boolean isStaticCall) throws Exception {
            runTest(
                    evmVersion,
                    isStaticCall,
                    Address.BLS12_MAP_FP2_TO_G2,
                    "0000000000000000000000000000000018c16fe362b7dbdfa102e42bdfd3e2f4e6191d479437a59db4eb716986bf08ee1f42634db66bde97d6c16bbfd342b3b8000000000000000000000000000000000e37812ce1b146d998d5f92bdd5ada2a31bfd63dfe18311aa91637b5f279dd045763166aa1615e46a50d8d8f475f184e",
                    "00000000000000000000000000000000038af300ef34c7759a6caaa4e69363cafeed218a1f207e93b2c70d91a1263d375d6730bd6b6509dcac3ba5b567e85bf3000000000000000000000000000000000da75be60fb6aa0e9e3143e40c42796edf15685cafe0279afd2a67c3dff1c82341f17effd402e4f1af240ea90f4b659b0000000000000000000000000000000019b148cbdf163cf0894f29660d2e7bfb2b68e37d54cc83fd4e6e62c020eaa48709302ef8e746736c0e19342cc1ce3df4000000000000000000000000000000000492f4fed741b073e5a82580f7c663f9b79e036b70ab3e51162359cec4e77c78086fe879b65ca7a47d34374c8315ac5e");
        }

        private void runTest(
                final EvmVersion evmVersion,
                final boolean isStaticCall,
                final Address precompile,
                final String inputHex,
                final String expectedOutputHexWhenBlsActive)
                throws Exception {
            if (evmVersion != EvmVersion.EVM_0_70) {
                setUpHistoricalContext(400L);
            }
            final var contract = testWeb3jService.deploy(BLSPrecompileCall::deploy);
            final var precompileAddress = precompile.toHexString();
            final var input = ByteString.fromHex(inputHex).toByteArray();

            final var result = (isStaticCall
                            ? contract.call_staticCallBLSAddress(precompileAddress, input)
                            : contract.call_callBLSAddress(precompileAddress, input))
                    .send();

            assertThat(result.component1()).isTrue();
            final var expected = evmVersion == EvmVersion.EVM_0_70
                    ? ByteString.fromHex(expectedOutputHexWhenBlsActive).toByteArray()
                    : EMPTY_BYTES;
            assertThat(result.component2()).isEqualTo(expected);
        }
    }
}
