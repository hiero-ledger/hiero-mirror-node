// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.ImporterProperties.HederaNetwork.MAINNET;
import static org.hiero.mirror.importer.ImporterProperties.HederaNetwork.TESTNET;
import static org.hiero.mirror.importer.migration.SyntheticCryptoTransferApprovalMigration.LOWER_BOUND_TIMESTAMP;
import static org.hiero.mirror.importer.migration.SyntheticCryptoTransferApprovalMigration.UPPER_BOUND_TIMESTAMP;

import com.google.common.collect.Range;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityHistory;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.NftTransfer;
import org.hiero.mirror.common.domain.token.TokenTransfer;
import org.hiero.mirror.common.domain.transaction.CryptoTransfer;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.repository.CryptoTransferRepository;
import org.hiero.mirror.importer.repository.TokenTransferRepository;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.tuple.Pair;

@RequiredArgsConstructor
@Tag("migration")
class SyntheticCryptoTransferApprovalMigrationTest extends ImporterIntegrationTest {

    private static final long START_TIMESTAMP = 1568415600193620000L;
    private static final long END_TIMESTAMP = 1568528100472477002L;
    private static final AtomicLong count = new AtomicLong(100000);
    private static final EntityId contractId = EntityId.of("0.0.2119901");
    private static final EntityId contractId2 = EntityId.of("0.0.2119902");
    private static final EntityId priorContractId = EntityId.of("0.0.2119900");
    private static final RecordFile RECORD_FILE = RecordFile.builder()
            .consensusStart(START_TIMESTAMP)
            .consensusEnd(END_TIMESTAMP)
            .hapiVersionMajor(0)
            .hapiVersionMinor(39)
            .hapiVersionPatch(1)
            .build();
    private final SyntheticCryptoTransferApprovalMigration migration;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final TransactionRepository transactionRepository;
    private final TokenTransferRepository tokenTransferRepository;
    private final ImporterProperties importerProperties;

    private Entity currentKeyUnaffectedEntity;
    private Entity currentKeyAffectedEntity;
    private Entity noKeyEntity;
    private EntityId payerAccountId;
    private Entity thresholdTwoKeyEntity;

    @BeforeEach
    void setup() {
        importerProperties.setNetwork(MAINNET);
        migration.setExecuted(false);
        migration.setComplete(false);

        // The migration fixes data caused by bug in previous consensus node release which was later fixed in consensus
        // node itself. In addition, it uses hardcoded entity ids. Non-zero realm / shard should not apply.
        commonProperties.setRealm(0);
        commonProperties.setShard(0);
    }

    @AfterEach
    void teardown() {
        importerProperties.setNetwork(TESTNET);
    }

    @Test
    void checksum() {
        assertThat(migration.getChecksum()).isOne();
    }

