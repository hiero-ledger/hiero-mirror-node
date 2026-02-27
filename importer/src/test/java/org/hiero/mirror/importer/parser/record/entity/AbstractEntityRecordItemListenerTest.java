// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;
import static org.hiero.mirror.importer.domain.StreamFilename.FileType.DATA;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.entity.AbstractEntity;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.CryptoTransfer;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.hiero.mirror.importer.parser.record.RecordStreamFileListener;
import org.hiero.mirror.importer.repository.ContractRepository;
import org.hiero.mirror.importer.repository.ContractResultRepository;
import org.hiero.mirror.importer.repository.ContractTransactionHashRepository;
import org.hiero.mirror.importer.repository.CryptoTransferRepository;
import org.hiero.mirror.importer.repository.CustomFeeRepository;
import org.hiero.mirror.importer.repository.EntityHistoryRepository;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.hiero.mirror.importer.repository.EntityTransactionRepository;
import org.hiero.mirror.importer.repository.LiveHashRepository;
import org.hiero.mirror.importer.repository.StakingRewardTransferRepository;
import org.hiero.mirror.importer.repository.TopicMessageRepository;
import org.hiero.mirror.importer.repository.TopicRepository;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.hiero.mirror.importer.util.Utility;
import org.springframework.transaction.support.TransactionTemplate;

public abstract class AbstractEntityRecordItemListenerTest extends ImporterIntegrationTest {

    protected static final ContractID CONTRACT_ID =
            DOMAIN_BUILDER.entityNum(901).toContractID();
    protected static final ContractID CREATED_CONTRACT_ID =
            DOMAIN_BUILDER.entityNum(902).toContractID();
    protected static final SignatureMap DEFAULT_SIG_MAP = getDefaultSigMap();
    protected static final String KEY = "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110fff";
    protected static final String KEY2 = "0a3312200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92";
    protected static final AccountID PAYER = DOMAIN_BUILDER.entityNum(2002).toAccountID();
    protected static final AccountID PAYER2 = DOMAIN_BUILDER.entityNum(2003).toAccountID();
    protected static final AccountID PAYER3 = DOMAIN_BUILDER.entityNum(2006).toAccountID();
    protected static final AccountID RECEIVER = DOMAIN_BUILDER.entityNum(2004).toAccountID();
    protected static final AccountID SPENDER = DOMAIN_BUILDER.entityNum(2005).toAccountID();
    protected static final AccountID DEFAULT_ACCOUNT_ID = AccountID.getDefaultInstance();
    protected static final AccountID NODE = DOMAIN_BUILDER.entityNum(3).toAccountID();
    protected static final AccountID PROXY = DOMAIN_BUILDER.entityNum(1003).toAccountID();
    protected static final AccountID PROXY_UPDATE =
            DOMAIN_BUILDER.entityNum(3000).toAccountID();
    protected static final String TRANSACTION_MEMO = "transaction memo";

    @Resource
    protected ContractRepository contractRepository;

    @Resource
    protected ContractResultRepository contractResultRepository;

    @Resource
    protected ContractTransactionHashRepository contractTransactionHashRepository;

    @Resource
    protected CryptoTransferRepository cryptoTransferRepository;

    @Resource
    protected CustomFeeRepository customFeeRepository;

    @Resource
    protected DomainBuilder domainBuilder;

    @Resource
    protected EntityProperties entityProperties;

    @Resource
    protected EntityRecordItemListener entityRecordItemListener;

    @Resource
    protected EntityRepository entityRepository;

    @Resource
    protected EntityHistoryRepository entityHistoryRepository;

    @Resource
    protected EntityTransactionRepository entityTransactionRepository;

    @Resource
    protected LiveHashRepository liveHashRepository;

    @Resource
    protected RecordItemBuilder recordItemBuilder;

    @Resource
    protected RecordStreamFileListener recordStreamFileListener;

    @Resource
    protected StakingRewardTransferRepository stakingRewardTransferRepository;

    @Resource
    protected TopicMessageRepository topicMessageRepository;

    @Resource
    protected TopicRepository topicRepository;

    @Resource
    protected TransactionRepository transactionRepository;

    @Resource
    private TransactionTemplate transactionTemplate;

    private long nextIndex = 0L;

