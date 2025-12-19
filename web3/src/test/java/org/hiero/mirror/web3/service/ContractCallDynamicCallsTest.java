// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.state.MirrorNodeState;
import org.hiero.mirror.web3.utils.ContractFunctionProviderRecord;
import org.hiero.mirror.web3.web3j.generated.DynamicEthCalls;
import org.hiero.mirror.web3.web3j.generated.DynamicEthCalls.AccountAmount;
import org.hiero.mirror.web3.web3j.generated.DynamicEthCalls.NftTransfer;
import org.hiero.mirror.web3.web3j.generated.DynamicEthCalls.TokenTransferList;
import org.hiero.mirror.web3.web3j.generated.DynamicEthCalls.TransferList;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ContractCallDynamicCallsTest extends AbstractContractCallServiceOpcodeTracerTest {

    private static final BigInteger DEFAULT_TOKEN_AMOUNT = BigInteger.ONE;
    private static final List<BigInteger> EMPTY_SERIAL_NUMBERS_LIST = List.of();

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            FUNGIBLE_COMMON,        100,
                            NON_FUNGIBLE_UNIQUE,    0,      NftMetadata
                            """)
    void mintTokenGetTotalSupplyAndBalanceOfTreasury(
            final TokenTypeEnum tokenType, final long amount, final String metadata) {
        // Given
        final var treasury = accountEntityPersist();
        final var treasuryAddress = toAddress(treasury.getId());

        final var tokenEntity = persistTokenWithAutoRenewAndTreasuryAccounts(tokenType, treasury)
                .getLeft();
        final var tokenAddress = toAddress(tokenEntity.getId());

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_mintTokenGetTotalSupplyAndBalanceOfTreasury(
                tokenAddress.toHexString(),
                BigInteger.valueOf(amount),
                metadata == null ? List.of() : List.of(metadata.getBytes()),
                treasuryAddress.toHexString());

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void mintMultipleNftTokensGetTotalSupply() throws Exception {
        // Given
        final var treasury = accountEntityPersist();

        final var tokenEntity = persistTokenWithAutoRenewAndTreasuryAccounts(
                        TokenTypeEnum.NON_FUNGIBLE_UNIQUE, treasury)
                .getLeft();
        final var tokenAddress = toAddress(tokenEntity.getId());

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.call_mintMultipleNftTokensGetTotalSupplyExternal(
                tokenAddress.toHexString(), List.of(domainBuilder.bytes(12)), List.of(domainBuilder.bytes(12)));

        final var functionCallWithSend = contract.send_mintMultipleNftTokensGetTotalSupplyExternal(
                tokenAddress.toHexString(), List.of(domainBuilder.bytes(12)), List.of(domainBuilder.bytes(12)));

        // Then
        final var result = functionCall.send();

        BigInteger firstSerialNumber = result.component1().getLast();
        assertThat(firstSerialNumber).isEqualTo(BigInteger.ONE);

        BigInteger secondSerialNumber = result.component2().getLast();
        assertThat(secondSerialNumber).isEqualTo(BigInteger.TWO);

        verifyEthCallAndEstimateGas(functionCallWithSend, contract);
        verifyOpcodeTracerCall(functionCallWithSend.encodeFunctionCall(), contract);
    }

    @Test
    void mintNftAndBurnNft() {
        // Given
        final var treasury = accountEntityPersist();

        final var tokenEntity = persistTokenWithAutoRenewAndTreasuryAccounts(
                        TokenTypeEnum.NON_FUNGIBLE_UNIQUE, treasury)
                .getLeft();
        final var tokenAddress = toAddress(tokenEntity.getId());

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall =
                contract.send_mintNftAndBurnNft(tokenAddress.toHexString(), List.of(domainBuilder.bytes(12)));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON,
            NON_FUNGIBLE_UNIQUE
            """)
    void burnTokenGetTotalSupplyAndBalanceOfTreasury(final TokenTypeEnum tokenType) {
        // Given
        final var treasuryAccount = accountEntityPersist();
        final var tokenEntity = tokenEntityPersist();

        tokenAccountPersist(tokenEntity.getId(), treasuryAccount.getId());

        if (tokenType.equals(TokenTypeEnum.FUNGIBLE_COMMON)) {
            fungibleTokenPersist(tokenEntity, treasuryAccount);
        } else {
            var token = nonFungibleTokenPersist(tokenEntity, treasuryAccount);
            nonFungibleTokenInstancePersist(token, 1L, treasuryAccount.toEntityId(), treasuryAccount.toEntityId());
        }

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = tokenType.equals(TokenTypeEnum.FUNGIBLE_COMMON)
                ? contract.send_burnTokenGetTotalSupplyAndBalanceOfTreasury(
                        getAddressFromEntity(tokenEntity),
                        DEFAULT_TOKEN_AMOUNT,
                        EMPTY_SERIAL_NUMBERS_LIST,
                        getAddressFromEntity(treasuryAccount))
                : contract.send_burnTokenGetTotalSupplyAndBalanceOfTreasury(
                        getAddressFromEntity(tokenEntity),
                        BigInteger.ZERO,
                        DEFAULT_SERIAL_NUMBERS_LIST,
                        getAddressFromEntity(treasuryAccount));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON,
            NON_FUNGIBLE_UNIQUE
            """)
    void wipeTokenGetTotalSupplyAndBalanceOfTreasury(final TokenTypeEnum tokenType) {
        // Given
        final var treasuryAccount = accountEntityPersist();
        final var sender = accountEntityPersist();

        final var tokenEntity = setUpToken(tokenType, treasuryAccount, sender, sender);

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_wipeTokenGetTotalSupplyAndBalanceOfTreasury(
                getAddressFromEntity(tokenEntity),
                DEFAULT_TOKEN_AMOUNT,
                tokenType.equals(TokenTypeEnum.FUNGIBLE_COMMON)
                        ? EMPTY_SERIAL_NUMBERS_LIST
                        : DEFAULT_SERIAL_NUMBERS_LIST,
                getAddressFromEntity(sender));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON,
            NON_FUNGIBLE_UNIQUE
            """)
    void pauseTokenGetPauseStatusUnpauseGetPauseStatus(final TokenTypeEnum tokenType) {
        // Given
        final var treasuryAccount = accountEntityPersist();
        final var sender = accountEntityPersist();

        final var tokenEntity = setUpToken(tokenType, treasuryAccount, sender, sender);
        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall =
                contract.send_pauseTokenGetPauseStatusUnpauseGetPauseStatus(getAddressFromEntity(tokenEntity));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    /**
     * The test calls HederaTokenService.freezeToken(token, account) precompiled system contract to freeze a given
     * fungible/non-fungible token for a given account.
     *
     * @param tokenType
     */
    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON,
            NON_FUNGIBLE_UNIQUE
            """)
    void freezeTokenGetPauseStatusUnpauseGetPauseStatus(final TokenTypeEnum tokenType) {
        // Given
        final var treasuryAccount = accountEntityPersist();
        final var sender = accountEntityPersist();

        final var tokenEntity = setUpToken(tokenType, treasuryAccount, sender, sender);

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_freezeTokenGetPauseStatusUnpauseGetPauseStatus(
                getAddressFromEntity(tokenEntity), getAddressFromEntity(treasuryAccount));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void associateTokenTransferEthCallFail() {
        // Given
        final var treasuryAccount = accountEntityPersist();
        final var sender = accountEntityPersist();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_associateTokenTransfer(
                toAddress(EntityId.of(21496934L)).toHexString(), // Not existing address
                getAddressFromEntity(treasuryAccount),
                getAddressFromEntity(sender),
                BigInteger.ZERO,
                DEFAULT_SERIAL_NUMBER);

        final var contractFunctionProvider = ContractFunctionProviderRecord.builder()
                .contractAddress(Address.fromHexString(contract.getContractAddress()))
                .expectedErrorMessage("Failed to associate tokens")
                .build();

        // Then
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .satisfies(ex -> {
                    MirrorEvmTransactionException exception = (MirrorEvmTransactionException) ex;
                    assertEquals("Failed to associate tokens", exception.getDetail());
                });

        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contractFunctionProvider);
    }

    @Test
    void associateTokenTransferEthCallFails() throws InvocationTargetException, IllegalAccessException {
        // Given
        final var backupProperties = mirrorNodeEvmProperties.getProperties();

        try {
            // Re-init the captors, because the flag was changed.
            super.setUpArgumentCaptors();
            Method postConstructMethod = Arrays.stream(MirrorNodeState.class.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(PostConstruct.class))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("@PostConstruct method not found"));

            postConstructMethod.setAccessible(true); // Make the method accessible
            postConstructMethod.invoke(state);

            final Map<String, String> propertiesMap = new HashMap<>();
            propertiesMap.put("contracts.maxRefundPercentOfGasLimit", "100");
            propertiesMap.put("contracts.maxGasPerSec", "15000000");
            mirrorNodeEvmProperties.setProperties(propertiesMap);

            final var treasuryAccount = accountEntityPersist();
            final var sender = accountEntityPersist();

            final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

            // When
            final var functionCall = contract.send_associateTokenTransfer(
                    toAddress(EntityId.of(21496934L)).toHexString(), // Not existing address
                    getAddressFromEntity(treasuryAccount),
                    getAddressFromEntity(sender),
                    BigInteger.ZERO,
                    DEFAULT_SERIAL_NUMBER);

            final var contractFunctionProvider = ContractFunctionProviderRecord.builder()
                    .contractAddress(Address.fromHexString(contract.getContractAddress()))
                    .expectedErrorMessage("Failed to associate tokens")
                    .build();

            // Then
            assertThatThrownBy(functionCall::send)
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .satisfies(ex -> {
                        MirrorEvmTransactionException exception = (MirrorEvmTransactionException) ex;
                        assertEquals("Failed to associate tokens", exception.getDetail());
                    });

            verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contractFunctionProvider);
        } finally {
            // Restore changed property values.
            mirrorNodeEvmProperties.setProperties(backupProperties);
        }
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            FUNGIBLE_COMMON,        1,      0
                            NON_FUNGIBLE_UNIQUE,    0,      1
                            """)
    void associateTokenTransfer(final TokenTypeEnum tokenType, final long amount, final long serialNumber) {
        // Given
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var treasuryAddress = toAddress(treasuryEntityId);
        final var senderEntityId = accountEntityPersist().toEntityId();
        final var senderAddress = toAddress(senderEntityId);

        final var tokenEntity = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenCustomizable(
                        t -> t.treasuryAccountId(treasuryEntityId).kycKey(null))
                : nftPersistWithNullKycKey(treasuryEntityId);
        final var tokenAddress = toAddress(tokenEntity.getTokenId());

        tokenAccountPersist(tokenEntity.getTokenId(), treasuryEntityId.getId());

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_associateTokenTransfer(
                tokenAddress.toHexString(),
                treasuryAddress.toHexString(),
                senderAddress.toHexString(),
                BigInteger.valueOf(amount),
                BigInteger.valueOf(serialNumber));
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            FUNGIBLE_COMMON,        1,      0,  IERC20: failed to transfer
                            NON_FUNGIBLE_UNIQUE,    0,      1,  IERC721: failed to transfer
                            """)
    void associateTokenDissociateFailTransferEthCall(
            final TokenTypeEnum tokenType,
            final long amount,
            final long serialNumber,
            final String expectedErrorMessage) {
        // Given
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var ownerEntityId = accountEntityPersist().toEntityId();
        final var ownerAddress = toAddress(ownerEntityId);
        final var senderEntityId = accountEntityPersist().toEntityId();
        final var senderAddress = toAddress(senderEntityId);

        final var token = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersistWithTreasuryAccount(treasuryEntityId)
                : nftPersist(treasuryEntityId, ownerEntityId, ownerEntityId);
        final var tokenAddress = toAddress(token.getTokenId());

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_associateTokenDissociateFailTransfer(
                tokenAddress.toHexString(),
                ownerAddress.toHexString(),
                senderAddress.toHexString(),
                BigInteger.valueOf(amount),
                BigInteger.valueOf(serialNumber));

        final var contractFunctionProvider = ContractFunctionProviderRecord.builder()
                .contractAddress(Address.fromHexString(contract.getContractAddress()))
                .expectedErrorMessage(expectedErrorMessage)
                .build();

        // Then
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .satisfies(ex -> {
                    MirrorEvmTransactionException exception = (MirrorEvmTransactionException) ex;
                    assertEquals(expectedErrorMessage, exception.getDetail());
                });

        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contractFunctionProvider);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            FUNGIBLE_COMMON,        1,      0
                            NON_FUNGIBLE_UNIQUE,    0,      1
                            """)
    void approveTokenGetAllowance(final TokenTypeEnum tokenType, final long amount, final long serialNumber) {
        // Given
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var ownerEntityId = accountEntityPersist().toEntityId();
        final var ownerAddress = toAddress(ownerEntityId);
        final var spenderEntityId = accountEntityPersist().toEntityId();

        final var token = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersistWithTreasuryAccount(treasuryEntityId)
                : nftPersist(treasuryEntityId, ownerEntityId, spenderEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId);

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAccountPersist(tokenId, contractEntityId.getId());
        tokenAccountPersist(tokenId, ownerEntityId.getId());

        if (tokenType == TokenTypeEnum.NON_FUNGIBLE_UNIQUE) {
            nftAllowancePersist(tokenId, contractEntityId.getId(), ownerEntityId);
        }

        // When
        final var spenderAddress =
                tokenType == TokenTypeEnum.FUNGIBLE_COMMON ? ownerAddress : toAddress(spenderEntityId);
        final var functionCall = contract.send_approveTokenGetAllowance(
                tokenAddress.toHexString(),
                spenderAddress.toHexString(),
                BigInteger.valueOf(amount),
                BigInteger.valueOf(serialNumber));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            FUNGIBLE_COMMON,        1,      0
                            NON_FUNGIBLE_UNIQUE,    0,      1
                            """)
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Native secp256k1 DLL not available on Windows")
    void approveTokenTransferFromGetAllowanceGetBalance(
            final TokenTypeEnum tokenType, final long amount, final long serialNumber) {
        // Given
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var ownerEntityId = accountEntityPersist().toEntityId();
        final var spender = accountEntityWithEvmAddressPersist();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var token = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersistWithTreasuryAccount(treasuryEntityId)
                : nftPersist(treasuryEntityId, contractEntityId, spender.toEntityId());
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId);

        tokenAccountPersist(tokenId, contractEntityId.getId());
        tokenAccountPersist(tokenId, spender.getId());
        tokenAccountPersist(tokenId, ownerEntityId.getId());

        if (tokenType == TokenTypeEnum.NON_FUNGIBLE_UNIQUE) {
            nftAllowancePersist(tokenId, contractEntityId.getId(), ownerEntityId);
        }

        // When
        final var functionCall = contract.send_approveTokenTransferFromGetAllowanceGetBalance(
                tokenAddress.toHexString(),
                getAliasFromEntity(spender),
                BigInteger.valueOf(amount),
                BigInteger.valueOf(serialNumber));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            FUNGIBLE_COMMON,        1,      0
                            NON_FUNGIBLE_UNIQUE,    0,      1
                            """)
    void approveTokenTransferGetAllowanceGetBalance(
            final TokenTypeEnum tokenType, final long amount, final long serialNumber) {
        // Given
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var sender = accountEntityWithEvmAddressPersist();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var senderEntityId = sender.toEntityId();

        final var token = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersistWithTreasuryAccount(treasuryEntityId)
                : nftPersist(treasuryEntityId, senderEntityId, senderEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId);

        tokenAccountPersist(tokenId, sender.getId());
        tokenAccountPersist(tokenId, contractEntityId.getId());

        // When
        final var functionCall = contract.send_approveTokenTransferGetAllowanceGetBalance(
                tokenAddress.toHexString(),
                getAliasFromEntity(sender),
                BigInteger.valueOf(amount),
                BigInteger.valueOf(serialNumber));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            FUNGIBLE_COMMON,        1,      0
                            NON_FUNGIBLE_UNIQUE,    0,      1
                            """)
    void approveTokenCryptoTransferGetAllowanceGetBalance(
            final TokenTypeEnum tokenType, final long amount, final long serialNumber) {
        // Given
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var spender = accountEntityWithEvmAddressPersist();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var spenderEntityId = spender.toEntityId();

        final var token = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersistWithTreasuryAccount(treasuryEntityId)
                : nftPersist(treasuryEntityId, spenderEntityId, spenderEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId);
        final var spenderAddress = getAliasFromEntity(spender);
        tokenAccountPersist(tokenId, spender.getId());
        tokenAccountPersist(tokenId, contractEntityId.getId());

        TokenTransferList tokenTransferList;
        if (tokenType == TokenTypeEnum.FUNGIBLE_COMMON) {
            tokenTransferList = new TokenTransferList(
                    tokenAddress.toHexString(),
                    List.of(
                            new AccountAmount(contractAddress.toHexString(), BigInteger.valueOf(-amount), false),
                            new AccountAmount(spenderAddress, BigInteger.valueOf(amount), false)),
                    List.of());
        } else {
            tokenTransferList = new TokenTransferList(
                    tokenAddress.toHexString(),
                    List.of(),
                    List.of(new NftTransfer(
                            contractAddress.toHexString(),
                            spenderAddress,
                            BigInteger.valueOf(serialNumber),
                            Boolean.FALSE)));
        }

        // When
        final var functionCall = contract.send_approveTokenCryptoTransferGetAllowanceGetBalance(
                new TransferList(List.of()), List.of(tokenTransferList));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void approveForAllTokenTransferFromGetAllowance() {
        // Given
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var spender = accountEntityWithEvmAddressPersist();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var spenderEntityId = spender.toEntityId();
        final var token = nftPersist(treasuryEntityId, spenderEntityId, spenderEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId);

        tokenAccountPersist(tokenId, spender.getId());
        tokenAccountPersist(tokenId, contractEntityId.getId());

        // When
        final var functionCall = contract.send_approveForAllTokenTransferGetAllowance(
                tokenAddress.toHexString(), getAliasFromEntity(spender), DEFAULT_SERIAL_NUMBER);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void approveForAllCryptoTransferGetAllowance() {
        // Given
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var spender = accountEntityWithEvmAddressPersist();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var spenderEntityId = spender.toEntityId();
        final var token = nftPersist(treasuryEntityId, spenderEntityId, spenderEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId);

        tokenAccountPersist(tokenId, spender.getId());
        tokenAccountPersist(tokenId, contractEntityId.getId());
        nftAllowancePersist(tokenId, contractEntityId.getId(), contractEntityId);

        var tokenTransferList = new TokenTransferList(
                tokenAddress.toHexString(),
                List.of(),
                List.of(new NftTransfer(
                        contractAddress.toHexString(),
                        getAliasFromEntity(spender),
                        DEFAULT_SERIAL_NUMBER,
                        Boolean.TRUE)));

        // When
        final var functionCall = contract.send_approveForAllCryptoTransferGetAllowance(
                new TransferList(List.of()), List.of(tokenTransferList));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            FUNGIBLE_COMMON,        1,      0,      false
                            NON_FUNGIBLE_UNIQUE,    0,      1,      true
                            """)
    void cryptoTransferFromGetAllowanceGetBalance(
            final TokenTypeEnum tokenType, final long amount, final long serialNumber, final boolean approvalForAll) {
        // Given
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var spender = accountEntityWithEvmAddressPersist();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var spenderEntityId = spender.toEntityId();
        final var token = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersistWithTreasuryAccount(treasuryEntityId)
                : nftPersist(treasuryEntityId, spenderEntityId, spenderEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId);

        tokenAccountPersist(tokenId, spender.getId());
        tokenAccountPersist(tokenId, contractEntityId.getId());
        nftAllowancePersist(tokenId, spender.getId(), contractEntityId);
        nftAllowancePersist(tokenId, contractEntityId.getId(), contractEntityId);

        TokenTransferList tokenTransferList;
        if (tokenType == TokenTypeEnum.FUNGIBLE_COMMON) {
            tokenTransferList = new TokenTransferList(
                    tokenAddress.toHexString(),
                    List.of(
                            new AccountAmount(
                                    contractAddress.toHexString(), BigInteger.valueOf(-amount), approvalForAll),
                            new AccountAmount(getAliasFromEntity(spender), BigInteger.valueOf(amount), approvalForAll)),
                    List.of());
        } else {
            tokenTransferList = new TokenTransferList(
                    tokenAddress.toHexString(),
                    List.of(),
                    List.of(new NftTransfer(
                            contractAddress.toHexString(),
                            getAliasFromEntity(spender),
                            BigInteger.valueOf(serialNumber),
                            approvalForAll)));
        }

        // When
        final var functionCall = contract.send_cryptoTransferFromGetAllowanceGetBalance(
                new TransferList(List.of()), List.of(tokenTransferList));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void transferFromNFTGetAllowance() {
        // Given
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var spenderEntityId = accountEntityPersist().toEntityId();

        final var token = nftPersist(treasuryEntityId, spenderEntityId, spenderEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId);

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        tokenAccountPersist(tokenId, spenderEntityId.getId());
        tokenAccountPersist(tokenId, contractEntityId.getId());
        nftAllowancePersist(tokenId, contractEntityId.getId(), spenderEntityId);

        // When
        final var functionCall =
                contract.send_transferFromNFTGetAllowance(tokenAddress.toHexString(), DEFAULT_SERIAL_NUMBER);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            FUNGIBLE_COMMON,        1,      0
                            NON_FUNGIBLE_UNIQUE,    0,      1
                            """)
    void transferFromGetAllowanceGetBalance(final TokenTypeEnum tokenType, final long amount, final long serialNumber) {
        // Given
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var spender = accountEntityWithEvmAddressPersist();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);

        final var token = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersistWithTreasuryAccount(treasuryEntityId)
                : nftPersist(treasuryEntityId, treasuryEntityId, treasuryEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId);

        tokenAccountPersist(tokenId, treasuryEntityId.getId());
        tokenAccountPersist(tokenId, spender.getId());
        tokenAccountPersist(tokenId, contractEntityId.getId());

        // When
        final var functionCall = contract.send_transferFromGetAllowanceGetBalance(
                tokenAddress.toHexString(),
                getAliasFromEntity(spender),
                BigInteger.valueOf(amount),
                BigInteger.valueOf(serialNumber));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            FUNGIBLE_COMMON
            NON_FUNGIBLE_UNIQUE
            """)
    void grantKycRevokeKyc(final TokenTypeEnum tokenType) {
        // Given
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var spender = accountEntityWithEvmAddressPersist();

        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        final var token = tokenType == TokenTypeEnum.FUNGIBLE_COMMON
                ? fungibleTokenPersistWithTreasuryAccount(treasuryEntityId)
                : nftPersist(treasuryEntityId, treasuryEntityId, treasuryEntityId);
        final var tokenId = token.getTokenId();
        final var tokenAddress = toAddress(tokenId);

        tokenAccountPersist(tokenId, spender.getId());

        // When
        final var functionCall =
                contract.send_grantKycRevokeKyc(tokenAddress.toHexString(), getAliasFromEntity(spender));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void getAddressThis() {
        // Given
        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        // When
        final var functionCall = contract.send_getAddressThis();

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void getAddressThisWithEvmAliasRecipient() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        final var contractEntityOptional =
                commonEntityAccessor.get(Address.fromHexString(contract.getContractAddress()), Optional.empty());
        final var contractEntity = contractEntityOptional.orElseThrow();
        final var canonicalAddress =
                commonEntityAccessor.evmAddressFromId(contractEntity.toEntityId(), Optional.empty());

        // When
        final var functionCall = contract.call_getAddressThis();
        final var result = functionCall.send();

        // Then
        assertEquals(Bytes.wrap(canonicalAddress).toHexString(), result);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void getAddressThisWithLongZeroRecipientThatHasEvmAlias() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(DynamicEthCalls::deploy);

        final var contractEntityOptional =
                commonEntityAccessor.get(Address.fromHexString(contract.getContractAddress()), Optional.empty());
        final var contractEntity = contractEntityOptional.orElseThrow();
        final var canonicalAddress =
                commonEntityAccessor.evmAddressFromId(contractEntity.toEntityId(), Optional.empty());

        // When
        final var functionCall = contract.call_getAddressThis();
        final String result = functionCall.send();

        // Then
        assertEquals(Bytes.wrap(canonicalAddress).toHexString(), result);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    private Token nftPersistWithNullKycKey(EntityId treasuryEntityId) {
        final var token = nonFungibleTokenCustomizable(
                n -> n.treasuryAccountId(treasuryEntityId).kycKey(null));
        nftPersistCustomizable(
                n -> n.accountId(treasuryEntityId).tokenId(token.getTokenId()).spender(treasuryEntityId.getId()));
        return token;
    }

    private Entity setUpToken(TokenTypeEnum tokenType, Entity treasuryAccount, Entity owner, Entity spender) {
        final var tokenEntity = tokenEntityPersist();
        final var tokenId = tokenEntity.getId();
        final var spenderId = spender.getId();
        final var ownerId = owner.getId();
        tokenAccountPersist(tokenId, treasuryAccount.getId());
        tokenAccountPersist(tokenId, spenderId);

        if (!Objects.equals(ownerId, spenderId)) {
            tokenAccountPersist(tokenId, ownerId);
        }

        if (tokenType.equals(TokenTypeEnum.FUNGIBLE_COMMON)) {
            fungibleTokenPersist(tokenEntity, treasuryAccount);
        } else {
            var token = nonFungibleTokenPersist(tokenEntity, treasuryAccount);
            nonFungibleTokenInstancePersist(token, 1L, owner.toEntityId(), spender.toEntityId());
        }

        return tokenEntity;
    }
}
