// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.ACCOUNT;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.assertj.core.groups.Tuple;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.entity.CryptoAllowance;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.hiero.mirror.common.domain.entity.TokenAllowance;
import org.hiero.mirror.common.domain.node.Node;
import org.hiero.mirror.common.domain.schedule.Schedule;
import org.hiero.mirror.common.domain.token.AbstractTokenAccount;
import org.hiero.mirror.common.domain.token.DissociateTokenTransfer;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.common.domain.token.NftTransfer;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenAccountHistory;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.token.TokenPauseStatusEnum;
import org.hiero.mirror.common.domain.token.TokenSupplyTypeEnum;
import org.hiero.mirror.common.domain.token.TokenTransfer;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.CryptoAllowanceRepository;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.hiero.mirror.importer.repository.NftAllowanceRepository;
import org.hiero.mirror.importer.repository.NftRepository;
import org.hiero.mirror.importer.repository.NodeRepository;
import org.hiero.mirror.importer.repository.ScheduleRepository;
import org.hiero.mirror.importer.repository.TokenAccountHistoryRepository;
import org.hiero.mirror.importer.repository.TokenAccountRepository;
import org.hiero.mirror.importer.repository.TokenAllowanceRepository;
import org.hiero.mirror.importer.repository.TokenRepository;
import org.hiero.mirror.importer.repository.TokenTransferRepository;
import org.hiero.mirror.importer.repository.TopicMessageLookupRepository;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

@RequiredArgsConstructor
class BatchUpserterTest extends ImporterIntegrationTest {

    private static final Key KEY = Key.newBuilder()
            .setEd25519(ByteString.copyFromUtf8("0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c"))
            .build();

    private final BatchPersister batchPersister;
    private final CryptoAllowanceRepository cryptoAllowanceRepository;
    private final EntityRepository entityRepository;
    private final NftRepository nftRepository;
    private final NftAllowanceRepository nftAllowanceRepository;
    private final NodeRepository nodeRepository;
    private final ScheduleRepository scheduleRepository;
    private final TokenRepository tokenRepository;
    private final TokenAccountHistoryRepository tokenAccountHistoryRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenAllowanceRepository tokenAllowanceRepository;
    private final TokenTransferRepository tokenTransferRepository;
    private final TopicMessageLookupRepository topicMessageLookupRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionOperations transactionOperations;

