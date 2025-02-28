// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.AbstractCustomFee;
import com.hedera.mirror.common.domain.token.FallbackFee;
import com.hedera.mirror.common.domain.token.FractionalFee;
import com.hedera.mirror.common.domain.token.RoyaltyFee;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.viewmodel.BlockType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.commons.lang3.tuple.Pair;

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
            final Entity tokenEntity, final Entity accountEntity, final Range<Long> historicalRange) {
        domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(accountEntity.getId())
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
        fungibleTokenPersistHistorical(tokenEntity, historicalRange);
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenEntity.getId())
                        .accountId(account.getId())
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .associated(true)
                        .timestampRange(historicalRange))
                .persist();
        return Pair.of(account, tokenEntity);
    }

    protected Entity accountEntityPersistHistoricalCustomizable(
            final Range<Long> timestampRange, Consumer<Entity.EntityBuilder<?, ?>> customizer) {

        return domainBuilder
                .entity()
                .customize(e -> {
                    e.type(EntityType.ACCOUNT)
                            .balance(DEFAULT_ACCOUNT_BALANCE)
                            .createdTimestamp(timestampRange.lowerEndpoint())
                            .timestampRange(timestampRange);
                    customizer.accept(e);
                })
                .persist();
    }

    protected Entity accountEntityPersistWithEvmAddressHistorical(final Range<Long> timestampRange) {
        return accountEntityPersistHistoricalCustomizable(timestampRange, e -> {});
    }

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

    protected Entity tokenEntityPersistHistorical(final Range<Long> timestampRange) {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.TOKEN).timestampRange(timestampRange))
                .persist();
    }

    protected void fungibleTokenPersistHistorical(Entity tokenEntity, final Range<Long> timestampRange) {
        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.FUNGIBLE_COMMON)
                        .timestampRange(timestampRange)
                        .createdTimestamp(timestampRange.lowerEndpoint()))
                .persist();
    }

    protected Entity fungibleTokenPersistHistorical(final Range<Long> timestampRange) {
        final var entity = tokenEntityPersistHistorical(timestampRange);
        fungibleTokenPersistHistorical(entity, timestampRange);
        return entity;
    }

    protected Entity nftPersistHistorical(final Range<Long> timestampRange) {
        final var tokenEntity = tokenEntityPersistHistorical(timestampRange);
        domainBuilder
                .tokenHistory()
                .customize(t -> t.tokenId(tokenEntity.getId())
                        .type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .timestampRange(timestampRange))
                .persist();
        domainBuilder
                .nftHistory()
                .customize(n -> n.tokenId(tokenEntity.getId())
                        .serialNumber(DEFAULT_SERIAL_NUMBER.longValue())
                        .timestampRange(timestampRange))
                .persist();

        return tokenEntity;
    }

    protected void tokenAllowancePersistHistorical(
            final Entity tokenEntity, final Entity owner, final Entity spender, final Range<Long> timestampRange) {
        domainBuilder
                .tokenAllowanceHistory()
                .customize(a -> a.tokenId(tokenEntity.getId())
                        .owner(owner.getId())
                        .spender(spender.getId())
                        .amount(50L)
                        .amountGranted(50L)
                        .timestampRange(timestampRange))
                .persist();
    }

    protected void nftAllowancePersistHistorical(
            final Entity tokenEntity, final Entity owner, final Entity spender, final Range<Long> timestampRange) {
        domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.tokenId(tokenEntity.getId())
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
            final Entity feeCollector,
            final Entity tokenEntity,
            final TokenTypeEnum tokenType,
            final Range<Long> timestampRange) {
        final var fixedFee = com.hedera.mirror.common.domain.token.FixedFee.builder()
                .allCollectorsAreExempt(true)
                .amount(domainBuilder.number())
                .collectorAccountId(feeCollector.toEntityId())
                .denominatingTokenId(tokenEntity.toEntityId())
                .build();

        final var fractionalFee = TokenTypeEnum.FUNGIBLE_COMMON.equals(tokenType)
                ? FractionalFee.builder()
                        .allCollectorsAreExempt(true)
                        .collectorAccountId(feeCollector.toEntityId())
                        .denominator(domainBuilder.number())
                        .maximumAmount(domainBuilder.number())
                        .minimumAmount(1L)
                        .numerator(domainBuilder.number())
                        .netOfTransfers(true)
                        .build()
                : null;

        final var fallbackFee = FallbackFee.builder()
                .amount(domainBuilder.number())
                .denominatingTokenId(tokenEntity.toEntityId())
                .build();

        final var royaltyFee = TokenTypeEnum.NON_FUNGIBLE_UNIQUE.equals(tokenType)
                ? RoyaltyFee.builder()
                        .allCollectorsAreExempt(true)
                        .collectorAccountId(feeCollector.toEntityId())
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
}
