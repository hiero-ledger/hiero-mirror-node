// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.token.NftTransfer.WILDCARD_SERIAL_NUMBER;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.AbstractTokenAccount;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.repository.TokenAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@RequiredArgsConstructor
class TokenTreasuryChangeTest extends AbstractEntityRecordItemListenerTest {

    private final TokenAccountRepository tokenAccountRepository;

    @ParameterizedTest
    @ValueSource(strings = {"sameTransaction", "sameBatch", "separate"})
    void treasuryChangeRegardlessOfKeys(String timing) {
        var oldTreasury = domainBuilder.entityId();
        var newTreasury = domainBuilder.entityId();
        var token = domainBuilder
                .token()
                .customize(t -> t.treasuryAccountId(oldTreasury).timestampRange(Range.atLeast(1L)))
                .persist();
        var tokenId = EntityId.of(token.getTokenId()).toTokenID();
        var relationship = domainBuilder
                .tokenAccount()
                .customize(t -> t.tokenId(token.getTokenId())
                        .accountId(newTreasury.getId())
                        .balance(0L)
                        .timestampRange(Range.atLeast(1L))
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycStatus(TokenKycStatusEnum.REVOKED))
                .persist();
        var removedKey =
                Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build();
        var removal = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> {
                    b.clear().setToken(tokenId).setFreezeKey(removedKey).setKycKey(removedKey);
                    if (timing.equals("sameTransaction")) {
                        b.setTreasury(newTreasury.toAccountID());
                    }
                })
                .build();
        var promotion = timing.equals("sameTransaction")
                ? removal
                : recordItemBuilder
                        .tokenUpdate()
                        .transactionBody(b -> b.clear().setToken(tokenId).setTreasury(newTreasury.toAccountID()))
                        .build();

        switch (timing) {
            case "sameTransaction" -> parseRecordItemAndCommit(promotion);
            case "sameBatch" -> parseRecordItemsAndCommit(List.of(removal, promotion));
            default -> {
                parseRecordItemAndCommit(removal);
                parseRecordItemAndCommit(promotion);
            }
        }