    private static SignatureMap getDefaultSigMap() {
        String key1 = "11111111111111111111c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e91";
        String signature1 = "Signature 1 here";
        String key2 = "22222222222222222222c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e91";
        String signature2 = "Signature 2 here";

        SignatureMap.Builder sigMap = SignatureMap.newBuilder();
        SignaturePair.Builder sigPair = SignaturePair.newBuilder();
        sigPair.setEd25519(ByteString.copyFromUtf8(signature1));
        sigPair.setPubKeyPrefix(ByteString.copyFromUtf8(key1));

        sigMap.addSigPair(sigPair);

        sigPair = SignaturePair.newBuilder();
        sigPair.setEd25519(ByteString.copyFromUtf8(signature2));
        sigPair.setPubKeyPrefix(ByteString.copyFromUtf8(key2));

        sigMap.addSigPair(sigPair);

        return sigMap.build();
    }

    @SneakyThrows
    protected static Key keyFromString(String key) {
        var bytes = Hex.decodeHex(key);
        return Key.newBuilder().setEd25519(DomainUtils.fromBytes(bytes)).build();
    }

    private static Builder defaultTransactionBodyBuilder() {
        TransactionBody.Builder body = TransactionBody.newBuilder();
        body.setTransactionFee(100L);
        body.setMemo(TRANSACTION_MEMO);
        body.setNodeAccountID(NODE);
        body.setTransactionID(Utility.getTransactionId(PAYER));
        body.setTransactionValidDuration(Duration.newBuilder().setSeconds(120).build());
        return body;
    }

    protected final void assertAccount(AccountID accountId, Entity dbEntity) {
        assertThat(accountId)
                .isNotEqualTo(AccountID.getDefaultInstance())
                .extracting(AccountID::getShardNum, AccountID::getRealmNum, AccountID::getAccountNum)
                .containsExactly(dbEntity.getShard(), dbEntity.getRealm(), dbEntity.getNum());
        assertThat(dbEntity.getType()).isEqualTo(EntityType.ACCOUNT);
    }

    protected void parseRecordItemAndCommit(RecordItem recordItem) {
        transactionTemplate.executeWithoutResult(status -> {
            Instant instant = Instant.ofEpochSecond(0, recordItem.getConsensusTimestamp());
            String filename = StreamFilename.getFilename(StreamType.RECORD, DATA, instant);
            long consensusStart = recordItem.getConsensusTimestamp();
            RecordFile recordFile = recordFile(consensusStart, consensusStart + 1, filename);

            entityRecordItemListener.onItem(recordItem);
            // commit, close connection
            recordStreamFileListener.onEnd(recordFile);
            parserContext.clear();
        });
    }

    protected void parseRecordItemsAndCommit(List<RecordItem> recordItems) {
        transactionTemplate.executeWithoutResult(status -> {
            Instant instant = Instant.ofEpochSecond(0, recordItems.get(0).getConsensusTimestamp());
            String filename = StreamFilename.getFilename(StreamType.RECORD, DATA, instant);
            long consensusStart = recordItems.get(0).getConsensusTimestamp();
            long consensusEnd = recordItems.get(recordItems.size() - 1).getConsensusTimestamp();
            RecordFile recordFile = recordFile(consensusStart, consensusEnd, filename);

            // process each record item
            for (RecordItem recordItem : recordItems) {
                entityRecordItemListener.onItem(recordItem);
            }

            // commit, close connection
            recordStreamFileListener.onEnd(recordFile);
            parserContext.clear();
        });
    }

    protected void assertRecordTransfers(TransactionRecord txnRecord) {
        long consensusTimestamp = DomainUtils.timeStampInNanos(txnRecord.getConsensusTimestamp());
        if (entityProperties.getPersist().isCryptoTransferAmounts()) {
            TransferList transferList = txnRecord.getTransferList();
            for (AccountAmount accountAmount : transferList.getAccountAmountsList()) {
                EntityId account = EntityId.of(accountAmount.getAccountID());
                assertThat(cryptoTransferRepository.findById(
                                new CryptoTransfer.Id(accountAmount.getAmount(), consensusTimestamp, account.getId())))
                        .isPresent();
            }
        } else {
            assertThat(cryptoTransferRepository.count()).isZero();
        }
    }

    protected void assertRecordItem(RecordItem recordItem) {
        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
    }

