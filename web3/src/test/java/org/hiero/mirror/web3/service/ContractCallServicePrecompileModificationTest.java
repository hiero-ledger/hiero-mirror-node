// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hedera.hapi.node.base.TokenType.FUNGIBLE_COMMON;
import static com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.services.utils.EntityIdUtils.asHexedEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.entityIdFromContractId;
import static com.hedera.services.utils.EntityIdUtils.toContractID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties.ALLOW_LONG_ZERO_ADDRESSES;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.EMPTY_UNTRIMMED_ADDRESS;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.NEW_ECDSA_KEY;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.ZERO_VALUE;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper.KeyValueType;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.Key.KeyCase;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenPauseStatusEnum;
import org.hiero.mirror.common.domain.token.TokenSupplyTypeEnum;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.web3.evm.exception.PrecompileNotSupportedException;
import org.hiero.mirror.web3.evm.utils.EvmTokenUtils;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.utils.BytecodeUtils;
import org.hiero.mirror.web3.utils.ContractFunctionProviderRecord;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract.AccountAmount;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract.Expiry;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract.FixedFee;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract.FractionalFee;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract.HederaToken;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract.KeyValue;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract.NftTransfer;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract.RoyaltyFee;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract.TokenKey;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract.TokenTransferList;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract.TransferList;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ContractCallServicePrecompileModificationTest extends AbstractContractCallServiceOpcodeTracerTest {

    private static Stream<Arguments> tokenData() {
        return Stream.of(Arguments.of(FUNGIBLE_COMMON.name(), true), Arguments.of(NON_FUNGIBLE_UNIQUE.name(), false));
    }

    @Test
    void transferFrom() throws Exception {
        // Given
        final var owner = accountEntityPersist();
        final var spender = accountEntityWithEvmAddressPersist();
        final var recipient = accountEntityWithEvmAddressPersist();

        final var tokenEntity = tokenEntityPersist();
        final var tokenId = tokenEntity.getId();
        fungibleTokenPersist(tokenEntity, owner);

        tokenAccountPersist(tokenId, spender.getId());
        tokenAccountPersist(tokenId, recipient.getId());

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccount(ta -> ta.tokenId(tokenId).accountId(contractEntityId.getId()));

        tokenAllowancePersistCustomizable(e -> e.owner(spender.getId())
                .amount(DEFAULT_AMOUNT_GRANTED)
                .amountGranted(DEFAULT_AMOUNT_GRANTED)
                .spender(contractEntityId.getId())
                .tokenId(tokenEntity.getId()));

        // When
        final var functionCall = contract.call_transferFrom(
                getAddressFromEntity(tokenEntity),
                getAliasFromEntity(spender),
                getAliasFromEntity(recipient),
                BigInteger.ONE);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @CsvSource({"1", "0"})
    void approve(final BigInteger allowance) throws Exception {
        // Given
        final var spender = accountEntityPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();

        tokenAccountPersist(tokenId, spender.getId());

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccount(ta -> ta.tokenId(tokenId).accountId(contractEntityId.getId()));

        // When
        final var functionCall = contract.call_approveExternal(
                toAddress(tokenId).toHexString(), getAddressFromEntity(spender), allowance);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void approveNFT(final Boolean approve) throws Exception {
        // Given
        final var owner = accountEntityPersist();
        final var spender = accountEntityPersist();

        var token = nonFungibleTokenPersist();
        final var tokenId = token.getTokenId();

        tokenAccountPersist(tokenId, owner.getId());
        tokenAccountPersist(tokenId, spender.getId());

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccount(ta -> ta.tokenId(tokenId).accountId(contractEntityId.getId()));

        nonFungibleTokenInstancePersist(token, 1L, contractEntityId, spender.toEntityId());

        // When
        final var functionCall = contract.call_approveNFTExternal(
                toAddress(tokenId).toHexString(),
                approve ? getAddressFromEntity(spender) : Address.ZERO.toHexString(),
                DEFAULT_SERIAL_NUMBER);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void setApprovalForAll(boolean longZeroAddressAllowed) throws Exception {
        // Given
        final var spender = accountEntityWithEvmAddressPersist();

        final var token = nonFungibleTokenPersist();
        final var tokenId = token.getTokenId();

        String spenderAddress;
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));
        if (longZeroAddressAllowed) {
            spenderAddress = getAddressFromEntity(spender);
        } else {
            spenderAddress = getAliasFromEntity(spender);
        }

        tokenAccountPersist(tokenId, spender.getId());

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccount(ta -> ta.tokenId(tokenId).accountId(contractEntityId.getId()));

        nonFungibleTokenInstancePersist(token, 1L, contractEntityId, spender.toEntityId());

        // When
        final var functionCall =
                contract.call_setApprovalForAllExternal(toAddress(tokenId).toHexString(), spenderAddress, Boolean.TRUE);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);

        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void associateToken(final Boolean single) throws Exception {
        // Given
        final var notAssociatedAccount = accountEntityPersist();

        final var token = fungibleTokenPersist();
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = single
                ? contract.call_associateTokenExternal(
                        getAddressFromEntity(notAssociatedAccount),
                        toAddress(token.getTokenId()).toHexString())
                : contract.call_associateTokensExternal(
                        getAddressFromEntity(notAssociatedAccount),
                        List.of(toAddress(token.getTokenId()).toHexString()));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void associateTokenWithNullAutoRenew(final Boolean single) throws Exception {
        // Given
        final var notAssociatedAccount = accountEntityPersistCustomizable(e -> e.type(EntityType.ACCOUNT)
                .balance(DEFAULT_ACCOUNT_BALANCE)
                .autoRenewAccountId(null)
                .alias(null)
                .evmAddress(null));

        final var token = fungibleTokenPersist();
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = single
                ? contract.call_associateTokenExternal(
                        getAddressFromEntity(notAssociatedAccount),
                        toAddress(token.getTokenId()).toHexString())
                : contract.call_associateTokensExternal(
                        getAddressFromEntity(notAssociatedAccount),
                        List.of(toAddress(token.getTokenId()).toHexString()));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void associateTokenHRC() throws Exception {
        // Given
        final var token = fungibleTokenPersist();
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_associateWithRedirect(
                toAddress(token.getTokenId()).toHexString());

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void dissociateToken(final Boolean single) throws Exception {
        // Given
        final var associatedAccount = accountEntityWithEvmAddressPersist();

        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();

        tokenAccount(
                ta -> ta.tokenId(tokenId).accountId(associatedAccount.getId()).balance(0L));

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var tokenAddress = toAddress(tokenId).toHexString();
        final var associatedAccountAlias = getAliasFromEntity(associatedAccount);
        // When
        final var functionCall = single
                ? contract.call_dissociateTokenExternal(associatedAccountAlias, tokenAddress)
                : contract.call_dissociateTokensExternal(associatedAccountAlias, List.of(tokenAddress));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void dissociateTokenHRC() throws Exception {
        // Given
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var contractAddress = Address.fromHexString(contract.getContractAddress());
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        tokenAccount(ta -> ta.tokenId(tokenId).accountId(contractEntityId.getId()));

        // When
        final var functionCall =
                contract.call_dissociateWithRedirect(toAddress(tokenId).toHexString());

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest(name = "Associate {0} token")
    @MethodSource("tokenData")
    void associateToken(final String tokenType, final boolean isFungible) throws Exception {
        final var token = persistToken(isFungible);
        final var tokenAddress = getTokenAddress(token);
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var functionCall = contract.call_associate(tokenAddress);
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest(name = "Associate {0} token twice fails")
    @MethodSource("tokenData")
    void associateTokenTwiceFails(final String tokenType, final boolean isFungible) throws Exception {
        final var token = persistToken(isFungible);
        final var tokenAddress = getTokenAddress(token);
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var contractEntityId = getEntityId(contract.getContractAddress());
        tokenAccountPersist(token.getTokenId(), contractEntityId.getId());

        final var functionCall = contract.call_associate(tokenAddress);
        assertThat(functionCall.send())
                .isNotNull()
                .isEqualTo(BigInteger.valueOf(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT.protoOrdinal()));

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest(name = "Dissociate {0} token")
    @MethodSource("tokenData")
    void dissociateToken(final String tokenType, final boolean isFungible) throws Exception {
        final var token = persistToken(isFungible);
        final var tokenAddress = getTokenAddress(token);
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var contractEntityId = getEntityId(contract.getContractAddress());
        tokenAccount(ta -> ta.tokenId(token.getTokenId())
                .accountId(contractEntityId.getId())
                .balance(0));

        final var functionCall = contract.call_dissociate(tokenAddress);

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest(name = "Dissociate fails for {0} token when no association is present")
    @MethodSource("tokenData")
    void dissociateWhenNotAssociatedFails(final String tokenType, final boolean isFungible) throws Exception {
        final var token = persistToken(isFungible);
        final var tokenAddress = getTokenAddress(token);
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var functionCall = contract.call_dissociate(tokenAddress);
        assertThat(functionCall.send())
                .isNotNull()
                .isEqualTo(BigInteger.valueOf(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT.protoOrdinal()));

        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest(name = "Dissociate fails for {0} token when balance is not zero")
    @MethodSource("tokenData")
    void dissociateWhenBalanceNotZeroFails(final String tokenType, final boolean isFungible) throws Exception {
        final var token = persistToken(isFungible);
        final var tokenAddress = getTokenAddress(token);
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var contractEntityId = getEntityId(contract.getContractAddress());
        tokenAccountPersist(token.getTokenId(), contractEntityId.getId());
        final var functionCall = contract.call_dissociate(tokenAddress);

        ResponseCodeEnum statusCode;
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            statusCode = isFungible ? TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES : ACCOUNT_STILL_OWNS_NFTS;
        } else {
            statusCode = isFungible ? SUCCESS : ACCOUNT_STILL_OWNS_NFTS;
        }
        final var expected = BigInteger.valueOf(statusCode.protoOrdinal());
        assertThat(functionCall.send()).isNotNull().isEqualTo(expected);
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest(name = "isAssociated returns true for {0} token when association exists")
    @MethodSource("tokenData")
    void isAssociated(final String tokenType, final boolean isFungible) throws Exception {
        final var token = persistToken(isFungible);
        final var tokenAddress = getTokenAddress(token);
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var contractEntityId = getEntityId(contract.getContractAddress());
        tokenAccountPersist(token.getTokenId(), contractEntityId.getId());
        final var functionCall = contract.call_isAssociated(tokenAddress);

        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertTrue(functionCall.send());
            verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
            verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @ParameterizedTest(name = "isAssociated returns false for {0} token when no association exists")
    @MethodSource("tokenData")
    void isAssociatedWhenNoAssociation(final String tokenType, final boolean isFungible) throws Exception {
        final var token = persistToken(isFungible);
        final var tokenAddress = getTokenAddress(token);
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var functionCall = contract.call_isAssociated(tokenAddress);

        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertFalse(functionCall.send());
            verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
            verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
        } else {
            assertThrows(PrecompileNotSupportedException.class, functionCall::send);
        }
    }

    @Test
    void mintFungibleToken() throws Exception {
        // Given
        final var treasury = accountEntityPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(treasury.toEntityId());
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, treasury.getId());

        final var totalSupply = token.getTotalSupply();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_mintTokenExternal(
                toAddress(tokenId).toHexString(), BigInteger.valueOf(30), new ArrayList<>());
        final var result = functionCall.send();

        // Then
        assertThat(result.component2()).isEqualTo(BigInteger.valueOf(totalSupply + 30L));
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @CsvSource({"true, true", "true, false", "false, true", "false, false"})
    void mintNFT(final boolean useAlias, boolean longZeroAddressAllowed) throws Exception {
        // Given
        final var treasury = useAlias ? accountEntityWithEvmAddressPersist() : accountEntityPersist();
        final var tokenEntity = tokenEntityPersistWithAutoRenewAccount(
                useAlias ? accountEntityWithEvmAddressPersist() : accountEntityPersist());

        nonFungibleTokenPersist(tokenEntity, treasury);

        tokenAccountPersist(tokenEntity.getId(), treasury.getId());

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));

        // When
        final var functionCall = contract.call_mintTokenExternal(
                getAddressFromEntity(tokenEntity), BigInteger.ZERO, List.of(domainBuilder.nonZeroBytes(12)));

        final var result = functionCall.send();

        // Then
        assertThat(result.component3().getFirst()).isEqualTo(BigInteger.ONE);
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);

        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(false));
    }

    @Test
    void mintNFTWithContractWithoutKey() throws Exception {
        // Given
        final var treasury = accountEntityPersist();
        final var tokenEntity = tokenEntityPersist();

        nonFungibleTokenPersist(tokenEntity, treasury);

        tokenAccountPersist(tokenEntity.getId(), treasury.getId());

        final var contract = testWeb3jService.deployWithoutPersist(ModificationPrecompileTestContract::deploy);
        final var contractAddress = contract.getContractAddress();
        final var contractID = toContractID(Address.fromHexString(contractAddress));
        final var contractEntityId = entityIdFromContractId(contractID);
        contractPersistWithNoKey(
                BytecodeUtils.extractRuntimeBytecode(ModificationPrecompileTestContract.BINARY), contractEntityId);
        // When
        final var functionCall = contract.call_mintTokenExternal(
                getAddressFromEntity(tokenEntity), BigInteger.ZERO, List.of(domainBuilder.nonZeroBytes(12)));

        final var result = functionCall.send();

        // Then
        assertThat(result.component3().getFirst()).isEqualTo(BigInteger.ONE);
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    /**
     * Burns tokens from the token's treasury account. The operation decreases the total supply of the token.
     */
    @Test
    void burnFungibleToken() throws Exception {
        // Given
        final var treasuryAccount = accountEntityPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(treasuryAccount.toEntityId());
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, treasuryAccount.getId());

        final var sender = accountEntityPersist();
        accountBalanceRecordsPersist(sender);

        tokenBalancePersist(treasuryAccount.toEntityId(), EntityId.of(tokenId), treasuryAccount.getBalanceTimestamp());

        testWeb3jService.setSender(getAddressFromEntity(sender));

        final var tokenTotalSupply = token.getTotalSupply();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_burnTokenExternal(
                toAddress(tokenId).toHexString(), BigInteger.valueOf(4), new ArrayList<>());

        final var result = functionCall.send();

        // Then
        assertThat(result.component2()).isEqualTo(BigInteger.valueOf(tokenTotalSupply - 4L));
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void burnNFT() throws Exception {
        // Given
        final var treasury = accountEntityPersist();
        final var token = nonFungibleTokenPersistWithTreasury(treasury.toEntityId());
        final var tokenId = token.getTokenId();

        tokenAccountPersist(tokenId, treasury.getId());
        final var totalSupply = token.getTotalSupply();

        tokenBalancePersist(treasury.toEntityId(), EntityId.of(tokenId), treasury.getBalanceTimestamp());

        final var nft = domainBuilder
                .nft()
                .customize(n -> n.tokenId(tokenId).serialNumber(1L).accountId(treasury.toEntityId()))
                .persist();

        domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(treasury.toEntityId())
                        .createdTimestamp(treasury.getCreatedTimestamp())
                        .serialNumber(nft.getSerialNumber())
                        .timestampRange(treasury.getTimestampRange())
                        .tokenId(tokenId))
                .persist();

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_burnTokenExternal(
                toAddress(tokenId).toHexString(), BigInteger.ZERO, DEFAULT_SERIAL_NUMBERS_LIST);

        final var result = functionCall.send();

        // Then
        assertThat(result.component2()).isEqualTo(BigInteger.valueOf(totalSupply - 1));
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    /**
     * Wiping tokens is the process of removing tokens from an account's balance. The total supply is not affected.
     */
    @Test
    void wipeFungibleToken() throws Exception {
        // Given
        final var owner = accountEntityWithEvmAddressPersist();

        final var tokenEntity = tokenEntityPersist();
        fungibleTokenPersist(tokenEntity, treasuryEntity);

        tokenAccountPersist(tokenEntity.getId(), owner.getId());

        Long createdTimestamp = owner.getCreatedTimestamp();
        tokenBalancePersist(owner.toEntityId(), tokenEntity.toEntityId(), createdTimestamp);
        accountBalancePersist(treasuryEntity, createdTimestamp);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_wipeTokenAccountExternal(
                getAddressFromEntity(tokenEntity), getAliasFromEntity(owner), BigInteger.valueOf(4));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void wipeNFT() throws Exception {
        // Given
        final var owner = accountEntityWithEvmAddressPersist();
        final var tokenTreasury = accountEntityPersist().toEntityId();
        final var token = nonFungibleTokenPersistWithTreasury(tokenTreasury);
        final var tokenId = token.getTokenId();

        tokenAccountPersist(tokenId, owner.getId());
        nftPersistCustomizable(n -> n.tokenId(tokenId).accountId(owner.toEntityId()));

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_wipeTokenAccountNFTExternal(
                toAddress(tokenId).toHexString(), getAliasFromEntity(owner), DEFAULT_SERIAL_NUMBERS_LIST);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void grantTokenKyc() throws Exception {
        // Given
        final var accountWithoutGrant = accountEntityWithEvmAddressPersist();

        final var token = nonFungibleTokenPersist();
        final var tokenId = token.getTokenId();

        tokenAccountPersist(tokenId, accountWithoutGrant.getId());

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_grantTokenKycExternal(
                toAddress(tokenId).toHexString(), getAliasFromEntity(accountWithoutGrant));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void revokeTokenKyc() throws Exception {
        // Given
        final var accountWithGrant = accountEntityWithEvmAddressPersist();

        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();

        tokenAccountPersist(tokenId, accountWithGrant.getId());

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_revokeTokenKycExternal(
                toAddress(tokenId).toHexString(), getAliasFromEntity(accountWithGrant));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void deleteToken() throws Exception {
        // Given
        final var treasury = accountEntityPersist();
        final var tokenEntity = tokenEntityPersist();

        fungibleTokenPersist(tokenEntity, treasury);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_deleteTokenExternal(getAddressFromEntity(tokenEntity));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void freezeToken() throws Exception {
        // Given
        final var accountWithoutFreeze = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, accountWithoutFreeze.getId());

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_freezeTokenExternal(
                toAddress(tokenId).toHexString(), getAliasFromEntity(accountWithoutFreeze));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void unfreezeToken() throws Exception {
        // Given
        final var accountWithFreeze = accountEntityWithEvmAddressPersist();

        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();

        tokenAccount(ta -> ta.tokenId(tokenId)
                .accountId(accountWithFreeze.getId())
                .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                .balance(100L));

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_unfreezeTokenExternal(
                toAddress(tokenId).toHexString(), getAliasFromEntity(accountWithFreeze));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void pauseToken() throws Exception {
        // Given
        final var treasury = accountEntityPersist();
        final var tokenEntity = tokenEntityPersist();

        fungibleTokenPersist(tokenEntity, treasury);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_pauseTokenExternal(getAddressFromEntity(tokenEntity));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void unpauseToken() throws Exception {
        // Given
        final var sender = accountEntityPersist();

        final var token = fungibleTokenCustomizable(
                t -> t.treasuryAccountId(sender.toEntityId()).pauseStatus(TokenPauseStatusEnum.PAUSED));
        final var tokenId = token.getTokenId();
        tokenAccountPersist(tokenId, sender.getId());

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall =
                contract.call_unpauseTokenExternal(toAddress(tokenId).toHexString());

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void createFungibleToken() throws Exception {
        // Given
        final var value = 10000L * 100_000_000_000L;
        final var sender = accountEntityPersist();

        accountBalanceRecordsPersist(sender);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        testWeb3jService.setValue(value);

        testWeb3jService.setSender(toAddress(sender.toEntityId()).toHexString());

        final var treasuryAccount = accountEntityPersist();

        final var token = populateHederaToken(
                contract.getContractAddress(), TokenTypeEnum.FUNGIBLE_COMMON, treasuryAccount.toEntityId());
        final var initialTokenSupply = BigInteger.valueOf(10L);
        final var decimalPlacesSupportedByToken = BigInteger.valueOf(10L); // e.g. 1.0123456789

        // When
        final var functionCall =
                contract.call_createFungibleTokenExternal(token, initialTokenSupply, decimalPlacesSupportedByToken);
        final var result = functionCall.send();

        final var contractFunctionProvider = ContractFunctionProviderRecord.builder()
                .contractAddress(Address.fromHexString(contract.getContractAddress()))
                .sender(toAddress(sender.toEntityId()))
                .value(value)
                .build();

        // Then
        assertThat(result.component2()).isNotEqualTo(Address.ZERO.toHexString());

        verifyEthCallAndEstimateGas(functionCall, contract, value);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contractFunctionProvider);
    }

    @Test
    void createFungibleTokenWithCustomFees() throws Exception {
        // Given
        var initialSupply = BigInteger.valueOf(10L);
        var decimals = BigInteger.valueOf(10L);
        var value = 10000L * 100_000_000L;

        final var sender = accountEntityPersist();

        accountBalanceRecordsPersist(sender);

        final var treasuryAccount = accountEntityPersist();

        final var tokenForDenomination = fungibleTokenPersist();
        final var feeCollector = accountEntityWithEvmAddressPersist();
        final var tokenId = tokenForDenomination.getTokenId();

        tokenAccountPersist(tokenId, feeCollector.getId());

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        testWeb3jService.setSender(toAddress(sender.toEntityId()).toHexString());
        testWeb3jService.setValue(value);

        final var token = populateHederaToken(
                contract.getContractAddress(), TokenTypeEnum.FUNGIBLE_COMMON, treasuryAccount.toEntityId());

        final var fixedFee = new FixedFee(
                BigInteger.valueOf(100L),
                toAddress(tokenId).toHexString(),
                false,
                false,
                getAliasFromEntity(feeCollector));
        final var fractionalFee = new FractionalFee(
                BigInteger.valueOf(1L),
                BigInteger.valueOf(100L),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(1000L),
                false,
                getAliasFromEntity(feeCollector));

        // When
        final var functionCall = contract.call_createFungibleTokenWithCustomFeesExternal(
                token, initialSupply, decimals, List.of(fixedFee), List.of(fractionalFee));
        final var result = functionCall.send();

        final var contractFunctionProvider = ContractFunctionProviderRecord.builder()
                .contractAddress(Address.fromHexString(contract.getContractAddress()))
                .sender(toAddress(sender.toEntityId()))
                .value(value)
                .build();

        // Then
        assertThat(result.component2()).isNotEqualTo(Address.ZERO.toHexString());

        verifyEthCallAndEstimateGas(functionCall, contract, value);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contractFunctionProvider);
    }

    @Test
    void createNonFungibleTokenWithCustomFees() throws Exception {
        // Given
        var value = 10000L * 100_000_000L;
        final var sender = accountEntityPersist();

        accountBalanceRecordsPersist(sender);

        final var tokenForDenomination = fungibleTokenPersist();
        final var feeCollector = accountEntityWithEvmAddressPersist();
        final var tokenId = tokenForDenomination.getTokenId();

        tokenAccountPersist(tokenId, feeCollector.getId());

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        testWeb3jService.setSender(toAddress(sender.toEntityId()).toHexString());
        testWeb3jService.setValue(value);

        final var treasuryAccount = accountEntityPersist();
        final var token = populateHederaToken(
                contract.getContractAddress(), TokenTypeEnum.NON_FUNGIBLE_UNIQUE, treasuryAccount.toEntityId());
        final var fixedFee = new FixedFee(
                BigInteger.valueOf(100L),
                toAddress(tokenId).toHexString(),
                false,
                false,
                getAliasFromEntity(feeCollector));
        final var royaltyFee = new RoyaltyFee(
                BigInteger.valueOf(1L),
                BigInteger.valueOf(100L),
                BigInteger.valueOf(10L),
                toAddress(tokenId).toHexString(),
                false,
                getAliasFromEntity(feeCollector));

        // When
        testWeb3jService.setValue(value);
        final var functionCall = contract.call_createNonFungibleTokenWithCustomFeesExternal(
                token, List.of(fixedFee), List.of(royaltyFee));
        final var result = functionCall.send();

        final var contractFunctionProvider = ContractFunctionProviderRecord.builder()
                .contractAddress(Address.fromHexString(contract.getContractAddress()))
                .sender(toAddress(sender.toEntityId()))
                .value(value)
                .build();
        // Then
        assertThat(result.component2()).isNotEqualTo(Address.ZERO.toHexString());

        verifyEthCallAndEstimateGas(functionCall, contract, value);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contractFunctionProvider);
    }

    @Test
    void createNonFungibleToken() throws Exception {
        // Given
        var value = 10000L * 100_000_000L;
        final var sender = accountEntityPersist();

        accountBalanceRecordsPersist(sender);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        testWeb3jService.setSender(toAddress(sender.toEntityId()).toHexString());

        final var treasuryAccount = accountEntityPersist();
        final var token = populateHederaToken(
                contract.getContractAddress(), TokenTypeEnum.NON_FUNGIBLE_UNIQUE, treasuryAccount.toEntityId());

        // When
        testWeb3jService.setValue(value);
        final var functionCall = contract.call_createNonFungibleTokenExternal(token);
        final var result = functionCall.send();

        final var contractFunctionProvider = ContractFunctionProviderRecord.builder()
                .contractAddress(Address.fromHexString(contract.getContractAddress()))
                .sender(toAddress(sender.toEntityId()))
                .value(value)
                .build();

        // Then
        assertThat(result.component2()).isNotEqualTo(Address.ZERO.toHexString());

        verifyEthCallAndEstimateGas(functionCall, contract, value);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contractFunctionProvider);
    }

    @Test
    void create2ContractAndTransferFromIt() throws Exception {
        // Given
        final var receiver = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersist();
        final var tokenId = token.getTokenId();
        final var sponsor = accountEntityWithEvmAddressPersist();
        tokenBalancePersist(sponsor.toEntityId(), EntityId.of(tokenId), sponsor.getCreatedTimestamp());
        accountBalanceRecordsPersist(sponsor);

        tokenAccountPersist(tokenId, sponsor.getId());
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        final var functionCall = contract.call_createContractViaCreate2AndTransferFromIt(
                toAddress(tokenId).toHexString(),
                getAliasFromEntity(sponsor),
                getAliasFromEntity(receiver),
                BigInteger.valueOf(10L));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, 0L);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void notExistingPrecompileCall() throws Exception {
        // Given
        final var token = fungibleTokenPersist();
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var modularizedCall = contract.call_callNotExistingPrecompile(asHexedEvmAddress(token.getTokenId()));
            assertThat(Bytes.wrap(modularizedCall.send())).isEqualTo(Bytes.EMPTY);
        } else {
            final var functionCall = contract.send_callNotExistingPrecompile(asHexedEvmAddress(token.getTokenId()));
            assertThatThrownBy(functionCall::send)
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage(INVALID_TOKEN_ID.name());
        }
    }

    @Test
    void createFungibleTokenWithInheritKeysCall() throws Exception {
        // Given
        final var value = 10000 * 100_000_000L;
        final var sender = accountEntityWithEvmAddressPersist();
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        // When
        testWeb3jService.setValue(value);
        final var functionCall = contract.call_createFungibleTokenWithInheritKeysExternal();

        // Then
        testWeb3jService.setSender(getAliasFromEntity(sender));

        functionCall.send();
        final var result = testWeb3jService.getTransactionResult();
        assertThat(result).isNotEqualTo(EMPTY_UNTRIMMED_ADDRESS);

        verifyEthCallAndEstimateGas(functionCall, contract, value);
    }

    @Test
    void createTokenWithInvalidMemoFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenToCreate = convertTokenEntityToHederaToken(domainBuilder
                .token()
                .customize(t -> t.metadata(new byte[mirrorNodeEvmProperties.getMaxMemoUtf8Bytes() + 1]))
                .get());

        // When
        testWeb3jService.setValue(10000L * 100_000_000L);
        final var function = contract.call_createFungibleTokenExternal(
                tokenToCreate, BigInteger.valueOf(10L), BigInteger.valueOf(10L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createTokenWithInvalidNameFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenToCreate = convertTokenEntityToHederaToken(domainBuilder
                .token()
                .customize(t -> t.name(new String(
                        new byte[mirrorNodeEvmProperties.getMaxTokenNameUtf8Bytes() + 1], StandardCharsets.UTF_8)))
                .get());

        // When
        testWeb3jService.setValue(10000L * 100_000_000L);
        final var function = contract.call_createFungibleTokenExternal(
                tokenToCreate, BigInteger.valueOf(10L), BigInteger.valueOf(10L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createTokenWithInvalidSymbolFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenToCreate = convertTokenEntityToHederaToken(domainBuilder
                .token()
                .customize(t -> t.symbol(new String(
                        new byte[mirrorNodeEvmProperties.getMaxTokenNameUtf8Bytes() + 1], StandardCharsets.UTF_8)))
                .get());

        // When
        testWeb3jService.setValue(10000L * 100_000_000L);
        final var function = contract.call_createFungibleTokenExternal(
                tokenToCreate, BigInteger.valueOf(10L), BigInteger.valueOf(10L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createTokenWithInvalidDecimalsFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenToCreate = convertTokenEntityToHederaToken(
                domainBuilder.token().customize(t -> t.decimals(-1)).get());

        // When
        testWeb3jService.setValue(10000L * 100_000_000L);
        final var function = contract.call_createFungibleTokenExternal(
                tokenToCreate, BigInteger.valueOf(10L), BigInteger.valueOf(10L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createTokenWithInvalidInitialSupplyFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenToCreate = convertTokenEntityToHederaToken(
                domainBuilder.token().customize(t -> t.initialSupply(-1L)).get());

        // When
        testWeb3jService.setValue(10000L * 100_000_000L);
        final var function = contract.call_createFungibleTokenExternal(
                tokenToCreate, BigInteger.valueOf(10L), BigInteger.valueOf(10L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createTokenWithInvalidInitialSupplyGreaterThanMaxSupplyFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenToCreate = convertTokenEntityToHederaToken(domainBuilder
                .token()
                .customize(t -> t.initialSupply(10_000_001L))
                .get());

        // When
        testWeb3jService.setValue(10000L * 100_000_000L);
        final var function = contract.call_createFungibleTokenExternal(
                tokenToCreate, BigInteger.valueOf(10L), BigInteger.valueOf(10L));

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createNftWithNoSupplyKeyFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenToCreate = convertTokenEntityToHederaToken(
                domainBuilder.token().customize(t -> t.supplyKey(new byte[0])).get());

        // When
        testWeb3jService.setValue(10000L * 100_000_000L);
        final var function = contract.call_createNonFungibleTokenExternal(tokenToCreate);

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createNftWithNoTreasuryFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenToCreate = convertTokenEntityToHederaToken(
                domainBuilder.token().customize(t -> t.treasuryAccountId(null)).get());

        // When
        testWeb3jService.setValue(10000L * 100_000_000L);
        final var function = contract.call_createNonFungibleTokenExternal(tokenToCreate);

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createNftWithDefaultFreezeWithNoKeyFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenToCreate = convertTokenEntityToHederaToken(domainBuilder
                .token()
                .customize(t -> t.freezeDefault(true).freezeKey(new byte[0]))
                .get());

        // When
        testWeb3jService.setValue(10000L * 100_000_000L);
        final var function = contract.call_createNonFungibleTokenExternal(tokenToCreate);

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @Test
    void createNftWithInvalidAutoRenewAccountFails() {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenToCreate = convertTokenEntityToHederaToken(
                domainBuilder.token().get(),
                domainBuilder.entity().customize(e -> e.autoRenewPeriod(10L)).get());

        // When
        testWeb3jService.setValue(10000L * 100_000_000L);
        final var function = contract.call_createNonFungibleTokenExternal(tokenToCreate);

        // Then
        assertThatThrownBy(function::send).isInstanceOf(MirrorEvmTransactionException.class);
    }

    @ParameterizedTest
    @CsvSource({"single", "multiple"})
    void transferToken(final String type) throws Exception {
        // Given
        final var tokenEntity = tokenEntityPersist();
        final var treasuryAccount = accountEntityPersist();
        fungibleTokenPersist(tokenEntity, treasuryAccount);

        final var sender = accountEntityWithEvmAddressPersist();
        final var receiver = accountEntityWithEvmAddressPersist();

        final var tokenId = tokenEntity.getId();
        // Create token-account associations so sender and receiver can operate with the token
        tokenAccountPersist(tokenId, sender.getId());
        tokenAccountPersist(tokenId, receiver.getId());

        accountBalanceRecordsPersist(sender.toEntityId(), sender.getCreatedTimestamp(), sender.getBalance());
        accountBalanceRecordsPersist(receiver.toEntityId(), receiver.getCreatedTimestamp(), receiver.getBalance());

        tokenBalancePersist(sender.toEntityId(), tokenEntity.toEntityId(), sender.getBalanceTimestamp());

        tokenBalancePersist(receiver.toEntityId(), tokenEntity.toEntityId(), receiver.getBalanceTimestamp());

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        // When

        final var functionCall = "single".equals(type)
                ? contract.call_transferTokenExternal(
                        getAddressFromEntity(tokenEntity),
                        getAliasFromEntity(sender),
                        getAliasFromEntity(receiver),
                        BigInteger.valueOf(1L))
                : contract.call_transferTokensExternal(
                        getAddressFromEntity(tokenEntity),
                        List.of(getAliasFromEntity(sender), getAliasFromEntity(receiver)),
                        List.of(BigInteger.ONE, BigInteger.valueOf(-1L)));

        final var contractFunctionProvider = ContractFunctionProviderRecord.builder()
                .contractAddress(Address.fromHexString(contract.getContractAddress()))
                .sender(Address.fromHexString(getAliasFromEntity(sender)))
                .build();

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contractFunctionProvider);
    }

    @ParameterizedTest
    @CsvSource({"single", "multiple"})
    void transferNft(final String type) throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var contractEntityId =
                EvmTokenUtils.entityIdFromEvmAddress(Address.fromHexString(contract.getContractAddress()));

        final var sender = accountEntityWithEvmAddressPersist();

        final var treasuryAccount = accountEntityPersist().toEntityId();
        final var token = nonFungibleTokenPersistWithTreasury(treasuryAccount);
        final var tokenId = token.getTokenId();
        accountBalanceRecordsPersist(sender);
        nftPersistCustomizable(n -> n.tokenId(tokenId).accountId(sender.toEntityId()));
        final var receiver = accountEntityWithEvmAddressPersist();

        nftAllowancePersist(tokenId, contractEntityId.getId(), sender.toEntityId());

        tokenAccountPersist(tokenId, sender.getId());
        tokenAccountPersist(tokenId, receiver.getId());

        // When
        testWeb3jService.setSender(getAliasFromEntity(sender));

        final var functionCall = "single".equals(type)
                ? contract.call_transferNFTExternal(
                        toAddress(tokenId).toHexString(),
                        getAliasFromEntity(sender),
                        getAliasFromEntity(receiver),
                        DEFAULT_SERIAL_NUMBER)
                : contract.call_transferNFTsExternal(
                        toAddress(tokenId).toHexString(),
                        List.of(getAliasFromEntity(sender)),
                        List.of(getAliasFromEntity(receiver)),
                        DEFAULT_SERIAL_NUMBERS_LIST);

        final var contractFunctionProvider = ContractFunctionProviderRecord.builder()
                .contractAddress(Address.fromHexString(contract.getContractAddress()))
                .sender(Address.fromHexString(getAliasFromEntity(sender)))
                .build();

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contractFunctionProvider);
    }

    @Test
    void transferFromNft() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var contractEntityId =
                EvmTokenUtils.entityIdFromEvmAddress(Address.fromHexString(contract.getContractAddress()));

        final var sender = accountEntityWithEvmAddressPersist();

        accountBalanceRecordsPersist(sender);

        final var treasuryAccount = accountEntityPersist().toEntityId();
        final var token = nonFungibleTokenPersistWithTreasury(treasuryAccount);
        final var tokenId = token.getTokenId();
        nftPersistCustomizable(n -> n.tokenId(tokenId).accountId(sender.toEntityId()));
        final var receiver = accountEntityWithEvmAddressPersist();
        nftAllowancePersist(tokenId, contractEntityId.getId(), sender.toEntityId());
        tokenAccountPersist(tokenId, sender.getId());
        tokenAccountPersist(tokenId, receiver.getId());

        // When
        testWeb3jService.setSender(getAliasFromEntity(sender));

        final var functionCall = contract.call_transferFromNFTExternal(
                toAddress(tokenId).toHexString(),
                getAliasFromEntity(sender),
                getAliasFromEntity(receiver),
                DEFAULT_SERIAL_NUMBER);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        final var callData = functionCall.encodeFunctionCall();
        final var contractFunctionProvider = ContractFunctionProviderRecord.builder()
                .contractAddress(Address.fromHexString(contract.getContractAddress()))
                .sender(Address.fromHexString(getAliasFromEntity(sender)))
                .build();
        verifyOpcodeTracerCall(callData, contractFunctionProvider);
    }

    @Test
    void cryptoTransferHbars() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var sender = accountEntityWithEvmAddressPersist();
        final var receiver = accountEntityWithEvmAddressPersist();
        final var payer = accountEntityWithEvmAddressPersist();

        long timestampForBalances = payer.getCreatedTimestamp();
        accountBalancePersist(payer, timestampForBalances);
        accountBalancePersist(sender, timestampForBalances);
        accountBalancePersist(treasuryEntity, timestampForBalances);

        // When
        testWeb3jService.setSender(getAliasFromEntity(payer));
        final var transferList = new TransferList(List.of(
                new AccountAmount(getAliasFromEntity(sender), BigInteger.valueOf(-5L), false),
                new AccountAmount(getAliasFromEntity(receiver), BigInteger.valueOf(5L), false)));

        final var functionCall = contract.call_cryptoTransferExternal(transferList, new ArrayList<>());

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(
                functionCall.encodeFunctionCall(),
                getContractFunctionProviderWithSender(contract.getContractAddress(), payer));
    }

    @Test
    void cryptoTransferToken() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var sender = accountEntityWithEvmAddressPersist();
        final var token = fungibleTokenPersist();
        final var receiver = accountEntityWithEvmAddressPersist();
        final var payer = accountEntityWithEvmAddressPersist();
        final var tokenId = token.getTokenId();

        long timestampForBalances = payer.getCreatedTimestamp();
        final var entity = EntityId.of(tokenId);
        accountBalancePersist(payer, timestampForBalances);
        tokenBalancePersist(payer.toEntityId(), entity, timestampForBalances);

        accountBalancePersist(sender, timestampForBalances);
        tokenBalancePersist(sender.toEntityId(), entity, timestampForBalances);

        tokenBalancePersist(receiver.toEntityId(), entity, timestampForBalances);
        accountBalancePersist(treasuryEntity, timestampForBalances);

        tokenAccountPersist(tokenId, sender.getId());
        tokenAccountPersist(tokenId, receiver.getId());
        tokenAccountPersist(tokenId, payer.getId());
        // When
        testWeb3jService.setSender(getAliasFromEntity(payer));
        final var tokenTransferList = new TokenTransferList(
                toAddress(tokenId).toHexString(),
                List.of(
                        new AccountAmount(getAliasFromEntity(sender), BigInteger.valueOf(5L), false),
                        new AccountAmount(getAliasFromEntity(receiver), BigInteger.valueOf(-5L), false)),
                new ArrayList<>());

        final var functionCall =
                contract.call_cryptoTransferExternal(new TransferList(new ArrayList<>()), List.of(tokenTransferList));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(
                functionCall.encodeFunctionCall(),
                getContractFunctionProviderWithSender(contract.getContractAddress(), payer));
    }

    @Test
    void cryptoTransferHbarsAndToken() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var sender = accountEntityWithEvmAddressPersist();
        final var tokenEntity = fungibleTokenPersist();
        final var receiver = accountEntityWithEvmAddressPersist();
        final var payer = accountEntityWithEvmAddressPersist();
        long timestampForBalances = payer.getCreatedTimestamp();
        final var tokenId = tokenEntity.getTokenId();
        final var entity = EntityId.of(tokenId);

        accountBalancePersist(payer, timestampForBalances);
        tokenBalancePersist(payer.toEntityId(), entity, timestampForBalances);

        accountBalancePersist(sender, timestampForBalances);
        tokenBalancePersist(sender.toEntityId(), entity, timestampForBalances);

        tokenBalancePersist(receiver.toEntityId(), entity, timestampForBalances);

        accountBalancePersist(treasuryEntity, timestampForBalances);

        tokenAccountPersist(tokenId, sender.getId());
        tokenAccountPersist(tokenId, receiver.getId());
        tokenAccountPersist(tokenId, payer.getId());

        // When
        testWeb3jService.setSender(getAliasFromEntity(payer));
        final var transferList = new TransferList(List.of(
                new AccountAmount(getAliasFromEntity(sender), BigInteger.valueOf(-5L), false),
                new AccountAmount(getAliasFromEntity(receiver), BigInteger.valueOf(5L), false)));

        final var tokenTransferList = new TokenTransferList(
                toAddress(tokenId).toHexString(),
                List.of(
                        new AccountAmount(getAliasFromEntity(sender), BigInteger.valueOf(5L), false),
                        new AccountAmount(getAliasFromEntity(receiver), BigInteger.valueOf(-5L), false)),
                new ArrayList<>());

        final var functionCall = contract.call_cryptoTransferExternal(transferList, List.of(tokenTransferList));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(
                functionCall.encodeFunctionCall(),
                getContractFunctionProviderWithSender(contract.getContractAddress(), payer));
    }

    @Test
    void cryptoTransferNft() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var sender = accountEntityWithEvmAddressPersist();
        final var treasuryEntityId = accountEntityPersist().toEntityId();
        final var receiver = accountEntityWithEvmAddressPersist();
        final var payer = accountEntityWithEvmAddressPersist();
        accountBalanceRecordsPersist(payer);

        final var token = nonFungibleTokenPersistWithTreasury(treasuryEntityId);
        final var tokenId = token.getTokenId();
        nftPersistCustomizable(n -> n.tokenId(tokenId).accountId(sender.toEntityId()));

        tokenAccountPersist(tokenId, payer.getId());
        tokenAccountPersist(tokenId, sender.getId());
        tokenAccountPersist(tokenId, receiver.getId());

        // When
        testWeb3jService.setSender(getAliasFromEntity(payer));
        final var tokenTransferList = new TokenTransferList(
                toAddress(tokenId).toHexString(),
                new ArrayList<>(),
                List.of(new NftTransfer(
                        getAliasFromEntity(sender), getAliasFromEntity(receiver), DEFAULT_SERIAL_NUMBER, false)));

        final var functionCall =
                contract.call_cryptoTransferExternal(new TransferList(new ArrayList<>()), List.of(tokenTransferList));

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(
                functionCall.encodeFunctionCall(),
                getContractFunctionProviderWithSender(contract.getContractAddress(), payer));
    }

    @Test
    void updateTokenInfo() throws Exception {
        // Given
        final var treasuryAccount = accountEntityPersist();
        final var tokenWithAutoRenewPair =
                persistTokenWithAutoRenewAndTreasuryAccounts(TokenTypeEnum.FUNGIBLE_COMMON, treasuryAccount);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var token = populateHederaToken(
                contract.getContractAddress(), TokenTypeEnum.FUNGIBLE_COMMON, treasuryAccount.toEntityId());

        // When
        final var functionCall =
                contract.call_updateTokenInfoExternal(getAddressFromEntity(tokenWithAutoRenewPair.getLeft()), token);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @Test
    void updateTokenExpiry() throws Exception {
        // Given
        final var treasuryAccount = accountEntityPersist();
        final var tokenWithAutoRenewPair =
                persistTokenWithAutoRenewAndTreasuryAccounts(TokenTypeEnum.FUNGIBLE_COMMON, treasuryAccount);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenExpiry = new Expiry(
                BigInteger.valueOf(Instant.now().getEpochSecond() + 8_000_000L),
                toAddress(tokenWithAutoRenewPair.getRight().toEntityId()).toHexString(),
                BigInteger.valueOf(8_000_000));

        // When
        final var functionCall = contract.call_updateTokenExpiryInfoExternal(
                getAddressFromEntity(tokenWithAutoRenewPair.getLeft()), tokenExpiry);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            FUNGIBLE_COMMON, ED25519
                            FUNGIBLE_COMMON, ECDSA_SECPK256K1
                            FUNGIBLE_COMMON, CONTRACT_ID
                            FUNGIBLE_COMMON, DELEGATABLE_CONTRACT_ID
                            NON_FUNGIBLE_UNIQUE, ED25519
                            NON_FUNGIBLE_UNIQUE, ECDSA_SECPK256K1
                            NON_FUNGIBLE_UNIQUE, CONTRACT_ID
                            NON_FUNGIBLE_UNIQUE, DELEGATABLE_CONTRACT_ID
                            """)
    void updateTokenKey(final TokenTypeEnum tokenTypeEnum, final KeyValueType keyValueType) throws Exception {
        // Given
        final var allCasesKeyType = 0b1111111;
        final var treasuryAccount = accountEntityPersist();
        final var tokenWithAutoRenewPair = persistTokenWithAutoRenewAndTreasuryAccounts(tokenTypeEnum, treasuryAccount);

        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);

        final var tokenKeys = new ArrayList<TokenKey>();
        tokenKeys.add(new TokenKey(
                BigInteger.valueOf(allCasesKeyType), getKeyValueForType(keyValueType, contract.getContractAddress())));

        // When
        final var functionCall = contract.call_updateTokenKeysExternal(
                getAddressFromEntity(tokenWithAutoRenewPair.getLeft()), tokenKeys);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract, ZERO_VALUE);
        verifyOpcodeTracerCall(functionCall.encodeFunctionCall(), contract);
    }

    private HederaToken populateHederaToken(
            final String contractAddress, final TokenTypeEnum tokenType, final EntityId treasuryAccountId) {
        final var autoRenewAccount =
                accountEntityWithEvmAddressPersist(); // the account that is going to be charged for token renewal upon
        // expiration
        final var tokenEntity = domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).autoRenewAccountId(autoRenewAccount.getId()))
                .persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.tokenId(tokenEntity.getId()).type(tokenType).treasuryAccountId(treasuryAccountId))
                .persist();

        final var supplyKey = new KeyValue(
                Boolean.FALSE,
                contractAddress,
                new byte[0],
                new byte[0],
                Address.ZERO.toHexString()); // the key needed for token minting or burning
        final var keys = new ArrayList<TokenKey>();
        keys.add(new TokenKey(AbstractContractCallServiceTest.KeyType.SUPPLY_KEY.getKeyTypeNumeric(), supplyKey));

        return new HederaToken(
                token.getName(),
                token.getSymbol(),
                getAddressFromEntityId(treasuryAccountId), // id of the account holding the initial token supply
                tokenEntity.getMemo(), // token description encoded in UTF-8 format
                true,
                BigInteger.valueOf(10_000L),
                false,
                keys,
                new Expiry(
                        BigInteger.valueOf(Instant.now().getEpochSecond() + 8_000_000L),
                        getAliasFromEntity(autoRenewAccount),
                        BigInteger.valueOf(8_000_000)));
    }

    private KeyValue getKeyValueForType(final KeyValueType keyValueType, String contractAddress) {
        final var ed25519Key =
                Bytes.wrap(domainBuilder.key(KeyCase.ED25519)).slice(2).toArray();

        return switch (keyValueType) {
            case CONTRACT_ID ->
                new KeyValue(Boolean.FALSE, contractAddress, new byte[0], new byte[0], Address.ZERO.toHexString());
            case ED25519 ->
                new KeyValue(
                        Boolean.FALSE, Address.ZERO.toHexString(), ed25519Key, new byte[0], Address.ZERO.toHexString());
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

    private HederaToken convertTokenEntityToHederaToken(final Token token) {
        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.id(token.getTokenId())).get();
        final var treasuryAccountId = token.getTreasuryAccountId();
        final var keys = new ArrayList<TokenKey>();
        final var entityRenewAccountId = tokenEntity.getAutoRenewAccountId();

        return new HederaToken(
                token.getName(),
                token.getSymbol(),
                treasuryAccountId != null
                        ? asHexedEvmAddress(new Id(
                                treasuryAccountId.getShard(), treasuryAccountId.getRealm(), treasuryAccountId.getNum()))
                        : Address.ZERO.toHexString(),
                new String(token.getMetadata(), StandardCharsets.UTF_8),
                token.getSupplyType().equals(TokenSupplyTypeEnum.FINITE),
                BigInteger.valueOf(token.getMaxSupply()),
                token.getFreezeDefault(),
                keys,
                new Expiry(
                        BigInteger.valueOf(tokenEntity.getEffectiveExpiration()),
                        toAddress(entityRenewAccountId).toHexString(),
                        BigInteger.valueOf(tokenEntity.getEffectiveExpiration())));
    }

    private HederaToken convertTokenEntityToHederaToken(final Token token, final Entity entity) {
        final var treasuryAccountId = token.getTreasuryAccountId();
        final var keys = new ArrayList<TokenKey>();
        final var entityRenewAccountId = entity.getAutoRenewAccountId();

        return new HederaToken(
                token.getName(),
                token.getSymbol(),
                asHexedEvmAddress(
                        new Id(treasuryAccountId.getShard(), treasuryAccountId.getRealm(), treasuryAccountId.getNum())),
                new String(token.getMetadata(), StandardCharsets.UTF_8),
                token.getSupplyType().equals(TokenSupplyTypeEnum.FINITE),
                BigInteger.valueOf(token.getMaxSupply()),
                token.getFreezeDefault(),
                keys,
                new Expiry(
                        BigInteger.valueOf(entity.getEffectiveExpiration()),
                        toAddress(entityRenewAccountId).toHexString(),
                        BigInteger.valueOf(entity.getEffectiveExpiration())));
    }

    private void contractPersistWithNoKey(final String binary, final EntityId entityId) {
        final var contractBytes = Hex.decode(binary.replace(HEX_PREFIX, ""));
        final var entity = domainBuilder
                .entity(entityId)
                .customize(e -> e.type(CONTRACT).alias(null).evmAddress(null).key(null))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(entity.getId()).runtimeBytecode(contractBytes))
                .persist();

        domainBuilder
                .contractState()
                .customize(c -> c.contractId(entity.getId()))
                .persist();
    }

    private Token persistToken(final boolean isFungible) {
        return isFungible ? fungibleTokenPersist() : nonFungibleTokenPersist();
    }
}
