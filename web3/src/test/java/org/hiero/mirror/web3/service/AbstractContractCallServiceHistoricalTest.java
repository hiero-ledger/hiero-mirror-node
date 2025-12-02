// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import com.google.common.collect.Range;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.commons.lang3.tuple.Pair;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.TokenBalance;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.AbstractCustomFee;
import org.hiero.mirror.common.domain.token.FallbackFee;
import org.hiero.mirror.common.domain.token.FixedFee;
import org.hiero.mirror.common.domain.token.FractionalFee;
import org.hiero.mirror.common.domain.token.NftHistory;
import org.hiero.mirror.common.domain.token.RoyaltyFee;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenHistory;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.viewmodel.BlockType;

public abstract class AbstractContractCallServiceHistoricalTest extends AbstractContractCallServiceTest {

    protected Range<Long> setUpHistoricalContext(final long blockNumber) {
        final var recordFile = recordFilePersist(blockNumber);
        return setupHistoricalStateInService(blockNumber, recordFile);
    }

    protected RecordFile recordFilePersist(final long blockNumber) {
        return domainBuilder.recordFile().customize(f -> f.index(blockNumber)).persist();
    }

    protected Range<Long> setupHistoricalStateInService(final long blockNumber, final RecordFile recordFile) {
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(blockNumber)));
        final var historicalRange = Range.closedOpen(recordFile.getConsensusStart(), recordFile.getConsensusEnd());
        testWeb3jService.setHistoricalRange(historicalRange);
        return historicalRange;
    }

    protected void setupHistoricalStateInService(final long blockNumber, final Range<Long> timestampRange) {
        testWeb3jService.setBlockType(BlockType.of(String.valueOf(blockNumber)));
        testWeb3jService.setHistoricalRange(timestampRange);
    }

    protected void tokenAccountFrozenRelationshipPersistHistorical(
            final long tokenId, final long accountEntityId, final Range<Long> historicalRange) {
        domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.tokenId(tokenId)
                        .accountId(accountEntityId)
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .associated(true)
                        .timestampRange(historicalRange))
                .persist();
    }

    protected Pair<Entity, Entity> accountTokenAndFrozenRelationshipPersistHistorical(
            final Range<Long> historicalRange) {
        final var account = accountEntityPersistWithEvmAddressHistorical(historicalRange);
        final var tokenEntity = tokenEntityPersistHistorical(historicalRange);
        fungibleTokenPersistHistoricalCustomizable(historicalRange, t -> t.tokenId(tokenEntity.getId()));
        tokenAccount(ta -> ta.tokenId(tokenEntity.getId())
                .accountId(account.getId())
                .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                .timestampRange(historicalRange));
        return Pair.of(account, tokenEntity);
    }

    /**
     * Method that persists Entity object of type ACCOUNT with DEFAULT_ACCOUNT_BALANCE,
     * createdTimestamp, timestampRange and additional customization provided in customizer object
     *
     * @param timestampRange the timestamp range with which to persist the tokenHistory object
     * @param customizer  the consumer used to customize the Entity
     * @return Entity object that is persisted in the database
     */
    protected Entity accountEntityPersistHistoricalCustomizable(
            final Range<Long> timestampRange, Consumer<Entity.EntityBuilder<?, ?>> customizer) {

        return domainBuilder
                .entity()
                .customize(e -> {
                    e.type(EntityType.ACCOUNT)
                            .balance(getDefaultAccountBalance())
                            .createdTimestamp(timestampRange.lowerEndpoint())
                            .timestampRange(timestampRange);
                    customizer.accept(e);
                })
                .persist();
    }

    /**
     * Method that persists an Entity object of type ACCOUNT and no specific customization
     *
     * @param timestampRange the timestamp range with which to persist the Entity object
     * @return Entity object that is persisted in the database
     */
    protected Entity accountEntityPersistWithEvmAddressHistorical(final Range<Long> timestampRange) {
        return accountEntityPersistHistoricalCustomizable(timestampRange, e -> {});
    }

    /**
     * Method that persists an Entity object of type ACCOUNT with evmAddress and alias set to null
     *
     * @param timestampRange the timestamp range with which to persist the Entity object
     * @return Entity object that is persisted in the database
     */
    protected Entity accountEntityPersistHistorical(final Range<Long> timestampRange) {
        return accountEntityPersistHistoricalCustomizable(
                timestampRange, e -> e.evmAddress(null).alias(null));
    }

    protected void accountBalancePersistHistorical(
            final EntityId entityId, final long balance, final Range<Long> timestampRange) {
        // There needs to be an entry for account 0.0.2 in order for the account balance query to return the correct
        // result
        domainBuilder
                .accountBalance()
                .customize(
                        ab -> ab.id(new AccountBalance.Id(timestampRange.lowerEndpoint(), treasuryEntity.toEntityId()))
                                .balance(balance))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(timestampRange.lowerEndpoint(), entityId))
                        .balance(balance))
                .persist();
    }

    protected void tokenBalancePersistHistorical(
            final EntityId accountId, final EntityId tokenId, final long balance, final Range<Long> timestampRange) {
        // There needs to be an entry for account 0.0.2 in order for the token balance query to return the correct
        // result
        domainBuilder
                .accountBalance()
                .customize(
                        ab -> ab.id(new AccountBalance.Id(timestampRange.lowerEndpoint(), treasuryEntity.toEntityId()))
                                .balance(balance))
                .persist();
        domainBuilder
                .tokenBalance()
                .customize(ab -> ab.id(new TokenBalance.Id(timestampRange.lowerEndpoint(), accountId, tokenId))
                        .balance(balance))
                .persist();
    }

    /**
     * Method used to persist an Entity object of type TOKEN with timestampRange
     *
     * @param timestampRange the timestamp range with which to persist the Entity object
     * @return Entity object that is persisted in the database
     */
    protected Entity tokenEntityPersistHistorical(final Range<Long> timestampRange) {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).timestampRange(timestampRange))
                .persist();
    }

    protected Entity tokenEntityPersistHistoricalCustomizable(
            final Range<Long> timestampRange, final Consumer<Entity.EntityBuilder<?, ?>> customizer) {
        return domainBuilder
                .entity()
                .customize(e -> {
                    e.type(EntityType.TOKEN).timestampRange(timestampRange);
                    customizer.accept(e);
                })
                .persist();
    }
    /**
     * Method used to persist fungible TokenHistory object with no customization
     *
     * @param timestampRange  the timestamp range with which to persist the tokenHistory object
     * @return TokenHistory object that is persisted in the database
     */
    protected TokenHistory fungibleTokenPersistHistorical(final Range<Long> timestampRange) {
        return fungibleTokenPersistHistoricalCustomizable(timestampRange, t -> {});
    }

    /**
     * Method used to persist fungible TokenHistory object with tokenEntity id, createdTimestamp,
     * timestampRange and customization
     *
     * @param timestampRange the timestamp range with which to persist the tokenHistory object
     * @param customizer the consumer used to customize the TokenHistory
     * @return TokenHistory object that is persisted in the database
     */
    protected TokenHistory fungibleTokenPersistHistoricalCustomizable(
            final Range<Long> timestampRange, final Consumer<TokenHistory.TokenHistoryBuilder<?, ?>> customizer) {
        final var tokenEntity = tokenEntityPersistHistorical(timestampRange);

        return domainBuilder
                .tokenHistory()
                .customize(t -> {
                    t.tokenId(tokenEntity.getId())
                            .type(TokenTypeEnum.FUNGIBLE_COMMON)
                            .timestampRange(timestampRange)
                            .createdTimestamp(timestampRange.lowerEndpoint());
                    customizer.accept(t);
                })
                .persist();
    }

    /**
     * Method used to persist non-fungible TokenHistory object with token id, kycStatus GRANTED,
     * timestampRange and customization
     *
     * @param timestampRange the timestamp range with which to persist the tokenHistory object
     * @param customizer the consumer used to customize the TokenHistory
     * @return TokenHistory object that is persisted in the database
     */
    protected TokenHistory nonFungibleTokenPersistHistoricalCustomizable(
            final Range<Long> timestampRange, final Consumer<TokenHistory.TokenHistoryBuilder<?, ?>> customizer) {
        final var tokenEntity = tokenEntityPersistHistorical(timestampRange);

        return domainBuilder
                .tokenHistory()
                .customize(t -> {
                    t.tokenId(tokenEntity.getId())
                            .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                            .kycStatus(TokenKycStatusEnum.GRANTED)
                            .timestampRange(timestampRange);
                    customizer.accept(t);
                })
                .persist();
    }

    /**
     * Method that persist NftHistory object with DEFAULT_SERIAL_NUMBER,
     * timestampRange and customization provided in the customizer
     *
     * @param timestampRange the timestamp range with which to persist the NftHistory object
     * @param customizer the consumer used to customize the NftHistory
     * @return NftHistory  that is persisted in the database
     */
    protected NftHistory nftPersistHistoricalCustomizable(
            final Range<Long> timestampRange, final Consumer<NftHistory.NftHistoryBuilder<?, ?>> customizer) {
        return domainBuilder
                .nftHistory()
                .customize(n -> {
                    n.serialNumber(DEFAULT_SERIAL_NUMBER.longValue()).timestampRange(timestampRange);
                    customizer.accept(n);
                })
                .persist();
    }

    /**
     * Method that persists non-fungible TokenHistory object with treasury account and NftHistory with
     * token id, spender, accountId, timestampRange as specific customization
     *
     * @param timestampRange the timestamp range with which to persist the TokenHistory object
     * @param treasury the treasury object that is set in the tokenHistory object
     * @param owner the owner object that is set in the nftHistory object
     * @param spender the spender object that is set in the nftHistory object
     * @return TokenHistory object that is persisted in the database
     */
    protected TokenHistory nftPersistHistorical(
            final Range<Long> timestampRange, final EntityId treasury, final EntityId owner, final EntityId spender) {
        final var token =
                nonFungibleTokenPersistHistoricalCustomizable(timestampRange, t -> t.treasuryAccountId(treasury));
        nftPersistHistoricalCustomizable(timestampRange, n -> n.tokenId(token.getTokenId())
                .spender(spender.getId())
                .accountId(owner)
                .timestampRange(timestampRange));
        return token;
    }

    /**
     * Method that persists non-fungible TokenHistory with no specific customization
     * and NftHistory with token id as specific customization
     *
     * @param timestampRange the timestamp range with which to persist the TokenHistory object
     * @return TokenHistory that is persisted in the database
     */
    protected TokenHistory nftPersistHistorical(final Range<Long> timestampRange) {
        final var token = nonFungibleTokenPersistHistoricalCustomizable(timestampRange, t -> {});
        nftPersistHistoricalCustomizable(timestampRange, n -> n.tokenId(token.getTokenId()));
        return token;
    }

    /**
     * Method that persists TokenAllowanceHistory object with token id,
     * owner id, spender id, DEFAULT_AMOUNT_GRANTED and timestampRange as specific customization
     *
     * @param tokenId the tokenId that is set in the tokenAllowanceHistory
     * @param owner the owner object that is set in tokenAllowanceHistory
     * @param spender the spender object that is set in tokenAllowanceHistory
     * @param timestampRange the timestamp range with which to persist the tokenAllowanceHistory object
     */
    protected void tokenAllowancePersistHistorical(
            final long tokenId, final Entity owner, final Entity spender, final Range<Long> timestampRange) {
        domainBuilder
                .tokenAllowanceHistory()
                .customize(a -> a.tokenId(tokenId)
                        .owner(owner.getId())
                        .spender(spender.getId())
                        .amount(DEFAULT_AMOUNT_GRANTED)
                        .amountGranted(DEFAULT_AMOUNT_GRANTED)
                        .timestampRange(timestampRange))
                .persist();
    }

    /**
     * Method that persists NftAllowanceHistory with token id, owner id, spender id,
     * approvedForAll set to true and timestampRange as specific customization
     *
     * @param tokenId the tokenId that is set in the nftAllowanceHistory
     * @param owner the owner object that is set in nftAllowanceHistory
     * @param spender the spender object that is set in nftAllowanceHistory
     * @param timestampRange the timestamp range with which to persist the nftAllowanceHistory object
     */
    protected void nftAllowancePersistHistorical(
            final long tokenId, final Entity owner, final Entity spender, final Range<Long> timestampRange) {
        domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.tokenId(tokenId)
                        .owner(owner.getId())
                        .spender(spender.getId())
                        .approvedForAll(true)
                        .timestampRange(timestampRange))
                .persist();
    }

    protected void cryptoAllowancePersistHistorical(
            final Entity owner, final EntityId spender, final long amount, final Range<Long> timestampRange) {
        domainBuilder
                .cryptoAllowanceHistory()
                .customize(ca -> ca.owner(owner.toEntityId().getId())
                        .spender(spender.getId())
                        .payerAccountId(owner.toEntityId())
                        .amount(amount)
                        .amountGranted(amount)
                        .timestampRange(timestampRange))
                .persist();
    }

    protected AbstractCustomFee customFeesWithFeeCollectorPersistHistorical(
            final EntityId feeCollector,
            final EntityId tokenEntity,
            final TokenTypeEnum tokenType,
            final Range<Long> timestampRange) {
        final var fixedFee = FixedFee.builder()
                .allCollectorsAreExempt(true)
                .amount(domainBuilder.number())
                .collectorAccountId(feeCollector)
                .denominatingTokenId(tokenEntity)
                .build();

        final var fractionalFee = TokenTypeEnum.FUNGIBLE_COMMON.equals(tokenType)
                ? FractionalFee.builder()
                        .allCollectorsAreExempt(true)
                        .collectorAccountId(feeCollector)
                        .denominator(domainBuilder.number())
                        .maximumAmount(domainBuilder.number())
                        .minimumAmount(DEFAULT_FEE_MIN_VALUE.longValue())
                        .numerator(domainBuilder.number())
                        .netOfTransfers(true)
                        .build()
                : null;

        final var fallbackFee = FallbackFee.builder()
                .amount(domainBuilder.number())
                .denominatingTokenId(tokenEntity)
                .build();

        final var royaltyFee = TokenTypeEnum.NON_FUNGIBLE_UNIQUE.equals(tokenType)
                ? RoyaltyFee.builder()
                        .allCollectorsAreExempt(true)
                        .collectorAccountId(feeCollector)
                        .denominator(domainBuilder.number())
                        .fallbackFee(fallbackFee)
                        .numerator(domainBuilder.number())
                        .build()
                : null;

        if (TokenTypeEnum.FUNGIBLE_COMMON.equals(tokenType)) {
            return domainBuilder
                    .customFeeHistory()
                    .customize(f -> f.entityId(tokenEntity.getId())
                            .fixedFees(List.of(fixedFee))
                            .fractionalFees(List.of(fractionalFee))
                            .royaltyFees(new ArrayList<>())
                            .timestampRange(timestampRange))
                    .persist();
        } else {
            return domainBuilder
                    .customFeeHistory()
                    .customize(f -> f.entityId(tokenEntity.getId())
                            .fixedFees(List.of(fixedFee))
                            .royaltyFees(List.of(royaltyFee))
                            .fractionalFees(new ArrayList<>())
                            .timestampRange(timestampRange))
                    .persist();
        }
    }

    /**
     * Returns the default account balance depending on override setting.
     *
     * @return the default account balance
     */
    private long getDefaultAccountBalance() {
        return mirrorNodeEvmProperties.isOverridePayerBalanceValidation()
                ? DEFAULT_SMALL_ACCOUNT_BALANCE
                : DEFAULT_ACCOUNT_BALANCE;
    }
}