    protected void assertTransactionAndRecord(TransactionBody transactionBody, TransactionRecord txnRecord) {
        var dbTransaction = getDbTransaction(txnRecord.getConsensusTimestamp());
        assertTransaction(transactionBody, dbTransaction);
        assertRecord(txnRecord, dbTransaction);
    }

    private void assertRecord(TransactionRecord txnRecord, Transaction dbTransaction) {
        assertThat(dbTransaction)
                .isNotNull()
                .returns(
                        DomainUtils.timeStampInNanos(txnRecord.getConsensusTimestamp()),
                        Transaction::getConsensusTimestamp)
                .returns(txnRecord.getTransactionFee(), Transaction::getChargedTxFee)
                .returns(txnRecord.getReceipt().getStatusValue(), Transaction::getResult)
                .returns(txnRecord.getTransactionHash().toByteArray(), Transaction::getTransactionHash)
                .returns(txnRecord.hasScheduleRef(), Transaction::isScheduled);
        assertRecordTransfers(txnRecord);
    }

    private void assertTransaction(TransactionBody transactionBody, Transaction dbTransaction) {
        var transactionId = transactionBody.getTransactionID();
        var validDurationSeconds = transactionBody.getTransactionValidDuration().getSeconds();
        var validStart = DomainUtils.timeStampInNanos(transactionId.getTransactionValidStart());

        assertThat(dbTransaction)
                .isNotNull()
                .returns(null, Transaction::getErrata)
                .returns(transactionBody.getTransactionFee(), Transaction::getMaxFee)
                .returns(transactionBody.getMemoBytes().toByteArray(), Transaction::getMemo)
                .returns(EntityId.of(transactionBody.getNodeAccountID()), Transaction::getNodeAccountId)
                .returns(EntityId.of(transactionId.getAccountID()), Transaction::getPayerAccountId)
                .returns(null, Transaction::getTransactionBytes)
                .returns(transactionBody.getDataCase().getNumber(), Transaction::getType)
                .returns(validStart, Transaction::getValidStartNs)
                .returns(validDurationSeconds, Transaction::getValidDurationSeconds);
    }