    @Test
    void cryptoAllowance() {
        CryptoAllowance cryptoAllowance1 = domainBuilder.cryptoAllowance().get();
        CryptoAllowance cryptoAllowance2 = domainBuilder.cryptoAllowance().get();
        CryptoAllowance cryptoAllowance3 = domainBuilder.cryptoAllowance().get();
        var cryptoAllowances = List.of(cryptoAllowance1, cryptoAllowance2, cryptoAllowance3);
        persist(batchPersister, cryptoAllowances);
        assertThat(cryptoAllowanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(cryptoAllowances);
    }

    @Test
    void entityInsertOnly() {
        var entities = new ArrayList<Entity>();
        long consensusTimestamp = 1;
        entities.add(getEntity(1, consensusTimestamp, consensusTimestamp, "memo-1"));
        entities.add(getEntity(2, consensusTimestamp, consensusTimestamp, null));
        entities.add(getEntity(3, consensusTimestamp, consensusTimestamp, "memo-3"));
        entities.add(getEntity(4, consensusTimestamp, consensusTimestamp, ""));

        persist(batchPersister, entities);
        entities.get(1).setMemo("");
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrderElementsOf(entities);
        assertThat(findHistory(Entity.class)).isEmpty();
    }

    @Test
    void entityInsertAndUpdate() {
        var entities = new ArrayList<Entity>();
        long consensusTimestamp = 1;
        entities.add(getEntity(1, consensusTimestamp, consensusTimestamp, "memo-1"));
        entities.add(getEntity(2, consensusTimestamp, consensusTimestamp, "memo-2"));
        entities.add(getEntity(3, consensusTimestamp, consensusTimestamp, "memo-3"));
        entities.add(getEntity(4, consensusTimestamp, consensusTimestamp, "memo-4"));

        persist(batchPersister, entities);

        assertThat(entityRepository.findAll()).containsExactlyInAnyOrderElementsOf(entities);

        // update
        var updatedEntities = new ArrayList<Entity>();
        long updateTimestamp = 5;

        // updated
        updatedEntities.add(getEntity(2, null, updateTimestamp, null));
        updatedEntities.add(getEntity(3, null, updateTimestamp, ""));
        updatedEntities.add(getEntity(4, null, updateTimestamp, "updated-memo-4"));

        // new inserts
        updatedEntities.add(getEntity(5, null, updateTimestamp, "memo-5"));
        updatedEntities.add(getEntity(6, null, updateTimestamp, "memo-6"));

        persist(batchPersister, updatedEntities); // copy inserts and updates

        assertThat(entityRepository.findAll())
                .hasSize(6)
                .extracting(Entity::getMemo)
                .containsExactlyInAnyOrder("memo-1", "memo-2", "", "updated-memo-4", "memo-5", "memo-6");
        assertThat(findHistory(Entity.class))
                .hasSize(3)
                .extracting(Entity::getId)
                .containsExactlyInAnyOrder(2L, 3L, 4L);
    }

    @Test
    void entityInsertAndUpdateBatched() {
        var entities = new ArrayList<Entity>();
        long consensusTimestamp = 1;
        entities.add(getEntity(1, consensusTimestamp, consensusTimestamp, "memo-1"));
        entities.add(getEntity(2, consensusTimestamp, consensusTimestamp, "memo-2"));
        entities.add(getEntity(3, consensusTimestamp, consensusTimestamp, "memo-3"));
        entities.add(getEntity(4, consensusTimestamp, consensusTimestamp, "memo-4"));

        // update
        long updateTimestamp = 5;

        // updated
        var updateEntities = new ArrayList<Entity>();
        updateEntities.add(getEntity(3, null, updateTimestamp, ""));
        updateEntities.add(getEntity(4, null, updateTimestamp, "updated-memo-4"));

        // new inserts
        updateEntities.add(getEntity(5, null, updateTimestamp, "memo-5"));
        updateEntities.add(getEntity(6, null, updateTimestamp, "memo-6"));

        persist(batchPersister, entities, updateEntities); // copy inserts and updates

        assertThat(entityRepository.findAll())
                .isNotEmpty()
                .hasSize(6)
                .extracting(Entity::getMemo)
                .containsExactlyInAnyOrder("memo-1", "memo-2", "", "updated-memo-4", "memo-5", "memo-6");
        assertThat(findHistory(Entity.class))
                .hasSize(2)
                .extracting(Entity::getId)
                .containsExactlyInAnyOrder(3L, 4L);
    }

    @Test
    void tokenInsertOnly() {
        var tokens = new ArrayList<Token>();
        tokens.add(getToken("0.0.2000", "0.0.1001", 1L));
        tokens.add(getToken("0.0.3000", "0.0.1001", 2L));
        tokens.add(getToken("0.0.4000", "0.0.1001", 3L));
        tokens.add(getToken("0.0.5000", "0.0.1001", 4L));

        persist(batchPersister, tokens);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);
    }

    @Test
    void tokenInsertAndUpdate() {
        // insert
        var tokens = new ArrayList<Token>();
        tokens.add(getToken("0.0.2000", "0.0.1001", 1L));
        tokens.add(getToken("0.0.3000", "0.0.1002", 2L));
        tokens.add(getToken("0.0.4000", "0.0.1003", 3L));
        tokens.add(getToken("0.0.5000", "0.0.1004", 4L));

        persist(batchPersister, tokens);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);

        // updates
        tokens.clear();
        tokens.add(getToken("0.0.4000", "0.0.2001", null));
        tokens.add(getToken("0.0.5000", "0.0.2002", null));
        tokens.add(getToken("0.0.6000", "0.0.2005", 5L));
        tokens.add(getToken("0.0.7000", "0.0.2006", 6L));

        persist(batchPersister, tokens);

