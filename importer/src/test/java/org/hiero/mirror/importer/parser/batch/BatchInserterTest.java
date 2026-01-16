// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.util.CommonUtils.nextBytes;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.TokenTransfer;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.domain.transaction.AssessedCustomFee;
import org.hiero.mirror.common.domain.transaction.CryptoTransfer;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.exception.ParserException;
import org.hiero.mirror.importer.parser.CommonParserProperties;
import org.hiero.mirror.importer.repository.CryptoTransferRepository;
import org.hiero.mirror.importer.repository.TokenTransferRepository;
import org.hiero.mirror.importer.repository.TopicMessageRepository;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

@RequiredArgsConstructor
class BatchInserterTest extends ImporterIntegrationTest {

    private final BatchPersister batchInserter;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final TopicMessageRepository topicMessageRepository;
    private final TokenTransferRepository tokenTransferRepository;

    @Test
    void persist() {
        var cryptoTransfers = new ArrayList<CryptoTransfer>();
        cryptoTransfers.add(domainBuilder.cryptoTransfer().get());
        cryptoTransfers.add(domainBuilder.cryptoTransfer().get());
        cryptoTransfers.add(domainBuilder.cryptoTransfer().get());

        var tokenTransfers = new ArrayList<TokenTransfer>();
        tokenTransfers.add(domainBuilder.tokenTransfer().get());
        tokenTransfers.add(domainBuilder.tokenTransfer().get());
        tokenTransfers.add(domainBuilder.tokenTransfer().get());

        batchInserter.persist(cryptoTransfers);
        batchInserter.persist(tokenTransfers);

        assertThat(cryptoTransferRepository.findAll()).containsExactlyInAnyOrderElementsOf(cryptoTransfers);
        assertThat(tokenTransferRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokenTransfers);
    }

    @Test
    void throwsParserException() throws SQLException, IOException {
        // given
        CopyManager copyManager = mock(CopyManager.class);
        doThrow(SQLException.class).when(copyManager).copyIn(any(), (Reader) any(), anyInt());
        PGConnection pgConnection = mock(PGConnection.class);
        doReturn(copyManager).when(pgConnection).getCopyAPI();
        DataSource dataSource = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        doReturn(conn).when(dataSource).getConnection();
        doReturn(pgConnection).when(conn).unwrap(any());
        var cryptoTransferBatchInserter2 = new BatchInserter(
                CryptoTransfer.class, dataSource, new SimpleMeterRegistry(), new CommonParserProperties());
        var cryptoTransfers = new HashSet<CryptoTransfer>();
        cryptoTransfers.add(domainBuilder.cryptoTransfer().get());

        // when
        assertThatThrownBy(() -> cryptoTransferBatchInserter2.persist(cryptoTransfers))
                .isInstanceOf(ParserException.class);
    }

    @Test
    void persistNull() {
        batchInserter.persist(null);
        assertThat(cryptoTransferRepository.count()).isZero();
    }

    @Test
    void largeConsensusSubmitMessage() {
        var topicMessages = new HashSet<TopicMessage>();
        topicMessages.add(topicMessage(6000)); // max 6KiB
        topicMessages.add(topicMessage(6000));
        topicMessages.add(topicMessage(6000));
        topicMessages.add(topicMessage(6000));

        batchInserter.persist(topicMessages);

        assertThat(topicMessageRepository.findAll()).hasSize(4).containsExactlyInAnyOrderElementsOf(topicMessages);
    }

    @Test
    void assessedCustomFees() {
        long consensusTimestamp = 10L;
        EntityId collectorId1 = EntityId.of("0.0.2000");
        EntityId collectorId2 = EntityId.of("0.0.2001");
        EntityId payerId1 = EntityId.of("0.0.3000");
        EntityId payerId2 = EntityId.of("0.0.3001");
        EntityId tokenId1 = EntityId.of("0.0.5000");
        EntityId tokenId2 = EntityId.of("0.0.5001");

        // fee paid in HBAR with empty effective payer list
        AssessedCustomFee assessedCustomFee1 = new AssessedCustomFee();
        assessedCustomFee1.setAmount(10L);
        assessedCustomFee1.setCollectorAccountId(collectorId1.getId());
        assessedCustomFee1.setConsensusTimestamp(consensusTimestamp);
        assessedCustomFee1.setPayerAccountId(payerId1);

        AssessedCustomFee assessedCustomFee2 = new AssessedCustomFee();
        assessedCustomFee2.setAmount(20L);
        assessedCustomFee2.setCollectorAccountId(collectorId2.getId());
        assessedCustomFee2.setConsensusTimestamp(consensusTimestamp);
        assessedCustomFee2.setEffectivePayerAccountIds(List.of(payerId1.getId()));
        assessedCustomFee2.setPayerAccountId(payerId1);
        assessedCustomFee2.setTokenId(tokenId1);

        AssessedCustomFee assessedCustomFee3 = new AssessedCustomFee();
        assessedCustomFee3.setAmount(30L);
        assessedCustomFee3.setCollectorAccountId(collectorId2.getId());
        assessedCustomFee3.setConsensusTimestamp(consensusTimestamp);
        assessedCustomFee3.setEffectivePayerAccountIds(List.of(payerId1.getId(), payerId2.getId()));
        assessedCustomFee3.setPayerAccountId(payerId2);
        assessedCustomFee3.setTokenId(tokenId2);

        List<AssessedCustomFee> assessedCustomFees =
                List.of(assessedCustomFee1, assessedCustomFee2, assessedCustomFee3);

        // when
        batchInserter.persist(assessedCustomFees);

        // then
        assertThat(jdbcOperations.query("select * from assessed_custom_fee", rowMapper(AssessedCustomFee.class)))
                .containsExactlyInAnyOrderElementsOf(assessedCustomFees);
    }

    private TopicMessage topicMessage(int messageSize) {
        return domainBuilder
                .topicMessage()
                .customize(t -> t.message(nextBytes(messageSize)))
                .get();
    }
}
