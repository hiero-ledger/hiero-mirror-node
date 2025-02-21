/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.generated.EthCall;
import com.hedera.mirror.web3.web3j.generated.EvmCodes;
import com.hedera.mirror.web3.web3j.generated.EvmCodes.G1Point;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.AssertionsForClassTypes;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.AbiTypes;
import org.web3j.abi.datatypes.Type;

@RequiredArgsConstructor
class ContractCallEvmCodesTest extends AbstractContractCallServiceTest {

    private static final String EMPTY_BLOCK_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final Long EVM_46_BLOCK = 150L;

    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @MockitoSpyBean
    private ContractExecutionService contractExecutionService;

    @Test
    void chainId() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var chainId = contract.call_chainId().send();
        assertThat(chainId).isEqualTo(mirrorNodeEvmProperties.chainIdBytes32().toBigInteger());
    }

    @Test
    void recoverAddressPrecompiledContract() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_recoverAddress().send();
        assertThat(result).isNotEmpty();
    }

    @Test
    void sha256PrecompiledContract() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_calculateSHA256().send();
        assertThat(Bytes.wrap(result).toHexString())
                .isEqualTo("0xe93bb3ba29b71e2623d3d6e4c0f266c41cb005259e8cad8c4d04f966053ac712");
    }

    @Test
    void calculateRIPEMD160PrecompiledContract() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_calculateRIPEMD160().send();
        assertThat(Bytes.wrap(result).toHexString())
                .isEqualTo("0x000000000000000000000000c4861db52a25298b7ba404b6f8a65d2d6473c1a9");
    }

    @Test
    void idPrecompiledContract() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_identity().send();
        assertThat(Bytes.wrap(result).toHexString()).contains("48656c6c6f2c20576f726c64");
    }

    @Test
    void bigIntegerModularExponentiationPrecompiledContract() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_modExp().send();
        assertThat(result).isEqualTo(BigInteger.valueOf(4));
    }

    @Test
    void altBN128AddPrecompiledContract() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_addPoints().send();
        assertThat(result)
                .isEqualTo(new G1Point(
                        new BigInteger("1368015179489954701390400359078579693043519447331113978918064868415326638035"),
                        new BigInteger(
                                "9918110051302171585080402603319702774565515993150576347155970296011118125764")));
    }

    @Test
    void altBN128MulPrecompiledContract() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_multiplyPoints().send();
        assertThat(result)
                .isEqualTo(new G1Point(
                        new BigInteger("3353031288059533942658390886683067124040920775575537747144343083137631628272"),
                        new BigInteger(
                                "19321533766552368860946552437480515441416830039777911637913418824951667761761")));
    }

    @Test
    void altBN128PairingPrecompiledContract() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_pairingCheck().send();
        assertThat(result).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void blake2BFPrecompileContract() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        List<byte[]> result = contract.call_blake2().send();
        var expectedResultHexString =
                "ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d17d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923";
        var resultBytes = Bytes.concatenate(result.stream().map(Bytes::wrap).toArray(Bytes[]::new))
                .toArray();
        var expected = ByteString.fromHex(expectedResultHexString).toByteArray();
        assertArrayEquals(resultBytes, expected);
    }

    @Test
    void getBlockPrevrandao() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_getBlockPrevrandao().send();
        assertThat(result).isNotNull();
        assertTrue(result.compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    void getBlockHashReturnsCorrectHash() throws Exception {
        testWeb3jService.setUseContractCallDeploy(true);
        domainBuilder.recordFile().customize(r -> r.index(1L)).persist();
        var latest = domainBuilder.recordFile().customize(r -> r.index(2L)).persist();
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_getBlockHash(BigInteger.valueOf(latest.getIndex()))
                .send();
        var expectedResult = Hex.decode(latest.getHash().substring(0, 64));
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void getGenesisBlockHashReturnsCorrectBlock() throws Exception {
        testWeb3jService.setUseContractCallDeploy(true);
        domainBuilder.recordFile().customize(r -> r.index(1L)).persist();
        domainBuilder.recordFile().customize(r -> r.index(2L)).persist();

        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_getBlockHash(BigInteger.ZERO).send();

        var expectedResult = Hex.decode(genesisRecordFile.getHash().substring(0, 64));
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void getLatestBlockHashIsNotEmpty() throws Exception {
        domainBuilder.recordFile().customize(r -> r.index(1L)).persist();
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_getLatestBlockHash().send();
        var expectedResult = Hex.decode(EMPTY_BLOCK_HASH);
        assertThat(result).isNotEqualTo(expectedResult);
    }

    @Test
    void getBlockHashAfterTheLatestReturnsZero() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result =
                contract.call_getBlockHash(BigInteger.valueOf(Long.MAX_VALUE)).send();
        var expectedResult = Hex.decode(EMPTY_BLOCK_HASH);
        assertThat(result).isEqualTo(expectedResult);
    }

    @ParameterizedTest
    @CsvSource({
        // function getCodeHash with parameter hedera system accounts, expected 0 bytes
        "0000000000000000000000000000000000000000000000000000000000000167",
        "0000000000000000000000000000000000000000000000000000000000000168",
        "00000000000000000000000000000000000000000000000000000000000002ee",
        "00000000000000000000000000000000000000000000000000000000000002e4",
    })
    void testSystemContractCodeHash(String input) throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.call_getCodeHash(input).send();
        var expectedResult = ByteString.fromHex((EMPTY_BLOCK_HASH)).toByteArray();
        assertThat(result).isEqualTo(expectedResult);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testNonSystemContractEthCallCodeHash() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        final var ethCallContract = testWeb3jService.deploy(EthCall::deploy);

        final List<Bytes> capturedOutputs = new ArrayList<>();
        doAnswer(invocation -> {
                    HederaEvmTransactionProcessingResult result =
                            (HederaEvmTransactionProcessingResult) invocation.callRealMethod();
                    capturedOutputs.add(result.getOutput()); // Capture the result
                    return result;
                })
                .when(contractExecutionService)
                .callContract(any(), any());

        final var result =
                contract.call_getCodeHash(ethCallContract.getContractAddress()).send();

        final var expectedOutput = capturedOutputs.getFirst().toHexString();
        final var byteArrOutput = FunctionReturnDecoder.decode(
                expectedOutput, List.of(TypeReference.create((Class<Type>) AbiTypes.getType("bytes32"))));
        assertArrayEquals(result, (byte[]) byteArrOutput.getFirst().getValue());
    }

    @Test
    void testNonSystemAccountCodeHash() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        final var address = toAddress(1078);
        final var autoRenewEntityId = entityIdFromEvmAddress(address);
        domainBuilder
                .entity()
                .customize(e -> e.id(autoRenewEntityId.getId())
                        .num(autoRenewEntityId.getNum())
                        .evmAddress(null)
                        .alias(toEvmAddress(autoRenewEntityId)))
                .persist();

        final var result = contract.call_getCodeHash(address.toString()).send();
        String keccak256ofEthCallExpected = "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470";
        assertThat(result)
                .isEqualTo(ByteString.fromHex((keccak256ofEthCallExpected)).toByteArray());
    }

    @Test
    void selfDestructCall() throws Exception {
        // Given
        final var senderAddress = toAddress(1043);
        final var senderAlias = Bytes.wrap(recoverAddressFromPubKey(ByteString.copyFrom(
                        Hex.decode("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d"))
                .substring(2)
                .toByteArray()));
        final var senderEntityId = entityIdFromEvmAddress(senderAddress);
        final var senderPublicKey = ByteString.copyFrom(
                Hex.decode("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d"));
        domainBuilder
                .entity()
                .customize(e -> e.id(senderEntityId.getId())
                        .num(senderEntityId.getNum())
                        .evmAddress(senderAlias.toArray())
                        .deleted(false)
                        .alias(senderPublicKey.toByteArray())
                        .balance(10000 * 100_000_000L))
                .persist();

        // When
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        var result = contract.send_destroyContract(senderAlias.toUnprefixedHexString())
                .send();

        // Then
        assertThat(result.getContractAddress()).isEqualTo("0x");
    }

    @Test
    void selfDestructCallWithSystemAccount() {
        // Given
        final var systemAccount = systemAccountEntityCustomizable(e -> e.evmAddress(null));
        final var systemAccountAddress = toAddress(systemAccount.toEntityId());
        // When
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);

        // Then
        assertThatThrownBy(() -> contract.send_destroyContract(systemAccountAddress.toUnprefixedHexString())
                        .send())
                .isInstanceOf(MirrorEvmTransactionException.class)
                .satisfies(ex -> {
                    MirrorEvmTransactionException exception = (MirrorEvmTransactionException) ex;
                    assertEquals(exception.getMessage(), INVALID_SOLIDITY_ADDRESS.name());
                });
    }

    @Test
    void testKZGCall() {
        // Given
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        final var functionCall = contract.send_tryKZGPrecompile();
        // Then
        assertDoesNotThrow(functionCall::send);
    }

    @Test
    void testKZGCallEvm46() {
        // Given
        final var recordFile =
                domainBuilder.recordFile().customize(f -> f.index(EVM_46_BLOCK)).persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_46_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFile.getConsensusStart(), recordFile.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        final var functionCall = contract.send_tryKZGPrecompile();
        // Then
        MirrorEvmTransactionException exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        AssertionsForClassTypes.assertThat(exception.getMessage())
                .isEqualTo(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION.name());
    }

    @Test
    void testTransientStorage() {
        // Given
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        final var functionCall = contract.send_tryTransientStorage();
        // Then
        assertDoesNotThrow(functionCall::send);
    }

    @Test
    void testTransientStorageEvm46() {
        // Given
        final var recordFile =
                domainBuilder.recordFile().customize(f -> f.index(EVM_46_BLOCK)).persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_46_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFile.getConsensusStart(), recordFile.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        // Then
        final var functionCall = contract.send_tryTransientStorage();
        MirrorEvmTransactionException exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        AssertionsForClassTypes.assertThat(exception.getMessage())
                .isEqualTo(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION.name());
    }

    @Test
    void testMCopy() {
        // Given
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        final var functionCall = contract.send_tryMcopy();
        // Then
        assertDoesNotThrow(functionCall::send);
    }

    @Test
    void testMCopyEvm46() {
        // Given
        final var recordFile =
                domainBuilder.recordFile().customize(f -> f.index(EVM_46_BLOCK)).persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_46_BLOCK)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFile.getConsensusStart(), recordFile.getConsensusEnd()));
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        // Then
        final var functionCall = contract.send_tryMcopy();
        MirrorEvmTransactionException exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        AssertionsForClassTypes.assertThat(exception.getMessage())
                .isEqualTo(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION.name());
    }
}
