// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static com.hederahashgraph.api.proto.java.TransactionRecord.BodyCase.CONTRACTCALLRESULT;
import static com.hederahashgraph.api.proto.java.TransactionRecord.BodyCase.CONTRACTCREATERESULT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.GeneratedMessage;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityTransaction;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.util.Version;

@RequiredArgsConstructor
class EntityRecordItemListenerEntityTransactionTest extends AbstractEntityRecordItemListenerTest {

    @BeforeEach
    void setup() {
        entityProperties.getPersist().setEntityTransactions(true);
    }

    @AfterEach
    void teardown() {
        entityProperties.getPersist().setEntityTransactions(false);
        entityProperties.getPersist().setItemizedTransfers(true);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideRecordItems")
    void testEntityTransactions(String name, RecordItem recordItem) {
        parseRecordItemAndCommit(recordItem);
        assertThat(entityTransactionRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(getExpectedEntityTransactions(recordItem));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideRecordItems")
    void testEntityTransactionsWhenDisabled(String name, RecordItem recordItem) {
        entityProperties.getPersist().setEntityTransactions(false);
        parseRecordItemAndCommit(recordItem);
        assertThat(entityTransactionRepository.findAll()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideRecordItems")
    void testEntityNftTransactionsWhenDisabled(String name, RecordItem recordItem) {
        entityProperties.getPersist().setEntityTransactions(false);
        entityProperties.getPersist().setEntityNftTransactions(false);
        parseRecordItemAndCommit(recordItem);
        assertThat(entityTransactionRepository.findAll()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideRecordItems")
    void testEntityNftTransactionsWhenEnabled(String name, RecordItem recordItem) {
        entityProperties.getPersist().setEntityTransactions(false);
        entityProperties.getPersist().setEntityNftTransactions(true);
        parseRecordItemAndCommit(recordItem);
        assertThat(entityTransactionRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(getExpectedEntityNftTransactions(recordItem));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideRecordItems")
    void testEntityTransactionsWhenItemizedTransfersDisabled(String name, RecordItem recordItem) {
        entityProperties.getPersist().setItemizedTransfers(false);
        parseRecordItemAndCommit(recordItem);
        assertThat(entityTransactionRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(getExpectedEntityTransactions(recordItem));
    }

    private Collection<EntityTransaction> getExpectedEntityTransactions(RecordItem recordItem) {
        return getEntities(recordItem).stream()
                .filter(entityProperties.getPersist()::shouldPersistEntityTransaction)
                .map(e -> TestUtils.toEntityTransaction(e, recordItem))
                .toList();
    }

    private Collection<EntityTransaction> getExpectedEntityNftTransactions(RecordItem recordItem) {
        // Entity NFT transactions are only added for sender/receiver of each NFT transfer
        var payerAccountId = recordItem.getPayerAccountId();
        var entityIds = new HashSet<EntityId>();

        for (var tokenTransferList : recordItem.getTransactionRecord().getTokenTransferListsList()) {
            for (var nftTransfer : tokenTransferList.getNftTransfersList()) {
                var senderId = EntityId.of(nftTransfer.getSenderAccountID());
                var receiverId = EntityId.of(nftTransfer.getReceiverAccountID());
                if (!senderId.equals(payerAccountId)) {
                    entityIds.add(senderId);
                }
                if (!receiverId.equals(payerAccountId)) {
                    entityIds.add(receiverId);
                }
            }
        }

        return entityIds.stream()
                .filter(entityId ->
                        entityProperties.getPersist().shouldPersistEntityNftTransaction(entityId, recordItem))
                .map(e -> TestUtils.toEntityTransaction(e, recordItem))
                .toList();
    }

    @SuppressWarnings("deprecation")
    private Set<EntityId> getEntities(RecordItem recordItem) {
        boolean includeTransfersInBody = entityProperties.getPersist().isItemizedTransfers();
        var entities = getEntities(recordItem.getTransactionBody(), includeTransfersInBody);
        entities.addAll(getEntities(recordItem.getTransactionRecord(), true));
        for (var sidecar : recordItem.getSidecarRecords()) {
            entities.addAll(getEntities(sidecar, false));
        }

        if (recordItem.getTransactionType() == TransactionType.CONSENSUSSUBMITMESSAGE.getProtoId()) {
            // Remove the topicId in ConsensusSubmitMessage tx
            var topicId = EntityId.of(
                    recordItem.getTransactionBody().getConsensusSubmitMessage().getTopicID());
            entities.remove(topicId);
        }

        var txnRecord = recordItem.getTransactionRecord();
        ContractFunctionResult contractFunctionResult = null;
        var txnRecordBodyCase = txnRecord.getBodyCase();
        if (txnRecordBodyCase == CONTRACTCALLRESULT) {
            contractFunctionResult = txnRecord.getContractCallResult();
        } else if (txnRecordBodyCase == CONTRACTCREATERESULT) {
            contractFunctionResult = txnRecord.getContractCreateResult();
        }

        if (contractFunctionResult != null
                && recordItem.getHapiVersion().isGreaterThanOrEqualTo(RecordFile.HAPI_VERSION_0_23_0)) {
            var rootContractId = EntityId.of(contractFunctionResult.getContractID());
            for (var contractId : contractFunctionResult.getCreatedContractIDsList()) {
                var entityId = EntityId.of(contractId);
                if (!rootContractId.equals(entityId)) {
                    entities.remove(entityId);
                }
            }
        }

        return entities;
    }

    private Set<EntityId> getEntities(GeneratedMessage message, boolean includeTransfers) {
        if (message instanceof SchedulableTransactionBody) {
            // Don't include any entities in a  SchedulableTransactionBody
            return Collections.emptySet();
        }

        var entities = new HashSet<EntityId>();
        for (var value : message.getAllFields().values()) {
            entities.addAll(getEntitiesInner(value, includeTransfers));
        }

        return entities;
    }

    private Set<EntityId> getEntitiesInner(Object value, boolean includeTransfers) {
        var entities = new HashSet<EntityId>();
        if (value instanceof AccountID accountId) {
            entities.add(EntityId.of(accountId));
        } else if (value instanceof ContractID contractId) {
            entities.add(EntityId.of(contractId));
        } else if (value instanceof FileID fileId) {
            entities.add(EntityId.of(fileId));
        } else if (value instanceof ScheduleID scheduleId) {
            entities.add(EntityId.of(scheduleId));
        } else if (value instanceof TokenID tokenId) {
            entities.add(EntityId.of(tokenId));
        } else if (value instanceof TopicID topicId) {
            entities.add(EntityId.of(topicId));
        } else if (value instanceof GeneratedMessage message) {
            if (!includeTransfers && (value instanceof TransferList || value instanceof TokenTransferList)) {
                return Collections.emptySet();
            }

            entities.addAll(getEntities(message, includeTransfers));
        } else if (value instanceof Collection<?> collection) {
            for (var element : collection) {
                entities.addAll(getEntitiesInner(element, includeTransfers));
            }
        }

        return entities;
    }

    @SneakyThrows
    private static Stream<Arguments> provideRecordItems() {
        var recordItemBuilder = new RecordItemBuilder();
        var methods = RecordItemBuilder.class.getMethods();

        var argumentsList = new ArrayList<Arguments>();
        for (var method : methods) {
            if (method.getReturnType() != RecordItemBuilder.Builder.class || method.getParameterCount() != 0) {
                continue;
            }

            var recordItem = ((RecordItemBuilder.Builder<?>) method.invoke(recordItemBuilder))
                    .recordItem(r -> r.hapiVersion(RecordFile.HAPI_VERSION_0_27_0))
                    .build();
            argumentsList.add(Arguments.of(method.getName(), recordItem));

            if (recordItem.getTransactionBody().hasContractCall()) {
                // Test contractCall before and after HAPI 0.23.0. Since HAPI 0.23.0,
                // ContractFunctionResult.createdContractIDs is marked as deprecated and each new child contract should
                // have its own child contractCreate transaction & record. As a result, mirrornode should not generate
                // EntityTransaction for such child contracts starting from HAPI 0.23.0.
                var version = recordItem.getHapiVersion().isLessThan(RecordFile.HAPI_VERSION_0_23_0)
                        ? RecordFile.HAPI_VERSION_0_23_0
                        : new Version(0, 22, 0);
                recordItem = ((RecordItemBuilder.Builder<?>) method.invoke(recordItemBuilder))
                        .recordItem(r -> r.hapiVersion(version))
                        .build();
                argumentsList.add(Arguments.of(String.format("%s_%s", method.getName(), version), recordItem));
            }
        }

        return argumentsList.stream();
    }
}
