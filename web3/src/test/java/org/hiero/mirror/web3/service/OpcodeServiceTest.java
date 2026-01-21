// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.node.app.hapi.utils.ethereum.EthTxSigs.extractSignatures;
import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.OUTPUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hiero.mirror.common.domain.entity.EntityType.TOKEN;
import static org.hiero.mirror.common.domain.transaction.TransactionType.ETHEREUMTRANSACTION;
import static org.hiero.mirror.common.util.CommonUtils.instant;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.NEW_ECDSA_KEY;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.NEW_ED25519_KEY;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData.EthTransactionType;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.common.TransactionHashParameter;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import org.hiero.mirror.web3.exception.EntityNotFoundException;
import org.hiero.mirror.web3.service.utils.KeyValueType;
import org.hiero.mirror.web3.utils.EvmEncodingFacade;
import org.hiero.mirror.web3.web3j.generated.DynamicEthCalls;
import org.hiero.mirror.web3.web3j.generated.EvmCodes;
import org.hiero.mirror.web3.web3j.generated.ExchangeRatePrecompile;
import org.hiero.mirror.web3.web3j.generated.NestedCalls;
import org.hiero.mirror.web3.web3j.generated.NestedCalls.HederaToken;
import org.hiero.mirror.web3.web3j.generated.NestedCalls.KeyValue;
import org.hiero.mirror.web3.web3j.generated.NestedCalls.TokenKey;
import org.hiero.mirror.web3.web3j.generated.StorageContract;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.web3j.abi.TypeEncoder;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.tx.Contract;

@RequiredArgsConstructor
class OpcodeServiceTest extends AbstractContractCallServiceOpcodeTracerTest {

    private static final long ZERO_AMOUNT = 0L;
    private static final long DEFAULT_TRANSACTION_VALUE = 100_000_000_000L;
    private static final String SUCCESS_PREFIX = "0x0000000000000000000000000000000000000000000000000000000000000020";

    private final OpcodeService opcodeService;