        assertThat(tokenRepository.findAll())
                .isNotEmpty()
                .hasSize(6)
                .extracting(Token::getTreasuryAccountId)
                .extracting(EntityId::toString)
                .containsExactlyInAnyOrder("0.0.1001", "0.0.1002", "0.0.2001", "0.0.2002", "0.0.2005", "0.0.2006");
    }

    @Test
    void tokenUpdateNegativeTotalSupply() {
        // given
        Token token = getToken("0.0.2000", "0.0.1001", 3L);
        tokenRepository.save(token);

        // when
        Token update = new Token();
        update.setTimestampLower(8L);
        update.setTokenId(token.getTokenId());
        update.setTotalSupply(-50L);
        persist(batchPersister, List.of(update));

        // then
        token.setTimestampLower(8L);
        token.setTotalSupply(token.getTotalSupply() - 50L);
        assertThat(tokenRepository.findAll()).containsOnly(token);
    }

    @Test
    void tokenAccountInsertOnly() {
        // inserts token first
        var tokens = new ArrayList<Token>();
        tokens.add(getToken("0.0.2000", "0.0.1001", 1L));
        tokens.add(getToken("0.0.2001", "0.0.1001", 2L));
        tokens.add(getToken("0.0.3000", "0.0.1001", 3L));

        persist(batchPersister, tokens);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);

        var tokenAccounts = new ArrayList<TokenAccount>();
        tokenAccounts.add(getTokenAccount("0.0.2000", "0.0.1001", 1L, true, Range.atLeast(1L)));
        tokenAccounts.add(getTokenAccount("0.0.2001", "0.0.1001", 2L, true, Range.atLeast(2L)));
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.4001", 3L, true, Range.atLeast(3L)));
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.4002", 4L, true, Range.atLeast(4L)));

        persist(batchPersister, tokenAccounts);

        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);
        assertThat(tokenAccountRepository.findAll())
                .isNotEmpty()
                .extracting(TokenAccount::getCreatedTimestamp, History::getTimestampLower)
                .containsExactlyInAnyOrder(
                        Tuple.tuple(1L, 1L), Tuple.tuple(2L, 2L), Tuple.tuple(3L, 3L), Tuple.tuple(4L, 4L));
    }

    @Test
    void tokenAccountInsertFreezeStatus() {
        // inserts token first
        var tokens = new ArrayList<Token>();
        tokens.add(getToken("0.0.2000", "0.0.1001", 1L, false, null, null, null));
        tokens.add(getToken("0.0.3000", "0.0.1001", 2L, true, KEY, null, null));
        tokens.add(getToken("0.0.4000", "0.0.1001", 3L, false, KEY, null, null));

        persist(batchPersister, tokens);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);

        // associate
        var tokenAccounts = new ArrayList<TokenAccount>();
        tokenAccounts.add(getTokenAccount("0.0.2000", "0.0.2001", 5L, true, Range.atLeast(5L)));
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.3001", 6L, true, Range.atLeast(6L)));
        tokenAccounts.add(getTokenAccount("0.0.4000", "0.0.4001", 7L, true, Range.atLeast(7L)));

        persist(batchPersister, tokenAccounts);

        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);
        assertThat(tokenAccountRepository.findAll())
                .extracting(History::getTimestampLower, TokenAccount::getFreezeStatus)
                .containsExactlyInAnyOrder(Tuple.tuple(5L, null), Tuple.tuple(6L, null), Tuple.tuple(7L, null));

        // reverse freeze status
        tokenAccounts.clear();
        tokenAccounts.add(getTokenAccount(
                "0.0.3000", "0.0.3001", null, null, TokenFreezeStatusEnum.UNFROZEN, null, Range.atLeast(10L)));
        tokenAccounts.add(getTokenAccount(
                "0.0.4000", "0.0.4001", null, null, TokenFreezeStatusEnum.FROZEN, null, Range.atLeast(11L)));

        persist(batchPersister, tokenAccounts);

        assertThat(tokenAccountRepository.findAll())
                .extracting(
                        TokenAccount::getCreatedTimestamp,
                        TokenAccount::getTimestampLower,
                        TokenAccount::getFreezeStatus)
                .containsExactlyInAnyOrder(
                        Tuple.tuple(5L, 5L, null),
                        Tuple.tuple(6L, 10L, TokenFreezeStatusEnum.UNFROZEN),
                        Tuple.tuple(7L, 11L, TokenFreezeStatusEnum.FROZEN));

        assertThat(tokenAccountHistoryRepository.findAll())
                .extracting(AbstractTokenAccount::getCreatedTimestamp, TokenAccountHistory::getFreezeStatus)
                .containsExactlyInAnyOrder(Tuple.tuple(6L, null), Tuple.tuple(7L, null));
    }

    @Test
    void tokenAccountInsertKycStatus() {
        // inserts token first
        var tokens = new ArrayList<Token>();
        tokens.add(getToken("0.0.2000", "0.0.1001", 1L, false, null, null, null));
        tokens.add(getToken("0.0.3000", "0.0.1001", 2L, false, null, KEY, null));

        persist(batchPersister, tokens);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);

        var tokenAccounts = new ArrayList<TokenAccount>();
        tokenAccounts.add(getTokenAccount("0.0.2000", "0.0.2001", 5L, true, Range.atLeast(5L)));
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.3001", 6L, true, Range.atLeast(6L)));

        persist(batchPersister, tokenAccounts);

        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);
        assertThat(tokenAccountRepository.findAll())
                .extracting(AbstractTokenAccount::getCreatedTimestamp, TokenAccount::getKycStatus)
                .containsExactlyInAnyOrder(Tuple.tuple(5L, null), Tuple.tuple(6L, null));

        // grant KYC
        tokenAccounts.clear();
        tokenAccounts.add(getTokenAccount(
                "0.0.3000", "0.0.3001", null, null, null, TokenKycStatusEnum.GRANTED, Range.atLeast(11L)));

        persist(batchPersister, tokenAccounts);

        assertThat(tokenAccountRepository.findAll())
                .extracting(AbstractTokenAccount::getCreatedTimestamp, TokenAccount::getKycStatus)
                .containsExactlyInAnyOrder(Tuple.tuple(5L, null), Tuple.tuple(6L, TokenKycStatusEnum.GRANTED));
        assertThat(findHistory(TokenAccount.class))
                .extracting(AbstractTokenAccount::getCreatedTimestamp, AbstractTokenAccount::getKycStatus)
                .containsExactlyInAnyOrder(Tuple.tuple(6L, null));
    }

    @Test
    void tokenAccountInsertWithMissingToken() {
        var tokenAccounts = new ArrayList<TokenAccount>();
        tokenAccounts.add(getTokenAccount("0.0.2000", "0.0.1001", 1L, false, Range.atLeast(1L)));
        tokenAccounts.add(getTokenAccount("0.0.2001", "0.0.1001", 2L, false, Range.atLeast(2L)));
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.4001", 3L, false, Range.atLeast(3L)));
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.4002", 4L, false, Range.atLeast(4L)));

        persist(batchPersister, tokenAccounts);
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokenAccounts);
    }

    @Test
    void tokenAccountInsertAndUpdate() {
        // inserts token first
        var tokens = new ArrayList<Token>();
        tokens.add(getToken("0.0.2000", "0.0.1001", 1L));
        tokens.add(getToken("0.0.2001", "0.0.1001", 2L));
        tokens.add(getToken("0.0.3000", "0.0.1001", 3L));
        tokens.add(getToken("0.0.4000", "0.0.1001", 4L));

        persist(batchPersister, tokens);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);

        var tokenAccounts = new ArrayList<TokenAccount>();
        tokenAccounts.add(getTokenAccount("0.0.2000", "0.0.1001", 5L, true, Range.atLeast(6L)));
        tokenAccounts.add(getTokenAccount("0.0.2001", "0.0.1001", 6L, true, Range.atLeast(7L)));
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.4001", 7L, true, Range.atLeast(8L)));
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.4002", 8L, true, Range.atLeast(9L)));

        persist(batchPersister, tokenAccounts);

        // update
        tokenAccounts.clear();
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.4001", null, null, Range.atLeast(10L)));
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.4002", null, null, Range.atLeast(11L)));
        tokenAccounts.add(getTokenAccount("0.0.4000", "0.0.7001", 10L, true, Range.atLeast(12L)));
        tokenAccounts.add(getTokenAccount("0.0.4000", "0.0.7002", 11L, true, Range.atLeast(13L)));

        persist(batchPersister, tokenAccounts);

        // assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokenAccounts);
        assertThat(tokenAccountRepository.findAll())
                .extracting(TokenAccount::getCreatedTimestamp, History::getTimestampLower)
                .containsExactlyInAnyOrder(
                        Tuple.tuple(5L, 6L),
                        Tuple.tuple(6L, 7L),
                        Tuple.tuple(7L, 10L),
                        Tuple.tuple(8L, 11L),
                        Tuple.tuple(10L, 12L),
                        Tuple.tuple(11L, 13L));

        assertThat(tokenAccountHistoryRepository.findAll())
                .extracting(TokenAccountHistory::getCreatedTimestamp)
                .containsExactlyInAnyOrder(7L, 8L);
    }

    @Test
    void topicMessageLookup() {
        // given
        var topicMessageLookup1 = domainBuilder.topicMessageLookup().persist();
        var topicMessageLookup2 = domainBuilder
                .topicMessageLookup()
                .customize(
                        t -> t.partition(topicMessageLookup1.getPartition()).topicId(topicMessageLookup1.getTopicId()))
                .get();
        var topicMessageLookup3 = domainBuilder.topicMessageLookup().get();

        // when
        persist(batchPersister, List.of(topicMessageLookup2, topicMessageLookup3));

        // then
        var merged = topicMessageLookup2.toBuilder()
                .sequenceNumberRange(
                        topicMessageLookup1.getSequenceNumberRange().span(topicMessageLookup2.getSequenceNumberRange()))
                .timestampRange(topicMessageLookup1.getTimestampRange().span(topicMessageLookup2.getTimestampRange()))
                .build();
        assertThat(topicMessageLookupRepository.findAll()).containsExactlyInAnyOrder(merged, topicMessageLookup3);
    }

    @Test
    void scheduleInsertOnly() {
        var schedules = new ArrayList<Schedule>();
        schedules.add(getSchedule(1L, "0.0.1001", null));
        schedules.add(getSchedule(2L, "0.0.1002", null));
        schedules.add(getSchedule(3L, "0.0.1003", null));
        schedules.add(getSchedule(4L, "0.0.1004", null));

        persist(batchPersister, schedules);
        assertThat(scheduleRepository.findAll()).containsExactlyInAnyOrderElementsOf(schedules);
    }

    @Test
    void scheduleInsertAndUpdate() {
        var schedules = new ArrayList<Schedule>();
        schedules.add(getSchedule(1L, "0.0.1001", null));
        schedules.add(getSchedule(2L, "0.0.1002", null));
        schedules.add(getSchedule(3L, "0.0.1003", null));
        schedules.add(getSchedule(4L, "0.0.1004", null));

        persist(batchPersister, schedules);
        assertThat(scheduleRepository.findAll()).containsExactlyInAnyOrderElementsOf(schedules);

        // update
        schedules.clear();

        schedules.add(getSchedule(null, "0.0.1003", 5L));
        schedules.add(getSchedule(null, "0.0.1004", 6L));
        schedules.add(getSchedule(7L, "0.0.1005", null));
        schedules.add(getSchedule(8L, "0.0.1006", null));

        persist(batchPersister, schedules);
        assertThat(scheduleRepository.findAll())
                .isNotEmpty()
                .hasSize(6)
                .extracting(Schedule::getExecutedTimestamp)
                .containsExactlyInAnyOrder(null, null, 5L, 6L, null, null);
    }

    @Test
    void nftUpdateWithoutExisting() {
        var nft = domainBuilder.nft().customize(n -> n.createdTimestamp(null)).get();
        persist(batchPersister, List.of(nft));
        assertThat(nftRepository.findAll()).containsExactly(nft);
        assertThat(findHistory(Nft.class)).isEmpty();
    }

    @Test
    void nftMint() {
        var nfts = List.of(domainBuilder.nft().get(), domainBuilder.nft().get());
        persist(batchPersister, nfts);
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrderElementsOf(nfts);
        assertThat(findHistory(Nft.class)).isEmpty();
    }

    @Test
    void nftInsertAndUpdate() {
        // nft mints
        var nft1 = domainBuilder.nft().get();
        var nft2 = domainBuilder.nft().get();
        var nft3 = domainBuilder.nft().get();
        var nft4 = domainBuilder.nft().get();

        // when
        persist(batchPersister, List.of(nft1, nft2, nft3, nft4));

        // then
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2, nft3, nft4);
        assertThat(findHistory(Nft.class)).isEmpty();

        // given transfer nft3 and nft4, mint nft5 and nft6
        var nft3Transfer = transferNft(nft3);
        var nft4Transfer = transferNft(nft4);
        var nft5 = domainBuilder.nft().get();
        var nft6 = domainBuilder.nft().get();

        // when
        persist(batchPersister, List.of(nft3Transfer, nft4Transfer, nft5, nft6));

        // then
        nft3Transfer.setCreatedTimestamp(nft3.getCreatedTimestamp());
        nft3Transfer.setDeleted(nft3.getDeleted());
        nft3Transfer.setMetadata(nft3.getMetadata());
        nft4Transfer.setCreatedTimestamp(nft4.getCreatedTimestamp());
        nft4Transfer.setDeleted(nft4.getDeleted());
        nft4Transfer.setMetadata(nft4.getMetadata());
        assertThat(nftRepository.findAll())
                .containsExactlyInAnyOrder(nft1, nft2, nft3Transfer, nft4Transfer, nft5, nft6);

        nft3.setTimestampUpper(nft3Transfer.getTimestampLower());
        nft4.setTimestampUpper(nft4Transfer.getTimestampLower());
        assertThat(findHistory(Nft.class)).containsExactlyInAnyOrder(nft3, nft4);
    }

    @Test
    void nftAllowance() {
        NftAllowance nftAllowance1 = domainBuilder.nftAllowance().get();
        NftAllowance nftAllowance2 = domainBuilder.nftAllowance().get();
        NftAllowance nftAllowance3 = domainBuilder.nftAllowance().get();
        var nftAllowance = List.of(nftAllowance1, nftAllowance2, nftAllowance3);
        persist(batchPersister, nftAllowance);
        assertThat(nftAllowanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(nftAllowance);
    }

    @Test
    void node() {
        var node1 = domainBuilder.node().get();
        var node2 = domainBuilder.node().get();
        var node3 = domainBuilder.node().get();
        var nodes = List.of(node1, node2, node3);
        persist(batchPersister, nodes);
        assertThat(nodeRepository.findAll()).containsExactlyInAnyOrderElementsOf(nodes);
    }

    @Test
    void nodeInsertAndUpdate() {

        var node1 = domainBuilder.node().get();
        var node2 = domainBuilder.node().get();
        var node3 = domainBuilder.node().get();
        var node4 = domainBuilder.node().get();

        // when
        persist(batchPersister, List.of(node1, node2, node3, node4));

        // then
        assertThat(nodeRepository.findAll()).containsExactlyInAnyOrder(node1, node2, node3, node4);
        assertThat(findHistory(Node.class)).isEmpty();

        // update node 3 and 4
        var node3Delete = deleteNode(node3);
        var node4Delete = deleteNode(node4);
        var node5 = domainBuilder.node().get();
        var node6 = domainBuilder.node().get();

        // when
        persist(batchPersister, List.of(node3Delete, node4Delete, node5, node6));

        // then
        assertThat(nodeRepository.findAll())
                .containsExactlyInAnyOrder(node1, node2, node3Delete, node4Delete, node5, node6);

        node3.setTimestampUpper(node3Delete.getTimestampLower());
        node4.setTimestampUpper(node4Delete.getTimestampLower());
        assertThat(findHistory(Node.class)).containsExactlyInAnyOrder(node3, node4);
    }

    @Test
    void tokenAllowance() {
        TokenAllowance tokenAllowance1 = domainBuilder.tokenAllowance().get();
        TokenAllowance tokenAllowance2 = domainBuilder.tokenAllowance().get();
        TokenAllowance tokenAllowance3 = domainBuilder.tokenAllowance().get();
        var tokenAllowance = List.of(tokenAllowance1, tokenAllowance2, tokenAllowance3);
        persist(batchPersister, tokenAllowance);
        assertThat(tokenAllowanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokenAllowance);
    }

    @Test
    void tokenDissociateTransfer() {
        // given
        var accountId1 = domainBuilder.entityId();
        var accountId2 = domainBuilder.entityId();
        var fungibleToken = domainBuilder.entityId();
        var nonFungibleToken1 = domainBuilder.entityId();
        var nonFungibleToken2 = domainBuilder.entityId();

        // Already deleted
        var nft1 = domainBuilder
                .nft()
                .customize(n -> n.accountId(null)
                        .deleted(true)
                        .timestampRange(Range.atLeast(domainBuilder.timestamp()))
                        .tokenId(nonFungibleToken1.getId()))
                .persist();
        // With delegating spender and spender, should preserve the two fields for history and clear them for current
        var nft2 = domainBuilder
                .nft()
                .customize(n -> n.accountId(accountId1)
                        .delegatingSpender(domainBuilder.id())
                        .spender(domainBuilder.id())
                        .tokenId(nonFungibleToken1.getId()))
                .persist();
        var nft3 = domainBuilder
                .nft()
                .customize(n -> n.accountId(accountId1).tokenId(nonFungibleToken1.getId()))
                .persist();
        // Owner is accountId2
        var nft4 = domainBuilder
                .nft()
                .customize(n -> n.accountId(accountId2).tokenId(nonFungibleToken1.getId()))
                .persist();
        // nonFungibleToken2
        var nft5 = domainBuilder
                .nft()
                .customize(n -> n.accountId(accountId1).tokenId(nonFungibleToken2.getId()))
                .persist();

        long dissociateTimestamp = domainBuilder.timestamp();
        var fungibleTokenTransfer = new DissociateTokenTransfer();
        fungibleTokenTransfer.setAmount(-10);
        fungibleTokenTransfer.setId(new TokenTransfer.Id(dissociateTimestamp, fungibleToken, accountId1));
        fungibleTokenTransfer.setIsApproval(false);
        fungibleTokenTransfer.setPayerAccountId(accountId1);
        var nonFungibleTokenTransfer1 = new DissociateTokenTransfer();
        nonFungibleTokenTransfer1.setAmount(-2);
        nonFungibleTokenTransfer1.setId(new TokenTransfer.Id(dissociateTimestamp, nonFungibleToken1, accountId1));
        nonFungibleTokenTransfer1.setIsApproval(false);
        nonFungibleTokenTransfer1.setPayerAccountId(accountId1);
        var nonFungibleTokenTransfer2 = new DissociateTokenTransfer();
        nonFungibleTokenTransfer2.setAmount(-1);
        nonFungibleTokenTransfer2.setId(new TokenTransfer.Id(dissociateTimestamp, nonFungibleToken2, accountId1));
        nonFungibleTokenTransfer2.setIsApproval(false);
        nonFungibleTokenTransfer2.setPayerAccountId(accountId1);
        var transaction = domainBuilder
                .transaction()
                .customize(t ->
                        t.consensusTimestamp(dissociateTimestamp).type(TransactionType.TOKENDISSOCIATE.getProtoId()))
                .persist();
        var tokenTransfers = List.of(fungibleTokenTransfer, nonFungibleTokenTransfer1, nonFungibleTokenTransfer2);

        // when
        persist(batchPersister, tokenTransfers);

        // then
        // history nft rows just have timetamp range closed
        var expectedNftHistory = Stream.of(nft2, nft3, nft5)
                .map(n -> n.toBuilder().build())
                .peek(n -> n.setTimestampUpper(dissociateTimestamp))
                .toList();
        Stream.of(nft2, nft3, nft5).forEach(n -> {
            n.setAccountId(null);
            n.setDelegatingSpender(null);
            n.setDeleted(true);
            n.setSpender(null);
            n.setTimestampLower(dissociateTimestamp);
        });
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2, nft3, nft4, nft5);
        assertThat(findHistory(Nft.class)).containsExactlyInAnyOrderElementsOf(expectedNftHistory);

        assertThat(tokenTransferRepository.findAll())
                .hasSize(1)
                .first()
                .usingRecursiveComparison()
                .isEqualTo(fungibleTokenTransfer);

        // Negative number in nft transfer is the number of NFTs burned
        transaction.setNftTransfer(List.of(
                NftTransfer.builder()
                        .isApproval(false)
                        .receiverAccountId(null)
                        .senderAccountId(accountId1)
                        .serialNumber(-2L)
                        .tokenId(nonFungibleToken1)
                        .build(),
                NftTransfer.builder()
                        .isApproval(false)
                        .receiverAccountId(null)
                        .senderAccountId(accountId1)
                        .serialNumber(-1L)
                        .tokenId(nonFungibleToken2)
                        .build()));
        assertThat(transactionRepository.findAll()).containsExactly(transaction);
    }

    private void persist(BatchPersister batchPersister, Collection<?>... items) {
        transactionOperations.executeWithoutResult(t -> {
            for (Collection<?> batch : items) {
                batchPersister.persist(batch);
            }
        });
    }

    private Entity getEntity(long id, Long createdTimestamp, long modifiedTimestamp, String memo) {
        Entity entity = new Entity();
        entity.setId(id);
        entity.setBalance(0L);
        entity.setCreatedTimestamp(createdTimestamp);
        entity.setDeclineReward(false);
        entity.setEthereumNonce(0L);
        entity.setTimestampLower(modifiedTimestamp);
        entity.setNum(id);
        entity.setRealm(0L);
        entity.setShard(0L);
        entity.setType(ACCOUNT);
        entity.setMemo(memo);
        entity.setStakedNodeId(-1L);
        entity.setStakePeriodStart(-1L);
        return entity;
    }

    private Token getToken(String tokenId, String treasuryAccountId, Long createdTimestamp) {
        return getToken(tokenId, treasuryAccountId, createdTimestamp, false, null, null, null);
    }

    @SneakyThrows
    private Token getToken(
            String tokenId,
            String treasuryAccountId,
            Long createdTimestamp,
            Boolean freezeDefault,
            Key freezeKey,
            Key kycKey,
            Key pauseKey) {
        var instr = "0011223344556677889900aabbccddeeff0011223344556677889900aabbccddeeff";
        var hexKey = Key.newBuilder()
                .setEd25519(ByteString.copyFrom(Hex.decodeHex(instr)))
                .build()
                .toByteArray();
        Token token = new Token();
        token.setCreatedTimestamp(createdTimestamp);
        token.setDecimals(1000);
        token.setFreezeDefault(freezeDefault);
        token.setFreezeKey(freezeKey != null ? freezeKey.toByteArray() : null);
        token.setInitialSupply(1_000_000_000L);
        token.setKycKey(kycKey != null ? kycKey.toByteArray() : null);
        token.setKycStatus(kycKey != null ? TokenKycStatusEnum.REVOKED : TokenKycStatusEnum.NOT_APPLICABLE);
        token.setPauseKey(pauseKey != null ? pauseKey.toByteArray() : null);
        token.setPauseStatus(pauseKey != null ? TokenPauseStatusEnum.UNPAUSED : TokenPauseStatusEnum.NOT_APPLICABLE);
        token.setMaxSupply(1_000_000_000L);
        token.setName("FOO COIN TOKEN" + tokenId);
        token.setSupplyKey(hexKey);
        token.setSupplyType(TokenSupplyTypeEnum.INFINITE);
        token.setSymbol("FOOTOK" + tokenId);
        token.setTimestampLower(3L);
        token.setTokenId(EntityId.of(tokenId).getId());
        token.setTotalSupply(token.getInitialSupply());
        token.setTreasuryAccountId(EntityId.of(treasuryAccountId));
        token.setType(TokenTypeEnum.FUNGIBLE_COMMON);
        token.setWipeKey(hexKey);

        var freezeStatus = TokenFreezeStatusEnum.NOT_APPLICABLE;
        if (freezeKey != null) {
            freezeStatus = freezeDefault ? TokenFreezeStatusEnum.FROZEN : TokenFreezeStatusEnum.UNFROZEN;
        }
        token.setFreezeStatus(freezeStatus);

        return token;
    }

    private TokenAccount getTokenAccount(
            String tokenId, String accountId, Long createdTimestamp, Boolean associated, Range<Long> timestampRange) {
        return getTokenAccount(tokenId, accountId, createdTimestamp, associated, null, null, timestampRange);
    }

    private TokenAccount getTokenAccount(
            String tokenId,
            String accountId,
            Long createdTimestamp,
            Boolean associated,
            TokenFreezeStatusEnum freezeStatus,
            TokenKycStatusEnum kycStatus,
            Range<Long> timestampRange) {
        return domainBuilder
                .tokenAccount()
                .customize(t -> t.accountId(EntityId.of(accountId).getId())
                        .automaticAssociation(false)
                        .associated(associated)
                        .createdTimestamp(createdTimestamp)
                        .freezeStatus(freezeStatus)
                        .kycStatus(kycStatus)
                        .timestampRange(timestampRange)
                        .tokenId(EntityId.of(tokenId).getId()))
                .get();
    }

    private Schedule getSchedule(Long createdTimestamp, String scheduleId, Long executedTimestamp) {
        Schedule schedule = new Schedule();
        schedule.setConsensusTimestamp(createdTimestamp);
        schedule.setCreatorAccountId(EntityId.of("0.0.123"));
        schedule.setExecutedTimestamp(executedTimestamp);
        schedule.setPayerAccountId(EntityId.of("0.0.456"));
        schedule.setScheduleId(EntityId.of(scheduleId));
        schedule.setTransactionBody("transaction body".getBytes());
        return schedule;
    }

    private Nft transferNft(Nft nft) {
        return nft.toBuilder()
                .accountId(domainBuilder.entityId())
                .createdTimestamp(null)
                .deleted(null)
                .metadata(null)
                .timestampRange(Range.atLeast(domainBuilder.timestamp()))
                .build();
    }

    private Node deleteNode(Node node) {
        return node.toBuilder()
                .deleted(true)
                .timestampRange(Range.atLeast(domainBuilder.timestamp()))
                .build();
    }
}