    @Test
    void empty() {
        migration.migrateAsync();
        assertThat(cryptoTransferRepository.findAll()).isEmpty();
        assertThat(transactionRepository.findAll()).isEmpty();
        assertThat(tokenTransferRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        entitySetup();
        var cryptoTransfersPair = getCryptoTransfersPair(
                contractId,
                contractId2,
                priorContractId,
                currentKeyUnaffectedEntity,
                currentKeyAffectedEntity,
                noKeyEntity,
                thresholdTwoKeyEntity);
        var nftTransfersTransactionPair = getNftTransfersTransactionPair(
                contractId,
                contractId2,
                priorContractId,
                currentKeyUnaffectedEntity,
                currentKeyAffectedEntity,
                noKeyEntity,
                thresholdTwoKeyEntity);
        var tokenTransfersPair = getTokenTransfersPair(
                contractId,
                contractId2,
                priorContractId,
                currentKeyUnaffectedEntity,
                currentKeyAffectedEntity,
                noKeyEntity,
                thresholdTwoKeyEntity);

        // when
        migration.migrateAsync();

        // then
        assertTransfers(cryptoTransfersPair, nftTransfersTransactionPair, tokenTransfersPair);
    }

    @Test
    void repeatableMigration() {
        // given
        migrate();
        var firstPassCryptoTransfers = cryptoTransferRepository.findAll();
        var firstPassNftTransfers = transactionRepository.findAll();
        var firstPassTokenTransfers = tokenTransferRepository.findAll();

        // when
        migration.migrateAsync();

        var secondPassCryptoTransfers = cryptoTransferRepository.findAll();
        var secondPassNftTransfers = transactionRepository.findAll();
        var secondPassTokenTransfers = tokenTransferRepository.findAll();

        // then
        assertThat(firstPassCryptoTransfers).containsExactlyInAnyOrderElementsOf(secondPassCryptoTransfers);
        assertThat(firstPassNftTransfers).containsExactlyInAnyOrderElementsOf(secondPassNftTransfers);
        assertThat(firstPassTokenTransfers).containsExactlyInAnyOrderElementsOf(secondPassTokenTransfers);
    }

    @Test
    void onEnd() {
        // given

        // Creating record files with version <0.38.0 and consensus start and end < RECORD_FILE
        domainBuilder
                .recordFile()
                .customize(r -> r.hapiVersionMajor(0)
                        .hapiVersionMinor(37)
                        .hapiVersionPatch(10)
                        .consensusStart(1568415600183620000L)
                        .consensusEnd(1568415600183620001L))
                .persist();
        domainBuilder
                .recordFile()
                .customize(r -> r.hapiVersionMajor(0)
                        .hapiVersionMinor(37)
                        .hapiVersionPatch(10)
                        .consensusStart(1568415600173620000L)
                        .consensusEnd(1568415600173620001L))
                .persist();

        entitySetup();
        var cryptoTransfersPair = getCryptoTransfersPair(
                contractId,
                contractId2,
                priorContractId,
                currentKeyUnaffectedEntity,
                currentKeyAffectedEntity,
                noKeyEntity,
                thresholdTwoKeyEntity);

        var nftTransfersTransactionPair = getNftTransfersTransactionPair(
                contractId,
                contractId2,
                priorContractId,
                currentKeyUnaffectedEntity,
                currentKeyAffectedEntity,
                noKeyEntity,
                thresholdTwoKeyEntity);

        var tokenTransfersPair = getTokenTransfersPair(
                contractId,
                contractId2,
                priorContractId,
                currentKeyUnaffectedEntity,
                currentKeyAffectedEntity,
                noKeyEntity,
                thresholdTwoKeyEntity);

        // when
        migration.onEnd(RECORD_FILE);
        while (!migration.isComplete()) {
            Uninterruptibles.sleepUninterruptibly(100L, TimeUnit.MILLISECONDS);
        }

        // then
        assertTransfers(cryptoTransfersPair, nftTransfersTransactionPair, tokenTransfersPair);
    }

    @Test
    void onEndHapiVersionNotMatched() {
        // given
        // Creating record files with version >0.38.0 which will not run the migration
        domainBuilder
                .recordFile()
                .customize(r -> r.hapiVersionMajor(0).hapiVersionMinor(39).hapiVersionPatch(0))
                .persist();

        entitySetup();
        var cryptoTransfersPair = getCryptoTransfersPair(
                contractId,
                contractId2,
                priorContractId,
                currentKeyUnaffectedEntity,
                currentKeyAffectedEntity,
                noKeyEntity,
                thresholdTwoKeyEntity);

        var nftTransfersTransactionPair = getNftTransfersTransactionPair(
                contractId,
                contractId2,
                priorContractId,
                currentKeyUnaffectedEntity,
                currentKeyAffectedEntity,
                noKeyEntity,
                thresholdTwoKeyEntity);

        var tokenTransfersPair = getTokenTransfersPair(
                contractId,
                contractId2,
                priorContractId,
                currentKeyUnaffectedEntity,
                currentKeyAffectedEntity,
                noKeyEntity,
                thresholdTwoKeyEntity);

        // when
        migration.onEnd(RECORD_FILE);

        // then
        // isApproval is not set to true
        assertThat(cryptoTransferRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(
                        Stream.of(cryptoTransfersPair.getLeft(), cryptoTransfersPair.getRight())
                                .flatMap(Collection::stream)
                                .toList());

        assertThat(tokenTransferRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(
                        Stream.of(tokenTransfersPair.getLeft(), tokenTransfersPair.getRight())
                                .flatMap(Collection::stream)
                                .toList());

        var repositoryNftTransfers = new ArrayList<NftTransfer>();
        transactionRepository.findAll().forEach(t -> repositoryNftTransfers.addAll(t.getNftTransfer()));
        assertThat(repositoryNftTransfers)
                .containsExactlyInAnyOrderElementsOf(
                        Stream.of(nftTransfersTransactionPair.getLeft(), nftTransfersTransactionPair.getRight())
                                .flatMap(Collection::stream)
                                .toList());
    }

    private void assertTransfers(
            Pair<List<CryptoTransfer>, List<CryptoTransfer>> cryptoTransfersPair,
            Pair<List<NftTransfer>, List<NftTransfer>> nftTransfersTransactionPair,
            Pair<List<TokenTransfer>, List<TokenTransfer>> tokenTransfersPair) {
        ArrayList<CryptoTransfer> expectedCryptoTransfers = new ArrayList<>(cryptoTransfersPair.getLeft());
        expectedCryptoTransfers.forEach(t -> t.setIsApproval(true));
        expectedCryptoTransfers.addAll(cryptoTransfersPair.getRight());
        assertThat(cryptoTransferRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedCryptoTransfers);

        var expectedTokenTransfers = new ArrayList<>(tokenTransfersPair.getLeft());
        expectedTokenTransfers.forEach(t -> t.setIsApproval(true));
        expectedTokenTransfers.addAll(tokenTransfersPair.getRight());
        assertThat(tokenTransferRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTokenTransfers);

        var expectedNftTransfers = new ArrayList<>(nftTransfersTransactionPair.getLeft());
        expectedNftTransfers.forEach(t -> t.setIsApproval(true));
        expectedNftTransfers.addAll(nftTransfersTransactionPair.getRight());

        var repositoryNftTransfers = new ArrayList<NftTransfer>();
        transactionRepository.findAll().forEach(t -> repositoryNftTransfers.addAll(t.getNftTransfer()));
        assertThat(repositoryNftTransfers).containsExactlyInAnyOrderElementsOf(expectedNftTransfers);
    }

    private void entitySetup() {
        currentKeyUnaffectedEntity = entityCurrentKey(contractId.getNum());
        currentKeyAffectedEntity = entityCurrentKey(contractId2.getNum());
        noKeyEntity = entityWithNoKey();
        payerAccountId = domainBuilder.entityId();
        thresholdTwoKeyEntity = domainBuilder
                .entity()
                .customize(e -> e.key(getThresholdTwoKey(contractId.getNum()))
                        .timestampRange(Range.atLeast(getTimestampWithinBoundary()))
                        .build())
                .persist();
    }

    private Pair<List<CryptoTransfer>, List<CryptoTransfer>> getCryptoTransfersPair(
            EntityId contractId,
            EntityId contractId2,
            EntityId priorContractId,
            Entity currentKeyUnaffectedEntity,
            Entity currentKeyAffectedEntity,
            Entity noKeyEntity,
            Entity thresholdTwoKeyEntity) {
        var pastKeyUnaffectedEntity = entityPastKey(contractId.getNum());
        var pastKeyAffectedEntity = entityPastKey(contractId2.getNum());

        return setupCryptoTransfers(
                contractId,
                priorContractId,
                currentKeyUnaffectedEntity,
                currentKeyAffectedEntity,
                noKeyEntity,
                pastKeyUnaffectedEntity,
                pastKeyAffectedEntity,
                thresholdTwoKeyEntity);
    }

    private Pair<List<TokenTransfer>, List<TokenTransfer>> getTokenTransfersPair(
            EntityId contractId,
            EntityId contractId2,
            EntityId priorContractId,
            Entity currentKeyUnaffectedEntity,
            Entity currentKeyAffectedEntity,
            Entity noKeyEntity,
            Entity thresholdTwoKeyEntity) {
        var tokenPastKeyUnaffectedEntity = entityPastKey(contractId.getNum());
        var tokenPastKeyAffectedEntity = entityPastKey(contractId2.getNum());
        return setupTokenTransfers(
                contractId,
                priorContractId,
                currentKeyUnaffectedEntity,
                currentKeyAffectedEntity,
                noKeyEntity,
                tokenPastKeyUnaffectedEntity,
                tokenPastKeyAffectedEntity,
                thresholdTwoKeyEntity);
    }

    private Pair<List<NftTransfer>, List<NftTransfer>> getNftTransfersTransactionPair(
            EntityId contractId,
            EntityId contractId2,
            EntityId priorContractId,
            Entity currentKeyUnaffectedEntity,
            Entity currentKeyAffectedEntity,
            Entity noKeyEntity,
            Entity thresholdTwoKeyEntity) {
        var nftPastKeyUnaffectedEntity = entityPastKey(contractId.getNum());
        var nftPastKeyAffectedEntity = entityPastKey(contractId2.getNum());
        return setupTransactionNfts(
                contractId,
                priorContractId,
                currentKeyUnaffectedEntity,
                currentKeyAffectedEntity,
                noKeyEntity,
                nftPastKeyUnaffectedEntity,
                nftPastKeyAffectedEntity,
                thresholdTwoKeyEntity);
    }

    private Pair<List<CryptoTransfer>, List<CryptoTransfer>> setupCryptoTransfers(
            EntityId contractId,
            EntityId priorGrandfatheredContractId,
            Entity currentKeyUnaffectedEntity,
            Entity currentKeyAffectedEntity,
            Entity noKeyEntity,
            Entity pastKeyUnaffectedEntity,
            Entity pastKeyAffectedEntity,
            Entity thresholdTwoKeyEntity) {
        var approvalTrueCryptoTransfers = new ArrayList<CryptoTransfer>();
        var unaffectedCryptoTransfers = new ArrayList<CryptoTransfer>();

        // crypto transfer with current threshold key matching the contract result sender id should not have isApproval
        // set to true
        var cryptoMatchingThreshold = persistCryptoTransfer(currentKeyUnaffectedEntity.getId(), null, false);
        // corresponding contract result for the synthetic crypto transfer
        persistContractResult(contractId, cryptoMatchingThreshold.getConsensusTimestamp());
        unaffectedCryptoTransfers.add(cryptoMatchingThreshold);

        // crypto transfer with past threshold key matching the contract result sender id should not have isApproval set
        // to true
        var pastCryptoMatchingThreshold = persistCryptoTransfer(
                pastKeyUnaffectedEntity.getId(), pastKeyUnaffectedEntity.getTimestampLower(), false);
        // corresponding contract result for the synthetic crypto transfer
        persistContractResult(contractId, pastCryptoMatchingThreshold.getConsensusTimestamp());
        unaffectedCryptoTransfers.add(pastCryptoMatchingThreshold);

        // crypto transfer with current threshold key not matching the contract result sender id should have isApproval
        // set to true
        var cryptoNotMatchingThreshold = persistCryptoTransfer(currentKeyAffectedEntity.getId(), null, false);
        persistContractResult(contractId, cryptoNotMatchingThreshold.getConsensusTimestamp());
        approvalTrueCryptoTransfers.add(cryptoNotMatchingThreshold);

        // crypto transfer with past threshold key not matching the contract result sender id should have isApproval set
        // to true
        var pastCryptoNotMatchingThreshold =
                persistCryptoTransfer(pastKeyAffectedEntity.getId(), pastKeyAffectedEntity.getTimestampLower(), false);
        persistContractResult(contractId, pastCryptoNotMatchingThreshold.getConsensusTimestamp());
        approvalTrueCryptoTransfers.add(pastCryptoNotMatchingThreshold);

        // crypto transfer with threshold key matching the contract result sender, but is outside the lower boundary,
        // isApproval
        // should not be affected
        var lowerBoundTransfer = persistCryptoTransfer(currentKeyAffectedEntity.getId(), LOWER_BOUND_TIMESTAMP, false);
        persistContractResult(contractId, lowerBoundTransfer.getConsensusTimestamp());
        unaffectedCryptoTransfers.add(lowerBoundTransfer);

        // crypto transfer with current threshold key not matching the contract result sender id but prior to the
        // grandfathered id. Should not have isApproval set to true
        var priorGrandfatheredTransfer = persistCryptoTransfer(currentKeyAffectedEntity.getId(), null, false);
        persistContractResult(priorGrandfatheredContractId, priorGrandfatheredTransfer.getConsensusTimestamp());
        unaffectedCryptoTransfers.add(priorGrandfatheredTransfer);

        // crypto transfer with no threshold key should not have isApproval set to true
        var noKeyCryptoTransfer = persistCryptoTransfer(noKeyEntity.getId(), null, false);
        persistContractResult(contractId, noKeyCryptoTransfer.getConsensusTimestamp());
        unaffectedCryptoTransfers.add(noKeyCryptoTransfer);

        // crypto transfer without a contract result, will not be affected by the migration
        unaffectedCryptoTransfers.add(domainBuilder.cryptoTransfer().persist());

        // crypto transfer with approved set to true, it should not be affected by the migration
        var cryptoNotMatchingThresholdApproved = persistCryptoTransfer(currentKeyAffectedEntity.getId(), null, true);
        persistContractResult(contractId, cryptoNotMatchingThresholdApproved.getConsensusTimestamp());
        unaffectedCryptoTransfers.add(cryptoNotMatchingThresholdApproved);

        // crypto transfer with threshold set to 2. A threshold over 1 should have isApproval set to
        // true
        var thresholdTwoTransfer = persistCryptoTransfer(thresholdTwoKeyEntity.getId(), null, false);
        persistContractResult(contractId, thresholdTwoTransfer.getConsensusTimestamp());
        approvalTrueCryptoTransfers.add(thresholdTwoTransfer);

        // transfer that would have isApproval set to true, but the contract result consensus timestamp is outside the
        // upper bound
        var outsideUpperBoundTransfer =
                persistCryptoTransfer(currentKeyAffectedEntity.getId(), UPPER_BOUND_TIMESTAMP + 1, false);
        persistContractResult(contractId, outsideUpperBoundTransfer.getConsensusTimestamp());
        unaffectedCryptoTransfers.add(outsideUpperBoundTransfer);

        return Pair.of(approvalTrueCryptoTransfers, unaffectedCryptoTransfers);
    }

    private Pair<List<NftTransfer>, List<NftTransfer>> setupTransactionNfts(
            EntityId contractId,
            EntityId priorGrandfatheredContractId,
            Entity currentKeyUnaffectedEntity,
            Entity currentKeyAffectedEntity,
            Entity noKeyEntity,
            Entity pastKeyUnaffectedEntity,
            Entity pastKeyAffectedEntity,
            Entity thresholdTwoKeyEntity) {
        var approvalTrueNftTransfers = new ArrayList<NftTransfer>();
        var unaffectedNftTransfers = new ArrayList<NftTransfer>();

        long insideBoundaryTimestamp = getTimestampWithinBoundary();
        long pastTimestamp = pastKeyAffectedEntity.getTimestampLower() + 1;

        // nft transfer with current threshold key matching the contract result sender id should not have isApproval set
        // to true
        var currentKeyUnaffectedNft = getNftTransfer(currentKeyUnaffectedEntity.toEntityId(), false);
        unaffectedNftTransfers.add(currentKeyUnaffectedNft);

        // nft transfer with past threshold key matching the contract result sender id should not have isApproval set to
        // true
        var pastKeyUnaffectedNft = getNftTransfer(pastKeyUnaffectedEntity.toEntityId(), false);
        unaffectedNftTransfers.add(pastKeyUnaffectedNft);

        // nft transfer with current threshold key not matching the contract result sender id should have isApproval set
        // to true
        var currentKeyAffectedNft = getNftTransfer(currentKeyAffectedEntity.toEntityId(), false);
        approvalTrueNftTransfers.add(currentKeyAffectedNft);

        // nft transfer with past threshold key not matching the contract result sender id should have isApproval set to
        // true
        var pastKeyAffectedNft = getNftTransfer(pastKeyAffectedEntity.toEntityId(), false);
        approvalTrueNftTransfers.add(pastKeyAffectedNft);

        // nft transfer with current threshold key not matching the contract result sender id but prior to the
        // grandfathered id, should not have isApproval set
        // to true
        var priorGrandfatheredNft = getNftTransfer(currentKeyAffectedEntity.toEntityId(), false);
        unaffectedNftTransfers.add(priorGrandfatheredNft);

        // nft transfer with no threshold key should not have isApproval set to true
        var noKeyNft = getNftTransfer(noKeyEntity.toEntityId(), false);
        unaffectedNftTransfers.add(noKeyNft);

        // nft transfer without a contract result, will not be affected by the migration
        var noContractResultNft = domainBuilder.nftTransfer().get();
        unaffectedNftTransfers.add(noContractResultNft);

        // nft transfer with approved set to true, it should not be affected by the migration
        var approvalTrueNft = getNftTransfer(currentKeyAffectedEntity.toEntityId(), true);
        unaffectedNftTransfers.add(approvalTrueNft);

        // nft transfer with a threshold set to 2. A threshold over 1 should have isApproval set to true
        var thresholdTwoNft = getNftTransfer(thresholdTwoKeyEntity.toEntityId(), false);
        approvalTrueNftTransfers.add(thresholdTwoNft);

        persistTransaction(pastTimestamp, List.of(pastKeyAffectedNft, pastKeyUnaffectedNft));
        persistTransaction(
                insideBoundaryTimestamp,
                List.of(
                        currentKeyUnaffectedNft,
                        currentKeyAffectedNft,
                        noKeyNft,
                        noContractResultNft,
                        approvalTrueNft,
                        thresholdTwoNft));
        var priorGrandfatheredTimestamp = getTimestampWithinBoundary();
        persistTransaction(priorGrandfatheredTimestamp, List.of(priorGrandfatheredNft));

        persistContractResult(contractId, pastTimestamp);
        persistContractResult(contractId, insideBoundaryTimestamp);
        persistContractResult(priorGrandfatheredContractId, priorGrandfatheredTimestamp);

        // nft transfer with threshold key matching the contract result sender but outside the lower boundary,
        // isApproval should
        // not be affected
        var outsideLowerBoundaryNft = List.of(getNftTransfer(currentKeyAffectedEntity.toEntityId(), false));
        var lowerBoundTransaction = persistTransaction(LOWER_BOUND_TIMESTAMP - 1, outsideLowerBoundaryNft);
        persistContractResult(currentKeyAffectedEntity.toEntityId(), lowerBoundTransaction.getConsensusTimestamp());
        unaffectedNftTransfers.addAll(outsideLowerBoundaryNft);

        // transaction with a list of nft transfers, the first and third should be migrated and the second should not
        var listNft = List.of(
                getNftTransfer(currentKeyAffectedEntity.toEntityId(), false),
                getNftTransfer(currentKeyUnaffectedEntity.toEntityId(), false),
                getNftTransfer(pastKeyAffectedEntity.toEntityId(), false));
        var nftsTransaction = persistTransaction(null, listNft);
        persistContractResult(contractId, nftsTransaction.getConsensusTimestamp());
        approvalTrueNftTransfers.add(listNft.get(0));
        unaffectedNftTransfers.add(listNft.get(1));
        approvalTrueNftTransfers.add(listNft.get(2));

        // transfer that would have isApproval set to true, but the contract result consensus timestamp is outside the
        // upper bound
        var outsideUpperBoundTransfer = getNftTransfer(currentKeyAffectedEntity.toEntityId(), false);
        var upperBoundTransaction = persistTransaction(UPPER_BOUND_TIMESTAMP + 2, List.of(outsideUpperBoundTransfer));
        persistContractResult(contractId, upperBoundTransaction.getConsensusTimestamp());
        unaffectedNftTransfers.add(outsideUpperBoundTransfer);

        return Pair.of(approvalTrueNftTransfers, unaffectedNftTransfers);
    }

    private Pair<List<TokenTransfer>, List<TokenTransfer>> setupTokenTransfers(
            EntityId contractId,
            EntityId priorGrandfatheredContractId,
            Entity currentKeyUnaffectedEntity,
            Entity currentKeyAffectedEntity,
            Entity noKeyEntity,
            Entity pastKeyUnaffectedEntity,
            Entity pastKeyAffectedEntity,
            Entity thresholdTwoKeyEntity) {
        var approvalTrueTokenTransfers = new ArrayList<TokenTransfer>();
        var unaffectedTokenTransfers = new ArrayList<TokenTransfer>();

        // token transfer with threshold key matching the contract result sender id should not have isApproval set to
        // true
        var tokenMatchingThreshold = persistTokenTransfer(currentKeyUnaffectedEntity.toEntityId(), null, false);
        persistContractResult(contractId, tokenMatchingThreshold.getId().getConsensusTimestamp());
        unaffectedTokenTransfers.add(tokenMatchingThreshold);

        // token transfer with past threshold key matching the contract result sender id should not have isApproval set
        // to true
        var pastTokenMatchingThreshold = persistTokenTransfer(
                pastKeyUnaffectedEntity.toEntityId(), pastKeyUnaffectedEntity.getTimestampLower(), false);
        persistContractResult(contractId, pastTokenMatchingThreshold.getId().getConsensusTimestamp());
        unaffectedTokenTransfers.add(pastTokenMatchingThreshold);

        // token transfer with threshold key not matching the contract result sender id should have isApproval set to
        // true
        var tokenNotMatchingThreshold = persistTokenTransfer(currentKeyAffectedEntity.toEntityId(), null, false);
        persistContractResult(contractId, tokenNotMatchingThreshold.getId().getConsensusTimestamp());
        approvalTrueTokenTransfers.add(tokenNotMatchingThreshold);

        // token transfer with past threshold key not matching the contract result sender id should have isApproval set
        // to true
        var pastTokenNotMatchingThreshold = persistTokenTransfer(
                pastKeyAffectedEntity.toEntityId(), pastKeyAffectedEntity.getTimestampLower(), false);
        persistContractResult(contractId, pastTokenNotMatchingThreshold.getId().getConsensusTimestamp());
        approvalTrueTokenTransfers.add(pastTokenNotMatchingThreshold);

        // token transfer with threshold key matching the contract result sender but outside the lower boundary,
        // isApproval
        // should not be affected
        var lowerBoundTokenTransfer =
                persistTokenTransfer(currentKeyAffectedEntity.toEntityId(), LOWER_BOUND_TIMESTAMP - 2, false);
        persistContractResult(contractId, lowerBoundTokenTransfer.getId().getConsensusTimestamp());
        unaffectedTokenTransfers.add(lowerBoundTokenTransfer);

        // token transfer with threshold key not matching the contract result sender id but prior to the grandfathered
        // id, should not have isApproval set to true
        var tokenPriorGrandfathered = persistTokenTransfer(currentKeyAffectedEntity.toEntityId(), null, false);
        persistContractResult(
                priorGrandfatheredContractId, tokenPriorGrandfathered.getId().getConsensusTimestamp());
        unaffectedTokenTransfers.add(tokenPriorGrandfathered);

        // token transfer with no key should not have isApproval set to true
        var noKeyTokenTransfer = persistTokenTransfer(noKeyEntity.toEntityId(), null, false);
        persistContractResult(contractId, noKeyTokenTransfer.getId().getConsensusTimestamp());
        unaffectedTokenTransfers.add(noKeyTokenTransfer);

        // token transfer without a contract result, will not be affected by the migration
        unaffectedTokenTransfers.add(domainBuilder.tokenTransfer().persist());

        // token transfer with the problem already fixed, it should not be affected by the migration
        var tokenNotMatchingThresholdFixed = persistTokenTransfer(currentKeyAffectedEntity.toEntityId(), null, true);
        persistContractResult(contractId, tokenNotMatchingThresholdFixed.getId().getConsensusTimestamp());
        unaffectedTokenTransfers.add(tokenNotMatchingThresholdFixed);

        // token transfer with threshold set to 2. A threshold over 1 should have isApproval set to true
        var thresholdTwoTransfer = persistTokenTransfer(thresholdTwoKeyEntity.toEntityId(), null, false);
        persistContractResult(contractId, thresholdTwoTransfer.getId().getConsensusTimestamp());
        approvalTrueTokenTransfers.add(thresholdTwoTransfer);

        // token transfer with threshold key matching the contract result sender but outside the upper boundary,
        // isApproval
        // should not be affected
        var upperBoundTokenTransfer =
                persistTokenTransfer(currentKeyAffectedEntity.toEntityId(), UPPER_BOUND_TIMESTAMP + 3, false);
        persistContractResult(contractId, upperBoundTokenTransfer.getId().getConsensusTimestamp());
        unaffectedTokenTransfers.add(upperBoundTokenTransfer);

        return Pair.of(approvalTrueTokenTransfers, unaffectedTokenTransfers);
    }

    private byte[] getThresholdKey(Long contractNum) {
        if (contractNum == null) {
            return null;
        }
        var thresholdKeyList = KeyList.newBuilder()
                .addKeys(Key.newBuilder().setEd25519(ByteString.EMPTY).build())
                .addKeys(Key.newBuilder()
                        .setContractID(ContractID.newBuilder().setContractNum(contractNum))
                        .build())
                .build();
        var thresholdKey = ThresholdKey.newBuilder().setKeys(thresholdKeyList).build();
        return Key.newBuilder().setThresholdKey(thresholdKey).build().toByteArray();
    }

    // Threshold key with threshold set to 2
    private byte[] getThresholdTwoKey(Long contractNum) {
        var thresholdKeyList = KeyList.newBuilder()
                .addKeys(Key.newBuilder()
                        .setContractID(ContractID.newBuilder().setContractNum(domainBuilder.id()))
                        .build())
                .addKeys(Key.newBuilder()
                        .setContractID(ContractID.newBuilder().setContractNum(contractNum))
                        .build())
                .build();
        var thresholdKey = ThresholdKey.newBuilder()
                .setKeys(thresholdKeyList)
                .setThreshold(2)
                .build();
        return Key.newBuilder().setThresholdKey(thresholdKey).build().toByteArray();
    }

    private void persistContractResult(EntityId contractId, long consensusTimestamp) {
        domainBuilder
                .contractResult()
                .customize(c -> c.contractId(contractId.getId())
                        .consensusTimestamp(consensusTimestamp)
                        .payerAccountId(payerAccountId))
                .persist();
    }

    private CryptoTransfer persistCryptoTransfer(long entityId, Long consensusTimestamp, boolean isApproval) {
        long consensus = consensusTimestamp == null ? getTimestampWithinBoundary() : consensusTimestamp;
        return domainBuilder
                .cryptoTransfer()
                .customize(t -> t.amount(-10) // debit from account
                        .entityId(entityId)
                        .isApproval(isApproval)
                        .payerAccountId(payerAccountId)
                        .consensusTimestamp(consensus))
                .persist();
    }

    private Entity entityWithNoKey() {
        return persistEntity(null, null);
    }

    private Entity entityCurrentKey(Long contractNum) {
        return persistEntity(contractNum, null);
    }

    private Entity entityPastKey(Long contractNum) {
        return persistEntity(domainBuilder.id(), contractNum);
    }

    private Entity persistEntity(Long currentContractNum, Long pastContractNum) {
        var timestamp = getTimestampWithinBoundary();
        var builder = domainBuilder.entity().customize(e -> e.key(getThresholdKey(currentContractNum))
                .timestampRange(Range.atLeast(timestamp)));
        var currentEntity = builder.persist();
        if (pastContractNum != null) {
            var range = currentEntity.getTimestampRange();
            var rangeUpdate1 = Range.closedOpen(range.lowerEndpoint() - 1000L, range.lowerEndpoint() - 1);
            var update1 = builder.customize(
                            e -> e.key(getThresholdKey(pastContractNum)).timestampRange(rangeUpdate1))
                    .get();
            var pastEntityHistory = saveHistory(update1);
            return domainBuilder
                    .entity()
                    .customize(e -> e.id(pastEntityHistory.getId())
                            .key(pastEntityHistory.getKey())
                            .num(pastEntityHistory.getNum())
                            .timestampRange(pastEntityHistory.getTimestampRange()))
                    .get();
        }

        return currentEntity;
    }

    private NftTransfer getNftTransfer(EntityId entityId, boolean isApproval) {
        return domainBuilder
                .nftTransfer()
                .customize(t -> t.senderAccountId(entityId).isApproval(isApproval))
                .get();
    }

    private Transaction persistTransaction(Long consensusTimestamp, List<NftTransfer> nftTransfers) {
        long consensus = consensusTimestamp == null ? getTimestampWithinBoundary() : consensusTimestamp;
        return domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensus)
                        .nftTransfer(nftTransfers)
                        .itemizedTransfer(null)
                        .payerAccountId(payerAccountId))
                .persist();
    }

    private TokenTransfer persistTokenTransfer(EntityId entityId, Long consensusTimestamp, boolean isApproval) {
        long consensus = consensusTimestamp == null ? getTimestampWithinBoundary() : consensusTimestamp;
        var id = TokenTransfer.Id.builder()
                .accountId(entityId)
                .consensusTimestamp(consensus)
                .tokenId(domainBuilder.entityId())
                .build();
        return domainBuilder
                .tokenTransfer()
                .customize(t -> t.amount(-10) // debit from account
                        .id(id)
                        .payerAccountId(payerAccountId)
                        .isApproval(isApproval))
                .persist();
    }

    private EntityHistory saveHistory(Entity entity) {
        return domainBuilder
                .entityHistory()
                .customize(e -> e.id(entity.getId())
                        .key(entity.getKey())
                        .num(entity.getNum())
                        .timestampRange(entity.getTimestampRange()))
                .persist();
    }

    private long getTimestampWithinBoundary() {
        return LOWER_BOUND_TIMESTAMP + count.incrementAndGet();
    }
}
