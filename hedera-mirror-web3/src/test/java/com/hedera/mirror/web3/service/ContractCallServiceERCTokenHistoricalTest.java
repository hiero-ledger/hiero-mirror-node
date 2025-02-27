// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.exception.BlockNumberNotFoundException.UNKNOWN_BLOCK_NUMBER;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.EVM_V_34_BLOCK;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenHistory;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.generated.ERCTestContractHistorical;
import java.math.BigInteger;
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
            final var tokenEntity = nftPersistHistorical(owner.toEntityId(), owner.toEntityId(), EntityId.EMPTY);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_getApproved(getAddressFromEntity(tokenEntity), DEFAULT_SERIAL_NUMBER)
                    : contract.call_getApprovedNonStatic(getAddressFromEntity(tokenEntity), DEFAULT_SERIAL_NUMBER);
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void getApproved(final boolean isStatic) {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var spender = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var nftToken = nftPersistHistorical(owner.toEntityId(), owner.toEntityId(), spender.toEntityId());
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_getApproved(getAddressFromEntity(nftToken), DEFAULT_SERIAL_NUMBER)
                    : contract.call_getApprovedNonStatic(getAddressFromEntity(nftToken), DEFAULT_SERIAL_NUMBER);
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void isApproveForAll(final boolean isStatic) {
            // Given
            final var owner = accountEntityPersistHistorical(historicalRange);
            final var spender = accountEntityPersistHistorical(historicalRange);
            final var nftToken = nftPersistHistorical(owner.toEntityId(), owner.toEntityId(), spender.toEntityId());
            nftAllowancePersistHistorical(nftToken, owner, spender, historicalRange);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_isApprovedForAll(
                            getAddressFromEntity(nftToken), getAddressFromEntity(owner), getAddressFromEntity(spender))
                    : contract.call_isApprovedForAllNonStatic(
                            getAddressFromEntity(nftToken), getAddressFromEntity(owner), getAddressFromEntity(spender));
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void isApproveForAllWithAlias(final boolean isStatic) {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var spender = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var nftToken = nftPersistHistorical(owner.toEntityId(), owner.toEntityId(), spender.toEntityId());
            nftAllowancePersistHistorical(nftToken, owner, spender, historicalRange);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_isApprovedForAll(
                            getAddressFromEntity(nftToken), getAliasFromEntity(owner), getAliasFromEntity(spender))
                    : contract.call_isApprovedForAllNonStatic(
                            getAddressFromEntity(nftToken), getAliasFromEntity(owner), getAliasFromEntity(spender));
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
            fungibleTokenAllowancePersistHistorical(token, owner, spender);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_allowance(
                            getAddressFromEntity(token), getAddressFromEntity(owner), getAddressFromEntity(spender))
                    : contract.call_allowanceNonStatic(
                            getAddressFromEntity(token), getAddressFromEntity(owner), getAddressFromEntity(spender));
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
            fungibleTokenAllowancePersistHistorical(token, owner, spender);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_allowance(
                            getAddressFromEntity(token), getAliasFromEntity(owner), getAliasFromEntity(spender))
                    : contract.call_allowanceNonStatic(
                            getAddressFromEntity(token), getAliasFromEntity(owner), getAliasFromEntity(spender));
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void decimals(final boolean isStatic) {
            // Given
            final var tokenEntity = tokenEntityPersistHistorical(historicalRange);
            invalidFungibleTokenPersist(tokenEntity);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_decimals(getAddressFromEntity(tokenEntity))
                    : contract.call_decimalsNonStatic(getAddressFromEntity(tokenEntity));
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void totalSupply(final boolean isStatic) {
            // Given
            final var spender = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var tokenEntity = tokenEntityPersistHistorical(historicalRange);
            final var token = fungibleTokenPersistHistorical(tokenEntity);
            final var totalSupply = token.getTotalSupply();
            balancePersistHistorical(toAddress(tokenEntity.getId()), toAddress(spender.getId()), totalSupply);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_totalSupply(getAddressFromEntity(tokenEntity))
                    : contract.call_totalSupplyNonStatic(getAddressFromEntity(tokenEntity));
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void symbol(final boolean isStatic) {
            // Given
            final var tokenEntity = tokenEntityPersistHistorical(historicalRange);
            fungibleTokenPersistHistorical(tokenEntity);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_symbol(getAddressFromEntity(tokenEntity))
                    : contract.call_symbolNonStatic(getAddressFromEntity(tokenEntity));
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void balanceOf(final boolean isStatic) {
            // Given
            final var owner = accountEntityPersistHistorical(historicalRange);
            final var tokenEntity = fungibleTokenPersistHistorical(historicalRange);
            tokenAccountFrozenRelationshipPersistHistorical(tokenEntity, owner, historicalRange);
            // The token needs to exist in the "token" table in order to get its type, so we duplicate the data for the
            // historical token.
            final var token =
                    fungibleTokenPersist(tokenEntity, domainBuilder.entity().get());
            balancePersistHistorical(toAddress(token.getTokenId()), toAddress(owner.getId()), token.getTotalSupply());
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_balanceOf(getAddressFromEntity(tokenEntity), getAddressFromEntity(owner))
                    : contract.call_balanceOfNonStatic(getAddressFromEntity(tokenEntity), getAddressFromEntity(owner));
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void balanceOfWithAlias(final boolean isStatic) {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var tokenEntity = fungibleTokenPersistHistorical(historicalRange);
            tokenAccountFrozenRelationshipPersistHistorical(tokenEntity, owner, historicalRange);
            // The token needs to exist in the "token" table in order to get its type, so we duplicate the data for the
            // historical token.
            final var token =
                    fungibleTokenPersist(tokenEntity, domainBuilder.entity().get());
            balancePersistHistorical(toAddress(token.getTokenId()), toAddress(owner.getId()), token.getTotalSupply());
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_balanceOf(getAddressFromEntity(tokenEntity), getAliasFromEntity(owner))
                    : contract.call_balanceOfNonStatic(getAddressFromEntity(tokenEntity), getAliasFromEntity(owner));
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void name(final boolean isStatic) {
            // Given
            final var tokenEntity = tokenEntityPersistHistorical(historicalRange);
            fungibleTokenPersistHistorical(tokenEntity);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_name(getAddressFromEntity(tokenEntity))
                    : contract.call_nameNonStatic(getAddressFromEntity(tokenEntity));
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void ownerOf(final boolean isStatic) {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var nftToken = nftPersistHistorical(owner.toEntityId());
            tokenAccountFrozenRelationshipPersistHistorical(nftToken, owner, historicalRange);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_getOwnerOf(getAddressFromEntity(nftToken), DEFAULT_SERIAL_NUMBER)
                    : contract.call_getOwnerOfNonStatic(getAddressFromEntity(nftToken), DEFAULT_SERIAL_NUMBER);
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void emptyOwnerOf(final boolean isStatic) {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var nftToken = nftPersistHistorical(owner.toEntityId());
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_getOwnerOf(getAddressFromEntity(nftToken), INVALID_SERIAL_NUMBER)
                    : contract.call_getOwnerOfNonStatic(getAddressFromEntity(nftToken), INVALID_SERIAL_NUMBER);
            // Then
            assertThatThrownBy(result::send).isInstanceOf(MirrorEvmTransactionException.class);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void tokenURI(final boolean isStatic) {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var metadata = "NFT_METADATA_URI";
            final var tokenEntity = tokenEntityPersistHistorical(historicalRange);
            nftPersistHistoricalWithMetadata(tokenEntity, owner, metadata);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_tokenURI(getAddressFromEntity(tokenEntity), DEFAULT_SERIAL_NUMBER)
                    : contract.call_tokenURINonStatic(getAddressFromEntity(tokenEntity), DEFAULT_SERIAL_NUMBER);
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
            final var tokenEntity = nftPersistHistorical(owner.toEntityId(), owner.toEntityId(), EntityId.EMPTY);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_getApproved(getAddressFromEntity(tokenEntity), DEFAULT_SERIAL_NUMBER)
                            .send()
                    : contract.call_getApprovedNonStatic(getAddressFromEntity(tokenEntity), DEFAULT_SERIAL_NUMBER)
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
            final var nftToken = nftPersistHistorical(owner.toEntityId(), owner.toEntityId(), spender.toEntityId());
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_getApproved(getAddressFromEntity(nftToken), DEFAULT_SERIAL_NUMBER)
                            .send()
                    : contract.call_getApprovedNonStatic(getAddressFromEntity(nftToken), DEFAULT_SERIAL_NUMBER)
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
            final var nftToken = nftPersistHistorical(owner.toEntityId(), owner.toEntityId(), spender.toEntityId());
            nftAllowancePersistHistorical(nftToken, owner, spender, historicalRange);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_isApprovedForAll(
                                    getAddressFromEntity(nftToken),
                                    getAddressFromEntity(owner),
                                    getAddressFromEntity(spender))
                            .send()
                    : contract.call_isApprovedForAllNonStatic(
                                    getAddressFromEntity(nftToken),
                                    getAddressFromEntity(owner),
                                    getAddressFromEntity(spender))
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
            final var nftToken = nftPersistHistorical(owner.toEntityId(), owner.toEntityId(), spender.toEntityId());
            nftAllowancePersistHistorical(nftToken, owner, spender, historicalRange);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_isApprovedForAll(
                                    getAddressFromEntity(nftToken),
                                    getAliasFromEntity(owner),
                                    getAliasFromEntity(spender))
                            .send()
                    : contract.call_isApprovedForAllNonStatic(
                                    getAddressFromEntity(nftToken),
                                    getAliasFromEntity(owner),
                                    getAliasFromEntity(spender))
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
            fungibleTokenAllowancePersistHistorical(token, owner, spender);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_allowance(
                                    getAddressFromEntity(token),
                                    getAddressFromEntity(owner),
                                    getAddressFromEntity(spender))
                            .send()
                    : contract.call_allowanceNonStatic(
                                    getAddressFromEntity(token),
                                    getAddressFromEntity(owner),
                                    getAddressFromEntity(spender))
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
            fungibleTokenAllowancePersistHistorical(token, owner, spender);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_allowance(
                                    getAddressFromEntity(token), getAliasFromEntity(owner), getAliasFromEntity(spender))
                            .send()
                    : contract.call_allowanceNonStatic(
                                    getAddressFromEntity(token), getAliasFromEntity(owner), getAliasFromEntity(spender))
                            .send();
            // Then
            assertThat(result).isEqualTo(BigInteger.valueOf(DEFAULT_AMOUNT_GRANTED));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void decimals(final boolean isStatic) throws Exception {
            // Given
            final var tokenEntity = tokenEntityPersistHistorical(historicalRange);
            invalidFungibleTokenPersist(tokenEntity);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_decimals(getAddressFromEntity(tokenEntity)).send()
                    : contract.call_decimalsNonStatic(getAddressFromEntity(tokenEntity))
                            .send();
            // Then
            assertThat(result).isEqualTo(BigInteger.valueOf(DEFAULT_DECIMALS));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void totalSupply(final boolean isStatic) throws Exception {
            // Given
            final var spender = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var tokenEntity = tokenEntityPersistHistorical(historicalRange);
            final var token = fungibleTokenPersistHistorical(tokenEntity);
            final var totalSupply = token.getTotalSupply();
            balancePersistHistorical(toAddress(tokenEntity.getId()), toAddress(spender.getId()), totalSupply);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_totalSupply(getAddressFromEntity(tokenEntity))
                            .send()
                    : contract.call_totalSupplyNonStatic(getAddressFromEntity(tokenEntity))
                            .send();
            // Then
            assertThat(result).isEqualTo(BigInteger.valueOf(totalSupply));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void symbol(final boolean isStatic) throws Exception {
            // Given
            final var tokenEntity = tokenEntityPersistHistorical(historicalRange);
            final var token = fungibleTokenPersistHistorical(tokenEntity);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_symbol(getAddressFromEntity(tokenEntity)).send()
                    : contract.call_symbolNonStatic(getAddressFromEntity(tokenEntity))
                            .send();
            // Then
            assertThat(result).isEqualTo(token.getSymbol());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void balanceOf(final boolean isStatic) throws Exception {
            // Given
            final var owner = accountEntityPersistHistorical(historicalRange);
            final var tokenEntity = fungibleTokenPersistHistorical(historicalRange);
            // The token needs to exist in the "token" table in order to get its type, so we duplicate the data for the
            // historical token.
            final var token =
                    fungibleTokenPersist(tokenEntity, domainBuilder.entity().get());
            tokenAccountFrozenRelationshipPersistHistorical(tokenEntity, owner, historicalRange);
            balancePersistHistorical(toAddress(token.getTokenId()), toAddress(owner.getId()), token.getTotalSupply());
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_balanceOf(getAddressFromEntity(tokenEntity), getAddressFromEntity(owner))
                            .send()
                    : contract.call_balanceOfNonStatic(getAddressFromEntity(tokenEntity), getAddressFromEntity(owner))
                            .send();
            // Then
            assertThat(result).isEqualTo(BigInteger.valueOf(DEFAULT_TOKEN_BALANCE));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void balanceOfWithAlias(final boolean isStatic) throws Exception {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var tokenEntity = fungibleTokenPersistHistorical(historicalRange);
            // The token needs to exist in the "token" table in order to get its type, so we duplicate the data for the
            // historical token.
            final var token =
                    fungibleTokenPersist(tokenEntity, domainBuilder.entity().get());
            tokenAccountFrozenRelationshipPersistHistorical(tokenEntity, owner, historicalRange);
            balancePersistHistorical(toAddress(token.getTokenId()), toAddress(owner.getId()), token.getTotalSupply());
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_balanceOf(getAddressFromEntity(tokenEntity), getAliasFromEntity(owner))
                            .send()
                    : contract.call_balanceOfNonStatic(getAddressFromEntity(tokenEntity), getAliasFromEntity(owner))
                            .send();
            // Then
            assertThat(result).isEqualTo(BigInteger.valueOf(DEFAULT_TOKEN_BALANCE));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void name(final boolean isStatic) throws Exception {
            // Given
            final var tokenEntity = tokenEntityPersistHistorical(historicalRange);
            final var token = fungibleTokenPersistHistorical(tokenEntity);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_name(getAddressFromEntity(tokenEntity)).send()
                    : contract.call_nameNonStatic(getAddressFromEntity(tokenEntity))
                            .send();
            // Then
            assertThat(result).isEqualTo(token.getName());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void ownerOf(final boolean isStatic) throws Exception {
            // Given
            final var owner = accountEntityPersistHistorical(historicalRange);
            final var nftToken = nftPersistHistorical(owner.toEntityId());
            tokenAccountFrozenRelationshipPersistHistorical(nftToken, owner, historicalRange);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_getOwnerOf(getAddressFromEntity(nftToken), DEFAULT_SERIAL_NUMBER)
                            .send()
                    : contract.call_getOwnerOfNonStatic(getAddressFromEntity(nftToken), DEFAULT_SERIAL_NUMBER)
                            .send();
            // Then
            assertThat(result).isEqualTo(getAddressFromEntity(owner));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void emptyOwnerOf(final boolean isStatic) throws Exception {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var nftToken = nftPersistHistorical(owner.toEntityId());
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var functionCall = isStatic
                    ? contract.call_getOwnerOf(getAddressFromEntity(nftToken), INVALID_SERIAL_NUMBER)
                    : contract.call_getOwnerOfNonStatic(getAddressFromEntity(nftToken), INVALID_SERIAL_NUMBER);
            // Then
            if (mirrorNodeEvmProperties.isModularizedServices()) {
                assertThatThrownBy(functionCall::send).isInstanceOf(MirrorEvmTransactionException.class);
            } else {
                assertThat(functionCall.send()).isEqualTo(Address.ZERO.toHexString());
            }
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void tokenURI(final boolean isStatic) throws Exception {
            // Given
            final var owner = accountEntityPersistWithEvmAddressHistorical(historicalRange);
            final var metadata = "NFT_METADATA_URI";
            final var tokenEntity = tokenEntityPersistHistorical(historicalRange);
            nftPersistHistoricalWithMetadata(tokenEntity, owner, metadata);
            final var contract = testWeb3jService.deploy(ERCTestContractHistorical::deploy);
            // When
            final var result = isStatic
                    ? contract.call_tokenURI(getAddressFromEntity(tokenEntity), DEFAULT_SERIAL_NUMBER)
                            .send()
                    : contract.call_tokenURINonStatic(getAddressFromEntity(tokenEntity), DEFAULT_SERIAL_NUMBER)
                            .send();
            // Then
            assertThat(result).isEqualTo(metadata);
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

    private void balancePersistHistorical(
            final Address tokenAddress, final Address senderAddress, final long totalSupply) {
        final var tokenEntityId = entityIdFromEvmAddress(tokenAddress);
        final var accountId = entityIdFromEvmAddress(senderAddress);
        final var tokenId = entityIdFromEvmAddress(tokenAddress);
        domainBuilder
                .tokenBalance()
                .customize(tb -> tb.id(new TokenBalance.Id(treasuryEntity.getCreatedTimestamp(), accountId, tokenId))
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

    private Entity nftPersistHistorical(final EntityId treasury) {
        return nftPersistHistorical(treasury, treasury);
    }

    private Entity nftPersistHistorical(final EntityId treasury, final EntityId owner) {
        return nftPersistHistorical(treasury, owner, owner);
    }

    private Entity nftPersistHistorical(final EntityId treasury, final EntityId owner, final EntityId spender) {
        return nftPersistHistorical(treasury, owner, spender, domainBuilder.key());
    }

    private Entity nftPersistHistorical(
            final EntityId treasury, final EntityId owner, final EntityId spender, final byte[] kycKey) {
        final var tokenEntity = tokenEntityPersistHistorical(historicalRange);
        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(treasury)
                        .timestampRange(historicalRange)
                        .kycKey(kycKey))
                .persist();
        domainBuilder
                .nftHistory()
                .customize(n -> n.tokenId(tokenEntity.getId())
                        .serialNumber(DEFAULT_SERIAL_NUMBER.longValue())
                        .spender(spender)
                        .accountId(owner)
                        .timestampRange(historicalRange))
                .persist();
        return tokenEntity;
    }

    private void nftPersistHistoricalWithMetadata(final Entity tokenEntity, final Entity owner, final String metadata) {
        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(owner.toEntityId())
                        .timestampRange(historicalRange))
                .persist();
        domainBuilder
                .nftHistory()
                .customize(n -> n.tokenId(tokenEntity.getId())
                        .serialNumber(DEFAULT_SERIAL_NUMBER.longValue())
                        .accountId(owner.toEntityId())
                        .metadata(metadata.getBytes())
                        .timestampRange(historicalRange))
                .persist();
    }

    private void invalidFungibleTokenPersist(final Entity tokenEntity) {
        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .decimals(DEFAULT_DECIMALS)
                        .timestampRange(historicalRange)
                        .createdTimestamp(historicalRange.lowerEndpoint()))
                .persist();
    }

    private TokenHistory fungibleTokenPersistHistorical(final Entity tokenEntity) {
        return domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .timestampRange(historicalRange)
                        .createdTimestamp(historicalRange.lowerEndpoint()))
                .persist();
    }

    private void fungibleTokenAllowancePersistHistorical(final Entity token, final Entity owner, final Entity spender) {
        domainBuilder
                .tokenAllowance()
                .customize(a -> a.tokenId(token.getId())
                        .owner(owner.getNum())
                        .spender(spender.getNum())
                        .amount(DEFAULT_AMOUNT_GRANTED)
                        .amountGranted(DEFAULT_AMOUNT_GRANTED)
                        .timestampRange(historicalRange))
                .persist();
    }
}