    @BeforeEach
    void configure() {
        setOpcodeEndpoint();
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
                            CONTRACT_ID,                ADMIN_KEY,
                            CONTRACT_ID,                KYC_KEY,
                            CONTRACT_ID,                FREEZE_KEY,
                            CONTRACT_ID,                WIPE_KEY,
                            CONTRACT_ID,                SUPPLY_KEY,
                            CONTRACT_ID,                FEE_SCHEDULE_KEY,
                            CONTRACT_ID,                PAUSE_KEY,
                            ED25519,                    ADMIN_KEY,
                            ED25519,                    KYC_KEY,
                            ED25519,                    FREEZE_KEY,
                            ED25519,                    WIPE_KEY,
                            ED25519,                    SUPPLY_KEY,
                            ED25519,                    FEE_SCHEDULE_KEY,
                            ED25519,                    PAUSE_KEY,
                            ECDSA_SECPK256K1,           ADMIN_KEY,
                            ECDSA_SECPK256K1,           KYC_KEY,
                            ECDSA_SECPK256K1,           FREEZE_KEY,
                            ECDSA_SECPK256K1,           WIPE_KEY,
                            ECDSA_SECPK256K1,           SUPPLY_KEY,
                            ECDSA_SECPK256K1,           FEE_SCHEDULE_KEY,
                            ECDSA_SECPK256K1,           PAUSE_KEY,
                            DELEGATABLE_CONTRACT_ID,    ADMIN_KEY,
                            DELEGATABLE_CONTRACT_ID,    KYC_KEY,
                            DELEGATABLE_CONTRACT_ID,    FREEZE_KEY,
                            DELEGATABLE_CONTRACT_ID,    WIPE_KEY,
                            DELEGATABLE_CONTRACT_ID,    SUPPLY_KEY,
                            DELEGATABLE_CONTRACT_ID,    FEE_SCHEDULE_KEY,
                            DELEGATABLE_CONTRACT_ID,    PAUSE_KEY,
                            """)
    void updateTokenKeysAndGetUpdatedTokenKeyForFungibleToken(final KeyValueType keyValueType, final KeyType keyType) {
        // Given
        final var token = fungibleTokenPersistWithTreasuryAccount(
                domainBuilder.entity().persist().toEntityId());
        final var tokenAddress = toAddress(token.getTokenId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var contractAddress = contract.getContractAddress();

        final var keyValue = getKeyValueForType(keyValueType, contractAddress);
        final var tokenKey = new TokenKey(keyType.getKeyTypeNumeric(), keyValue);
        final var expectedResult = TypeEncoder.encode(keyValue);
        final var expectedResultBytes = Bytes.fromHexString(expectedResult).toArray();

        final var functionCall = contract.call_updateTokenKeysAndGetUpdatedTokenKey(
                tokenAddress.toHexString(), List.of(tokenKey), keyType.getKeyTypeNumeric());
        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();

        final var options = new OpcodeTracerOptions();
        final var transactionIdOrHash =
                setUpEthereumTransactionWithSenderBalance(contract, callData, ZERO_AMOUNT, expectedResultBytes);

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponseWithExpectedReturnValue(
                opcodesResponse,
                options,
                SUCCESS_PREFIX + expectedResult,
                Address.fromHexString(contract.getContractAddress()));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
                            CONTRACT_ID,                ADMIN_KEY,
                            CONTRACT_ID,                KYC_KEY,
                            CONTRACT_ID,                FREEZE_KEY,
                            CONTRACT_ID,                WIPE_KEY,
                            CONTRACT_ID,                SUPPLY_KEY,
                            CONTRACT_ID,                FEE_SCHEDULE_KEY,
                            CONTRACT_ID,                PAUSE_KEY,
                            ED25519,                    ADMIN_KEY,
                            ED25519,                    KYC_KEY,
                            ED25519,                    FREEZE_KEY,
                            ED25519,                    WIPE_KEY,
                            ED25519,                    SUPPLY_KEY,
                            ED25519,                    FEE_SCHEDULE_KEY,
                            ED25519,                    PAUSE_KEY,
                            ECDSA_SECPK256K1,           ADMIN_KEY,
                            ECDSA_SECPK256K1,           KYC_KEY,
                            ECDSA_SECPK256K1,           FREEZE_KEY,
                            ECDSA_SECPK256K1,           WIPE_KEY,
                            ECDSA_SECPK256K1,           SUPPLY_KEY,
                            ECDSA_SECPK256K1,           FEE_SCHEDULE_KEY,
                            ECDSA_SECPK256K1,           PAUSE_KEY,
                            DELEGATABLE_CONTRACT_ID,    ADMIN_KEY,
                            DELEGATABLE_CONTRACT_ID,    KYC_KEY,
                            DELEGATABLE_CONTRACT_ID,    FREEZE_KEY,
                            DELEGATABLE_CONTRACT_ID,    WIPE_KEY,
                            DELEGATABLE_CONTRACT_ID,    SUPPLY_KEY,
                            DELEGATABLE_CONTRACT_ID,    FEE_SCHEDULE_KEY,
                            DELEGATABLE_CONTRACT_ID,    PAUSE_KEY
                            """)
    void updateTokenKeysAndGetUpdatedTokenKeyForNFT(final KeyValueType keyValueType, final KeyType keyType) {
        // Given
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var token = nftPersist(treasuryEntityId, treasuryEntityId, treasuryEntityId);
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var contractAddress = contract.getContractAddress();

        final var keyValue = getKeyValueForType(keyValueType, contractAddress);
        final var tokenKey = new TokenKey(keyType.getKeyTypeNumeric(), keyValue);
        final var expectedResult = TypeEncoder.encode(keyValue);
        final var expectedResultBytes = Bytes.fromHexString(expectedResult).toArray();
        final var tokenAddress = toAddress(token.getTokenId());
        final var functionCall = contract.call_updateTokenKeysAndGetUpdatedTokenKey(
                tokenAddress.toHexString(), List.of(tokenKey), keyType.getKeyTypeNumeric());
        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();

        final var options = new OpcodeTracerOptions(true, false, false);
        final var transactionIdOrHash =
                setUpEthereumTransactionWithSenderBalance(contract, callData, ZERO_AMOUNT, expectedResultBytes);

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponseWithExpectedReturnValue(
                opcodesResponse,
                options,
                SUCCESS_PREFIX + expectedResult,
                Address.fromHexString(contract.getContractAddress()));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON
            NON_FUNGIBLE_UNIQUE
            """)
    void updateTokenExpiryAndGetUpdatedTokenExpiry(final TokenTypeEnum tokenType) {
        // Given
        final var treasuryEntity = accountEntityPersist();
        final var tokenWithAutoRenewPair = persistTokenWithAutoRenewAndTreasuryAccounts(tokenType, treasuryEntity);
        final var tokenEntityId = tokenWithAutoRenewPair.getLeft();
        final var tokenAddress = toAddress(tokenEntityId.getId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenExpiry = new NestedCalls.Expiry(
                BigInteger.valueOf(Instant.now().getEpochSecond() + 8_000_000L),
                toAddress(tokenWithAutoRenewPair.getRight().toEntityId()).toHexString(),
                BigInteger.valueOf(8_000_000));

        final var functionCall =
                contract.call_updateTokenExpiryAndGetUpdatedTokenExpiry(tokenAddress.toHexString(), tokenExpiry);
        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();
        final var expectedResult = TypeEncoder.encode(tokenExpiry);
        final var expectedResultBytes = Bytes.fromHexString(expectedResult).toArray();

        final var transactionIdOrHash =
                setUpEthereumTransactionWithSenderBalance(contract, callData, ZERO_AMOUNT, expectedResultBytes);

        final var options = new OpcodeTracerOptions(true, false, false);

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponseWithExpectedReturnValue(
                opcodesResponse,
                options,
                HEX_PREFIX + expectedResult,
                Address.fromHexString(contract.getContractAddress()));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON
            NON_FUNGIBLE_UNIQUE
            """)
    void updateTokenInfoAndGetUpdatedTokenInfoSymbol(final TokenTypeEnum tokenType) {
        // Given
        final var treasuryEntity = accountEntityPersist();
        Pair<Entity, Entity> tokenWithAutoRenewPair =
                persistTokenWithAutoRenewAndTreasuryAccounts(tokenType, treasuryEntity);
        final var tokenEntityId = tokenWithAutoRenewPair.getLeft();
        final var tokenAddress = toAddress(tokenEntityId.getId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo = populateHederaToken(
                contract.getContractAddress(),
                tokenType,
                treasuryEntity.toEntityId(),
                tokenWithAutoRenewPair.getRight());

        final var functionCall =
                contract.call_updateTokenInfoAndGetUpdatedTokenInfoSymbol(tokenAddress.toHexString(), tokenInfo);
        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();
        final var expectedResultBytes = tokenInfo.symbol.getBytes();
        final var expectedResult =
                EvmEncodingFacade.encodeSymbol(tokenInfo.symbol).toHexString();

        final var transactionIdOrHash =
                setUpEthereumTransactionWithSenderBalance(contract, callData, ZERO_AMOUNT, expectedResultBytes);
        final var options = new OpcodeTracerOptions(true, false, false);

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);
        // Then
        verifyOpcodesResponseWithExpectedReturnValue(
                opcodesResponse, options, expectedResult, Address.fromHexString(contract.getContractAddress()));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON
            NON_FUNGIBLE_UNIQUE
            """)
    void updateTokenInfoAndGetUpdatedTokenInfoName(final TokenTypeEnum tokenType) {
        // Given
        final var treasuryEntity = accountEntityPersist();
        Pair<Entity, Entity> tokenWithAutoRenewPair =
                persistTokenWithAutoRenewAndTreasuryAccounts(tokenType, treasuryEntity);
        final var tokenEntityId = tokenWithAutoRenewPair.getLeft();
        final var tokenAddress = toAddress(tokenEntityId.getId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo = populateHederaToken(
                contract.getContractAddress(),
                tokenType,
                treasuryEntity.toEntityId(),
                tokenWithAutoRenewPair.getRight());

        final var functionCall =
                contract.call_updateTokenInfoAndGetUpdatedTokenInfoName(tokenAddress.toHexString(), tokenInfo);
        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();
        final var expectedResultBytes = tokenInfo.name.getBytes();
        final var expectedResult = EvmEncodingFacade.encodeName(tokenInfo.name).toHexString();

        final var transactionIdOrHash =
                setUpEthereumTransactionWithSenderBalance(contract, callData, ZERO_AMOUNT, expectedResultBytes);

        final var options = new OpcodeTracerOptions(true, false, false);

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponseWithExpectedReturnValue(
                opcodesResponse, options, expectedResult, Address.fromHexString(contract.getContractAddress()));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON
            NON_FUNGIBLE_UNIQUE
            """)
    void updateTokenInfoAndGetUpdatedTokenInfoMemo(final TokenTypeEnum tokenType) {
        // Given
        final var treasuryEntity = accountEntityPersist();
        Pair<Entity, Entity> tokenWithAutoRenewPair =
                persistTokenWithAutoRenewAndTreasuryAccounts(tokenType, treasuryEntity);
        final var tokenEntityId = tokenWithAutoRenewPair.getLeft();
        final var tokenAddress = toAddress(tokenEntityId.getId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo = populateHederaToken(
                contract.getContractAddress(),
                tokenType,
                treasuryEntity.toEntityId(),
                tokenWithAutoRenewPair.getRight());

        final var functionCall =
                contract.call_updateTokenInfoAndGetUpdatedTokenInfoMemo(tokenAddress.toHexString(), tokenInfo);
        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();
        final var expectedResultBytes = tokenInfo.memo.getBytes();
        final var expectedResult = EvmEncodingFacade.encodeName(tokenInfo.memo).toHexString();

        final var transactionIdOrHash =
                setUpEthereumTransactionWithSenderBalance(contract, callData, ZERO_AMOUNT, expectedResultBytes);

        final var options = new OpcodeTracerOptions(true, false, false);

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponseWithExpectedReturnValue(
                opcodesResponse, options, expectedResult, Address.fromHexString(contract.getContractAddress()));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON
            NON_FUNGIBLE_UNIQUE
            """)
    void deleteTokenAndGetTokenInfoIsDeleted(final TokenTypeEnum tokenType) {
        // Given
        final var treasuryEntity = accountEntityPersist();
        final var tokenEntityId = persistTokenWithAutoRenewAndTreasuryAccounts(tokenType, treasuryEntity)
                .getLeft();
        final var tokenAddress = toAddress(tokenEntityId.getId());
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);

        final var functionCall = contract.call_deleteTokenAndGetTokenInfoIsDeleted(tokenAddress.toHexString());
        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();
        final byte[] expectedResultBytes = {1};
        final var expectedResult = DomainUtils.bytesToHex(DomainUtils.leftPadBytes(expectedResultBytes, Bytes32.SIZE));

        final var transactionIdOrHash =
                setUpEthereumTransactionWithSenderBalance(contract, callData, ZERO_AMOUNT, expectedResultBytes);

        final var options = new OpcodeTracerOptions(true, false, false);

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponseWithExpectedReturnValue(
                opcodesResponse,
                options,
                HEX_PREFIX + expectedResult,
                Address.fromHexString(contract.getContractAddress()));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
                            true, false, true, true
                            false, false, false, false
                            true, true, true, true
                            """)
    void createFungibleTokenAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(
            final boolean withKeys,
            final boolean inheritKey,
            boolean defaultKycStatus,
            final boolean defaultFreezeStatus) {
        // Given
        defaultKycStatus = calculateDefaultKycStatus(defaultKycStatus);
        final var treasuryEntity = accountEntityPersist();
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo = getHederaToken(
                contract.getContractAddress(),
                TokenTypeEnum.FUNGIBLE_COMMON,
                withKeys,
                inheritKey,
                defaultFreezeStatus,
                treasuryEntity);

        final var functionCall =
                contract.call_createFungibleTokenAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(
                        tokenInfo, BigInteger.ONE, BigInteger.ONE);
        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();
        final var expectedResult = getExpectedResultFromBooleans(defaultKycStatus, defaultFreezeStatus, true);
        final var expectedResultBytes = expectedResult.getBytes();

        final var transactionIdOrHash = setUpEthereumTransactionWithSenderBalance(
                contract, callData, DEFAULT_TRANSACTION_VALUE, expectedResultBytes);
        final var options = new OpcodeTracerOptions(true, false, false);

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponseWithExpectedReturnValue(
                opcodesResponse,
                options,
                HEX_PREFIX + expectedResult,
                Address.fromHexString(contract.getContractAddress()));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
                            true, false, true, true
                            false, false, false, false
                            true, true, true, true
                            """)
    void createNFTAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(
            final boolean withKeys,
            final boolean inheritKey,
            boolean defaultKycStatus,
            final boolean defaultFreezeStatus) {
        // Given
        defaultKycStatus = calculateDefaultKycStatus(defaultKycStatus);
        final var treasuryEntity = accountEntityPersist();
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var tokenInfo = getHederaToken(
                contract.getContractAddress(),
                TokenTypeEnum.NON_FUNGIBLE_UNIQUE,
                withKeys,
                inheritKey,
                defaultFreezeStatus,
                treasuryEntity);

        final var functionCall =
                contract.call_createNFTAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(tokenInfo);
        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();
        final var expectedResult = getExpectedResultFromBooleans(defaultKycStatus, defaultFreezeStatus, true);
        final var expectedResultBytes = expectedResult.getBytes();

        final var transactionIdOrHash = setUpEthereumTransactionWithSenderBalance(
                contract, callData, DEFAULT_TRANSACTION_VALUE, expectedResultBytes);
        final var options = new OpcodeTracerOptions(true, false, false);

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponseWithExpectedReturnValue(
                opcodesResponse,
                options,
                HEX_PREFIX + expectedResult,
                Address.fromHexString(contract.getContractAddress()));
    }

    @ParameterizedTest
    @CsvSource({"true, true", "false, true", "true, false", "false, false"})
    void testGetUpdatedStorageCall(final boolean stack, final boolean memory) {
        final var senderEntity = accountPersistWithAccountBalances();
        final var contract = testWeb3jService.deploy(StorageContract::deploy);
        final var options = new OpcodeTracerOptions(stack, memory, true);
        final var functionCall = contract.send_updateStorage(BigInteger.ONE, BigInteger.TEN);

        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();
        final var transactionIdOrHash = setUp(
                ETHEREUMTRANSACTION,
                contract,
                callData,
                true,
                true,
                senderEntity.toEntityId(),
                ZERO_AMOUNT,
                domainBuilder.timestamp());

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponse(opcodesResponse, options, Address.fromHexString(contract.getContractAddress()));
    }

    @ParameterizedTest
    @CsvSource({"true, true", "false, true", "true, false", "false, false"})
    void testComplexStorageUpdates(final boolean stack, final boolean memory) {
        final var senderEntity = accountPersistWithAccountBalances();
        final var contract = testWeb3jService.deploy(StorageContract::deploy);
        final var options = new OpcodeTracerOptions(stack, memory, true);
        final var functionCall = contract.send_complexUpdate(BigInteger.ONE, BigInteger.TWO);

        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();
        final var transactionIdOrHash = setUp(
                ETHEREUMTRANSACTION,
                contract,
                callData,
                true,
                true,
                senderEntity.toEntityId(),
                ZERO_AMOUNT,
                domainBuilder.timestamp());

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponse(opcodesResponse, options, Address.fromHexString(contract.getContractAddress()));
    }

    @Test
    void testNativePrecompileCall() {
        final var senderEntity = accountPersistWithAccountBalances();
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        final var options = new OpcodeTracerOptions(true, true, true);
        final var functionCall = contract.send_calculateSHA256();

        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();
        final var transactionIdOrHash = setUp(
                ETHEREUMTRANSACTION,
                contract,
                callData,
                true,
                true,
                senderEntity.toEntityId(),
                ZERO_AMOUNT,
                domainBuilder.timestamp());

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponse(opcodesResponse, options, Address.fromHexString(contract.getContractAddress()));
        opcodesResponse.getOpcodes().forEach(opcode -> assertThat(opcode.getGasCost())
                .isNotNull());
    }

    @ParameterizedTest
    @CsvSource({
        "true, true, true",
        "false, true, true",
        "true, false, true",
        "true, true, false",
        "false, false, true",
        "false, true, false",
        "true, false, false",
        "false, false, false"
    })
    void callWithDifferentCombinationsOfTracerOptions(
            final boolean stack, final boolean memory, final boolean storage) {
        // Given
        final var senderEntity = accountPersistWithAccountBalances();
        final var treasuryEntity = accountEntityPersist();
        final var treasuryAddress = toAddress(treasuryEntity.getId());

        final var tokenEntity = persistTokenWithAutoRenewAndTreasuryAccounts(
                        TokenTypeEnum.FUNGIBLE_COMMON, treasuryEntity)
                .getLeft();
        final var tokenAddress = toAddress(tokenEntity.getId());
        final var options = new OpcodeTracerOptions(stack, memory, storage);
        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        final var functionCall = contract.send_mintTokenGetTotalSupplyAndBalanceOfTreasury(
                tokenAddress.toHexString(), BigInteger.valueOf(100), List.of(), treasuryAddress.toHexString());

        final var callData =
                Bytes.fromHexString(functionCall.encodeFunctionCall()).toArray();
        final var transactionIdOrHash = setUp(
                ETHEREUMTRANSACTION,
                contract,
                callData,
                true,
                true,
                senderEntity.toEntityId(),
                ZERO_AMOUNT,
                domainBuilder.timestamp());

        // When
        final var opcodesResponse = opcodeService.processOpcodeCall(transactionIdOrHash, options);

        // Then
        verifyOpcodesResponse(opcodesResponse, options, Address.fromHexString(contract.getContractAddress()));
    }

    @Test
    void callWithContractResultNotFoundExceptionTest() {
        // Given
        final var contract = testWeb3jService.deploy(ExchangeRatePrecompile::deploy);
        final var functionCall = contract.call_tinybarsToTinycents(BigInteger.TEN);
        final var callData = functionCall.encodeFunctionCall().getBytes();
        final var senderEntity = accountEntityPersist();
        final var consensusTimestamp = domainBuilder.timestamp();
        final var transactionIdOrHash = setUp(
                TransactionType.CONTRACTCALL,
                contract,
                callData,
                true,
                false,
                senderEntity.toEntityId(),
                DEFAULT_TRANSACTION_VALUE,
                consensusTimestamp);
        final var options = new OpcodeTracerOptions(true, false, false);

        // Then
        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> opcodeService.processOpcodeCall(transactionIdOrHash, options))
                .withMessage("Contract result not found: " + consensusTimestamp);
    }

    @Test
    void callWithTransactionNotFoundExceptionTest() {
        // Given
        final var contract = testWeb3jService.deploy(ExchangeRatePrecompile::deploy);
        final var functionCall = contract.call_tinybarsToTinycents(BigInteger.TEN);
        final var callData = functionCall.encodeFunctionCall().getBytes();
        final var senderEntity = accountEntityPersist();

        final var transactionIdOrHash = setUp(
                TransactionType.CONTRACTCALL,
                contract,
                callData,
                false,
                true,
                senderEntity.toEntityId(),
                DEFAULT_TRANSACTION_VALUE,
                domainBuilder.timestamp());
        final var options = new OpcodeTracerOptions(true, false, false);

        // Then
        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> opcodeService.processOpcodeCall(transactionIdOrHash, options))
                .withMessage("Transaction not found: " + transactionIdOrHash);
    }

    @Test
    void callWithContractTransactionHashNotFoundExceptionTest() {
        // Given
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        final var senderEntity = accountEntityPersist();
        final var treasuryEntity = accountEntityPersist();
        Pair<Entity, Entity> tokenWithAutoRenewPair =
                persistTokenWithAutoRenewAndTreasuryAccounts(TokenTypeEnum.FUNGIBLE_COMMON, treasuryEntity);
        final var tokenInfo = populateHederaToken(
                contract.getContractAddress(),
                TokenTypeEnum.FUNGIBLE_COMMON,
                treasuryEntity.toEntityId(),
                tokenWithAutoRenewPair.getRight());

        final var options = new OpcodeTracerOptions(true, false, false);
        final var functionCall =
                contract.call_createFungibleTokenAndGetIsTokenAndGetDefaultFreezeStatusAndGetDefaultKycStatus(
                        tokenInfo, BigInteger.ONE, BigInteger.ONE);
        final var transactionIdOrHash = setUp(
                ETHEREUMTRANSACTION,
                contract,
                functionCall.encodeFunctionCall().getBytes(),
                false,
                true,
                senderEntity.toEntityId(),
                DEFAULT_TRANSACTION_VALUE,
                domainBuilder.timestamp());

        // Then
        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> opcodeService.processOpcodeCall(transactionIdOrHash, options))
                .withMessage("Contract transaction hash not found: " + transactionIdOrHash);
    }

    private byte getByteFromBoolean(final boolean bool) {
        return (byte) (bool ? 1 : 0);
    }

    private String getExpectedResultFromBooleans(Boolean... booleans) {
        StringBuilder result = new StringBuilder();
        for (Boolean booleanValue : booleans) {
            final var byteValue = getByteFromBoolean(booleanValue);
            result.append(DomainUtils.bytesToHex(DomainUtils.leftPadBytes(new byte[] {byteValue}, Bytes32.SIZE)));
        }
        return result.toString();
    }

    private KeyValue getKeyValueForType(final KeyValueType keyValueType, String contractAddress) {
        return switch (keyValueType) {
            case INHERIT_ACCOUNT_KEY ->
                new KeyValue(
                        Boolean.TRUE, Address.ZERO.toHexString(), new byte[0], new byte[0], Address.ZERO.toHexString());
            case CONTRACT_ID ->
                new KeyValue(Boolean.FALSE, contractAddress, new byte[0], new byte[0], Address.ZERO.toHexString());
            case ED25519 ->
                new KeyValue(
                        Boolean.FALSE,
                        Address.ZERO.toHexString(),
                        NEW_ED25519_KEY,
                        new byte[0],
                        Address.ZERO.toHexString());
            case ECDSA_SECPK256K1 ->
                new KeyValue(
                        Boolean.FALSE,
                        Address.ZERO.toHexString(),
                        new byte[0],
                        NEW_ECDSA_KEY,
                        Address.ZERO.toHexString());
            case DELEGATABLE_CONTRACT_ID ->
                new KeyValue(Boolean.FALSE, Address.ZERO.toHexString(), new byte[0], new byte[0], contractAddress);
            default -> throw new RuntimeException("Unsupported key type: " + keyValueType.name());
        };
    }

    private TransactionIdOrHashParameter setUp(
            final TransactionType transactionType,
            final Contract contract,
            final byte[] callData,
            final boolean persistTransaction,
            final boolean persistContractResult,
            final EntityId senderEntityId,
            final long transactionValue,
            final long consensusTimestamp) {
        return setUpForSuccessWithExpectedResult(
                transactionType,
                contract,
                callData,
                persistTransaction,
                persistContractResult,
                senderEntityId,
                transactionValue,
                consensusTimestamp);
    }

    private TransactionIdOrHashParameter setUpEthereumTransactionWithSenderBalance(
            final Contract contract, final byte[] callData, final long transactionValue, final byte[] expectedResult) {
        // Create sender with sufficient balance for value transfer
        // With dynamic balance validation, value > 0 triggers validation
        final Entity senderEntity;
        if (transactionValue > 0) {
            senderEntity = accountEntityWithSufficientBalancePersist();
            // Persist account balance records
            domainBuilder
                    .accountBalance()
                    .customize(ab -> ab.id(new AccountBalance.Id(
                                    senderEntity.getCreatedTimestamp(), systemEntity.treasuryAccount()))
                            .balance(senderEntity.getBalance()))
                    .persist();
            domainBuilder
                    .accountBalance()
                    .customize(ab -> ab.id(new AccountBalance.Id(
                                    senderEntity.getCreatedTimestamp(), senderEntity.toEntityId()))
                            .balance(senderEntity.getBalance()))
                    .persist();
        } else {
            senderEntity = accountEntityWithEvmAddressPersist();
        }
        return setUpForSuccessWithExpectedResultAndBalance(
                ETHEREUMTRANSACTION,
                contract,
                callData,
                true,
                true,
                senderEntity.toEntityId(),
                transactionValue,
                expectedResult,
                senderEntity.getCreatedTimestamp() + 1);
    }

    private TransactionIdOrHashParameter setUpForSuccessWithExpectedResult(
            final TransactionType transactionType,
            final Contract contract,
            final byte[] callData,
            final boolean persistTransaction,
            final boolean persistContractResult,
            final EntityId senderEntityId,
            final long transactionValue,
            final long consensusTimestamp) {
        return setUpForSuccessWithExpectedResultAndBalance(
                transactionType,
                contract,
                callData,
                persistTransaction,
                persistContractResult,
                senderEntityId,
                transactionValue,
                null,
                consensusTimestamp);
    }

    private TransactionIdOrHashParameter setUpForSuccessWithExpectedResultAndBalance(
            final TransactionType transactionType,
            final Contract contract,
            final byte[] callData,
            final boolean persistTransaction,
            final boolean persistContractResult,
            final EntityId senderEntityId,
            final long transactionValue,
            final byte[] expectedResult,
            final long consensusTimestamp) {

        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var transaction = persistTransaction(
                consensusTimestamp, contractEntityId, senderEntityId, transactionType, persistTransaction);

        final EthereumTransaction ethTransaction;
        if (transactionType == ETHEREUMTRANSACTION) {
            ethTransaction = persistEthereumTransaction(
                    callData, consensusTimestamp, contract.getContractAddress(), persistTransaction, transactionValue);
        } else {
            ethTransaction = null;
        }
        final var contractResult = createContractResult(
                consensusTimestamp,
                contractEntityId,
                callData,
                senderEntityId,
                transaction,
                persistContractResult,
                transactionValue);

        persistContractActionsWithExpectedResult(
                senderEntityId,
                consensusTimestamp,
                contractEntityId,
                contract.getContractAddress(),
                expectedResult,
                transactionValue);

        if (persistTransaction && ethTransaction != null) {
            persistContractTransactionHash(
                    consensusTimestamp, contractEntityId, ethTransaction.getHash(), senderEntityId, contractResult);
        }

        if (ethTransaction != null) {
            return new TransactionHashParameter(Bytes.of(ethTransaction.getHash()));
        } else {
            return new TransactionIdParameter(transaction.getPayerAccountId(), instant(transaction.getValidStartNs()));
        }
    }

    private Transaction persistTransaction(
            final long consensusTimestamp,
            final EntityId contractEntityId,
            final EntityId senderEntityId,
            final TransactionType transactionType,
            final boolean persistTransaction) {
        final var validStartNs = consensusTimestamp - 1;

        final var transactionBuilder = domainBuilder.transaction().customize(transaction -> transaction
                .consensusTimestamp(consensusTimestamp)
                .entityId(contractEntityId)
                .payerAccountId(senderEntityId)
                .type(transactionType.getProtoId())
                .validStartNs(validStartNs));

        return persistTransaction ? transactionBuilder.persist() : transactionBuilder.get();
    }

    @SneakyThrows
    private EthereumTransaction persistEthereumTransaction(
            final byte[] callData,
            final long consensusTimestamp,
            final String contractAddress,
            final boolean persistTransaction,
            final long value) {

        final var gasPrice = domainBuilder.bytes(32);
        final var calculatedValue = value > 0 ? BigInteger.valueOf(value) : BigInteger.valueOf(ZERO_AMOUNT);
        final var chainIdBytes = Hex.decode("012a");
        final long nonce = 1L;

        // EIP-2930 signing payload: 0x01 || rlp([chainId, nonce, gasPrice, gasLimit, to, value, data, accessList])
        final var signingPayload = Bytes.concatenate(
                Bytes.of((byte) 1),
                Bytes.wrap(RLPEncoder.list(
                        chainIdBytes,
                        Integers.toBytes(nonce),
                        gasPrice,
                        Integers.toBytes(TRANSACTION_GAS_LIMIT),
                        Address.fromHexString(contractAddress).toArray(),
                        Integers.toBytesUnsigned(calculatedValue),
                        callData,
                        List.of())));

        final ECKeyPair ecKeyPair = Keys.createEcKeyPair();
        final Sign.SignatureData signatureData =
                Sign.signMessage(Hash.sha3(signingPayload.toArray()), ecKeyPair, false);

        accountEntityPersistCustomizable(e -> e.alias(ecKeyPair.getPublicKey().toByteArray()));

        final var recoveryId = signatureData.getV()[0] - 27;
        final var signatureR = signatureData.getR();
        final var signatureS = signatureData.getS();
        final var signatureV = signatureData.getV();

        final var rawTransaction = RLPEncoder.sequence(
                Integers.toBytes(1),
                List.of(
                        chainIdBytes,
                        Integers.toBytes(nonce),
                        gasPrice,
                        Integers.toBytes(TRANSACTION_GAS_LIMIT),
                        Address.fromHexString(contractAddress).toArray(),
                        Integers.toBytesUnsigned(calculatedValue),
                        callData,
                        List.of(),
                        Integers.toBytes(recoveryId),
                        signatureR,
                        signatureS));
        final var calculatedEthHash = Hash.sha3(rawTransaction);

        final var ethData = new EthTxData(
                rawTransaction,
                EthTransactionType.EIP2930,
                chainIdBytes,
                nonce,
                gasPrice,
                null,
                null,
                TRANSACTION_GAS_LIMIT,
                Address.fromHexString(contractAddress).toArray(),
                BigInteger.valueOf(value),
                callData,
                new byte[] {},
                new Object[0],
                recoveryId,
                null,
                signatureR,
                signatureS);
        final var signatures = extractSignatures(ethData);
        accountEntityPersistCustomizable(e -> e.evmAddress(signatures.address()));

        final var ethTransactionBuilder = domainBuilder
                .ethereumTransaction(true)
                .customize(ethereumTransaction -> ethereumTransaction
                        .accessList(null)
                        .type(1)
                        .chainId(chainIdBytes)
                        .nonce(nonce)
                        .callData(callData)
                        .data(rawTransaction)
                        .consensusTimestamp(consensusTimestamp)
                        .gasLimit(TRANSACTION_GAS_LIMIT)
                        .gasPrice(gasPrice)
                        .hash(calculatedEthHash)
                        .toAddress(Address.fromHexString(contractAddress).toArray())
                        .recoveryId(recoveryId)
                        .signatureR(signatureR)
                        .signatureS(signatureS)
                        .signatureV(signatureV)
                        .value(calculatedValue.toByteArray()));
        final var ethereumTransaction =
                persistTransaction ? ethTransactionBuilder.persist() : ethTransactionBuilder.get();
        return ethereumTransaction;
    }

    private ContractResult createContractResult(
            final long consensusTimestamp,
            final EntityId contractEntityId,
            final byte[] callData,
            final EntityId senderEntityId,
            final Transaction transaction,
            final boolean persistContractResult,
            final long value) {
        final var contractResultBuilder = domainBuilder.contractResult().customize(contractResult -> contractResult
                .amount(value > 0 ? value : ZERO_AMOUNT)
                .consensusTimestamp(consensusTimestamp)
                .contractId(contractEntityId.getId())
                .functionParameters(callData)
                .gasLimit(TRANSACTION_GAS_LIMIT)
                .senderId(senderEntityId)
                .transactionHash(transaction.getTransactionHash()));
        return persistContractResult ? contractResultBuilder.persist() : contractResultBuilder.get();
    }

    private void persistContractActionsWithExpectedResult(
            final EntityId senderEntityId,
            final long consensusTimestamp,
            final EntityId contractEntityId,
            final String contractAddress,
            final byte[] expectedResult,
            final long value) {
        domainBuilder
                .contractAction()
                .customize(contractAction -> contractAction
                        .caller(senderEntityId)
                        .callerType(EntityType.ACCOUNT)
                        .consensusTimestamp(consensusTimestamp)
                        .payerAccountId(senderEntityId)
                        .recipientContract(contractEntityId)
                        .recipientAddress(contractAddress.getBytes())
                        .gas(TRANSACTION_GAS_LIMIT)
                        .resultData(expectedResult)
                        .resultDataType(OUTPUT.getNumber())
                        .value(value > 0 ? value : ZERO_AMOUNT))
                .persist();
    }

    private void persistContractTransactionHash(
            final long consensusTimestamp,
            final EntityId contractEntityId,
            final byte[] ethHash,
            final EntityId senderEntityId,
            final ContractResult contractResult) {
        domainBuilder
                .contractTransactionHash()
                .customize(contractTransactionHash -> contractTransactionHash
                        .consensusTimestamp(consensusTimestamp)
                        .entityId(contractEntityId.getId())
                        .hash(ethHash)
                        .payerAccountId(senderEntityId.getId())
                        .transactionResult(contractResult.getTransactionResult()))
                .persist();
    }

    private Entity accountPersistWithAccountBalances() {
        final var entity = accountEntityWithEvmAddressPersist();

        domainBuilder
                .accountBalance()
                .customize(
                        ab -> ab.id(new AccountBalance.Id(entity.getCreatedTimestamp(), systemEntity.treasuryAccount()))
                                .balance(entity.getBalance()))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(entity.getCreatedTimestamp(), entity.toEntityId()))
                        .balance(entity.getBalance()))
                .persist();

        return entity;
    }

    private NestedCalls.HederaToken populateHederaToken(
            final String contractAddress,
            final TokenTypeEnum tokenType,
            final EntityId treasuryAccountId,
            Entity autoRenewAccount) {
        // expiration
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(TOKEN).autoRenewAccountId(autoRenewAccount.getId()))
                .persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(tokenType).treasuryAccountId(treasuryAccountId))
                .persist();

        final var supplyKey = new NestedCalls.KeyValue(
                Boolean.FALSE,
                contractAddress,
                new byte[0],
                new byte[0],
                Address.ZERO.toHexString()); // the key needed for token minting or burning
        final var keys = new ArrayList<TokenKey>();
        keys.add(new NestedCalls.TokenKey(
                AbstractContractCallServiceTest.KeyType.SUPPLY_KEY.getKeyTypeNumeric(), supplyKey));
        return new NestedCalls.HederaToken(
                token.getName(),
                token.getSymbol(),
                getAddressFromEntityId(treasuryAccountId), // id of the account holding the initial token supply
                tokenEntity.getMemo(), // token description encoded in UTF-8 format
                true,
                BigInteger.valueOf(10_000L),
                false,
                keys,
                new NestedCalls.Expiry(
                        BigInteger.valueOf(Instant.now().getEpochSecond() + 8_000_000L),
                        getAddressFromEntity(autoRenewAccount),
                        BigInteger.valueOf(8_000_000)));
    }

    private HederaToken getHederaToken(
            final String contractAddress,
            final TokenTypeEnum tokenType,
            final boolean withKeys,
            final boolean inheritAccountKey,
            final boolean freezeDefault,
            final Entity treasuryEntity) {
        final List<TokenKey> tokenKeys = new LinkedList<>();
        final var keyType = inheritAccountKey ? KeyValueType.INHERIT_ACCOUNT_KEY : KeyValueType.ECDSA_SECPK256K1;
        if (withKeys) {
            tokenKeys.add(new TokenKey(KeyType.KYC_KEY.getKeyTypeNumeric(), getKeyValueForType(keyType, null)));
            tokenKeys.add(new TokenKey(KeyType.FREEZE_KEY.getKeyTypeNumeric(), getKeyValueForType(keyType, null)));
        }

        return populateHederaToken(contractAddress, tokenType, treasuryEntity.toEntityId(), freezeDefault, tokenKeys);
    }

    private NestedCalls.HederaToken populateHederaToken(
            final String contractAddress,
            final TokenTypeEnum tokenType,
            final EntityId treasuryAccountId,
            boolean freezeDefault,
            List<TokenKey> tokenKeys) {
        final var autoRenewAccount =
                accountEntityWithEvmAddressPersist(); // the account that is going to be charged for token renewal upon
        // expiration
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(TOKEN).autoRenewAccountId(autoRenewAccount.getId()))
                .persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(tokenType).treasuryAccountId(treasuryAccountId))
                .persist();

        final var supplyKey = new NestedCalls.KeyValue(
                Boolean.FALSE,
                contractAddress,
                new byte[0],
                new byte[0],
                Address.ZERO.toHexString()); // the key needed for token minting or burning
        tokenKeys.add(new NestedCalls.TokenKey(
                AbstractContractCallServiceTest.KeyType.SUPPLY_KEY.getKeyTypeNumeric(), supplyKey));

        return new NestedCalls.HederaToken(
                token.getName(),
                token.getSymbol(),
                getAddressFromEntityId(treasuryAccountId), // id of the account holding the initial token supply
                tokenEntity.getMemo(), // token description encoded in UTF-8 format
                true,
                BigInteger.valueOf(10_000L),
                freezeDefault,
                tokenKeys,
                new NestedCalls.Expiry(
                        BigInteger.valueOf(Instant.now().getEpochSecond() + 8_000_000L),
                        getAliasFromEntity(autoRenewAccount),
                        BigInteger.valueOf(8_000_000)));
    }

    /**
     * Adjusts the default KYC status based on the modularized services flag.
     * <p>
     * In modularized services, the KYC status behaves inversely compared to mono: - Without a KYC key:
     * `KycNotApplicable` -> returns `TRUE`. - With a KYC key: Initial status is `Revoked` -> returns `FALSE`. This
     * method toggles the status when modularized services are enabled.
     *
     * @param defaultKycStatus The initial KYC status boolean.
     * @return The adjusted KYC status boolean.
     */
    private boolean calculateDefaultKycStatus(boolean defaultKycStatus) {
        return !defaultKycStatus;
    }
}