    protected com.hederahashgraph.api.proto.java.Transaction buildTransaction(
            Consumer<Builder> customBuilder, SignatureMap sigMap) {
        TransactionBody.Builder bodyBuilder = defaultTransactionBodyBuilder();
        customBuilder.accept(bodyBuilder);

        return com.hederahashgraph.api.proto.java.Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(bodyBuilder.build().toByteString())
                        .setSigMap(sigMap)
                        .build()
                        .toByteString())
                .build();
    }

    protected com.hederahashgraph.api.proto.java.Transaction buildTransaction(Consumer<Builder> customBuilder) {
        return buildTransaction(customBuilder, DEFAULT_SIG_MAP);
    }

    protected TransactionRecord buildTransactionRecordWithNoTransactions(
            Consumer<TransactionRecord.Builder> customBuilder, TransactionBody transactionBody, int status) {
        TransactionRecord.Builder recordBuilder = TransactionRecord.newBuilder();
        recordBuilder.setConsensusTimestamp(Utility.instantToTimestamp(Instant.now()));
        recordBuilder.setMemoBytes(ByteString.copyFromUtf8(transactionBody.getMemo()));
        recordBuilder.setTransactionFee(transactionBody.getTransactionFee());
        recordBuilder.setTransactionHash(ByteString.copyFromUtf8("TransactionHash"));
        recordBuilder.setTransactionID(transactionBody.getTransactionID());
        recordBuilder.getReceiptBuilder().setStatusValue(status);

        customBuilder.accept(recordBuilder);

        return recordBuilder.build();
    }

    protected TransactionRecord buildTransactionRecord(
            Consumer<TransactionRecord.Builder> customBuilder, TransactionBody transactionBody, int status) {
        TransactionRecord.Builder recordBuilder = TransactionRecord.newBuilder();
        recordBuilder.setConsensusTimestamp(Utility.instantToTimestamp(Instant.now()));
        recordBuilder.setMemoBytes(ByteString.copyFromUtf8(transactionBody.getMemo()));
        recordBuilder.setTransactionFee(transactionBody.getTransactionFee());
        recordBuilder.setTransactionHash(ByteString.copyFromUtf8("TransactionHash"));
        recordBuilder.setTransactionID(transactionBody.getTransactionID());
        recordBuilder.getReceiptBuilder().setStatusValue(status);

        // Give from payer to treasury and node
        TransferList.Builder transferList = recordBuilder.getTransferListBuilder();
        // Irrespective of transaction success, node and network fees are present.
        transferList.addAccountAmounts(
                AccountAmount.newBuilder().setAccountID(PAYER).setAmount(-2000).build());
        transferList.addAccountAmounts(AccountAmount.newBuilder()
                .setAccountID(systemEntity.networkAdminFeeAccount().toAccountID())
                .setAmount(1000)
                .build());
        transferList.addAccountAmounts(
                AccountAmount.newBuilder().setAccountID(NODE).setAmount(1000).build());

        if (transactionBody.hasCryptoTransfer() && status == ResponseCodeEnum.SUCCESS.getNumber()) {
            for (var aa : transactionBody.getCryptoTransfer().getTransfers().getAccountAmountsList()) {
                // handle alias case.
                // Network will correctly populate accountNum in record, ignore for test case
                if (aa.getAccountID().getAccountCase() == AccountID.AccountCase.ALIAS) {
                    continue;
                }

                transferList.addAccountAmounts(aa);
            }
        }
        customBuilder.accept(recordBuilder);
        return recordBuilder.build();
    }

    protected Transaction getDbTransaction(Timestamp consensusTimestamp) {
        return transactionRepository
                .findById(DomainUtils.timeStampInNanos(consensusTimestamp))
                .get();
    }

    protected Entity getTransactionEntity(Timestamp consensusTimestamp) {
        var transaction = transactionRepository
                .findById(DomainUtils.timeStampInNanos(consensusTimestamp))
                .get();
        return getEntity(transaction.getEntityId());
    }

    protected Entity getEntity(EntityId entityId) {
        return entityRepository.findById(entityId.getId()).get();
    }

    protected AccountAmount.Builder accountAmount(AccountID account, long amount) {
        return AccountAmount.newBuilder().setAccountID(account).setAmount(amount);
    }

    protected AccountAmount.Builder accountAmount(long accountId, long amount) {
        return accountAmount(EntityId.of(accountId), amount);
    }

    protected AccountAmount.Builder accountAmount(EntityId accountId, long amount) {
        return accountAmount(accountId.toAccountID(), amount);
    }

    protected AccountAmount.Builder accountAliasAmount(ByteString alias, long amount) {
        return AccountAmount.newBuilder()
                .setAccountID(AccountID.newBuilder()
                        .setShardNum(COMMON_PROPERTIES.getShard())
                        .setRealmNum(COMMON_PROPERTIES.getRealm())
                        .setAlias(alias))
                .setAmount(amount);
    }

    protected boolean isAccountAmountReceiverAccountAmount(CryptoTransfer cryptoTransfer, AccountAmount receiver) {
        var cryptoTransferId = cryptoTransfer.getId();
        return cryptoTransferId.getEntityId()
                        == EntityId.of(receiver.getAccountID()).getId()
                && cryptoTransferId.getAmount() == receiver.getAmount();
    }

    protected TransactionBody getTransactionBody(com.hederahashgraph.api.proto.java.Transaction transaction) {
        try {
            return TransactionBody.parseFrom(SignedTransaction.parseFrom(transaction.getSignedTransactionBytes())
                    .getBodyBytes());
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    protected Entity createEntity(
            EntityId entityId,
            EntityType entityType,
            Key adminKey,
            Long autoRenewAccountId,
            Long autoRenewPeriod,
            Boolean deleted,
            Long expiryTimeNs,
            String memo,
            Long createdTimestamp,
            Long modifiedTimestamp) {
        byte[] adminKeyBytes = rawBytesFromKey(adminKey);

        Entity entity = entityId.toEntity();
        entity.setAutoRenewAccountId(autoRenewAccountId);
        entity.setAutoRenewPeriod(autoRenewPeriod);
        entity.setCreatedTimestamp(createdTimestamp);
        entity.setDeclineReward(false);
        entity.setDeleted(deleted);
        entity.setExpirationTimestamp(expiryTimeNs);
        entity.setMemo(memo);
        entity.setTimestampLower(modifiedTimestamp);
        entity.setKey(adminKeyBytes);
        entity.setStakedNodeId(-1L);
        entity.setStakePeriodStart(-1L);
        entity.setType(entityType);

        if (entityType == EntityType.ACCOUNT) {
            entity.setEthereumNonce(0L);
        }

        return entity;
    }

    private byte[] rawBytesFromKey(Key key) {
        return key != null ? key.toByteArray() : null;
    }

    protected void assertTransactionInRepository(
            ResponseCodeEnum responseCode, long consensusTimestamp, Long entityId) {
        var transaction = transactionRepository.findById(consensusTimestamp).get();
        assertThat(transaction)
                .returns(responseCode.getNumber(), from(Transaction::getResult))
                .returns(TRANSACTION_MEMO.getBytes(), from(Transaction::getMemo));
        if (entityId != null) {
            assertThat(transaction).returns(entityId, t -> t.getEntityId().getId());
        }
    }

    protected void assertEntities(EntityId... entityIds) {
        assertThat(entityRepository.findAll())
                .allMatch(entity -> entity.getType() != null)
                .extracting(AbstractEntity::toEntityId)
                .containsExactlyInAnyOrder(entityIds);
    }

    protected void assertEntity(AbstractEntity expected) {
        AbstractEntity actual = getEntity(expected.toEntityId());
        assertThat(actual).isEqualTo(expected);
    }

    private RecordFile recordFile(long consensusStart, long consensusEnd, String filename) {
        return RecordFile.builder()
                .consensusStart(consensusStart)
                .consensusEnd(consensusEnd)
                .count(0L)
                .digestAlgorithm(DigestAlgorithm.SHA_384)
                .fileHash(UUID.randomUUID().toString())
                .hash(UUID.randomUUID().toString())
                .index(nextIndex++)
                .loadEnd(System.currentTimeMillis())
                .loadStart(System.currentTimeMillis())
                .name(filename)
                .previousHash("")
                .build();
    }

    @SuppressWarnings("deprecation")
    @SneakyThrows
    protected void buildContractFunctionResult(ContractFunctionResult.Builder builder) {
        builder.setAmount(10);
        builder.setBloom(ByteString.copyFromUtf8("bloom"));
        builder.setContractCallResult(ByteString.copyFromUtf8("call result"));
        builder.setContractID(CONTRACT_ID);
        builder.addCreatedContractIDs(CONTRACT_ID);
        builder.addCreatedContractIDs(CREATED_CONTRACT_ID);
        builder.setErrorMessage("call error message");
        builder.setEvmAddress(BytesValue.of(DomainUtils.fromBytes(domainBuilder.evmAddress())));
        builder.setFunctionParameters(ByteString.copyFromUtf8("function parameters"));
        builder.setGas(20);
        builder.setGasUsed(30);
        builder.addLogInfo(ContractLoginfo.newBuilder()
                .setBloom(ByteString.copyFromUtf8("bloom"))
                .setContractID(CONTRACT_ID)
                .setData(ByteString.copyFromUtf8("data"))
                .addTopic(ByteString.copyFromUtf8("Topic0"))
                .addTopic(ByteString.copyFromUtf8("Topic1"))
                .addTopic(ByteString.copyFromUtf8("Topic2"))
                .addTopic(ByteString.copyFromUtf8("Topic3"))
                .build());
        builder.addLogInfo(ContractLoginfo.newBuilder()
                .setBloom(ByteString.copyFromUtf8("bloom"))
                .setContractID(CREATED_CONTRACT_ID)
                .setData(ByteString.copyFromUtf8("data"))
                .addTopic(ByteString.copyFromUtf8("Topic0"))
                .addTopic(ByteString.copyFromUtf8("Topic1"))
                .addTopic(ByteString.copyFromUtf8("Topic2"))
                .addTopic(ByteString.copyFromUtf8("Topic3"))
                .build());
    }

    protected TransactionID transactionId(Entity payer, long validStartTimestamp) {
        return transactionId(payer.toEntityId(), validStartTimestamp);
    }

    protected TransactionID transactionId(EntityId payerAccountId, long validStartTimestamp) {
        var payer = AccountID.newBuilder()
                .setShardNum(payerAccountId.getShard())
                .setRealmNum(payerAccountId.getRealm())
                .setAccountNum(payerAccountId.getNum())
                .build();
        return TransactionID.newBuilder()
                .setAccountID(payer)
                .setTransactionValidStart(TestUtils.toTimestamp(validStartTimestamp))
                .build();
    }
}
