// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.hiero.mirror.web3.exception.BlockNumberNotFoundException.UNKNOWN_BLOCK_NUMBER;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.EVM_V_34_BLOCK;

import com.google.common.collect.Range;
import java.math.BigInteger;
import org.hiero.mirror.common.domain.balance.TokenBalance;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.NftHistory;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.web3j.generated.ERCTestContractHistorical;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContractCallServiceERCTokenHistoricalTest extends AbstractContractCallServiceHistoricalTest {
    private Range<Long> historicalRange;

    @Nested
    class BeforeEvm34Tests {
        @BeforeEach
        void beforeEach() {
            historicalRange = setUpHistoricalContextBeforeEvm34();
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void getApprovedEmptySpender(final boolean isStatic) {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var token =
                    nftPersistHistorical(historicalRange, owner.toEntityId(), owner.toEntityId(), EntityId.EMPTY);
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_getApproved(tokenAddress, DEFAULT_SERIAL_NUMBER)
                    : contract.call_getApprovedNonStatic(tokenAddress, DEFAULT_SERIAL_NUMBER);
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void getApproved(final boolean isStatic) {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var spender = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var token =
                    nftPersistHistorical(historicalRange, owner.toEntityId(), owner.toEntityId(), spender.toEntityId());
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_getApproved(tokenAddress, DEFAULT_SERIAL_NUMBER)
                    : contract.call_getApprovedNonStatic(tokenAddress, DEFAULT_SERIAL_NUMBER);
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void isApproveForAll(final boolean isStatic) {
            // Given
            final var owner = accountEntityPersistHistorical(historicalRange);
            final var spender = accountEntityPersistHistorical(historicalRange);
            final var token =
                    nftPersistHistorical(historicalRange, owner.toEntityId(), owner.toEntityId(), spender.toEntityId());
            final var tokenId = token.getTokenId();
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();
            final var ownerAddress = getAddressFromEntity(owner);
            final var spenderAddress = getAddressFromEntity(spender);
            nftAllowancePersistHistorical(tokenId, owner, spender, historicalRange);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_isApprovedForAll(tokenAddress, ownerAddress, spenderAddress)
                    : contract.call_isApprovedForAllNonStatic(tokenAddress, ownerAddress, spenderAddress);
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void isApproveForAllWithAlias(final boolean isStatic) {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var spender = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var token =
                    nftPersistHistorical(historicalRange, owner.toEntityId(), owner.toEntityId(), spender.toEntityId());
            final var tokenId = token.getTokenId();
            final var tokenAddress = toAddress(tokenId).toHexString();
            final var ownerAddress = getAliasFromEntity(owner);
            final var spenderAddress = getAliasFromEntity(spender);
            nftAllowancePersistHistorical(tokenId, owner, spender, historicalRange);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_isApprovedForAll(tokenAddress, ownerAddress, spenderAddress)
                    : contract.call_isApprovedForAllNonStatic(tokenAddress, ownerAddress, spenderAddress);
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void allowance(final boolean isStatic) {
            // Given
            final var owner = accountEntityPersistHistorical(historicalRange);
            final var spender = accountEntityPersistHistorical(historicalRange);
            final var token = fungibleTokenPersistHistorical(historicalRange);
            final var tokenId = token.getTokenId();
            final var tokenAddress = toAddress(tokenId).toHexString();
            final var ownerAddress = getAddressFromEntity(owner);
            final var spenderAddress = getAddressFromEntity(spender);
            fungibleTokenAllowancePersistHistorical(tokenId, owner, spender);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_allowance(tokenAddress, ownerAddress, spenderAddress)
                    : contract.call_allowanceNonStatic(tokenAddress, ownerAddress, spenderAddress);
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void allowanceWithAlias(final boolean isStatic) {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var spender = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var token = fungibleTokenPersistHistorical(historicalRange);
            final var tokenId = token.getTokenId();
            final var tokenAddress = toAddress(tokenId).toHexString();
            final var ownerAddress = getAliasFromEntity(owner);
            final var spenderAddress = getAliasFromEntity(spender);
            fungibleTokenAllowancePersistHistorical(tokenId, owner, spender);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_allowance(tokenAddress, ownerAddress, spenderAddress)
                    : contract.call_allowanceNonStatic(tokenAddress, ownerAddress, spenderAddress);
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void decimals(final boolean isStatic) {
            // Given
            final var token =
                    fungibleTokenPersistHistoricalCustomizable(historicalRange, t -> t.decimals(DEFAULT_DECIMALS));
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();
            // When
            final var result =
                    isStatic ? contract.call_decimals(tokenAddress) : contract.call_decimalsNonStatic(tokenAddress);
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void totalSupply(final boolean isStatic) {
            // Given
            final var spender = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var token = fungibleTokenPersistHistorical(historicalRange);
            final var totalSupply = token.getTotalSupply();
            final var tokenId = token.getTokenId();
            final var tokenAddress = toAddress(tokenId).toHexString();
            balancePersistHistorical(tokenId, spender.getId(), totalSupply);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_totalSupply(tokenAddress)
                    : contract.call_totalSupplyNonStatic(tokenAddress);
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void symbol(final boolean isStatic) {
            // Given
            final var token = fungibleTokenPersistHistorical(historicalRange);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();
            // When
            final var result =
                    isStatic ? contract.call_symbol(tokenAddress) : contract.call_symbolNonStatic(tokenAddress);
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void balanceOf(final boolean isStatic) {
            // Given
            final var owner = accountEntityPersistHistorical(historicalRange);
            final var tokenHistory = fungibleTokenPersistHistorical(historicalRange);
            final var tokenId = tokenHistory.getTokenId();
            tokenAccountFrozenRelationshipPersistHistorical(tokenId, owner.getId(), historicalRange);
            // The token needs to exist in the "token" table in order to get its type, so we duplicate the data for the
            // historical token.
            final var token = fungibleTokenCustomizable(t -> t.tokenId(tokenId));
            balancePersistHistorical(tokenId, owner.getId(), token.getTotalSupply());
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            final var tokenAddress = toAddress(tokenId).toHexString();
            final var ownerAddress = getAddressFromEntity(owner);
            // When
            final var result = isStatic
                    ? contract.call_balanceOf(tokenAddress, ownerAddress)
                    : contract.call_balanceOfNonStatic(tokenAddress, ownerAddress);
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void balanceOfWithAlias(final boolean isStatic) {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var tokenHistory = fungibleTokenPersistHistorical(historicalRange);
            final var tokenId = tokenHistory.getTokenId();
            tokenAccountFrozenRelationshipPersistHistorical(tokenId, owner.getId(), historicalRange);
            // The token needs to exist in the "token" table in order to get its type, so we duplicate the data for the
            // historical token.
            final var token = fungibleTokenCustomizable(t -> t.tokenId(tokenId));
            balancePersistHistorical(tokenId, owner.getId(), token.getTotalSupply());
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();
            final var ownerAddress = getAddressFromEntity(owner);
            // When
            final var result = isStatic
                    ? contract.call_balanceOf(tokenAddress, ownerAddress)
                    : contract.call_balanceOfNonStatic(tokenAddress, ownerAddress);
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void name(final boolean isStatic) {
            // Given
            final var token = fungibleTokenPersistHistorical(historicalRange);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();
            // When
            final var result = isStatic ? contract.call_name(tokenAddress) : contract.call_nameNonStatic(tokenAddress);
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void ownerOf(final boolean isStatic) {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var ownerEntityId = owner.toEntityId();
            final var token = nftPersistHistorical(historicalRange, ownerEntityId, ownerEntityId, ownerEntityId);
            final var tokenId = token.getTokenId();
            tokenAccountFrozenRelationshipPersistHistorical(tokenId, owner.getId(), historicalRange);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();
            // When
            final var result = isStatic
                    ? contract.call_getOwnerOf(tokenAddress, DEFAULT_SERIAL_NUMBER)
                    : contract.call_getOwnerOfNonStatic(tokenAddress, DEFAULT_SERIAL_NUMBER);
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void emptyOwnerOf(final boolean isStatic) {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange)
                    .toEntityId();
            final var token = nftPersistHistorical(historicalRange, owner, owner, owner);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();
            // When
            final var result = isStatic
                    ? contract.call_getOwnerOf(tokenAddress, INVALID_SERIAL_NUMBER)
                    : contract.call_getOwnerOfNonStatic(tokenAddress, INVALID_SERIAL_NUMBER);
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void tokenURI(final boolean isStatic) {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var tokenEntity = tokenEntityPersistHistorical(historicalRange);
            nftPersistHistorical(tokenEntity.getId(), owner.toEntityId());
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            final var tokenAddress = getAddressFromEntity(tokenEntity);
            // When
            final var result = isStatic
                    ? contract.call_tokenURI(tokenAddress, DEFAULT_SERIAL_NUMBER)
                    : contract.call_tokenURINonStatic(tokenAddress, DEFAULT_SERIAL_NUMBER);
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }
    }

    @Nested
    class AfterEvm34Tests {
        @BeforeEach
        void beforeEach() {
            historicalRange = setUpHistoricalContextAfterEvm34();
        }

        @ParameterizedTest // ОК
        @ValueSource(booleans = {true, false})
        void getApprovedEmptySpender(final boolean isStatic) throws Exception {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var token =
                    nftPersistHistorical(historicalRange, owner.toEntityId(), owner.toEntityId(), EntityId.EMPTY);
            final var tokenId = token.getTokenId();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            final var tokenAddress = toAddress(tokenId).toHexString();
            // When
            final var result = isStatic
                    ? contract.call_getApproved(tokenAddress, DEFAULT_SERIAL_NUMBER)
                            .send()
                    : contract.call_getApprovedNonStatic(tokenAddress, DEFAULT_SERIAL_NUMBER)
                            .send();
            // Then
            assertThat(result).isEqualTo(Address.ZERO.toHexString());
        }

        @ParameterizedTest // ОК
        @ValueSource(booleans = {true, false})
        void getApproved(final boolean isStatic) throws Exception {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var spender = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var token =
                    nftPersistHistorical(historicalRange, owner.toEntityId(), owner.toEntityId(), spender.toEntityId());
            final var tokenId = token.getTokenId();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            final var tokenAddress = toAddress(tokenId).toHexString();
            // When
            final var result = isStatic
                    ? contract.call_getApproved(tokenAddress, DEFAULT_SERIAL_NUMBER)
                            .send()
                    : contract.call_getApprovedNonStatic(tokenAddress, DEFAULT_SERIAL_NUMBER)
                            .send();
            // Then
            assertThat(result).isEqualTo(getAliasFromEntity(spender));
        }

        @ParameterizedTest // OK
        @ValueSource(booleans = {true, false})
        void isApproveForAll(final boolean isStatic) throws Exception {
            // Given
            final var owner = accountEntityPersistHistorical(historicalRange);
            final var spender = accountEntityPersistHistorical(historicalRange);
            final var token =
                    nftPersistHistorical(historicalRange, owner.toEntityId(), owner.toEntityId(), spender.toEntityId());
            final var tokenId = token.getTokenId();
            nftAllowancePersistHistorical(tokenId, owner, spender, historicalRange);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            final var tokenAddress = toAddress(tokenId).toHexString();
            final var ownerAddress = getAddressFromEntity(owner);
            final var spenderAddress = getAddressFromEntity(spender);
            // When
            final var result = isStatic
                    ? contract.call_isApprovedForAll(tokenAddress, ownerAddress, spenderAddress)
                            .send()
                    : contract.call_isApprovedForAllNonStatic(tokenAddress, ownerAddress, spenderAddress)
                            .send();
            // Then
            assertThat(result).isEqualTo(true);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void isApproveForAllWithAlias(final boolean isStatic) throws Exception {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var spender = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var token =
                    nftPersistHistorical(historicalRange, owner.toEntityId(), owner.toEntityId(), spender.toEntityId());
            final var tokenId = token.getTokenId();
            nftAllowancePersistHistorical(tokenId, owner, spender, historicalRange);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            final var tokenAddress = toAddress(tokenId).toHexString();
            final var ownerAddress = getAliasFromEntity(owner);
            final var spenderAddress = getAliasFromEntity(spender);
            // When
            final var result = isStatic
                    ? contract.call_isApprovedForAll(tokenAddress, ownerAddress, spenderAddress)
                            .send()
                    : contract.call_isApprovedForAllNonStatic(tokenAddress, ownerAddress, spenderAddress)
                            .send();
            // Then
            assertThat(result).isEqualTo(true);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void allowance(final boolean isStatic) throws Exception {
            // Given
            final var owner = accountEntityPersistHistorical(historicalRange);
            final var spender = accountEntityPersistHistorical(historicalRange);
            final var token = fungibleTokenPersistHistorical(historicalRange);
            final var tokenId = token.getTokenId();
            fungibleTokenAllowancePersistHistorical(tokenId, owner, spender);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            final var tokenAddress = toAddress(tokenId).toHexString();
            final var ownerAddress = getAddressFromEntity(owner);
            final var spenderAddress = getAddressFromEntity(spender);
            // When
            final var result = isStatic
                    ? contract.call_allowance(tokenAddress, ownerAddress, spenderAddress)
                            .send()
                    : contract.call_allowanceNonStatic(tokenAddress, ownerAddress, spenderAddress)
                            .send();
            // Then
            assertThat(result).isEqualTo(BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void allowanceWithAlias(final boolean isStatic) throws Exception {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var spender = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var token = fungibleTokenPersistHistorical(historicalRange);
            final var tokenId = token.getTokenId();
            fungibleTokenAllowancePersistHistorical(tokenId, owner, spender);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            final var tokenAddress = toAddress(tokenId).toHexString();
            final var ownerAddress = getAliasFromEntity(owner);
            final var spenderAddress = getAliasFromEntity(spender);
            // When
            final var result = isStatic
                    ? contract.call_allowance(tokenAddress, ownerAddress, spenderAddress)
                            .send()
                    : contract.call_allowanceNonStatic(tokenAddress, ownerAddress, spenderAddress)
                            .send();
            // Then
            assertThat(result).isEqualTo(BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void decimals(final boolean isStatic) throws Exception {
            // Given
            final var token =
                    fungibleTokenPersistHistoricalCustomizable(historicalRange, t -> t.decimals(DEFAULT_DECIMALS));
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_decimals(tokenAddress).send()
                    : contract.call_decimalsNonStatic(tokenAddress).send();
            // Then
            assertThat(result).isEqualTo(BigInteger.valueOf(DEFAULT_DECIMALS));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void totalSupply(final boolean isStatic) throws Exception {
            // Given
            final var spender = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var token = fungibleTokenPersistHistorical(historicalRange);
            final var totalSupply = token.getTotalSupply();
            final var tokenId = token.getTokenId();
            final var tokenAddress = toAddress(tokenId).toHexString();
            balancePersistHistorical(tokenId, spender.getId(), totalSupply);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_totalSupply(tokenAddress).send()
                    : contract.call_totalSupplyNonStatic(tokenAddress).send();
            // Then
            assertThat(result).isEqualTo(BigInteger.valueOf(totalSupply));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void symbol(final boolean isStatic) throws Exception {
            // Given
            final var token = fungibleTokenPersistHistorical(historicalRange);
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_symbol(tokenAddress).send()
                    : contract.call_symbolNonStatic(tokenAddress).send();
            // Then
            assertThat(result).isEqualTo(token.getSymbol());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void balanceOf(final boolean isStatic) throws Exception {
            // Given
            final var owner = accountEntityPersistHistorical(historicalRange);
            final var tokenHistory = fungibleTokenPersistHistorical(historicalRange);
            final var tokenId = tokenHistory.getTokenId();
            // The token needs to exist in the "token" table in order to get its type, so we duplicate the data for the
            // historical token.
            final var token = fungibleTokenCustomizable(t -> t.tokenId(tokenId));
            tokenAccountFrozenRelationshipPersistHistorical(tokenId, owner.getId(), historicalRange);
            balancePersistHistorical(tokenId, owner.getId(), token.getTotalSupply());
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            final var tokenAddress = toAddress(tokenId).toHexString();
            final var ownerAddress = getAddressFromEntity(owner);
            // When
            final var result = isStatic
                    ? contract.call_balanceOf(tokenAddress, ownerAddress).send()
                    : contract.call_balanceOfNonStatic(tokenAddress, ownerAddress)
                            .send();
            // Then
            assertThat(result).isEqualTo(BigInteger.valueOf(DEFAULT_TOKEN_BALANCE));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void balanceOfWithAlias(final boolean isStatic) throws Exception {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var tokenHistory = fungibleTokenPersistHistorical(historicalRange);
            final var tokenId = tokenHistory.getTokenId();
            // The token needs to exist in the "token" table in order to get its type, so we duplicate the data for the
            // historical token.
            final var token = fungibleTokenCustomizable(t -> t.tokenId(tokenId));
            tokenAccountFrozenRelationshipPersistHistorical(tokenId, owner.getId(), historicalRange);
            balancePersistHistorical(tokenId, owner.getId(), token.getTotalSupply());
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            final var tokenAddress = toAddress(tokenId).toHexString();
            final var ownerAddress = getAliasFromEntity(owner);
            // When
            final var result = isStatic
                    ? contract.call_balanceOf(tokenAddress, ownerAddress).send()
                    : contract.call_balanceOfNonStatic(tokenAddress, ownerAddress)
                            .send();
            // Then
            assertThat(result).isEqualTo(BigInteger.valueOf(DEFAULT_TOKEN_BALANCE));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void name(final boolean isStatic) throws Exception {
            // Given
            final var token = fungibleTokenPersistHistorical(historicalRange);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();
            // When
            final var result = isStatic
                    ? contract.call_name(tokenAddress).send()
                    : contract.call_nameNonStatic(tokenAddress).send();
            // Then
            assertThat(result).isEqualTo(token.getName());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void ownerOf(final boolean isStatic) throws Exception {
            // Given
            final var owner = accountEntityPersistHistorical(historicalRange);
            final var ownerEntityId = owner.toEntityId();
            final var token = nftPersistHistorical(historicalRange, ownerEntityId, ownerEntityId, ownerEntityId);
            final var tokenId = token.getTokenId();
            tokenAccountFrozenRelationshipPersistHistorical(tokenId, owner.getId(), historicalRange);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            final var tokenAddress = toAddress(tokenId).toHexString();
            // When
            final var result = isStatic
                    ? contract.call_getOwnerOf(tokenAddress, DEFAULT_SERIAL_NUMBER)
                            .send()
                    : contract.call_getOwnerOfNonStatic(tokenAddress, DEFAULT_SERIAL_NUMBER)
                            .send();
            // Then
            assertThat(result).isEqualTo(getAddressFromEntity(owner));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void emptyOwnerOf(final boolean isStatic) throws Exception {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange)
                    .toEntityId();
            final var token = nftPersistHistorical(historicalRange, owner, owner, owner);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            final var tokenAddress = toAddress(token.getTokenId()).toHexString();
            // When
            final var functionCall = isStatic
                    ? contract.call_getOwnerOf(tokenAddress, INVALID_SERIAL_NUMBER)
                    : contract.call_getOwnerOfNonStatic(tokenAddress, INVALID_SERIAL_NUMBER);
            // Then
            assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void tokenURI(final boolean isStatic) throws Exception {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var tokenEntity = tokenEntityPersistHistorical(historicalRange);
            final var nft = nftPersistHistorical(tokenEntity.getId(), owner.toEntityId());
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            final var expectedResult = new String(nft.getMetadata());
            final var tokenAddress = getAddressFromEntity(tokenEntity);
            // When
            final var result = isStatic
                    ? contract.call_tokenURI(tokenAddress, DEFAULT_SERIAL_NUMBER)
                            .send()
                    : contract.call_tokenURINonStatic(tokenAddress, DEFAULT_SERIAL_NUMBER)
                            .send();
            // Then
            assertThat(result).isEqualTo(expectedResult);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {51, Long.MAX_VALUE - 1})
    void ercReadOnlyPrecompileHistoricalNotExistingBlockTest(final long blockNumber) {
        // When
        testWeb3jService.setUseContractCallDeploy(true);
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(blockNumber)));
        // Then
        assertThatThrownBy(() -> testWeb3jService.deploy(ERCTestContractHistorical::deploy))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(UNKNOWN_BLOCK_NUMBER);
    }

    private Range<Long> setUpHistoricalContextAfterEvm34() {
        final var recordFile = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK))
                .persist();
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
        final var rangeAfterEvm34 = Range.closedOpen(recordFile.getConsensusStart(), recordFile.getConsensusEnd());
        testWeb3jService.setHistoricalRange(rangeAfterEvm34);
        return rangeAfterEvm34;
    }

    private Range<Long> setUpHistoricalContextBeforeEvm34() {
        final var recordFileBeforeEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK - 1))
                .persist();
        final var recordFileAfterEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK))
                .persist();
        final var rangeAfterEvm34 =
                Range.closedOpen(recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd());
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
        testWeb3jService.setHistoricalRange(
                Range.closedOpen(recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
        return rangeAfterEvm34;
    }

    private void balancePersistHistorical(final long tokenId, final long senderId, final long totalSupply) {
        final var tokenEntityId = EntityId.of(tokenId);
        final var accountId = EntityId.of(senderId);
        domainBuilder
                .tokenBalance()
                .customize(
                        tb -> tb.id(new TokenBalance.Id(treasuryEntity.getCreatedTimestamp(), accountId, tokenEntityId))
                                .balance(DEFAULT_TOKEN_BALANCE))
                .persist();
        domainBuilder
                .tokenBalance()
                // Expected total supply is 12345
                .customize(tb -> tb.balance(totalSupply - DEFAULT_TOKEN_BALANCE)
                        .id(new TokenBalance.Id(
                                treasuryEntity.getCreatedTimestamp(), domainBuilder.entityId(), tokenEntityId)))
                .persist();
    }

    private NftHistory nftPersistHistorical(final long tokenId, final EntityId owner) {
        nonFungibleTokenPersistHistoricalCustomizable(historicalRange, t -> t.tokenId(tokenId));
        return nftPersistHistoricalCustomizable(
                historicalRange, n -> n.tokenId(tokenId).accountId(owner));
    }

    private void fungibleTokenAllowancePersistHistorical(final long tokenId, final Entity owner, final Entity spender) {
        tokenAllowancePersistCustomizable(ta -> ta.tokenId(tokenId)
                .owner(owner.getId())
                .spender(spender.getId())
                .amount(DEFAULT_AMOUNT_GRANTED)
                .amountGranted(DEFAULT_AMOUNT_GRANTED)
                .timestampRange(historicalRange));
    }
}