        // The statuses don't depend on the token's keys, readers show NOT_APPLICABLE for a token without the key
        assertThat(tokenAccountRepository.findById(relationship.getId()))
                .get()
                .returns(TokenFreezeStatusEnum.UNFROZEN, TokenAccount::getFreezeStatus)
                .returns(TokenKycStatusEnum.GRANTED, TokenAccount::getKycStatus)
                .returns(promotion.getConsensusTimestamp(), TokenAccount::getTimestampLower)
                .returns(relationship.getCreatedTimestamp(), TokenAccount::getCreatedTimestamp)
                .returns(0L, TokenAccount::getBalance);
    }

    @Test
    void repeatedNftTreasuryChangesInOneBatch() {
        var oldTreasury = domainBuilder.entityId();
        var newTreasury = domainBuilder.entityId();
        var token = domainBuilder
                .token()
                .customize(t -> t.type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(oldTreasury)
                        .totalSupply(1L))
                .persist();
        var oldRelationship = domainBuilder
                .tokenAccount()
                .customize(t -> t.tokenId(token.getTokenId())
                        .accountId(oldTreasury.getId())
                        .associated(true)
                        .balance(1L)
                        .timestampRange(Range.atLeast(1L))
                        .freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                        .kycStatus(TokenKycStatusEnum.GRANTED))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(t -> t.tokenId(token.getTokenId())
                        .accountId(newTreasury.getId())
                        .associated(true)
                        .balance(0L)
                        .timestampRange(Range.atLeast(1L))
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycStatus(TokenKycStatusEnum.REVOKED))
                .persist();
        domainBuilder
                .nft()
                .customize(n ->
                        n.tokenId(token.getTokenId()).accountId(oldTreasury).serialNumber(1))
                .persist();
        var tokenId = EntityId.of(token.getTokenId()).toTokenID();
        var records = new ArrayList<RecordItem>();
        for (boolean returning : List.of(false, true)) {
            var sender = returning ? newTreasury : oldTreasury;
            var receiver = returning ? oldTreasury : newTreasury;
            records.add(recordItemBuilder
                    .tokenUpdate()
                    .transactionBody(b -> b.clear().setToken(tokenId).setTreasury(receiver.toAccountID()))
                    .record(r -> r.addTokenTransferLists(TokenTransferList.newBuilder()
                            .setToken(tokenId)
                            .addNftTransfers(NftTransfer.newBuilder()
                                    .setSenderAccountID(sender.toAccountID())
                                    .setReceiverAccountID(receiver.toAccountID())
                                    .setSerialNumber(WILDCARD_SERIAL_NUMBER))))
                    .build());
        }

        parseRecordItemsAndCommit(records);

        assertThat(tokenAccountRepository.findById(oldRelationship.getId()))
                .get()
                .returns(records.getLast().getConsensusTimestamp(), TokenAccount::getTimestampLower)
                .returns(1L, TokenAccount::getBalance)
                .returns(TokenFreezeStatusEnum.UNFROZEN, TokenAccount::getFreezeStatus)
                .returns(TokenKycStatusEnum.GRANTED, TokenAccount::getKycStatus);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void treasuryChangeWithoutBalanceTracking(boolean automatic) {
        var persist = entityProperties.getPersist();
        var trackBalance = persist.isTrackBalance();
        try {
            persist.setTrackBalance(false);
            treasuryChange(TokenType.FUNGIBLE_COMMON, automatic, 0, "all");
        } finally {
            persist.setTrackBalance(trackBalance);
        }
    }

    @ParameterizedTest
    @CsvSource({"SUCCESS,false", "SUCCESS,true", "FAIL_INVALID,false", "FAIL_INVALID,true"})
    void automaticAssociationWithoutTokenHistory(ResponseCodeEnum status, boolean emptyToken) {
        var treasury = domainBuilder.entityId();
        var tokenId = emptyToken ? EntityId.EMPTY : domainBuilder.entityId();
        var otherAccount = domainBuilder.entityId();
        var otherToken = domainBuilder.entityId();
        var item = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> b.clear().setToken(tokenId.toTokenID()).setTreasury(treasury.toAccountID()))
                .receipt(r -> r.setStatus(status))
                .record(r -> r.addAutomaticTokenAssociations(TokenAssociation.newBuilder()
                                .setAccountId(treasury.toAccountID())
                                .setTokenId(tokenId.toTokenID()))
                        .addAutomaticTokenAssociations(TokenAssociation.newBuilder()
                                .setAccountId(otherAccount.toAccountID())
                                .setTokenId(otherToken.toTokenID())))
                .build();

        parseRecordItemAndCommit(item);

        assertThat(tokenAccountRepository.findAll())
                .hasSize(2)
                .allSatisfy(t -> assertThat(t)
                        .returns(true, TokenAccount::getAssociated)
                        .returns(true, TokenAccount::getAutomaticAssociation)
                        .returns(item.getConsensusTimestamp(), TokenAccount::getCreatedTimestamp)
                        .returns(item.getConsensusTimestamp(), TokenAccount::getBalanceTimestamp)
                        .returns(Range.atLeast(item.getConsensusTimestamp()), TokenAccount::getTimestampRange));
    }

    @ParameterizedTest
    @MethodSource("treasuryChanges")
    void treasuryChange(TokenType type, boolean automatic, long balance, String batch) {
        var oldTreasury = domainBuilder.entityId();
        var newTreasury = domainBuilder.entityId();
        var tokenId = domainBuilder.entityId();
        var token = tokenId.toTokenID();
        var records = new ArrayList<RecordItem>();
        var create = recordItemBuilder
                .tokenCreate()
                .transactionBody(b -> b.clearCustomFees()
                        .setTokenType(type)
                        .setFreezeDefault(true)
                        .setInitialSupply(0)
                        .setTreasury(oldTreasury.toAccountID()))
                .receipt(r -> r.setTokenID(token).setNewTotalSupply(0))
                .record(r -> r.clearAutomaticTokenAssociations()
                        .addAutomaticTokenAssociations(TokenAssociation.newBuilder()
                                .setTokenId(token)
                                .setAccountId(oldTreasury.toAccountID())))
                .build();
        records.add(create);

        long associationTimestamp = 0;
        if (!automatic) {
            var association = recordItemBuilder
                    .tokenAssociate()
                    .transactionBody(b -> b.setAccount(newTreasury.toAccountID())
                            .clearTokens()
                            .addTokens(token))
                    .build();
            associationTimestamp = association.getConsensusTimestamp();
            records.add(association);
            records.add(recordItemBuilder
                    .tokenFreeze()
                    .transactionBody(
                            b -> b.setAccount(newTreasury.toAccountID()).setToken(token))
                    .build());
            records.add(recordItemBuilder
                    .tokenRevokeKyc()
                    .transactionBody(
                            b -> b.setAccount(newTreasury.toAccountID()).setToken(token))
                    .build());
        }

        if (balance > 0) {
            records.add(recordItemBuilder
                    .tokenMint()
                    .transactionBody(b -> {
                        b.clear().setToken(token);
                        if (type == TokenType.FUNGIBLE_COMMON) {
                            b.setAmount(balance);
                        } else {
                            b.addMetadata(recordItemBuilder.bytes(16));
                        }
                    })
                    .receipt(r -> {
                        r.clearSerialNumbers().setNewTotalSupply(balance);
                        if (type == TokenType.NON_FUNGIBLE_UNIQUE) {
                            r.addSerialNumbers(1);
                        }
                    })
                    .record(r -> {
                        var transfers = TokenTransferList.newBuilder().setToken(token);
                        if (type == TokenType.FUNGIBLE_COMMON) {
                            transfers.addTransfers(AccountAmount.newBuilder()
                                    .setAccountID(oldTreasury.toAccountID())
                                    .setAmount(balance));
                        } else {
                            transfers.addNftTransfers(NftTransfer.newBuilder()
                                    .setReceiverAccountID(oldTreasury.toAccountID())
                                    .setSerialNumber(1));
                        }
                        r.clearTokenTransferLists().addTokenTransferLists(transfers);
                    })
                    .build());
        }

        var update = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> b.clear().setToken(token).setTreasury(newTreasury.toAccountID()))
                .record(r -> {
                    if (automatic) {
                        r.addAutomaticTokenAssociations(TokenAssociation.newBuilder()
                                .setAccountId(newTreasury.toAccountID())
                                .setTokenId(token));
                    }
                    if (balance > 0) {
                        var transfers = TokenTransferList.newBuilder().setToken(token);
                        if (type == TokenType.FUNGIBLE_COMMON) {
                            transfers
                                    .addTransfers(AccountAmount.newBuilder()
                                            .setAccountID(oldTreasury.toAccountID())
                                            .setAmount(-balance))
                                    .addTransfers(AccountAmount.newBuilder()
                                            .setAccountID(newTreasury.toAccountID())
                                            .setAmount(balance));
                        } else {
                            transfers.addNftTransfers(NftTransfer.newBuilder()
                                    .setSenderAccountID(oldTreasury.toAccountID())
                                    .setReceiverAccountID(newTreasury.toAccountID())
                                    .setSerialNumber(WILDCARD_SERIAL_NUMBER));
                        }
                        r.addTokenTransferLists(transfers);
                    }
                })
                .build();
        records.add(update);

        switch (batch) {
            case "all" -> parseRecordItemsAndCommit(records);
            case "partial" -> {
                records.subList(0, records.size() - 2).forEach(this::parseRecordItemAndCommit);
                parseRecordItemsAndCommit(records.subList(records.size() - 2, records.size()));
            }
            default -> records.forEach(this::parseRecordItemAndCommit);
        }

        var id = new AbstractTokenAccount.Id();
        id.setAccountId(newTreasury.getId());
        id.setTokenId(tokenId.getId());
        var relationship = tokenAccountRepository.findById(id).orElseThrow();
        var updateTimestamp = update.getConsensusTimestamp();
        assertThat(relationship)
                .returns(true, TokenAccount::getAssociated)
                .returns(automatic, TokenAccount::getAutomaticAssociation)
                .returns(automatic ? updateTimestamp : associationTimestamp, TokenAccount::getCreatedTimestamp)
                .returns(Range.atLeast(updateTimestamp), TokenAccount::getTimestampRange)
                .returns(balance, TokenAccount::getBalance)
                .returns(TokenFreezeStatusEnum.UNFROZEN, TokenAccount::getFreezeStatus)
                .returns(TokenKycStatusEnum.GRANTED, TokenAccount::getKycStatus);
        if (!automatic) {
            assertThat(findHistory(TokenAccount.class).stream()
                            .filter(t -> t.getId().equals(id)
                                    && t.getTimestampRange().upperEndpoint().equals(updateTimestamp)))
                    .singleElement()
                    .returns(TokenFreezeStatusEnum.FROZEN, TokenAccount::getFreezeStatus)
                    .returns(TokenKycStatusEnum.REVOKED, TokenAccount::getKycStatus);
        }

        // Move the treasury back before explicitly restricting the former treasury. Its promotion statuses must
        // survive in history, while the later restrictions take precedence in the current relationship.
        var returnTreasury = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> b.clear().setToken(token).setTreasury(oldTreasury.toAccountID()))
                .record(r -> {
                    if (balance > 0) {
                        var transfers = TokenTransferList.newBuilder().setToken(token);
                        if (type == TokenType.FUNGIBLE_COMMON) {
                            transfers
                                    .addTransfers(AccountAmount.newBuilder()
                                            .setAccountID(newTreasury.toAccountID())
                                            .setAmount(-balance))
                                    .addTransfers(AccountAmount.newBuilder()
                                            .setAccountID(oldTreasury.toAccountID())
                                            .setAmount(balance));
                        } else {
                            transfers.addNftTransfers(NftTransfer.newBuilder()
                                    .setSenderAccountID(newTreasury.toAccountID())
                                    .setReceiverAccountID(oldTreasury.toAccountID())
                                    .setSerialNumber(WILDCARD_SERIAL_NUMBER));
                        }
                        r.addTokenTransferLists(transfers);
                    }
                })
                .build();
        var freeze = recordItemBuilder
                .tokenFreeze()
                .transactionBody(b -> b.setToken(token).setAccount(newTreasury.toAccountID()))
                .build();
        var revoke = recordItemBuilder
                .tokenRevokeKyc()
                .transactionBody(b -> b.setToken(token).setAccount(newTreasury.toAccountID()))
                .build();
        parseRecordItemsAndCommit(List.of(returnTreasury, freeze, revoke));
        assertThat(tokenAccountRepository.findById(id))
                .get()
                .returns(TokenFreezeStatusEnum.FROZEN, TokenAccount::getFreezeStatus)
                .returns(TokenKycStatusEnum.REVOKED, TokenAccount::getKycStatus)
                .returns(0L, TokenAccount::getBalance);
        assertThat(findHistory(TokenAccount.class).stream()
                        .filter(t -> t.getId().equals(id) && t.getTimestampLower() == updateTimestamp))
                .singleElement()
                .returns(
                        Range.closedOpen(updateTimestamp, freeze.getConsensusTimestamp()),
                        TokenAccount::getTimestampRange)
                .returns(TokenFreezeStatusEnum.UNFROZEN, TokenAccount::getFreezeStatus)
                .returns(TokenKycStatusEnum.GRANTED, TokenAccount::getKycStatus);
    }

    private static Stream<Arguments> treasuryChanges() {
        return Stream.of(TokenType.FUNGIBLE_COMMON, TokenType.NON_FUNGIBLE_UNIQUE)
                .flatMap(type -> Stream.of(true, false)
                        .flatMap(automatic -> Stream.of(0L, 1L)
                                .flatMap(balance -> Stream.of("all", "partial", "separate")
                                        .map(batch -> Arguments.of(type, automatic, balance, batch)))));
    }
}
