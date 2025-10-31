// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.domain;

import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.FIXED_FEE;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.hiero.mirror.common.domain.DomainBuilder.KEY_LENGTH_ECDSA;
import static org.hiero.mirror.common.domain.DomainBuilder.KEY_LENGTH_ED25519;
import static org.hiero.mirror.common.domain.transaction.RecordFile.HAPI_VERSION_0_49_0;
import static org.hiero.mirror.common.util.DomainUtils.TINYBARS_IN_ONE_HBAR;
import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;
import static org.hiero.mirror.common.util.DomainUtils.fromBytes;
import static org.hiero.mirror.common.util.DomainUtils.toBytes;
import static org.hiero.mirror.common.util.DomainUtils.trim;

import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
import com.hedera.hapi.node.hooks.legacy.EvmHookSpec;
import com.hedera.hapi.node.hooks.legacy.HookCreationDetails;
import com.hedera.hapi.node.hooks.legacy.HookExtensionPoint;
import com.hedera.hapi.node.hooks.legacy.LambdaEvmHook;
import com.hedera.hapi.node.hooks.legacy.LambdaSStoreTransactionBody;
import com.hedera.hapi.node.hooks.legacy.LambdaStorageSlot;
import com.hedera.hapi.node.hooks.legacy.LambdaStorageUpdate;
import com.hedera.services.stream.proto.CallOperationType;
import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.stream.proto.ContractActionType;
import com.hedera.services.stream.proto.ContractActions;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.StorageChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.AssessedCustomFee;
import com.hederahashgraph.api.proto.java.AtomicBatchTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.ContractNonceInfo;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAddLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.CustomFee.FeeCase;
import com.hederahashgraph.api.proto.java.CustomFeeLimit;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.FeeExemptKeyList;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FixedCustomFee;
import com.hederahashgraph.api.proto.java.FixedCustomFeeList;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.FreezeType;
import com.hederahashgraph.api.proto.java.HookEntityId;
import com.hederahashgraph.api.proto.java.HookId;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.LiveHash;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.NodeCreateTransactionBody;
import com.hederahashgraph.api.proto.java.NodeDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.NodeStake;
import com.hederahashgraph.api.proto.java.NodeStakeUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.NodeUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.PendingAirdropRecord;
import com.hederahashgraph.api.proto.java.PendingAirdropValue;
import com.hederahashgraph.api.proto.java.RealmID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import com.hederahashgraph.api.proto.java.ShardID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TokenAirdropTransactionBody;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenCancelAirdropTransactionBody;
import com.hederahashgraph.api.proto.java.TokenClaimAirdropTransactionBody;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenGrantKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenPauseTransactionBody;
import com.hederahashgraph.api.proto.java.TokenReference;
import com.hederahashgraph.api.proto.java.TokenRejectTransactionBody;
import com.hederahashgraph.api.proto.java.TokenRevokeKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TokenUnfreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUnpauseTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUpdateNftsTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.api.proto.java.UncheckedSubmitBody;
import com.hederahashgraph.api.proto.java.UtilPrngTransactionBody;
import jakarta.inject.Named;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.Value;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.aggregator.LogsBloomAggregator;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties.PersistProperties;
import org.hiero.mirror.importer.util.Utility;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.data.util.Version;

/**
 * Generates typical protobuf request and response objects with all fields populated.
 */
@Named
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RecordItemBuilder {

    public static final ByteString EVM_ADDRESS = ByteString.fromHex("ebb9a1be370150759408cd7af48e9eda2b8ead57");
    public static final byte[] LONDON_RAW_TX = Hex.decode(
            "02f87082012a022f2f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181880de0b6b3a764000083123456c001a0df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479a01aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66");
    public static final long DEFAULT_GAS_USED = 100000L;

    private static final long INITIAL_ID = 1000L;
    private static final RealmID REALM_ID = RealmID.getDefaultInstance();
    private static final ShardID SHARD_ID = ShardID.getDefaultInstance();

    private final AtomicBoolean autoCreation = new AtomicBoolean(false);
    private final Map<TransactionType, Supplier<Builder<?>>> builders = new EnumMap<>(TransactionType.class);
    private final AtomicLong entityId = new AtomicLong(INITIAL_ID);
    private final AtomicLong id = new AtomicLong(0L);
    private final SecureRandom random = new SecureRandom();
    private final Map<GeneratedMessage, EntityState> state = new ConcurrentHashMap<>();

    @Getter
    private final PersistProperties persistProperties =
            new PersistProperties(new SystemEntity(CommonProperties.getInstance()));

    private final CommonProperties commonProperties;
    private final SystemEntity systemEntity;

    @Setter
    private Instant now = Instant.now();

    @Autowired
    public RecordItemBuilder(CommonProperties commonProperties, SystemEntity systemEntity) {
        this.commonProperties = commonProperties;
        this.systemEntity = systemEntity;
        // Dynamically lookup method references for every transaction body builder in this class
        Collection<Supplier<Builder<?>>> suppliers = TestUtils.gettersByType(this, Builder.class);
        suppliers.forEach(s -> builders.put(s.get().type, s));
        reset();
    }

    // Intended for use only in unit tests
    public RecordItemBuilder() {
        this(CommonProperties.getInstance(), new SystemEntity(CommonProperties.getInstance()));
    }

    public Supplier<Builder<?>> lookup(TransactionType type) {
        return builders.get(type);
    }

    public Builder<ConsensusDeleteTopicTransactionBody.Builder> unknown() {
        var transactionBody = ConsensusDeleteTopicTransactionBody.newBuilder().setTopicID(topicId());
        return new Builder<>(TransactionType.UNKNOWN, transactionBody);
    }

    public Builder<AtomicBatchTransactionBody.Builder> atomicBatch() {
        var cryptoTransfer = cryptoTransfer().build().getTransaction();
        var contractCreate = contractCreate().build().getTransaction();
        var cryptoCreate = cryptoCreate().build().getTransaction();

        return atomicBatch(List.of(cryptoCreate, cryptoTransfer, contractCreate));
    }

    public Builder<AtomicBatchTransactionBody.Builder> atomicBatch(List<Transaction> transactions) {

        return new Builder<>(
                TransactionType.ATOMIC_BATCH,
                AtomicBatchTransactionBody.newBuilder()
                        .addAllTransactions(transactions.stream()
                                .map(Transaction::toByteString)
                                .toList()));
    }

    public Builder<ConsensusCreateTopicTransactionBody.Builder> consensusCreateTopic() {
        var transactionBody = ConsensusCreateTopicTransactionBody.newBuilder()
                .addAllFeeExemptKeyList(List.of(key(), key()))
                .addAllCustomFees(fixedCustomFees())
                .setAdminKey(key())
                .setAutoRenewAccount(accountId())
                .setAutoRenewPeriod(duration(3600))
                .setFeeScheduleKey(key())
                .setMemo(text(16))
                .setSubmitKey(key());
        return new Builder<>(TransactionType.CONSENSUSCREATETOPIC, transactionBody)
                .receipt(r -> r.setTopicID(topicId()));
    }

    public Builder<ConsensusDeleteTopicTransactionBody.Builder> consensusDeleteTopic() {
        var transactionBody = ConsensusDeleteTopicTransactionBody.newBuilder().setTopicID(topicId());
        return new Builder<>(TransactionType.CONSENSUSDELETETOPIC, transactionBody);
    }

    public Builder<ConsensusSubmitMessageTransactionBody.Builder> consensusSubmitMessage() {
        var topicId = topicId();
        var transactionBody = ConsensusSubmitMessageTransactionBody.newBuilder()
                .setMessage(bytes(128))
                .setTopicID(topicId);

        var builder = new Builder<>(TransactionType.CONSENSUSSUBMITMESSAGE, transactionBody)
                .incrementer((b, r) -> {
                    var receipt = r.getReceiptBuilder();
                    if (receipt.getStatus() != ResponseCodeEnum.SUCCESS) {
                        return;
                    }
                    receipt.setTopicSequenceNumber(state.get(topicId).getSequenceNumber());
                })
                .receipt(r -> r.setTopicRunningHash(bytes(48)).setTopicRunningHashVersion(3));

        var maxCustomFees = List.of(
                CustomFeeLimit.newBuilder()
                        .addFees(FixedFee.newBuilder().setAmount(id()))
                        .setAccountId(accountId())
                        .build(),
                CustomFeeLimit.newBuilder()
                        .addFees(FixedFee.newBuilder().setAmount(id()).setDenominatingTokenId(tokenId()))
                        .setAccountId(accountId())
                        .build());
        builder.transactionBodyWrapper.addAllMaxCustomFees(maxCustomFees);

        transactionBody.setChunkInfo(ConsensusMessageChunkInfo.newBuilder()
                .setInitialTransactionID(builder.transactionBodyWrapper.getTransactionID())
                .setNumber(1)
                .setTotal(1));

        return builder;
    }

    public Builder<ConsensusUpdateTopicTransactionBody.Builder> consensusUpdateTopic() {
        var transactionBody = ConsensusUpdateTopicTransactionBody.newBuilder()
                .setAdminKey(key())
                .setAutoRenewAccount(accountId())
                .setAutoRenewPeriod(duration(3600))
                .setCustomFees(FixedCustomFeeList.newBuilder().addAllFees(fixedCustomFees()))
                .setFeeExemptKeyList(FeeExemptKeyList.newBuilder().addAllKeys(List.of(key(), key())))
                .setFeeScheduleKey(key())
                .setExpirationTime(timestamp())
                .setMemo(StringValue.of(text(16)))
                .setSubmitKey(key())
                .setTopicID(topicId());
        return new Builder<>(TransactionType.CONSENSUSUPDATETOPIC, transactionBody);
    }

    public Builder<ContractCallTransactionBody.Builder> contractCall() {
        return contractCall(contractId());
    }

    @SuppressWarnings("deprecation")
    public Builder<ContractCallTransactionBody.Builder> contractCall(ContractID contractId) {
        ContractCallTransactionBody.Builder transactionBody = ContractCallTransactionBody.newBuilder()
                .setAmount(5_000L)
                .setContractID(contractId)
                .setFunctionParameters(nonZeroBytes(64))
                .setGas(10_000L);

        return new Builder<>(TransactionType.CONTRACTCALL, transactionBody)
                .receipt(r -> r.setContractID(contractId))
                .record(r -> r.setContractCallResult(
                        contractFunctionResult(contractId).clearCreatedContractIDs()))
                .sidecarRecords(r -> r.add(contractStateChanges(contractId)))
                .sidecarRecords(r -> r.add(contractActions()));
    }

    public Builder<ContractCreateTransactionBody.Builder> contractCreate() {
        return contractCreate(contractId());
    }

    @SuppressWarnings("deprecation")
    public Builder<ContractCreateTransactionBody.Builder> contractCreate(ContractID contractId) {
        ContractCreateTransactionBody.Builder transactionBody = ContractCreateTransactionBody.newBuilder()
                .setAdminKey(key())
                .setAutoRenewAccountId(accountId())
                .setAutoRenewPeriod(duration(30))
                .setConstructorParameters(bytes(64))
                .setDeclineReward(true)
                .setFileID(fileId())
                .setGas(10_000L)
                .setInitialBalance(10_000_000_000_000L)
                .setMaxAutomaticTokenAssociations(5)
                .setMemo(text(16))
                .setNewRealmAdminKey(key())
                .setProxyAccountID(accountId())
                .setRealmID(REALM_ID)
                .setShardID(SHARD_ID)
                .setStakedNodeId(1L);

        return new Builder<>(TransactionType.CONTRACTCREATEINSTANCE, transactionBody)
                .receipt(r -> r.setContractID(contractId))
                .record(r -> r.setContractCreateResult(
                        contractFunctionResult(contractId).addCreatedContractIDs(contractId)))
                .sidecarRecords(r -> r.add(contractStateChanges(contractId)))
                .sidecarRecords(r -> {
                    var contractActions = contractActions();
                    contractActions
                            .getActionsBuilder()
                            .getContractActionsBuilderList()
                            .forEach(ContractAction.Builder::clearRecipient);
                    r.add(contractActions);
                })
                .sidecarRecords(r -> r.add(contractBytecode(contractId)));
    }

    public Builder<ContractDeleteTransactionBody.Builder> contractDelete() {
        var contractId = contractId();
        ContractDeleteTransactionBody.Builder transactionBody = ContractDeleteTransactionBody.newBuilder()
                .setContractID(contractId)
                .setTransferAccountID(accountId());

        return new Builder<>(TransactionType.CONTRACTDELETEINSTANCE, transactionBody)
                .receipt(r -> r.setContractID(contractId));
    }

    public ContractFunctionResult.Builder contractFunctionResult() {
        return contractFunctionResult(contractId());
    }

    @SuppressWarnings("deprecation")
    public ContractFunctionResult.Builder contractFunctionResult(ContractID contractId) {
        var logsBloomAggregator = new LogsBloomAggregator();
        var contractLogInfos = List.of(contractLoginfo(contractId), contractLoginfo(contractId()));
        contractLogInfos.forEach(loginfo -> logsBloomAggregator.aggregate(DomainUtils.toBytes(loginfo.getBloom())));

        return ContractFunctionResult.newBuilder()
                .setAmount(5_000L)
                .setBloom(DomainUtils.fromBytes(logsBloomAggregator.getBloom()))
                .setContractCallResult(bytes(16))
                .setContractID(contractId)
                .addContractNonces(ContractNonceInfo.newBuilder()
                        .setContractId(contractId)
                        .setNonce(1)
                        .build())
                .addCreatedContractIDs(contractId())
                .setErrorMessage(text(10))
                .setFunctionParameters(bytes(64))
                .setGas(10_000L)
                .setGasUsed(DEFAULT_GAS_USED)
                .addAllLogInfo(contractLogInfos)
                .setSenderId(accountId())
                .setSignerNonce(Int64Value.of(10));
    }

    @SuppressWarnings("deprecation")
    public Builder<ContractUpdateTransactionBody.Builder> contractUpdate() {
        var contractId = contractId();
        ContractUpdateTransactionBody.Builder transactionBody = ContractUpdateTransactionBody.newBuilder()
                .setAdminKey(key())
                .setAutoRenewAccountId(accountId())
                .setAutoRenewPeriod(duration(30))
                .setContractID(contractId)
                .setDeclineReward(BoolValue.of(true))
                .setExpirationTime(timestamp())
                .setMaxAutomaticTokenAssociations(Int32Value.of(10))
                .setMemoWrapper(StringValue.of(text(16)))
                .setProxyAccountID(accountId())
                .setStakedAccountId(accountId());

        return new Builder<>(TransactionType.CONTRACTUPDATEINSTANCE, transactionBody)
                .receipt(r -> r.setContractID(contractId));
    }

    public Builder<CryptoAddLiveHashTransactionBody.Builder> cryptoAddLiveHash() {
        var builder = CryptoAddLiveHashTransactionBody.newBuilder()
                .setLiveHash(LiveHash.newBuilder()
                        .setAccountId(accountId())
                        .setDuration(duration(900))
                        .setHash(bytes(48))
                        .setKeys(KeyList.newBuilder().addKeys(key())));
        return new Builder<>(TransactionType.CRYPTOADDLIVEHASH, builder);
    }

    public Builder<CryptoDeleteLiveHashTransactionBody.Builder> cryptoDeleteLiveHash() {
        var builder = CryptoDeleteLiveHashTransactionBody.newBuilder()
                .setLiveHashToDelete(LiveHash.newBuilder()
                        .setAccountId(accountId())
                        .setDuration(duration(900))
                        .setHash(bytes(48))
                        .setKeys(KeyList.newBuilder().addKeys(key()))
                        .build()
                        .toByteString());
        return new Builder<>(TransactionType.CRYPTODELETELIVEHASH, builder);
    }

    public Builder<CryptoApproveAllowanceTransactionBody.Builder> cryptoApproveAllowance() {
        var builder = CryptoApproveAllowanceTransactionBody.newBuilder()
                .addCryptoAllowances(CryptoAllowance.newBuilder()
                        .setAmount(10L)
                        .setOwner(accountId())
                        .setSpender(accountId()))
                .addNftAllowances(NftAllowance.newBuilder()
                        .setOwner(accountId())
                        .addSerialNumbers(1L)
                        .addSerialNumbers(2L)
                        .setSpender(accountId())
                        .setTokenId(tokenId()))
                .addNftAllowances(NftAllowance.newBuilder()
                        .setApprovedForAll(BoolValue.of(false))
                        .setOwner(accountId())
                        .setSpender(accountId())
                        .setTokenId(tokenId()))
                .addNftAllowances(NftAllowance.newBuilder()
                        .setApprovedForAll(BoolValue.of(true))
                        .setOwner(accountId())
                        .setSpender(accountId())
                        .setTokenId(tokenId()))
                .addNftAllowances(NftAllowance.newBuilder()
                        .setApprovedForAll(BoolValue.of(true))
                        .setOwner(accountId())
                        .addSerialNumbers(2L)
                        .addSerialNumbers(3L)
                        .setSpender(accountId())
                        .setTokenId(tokenId()))
                .addTokenAllowances(TokenAllowance.newBuilder()
                        .setAmount(10L)
                        .setOwner(accountId())
                        .setSpender(accountId())
                        .setTokenId(tokenId()));
        // duplicate allowances
        builder.addCryptoAllowances(builder.getCryptoAllowances(0))
                .addTokenAllowances(builder.getTokenAllowances(0))
                .addNftAllowances(builder.getNftAllowances(0))
                .addNftAllowances(builder.getNftAllowances(2).toBuilder().setApprovedForAll(BoolValue.of(false)));
        return new Builder<>(TransactionType.CRYPTOAPPROVEALLOWANCE, builder);
    }

    @SuppressWarnings("deprecation")
    public Builder<CryptoCreateTransactionBody.Builder> cryptoCreate() {
        var builder = CryptoCreateTransactionBody.newBuilder()
                .setAlias(bytes(20))
                .setAutoRenewPeriod(duration(30))
                .setDeclineReward(true)
                .setInitialBalance(10_000_000_000_000L)
                .setKey(key())
                .setMaxAutomaticTokenAssociations(2)
                .setMemo(text(16))
                .setProxyAccountID(accountId())
                .setRealmID(REALM_ID)
                .setReceiverSigRequired(false)
                .setShardID(SHARD_ID)
                .addHookCreationDetails(hookCreationDetails())
                .setStakedNodeId(1L);
        return new Builder<>(TransactionType.CRYPTOCREATEACCOUNT, builder).receipt(r -> r.setAccountID(accountId()));
    }

    private HookCreationDetails.Builder hookCreationDetails() {
        return HookCreationDetails.newBuilder()
                .setExtensionPoint(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK)
                .setHookId(1L)
                .setLambdaEvmHook(LambdaEvmHook.newBuilder()
                        .setSpec(EvmHookSpec.newBuilder().setContractId(contractId()))
                        .build())
                .setAdminKey(key());
    }

    public Builder<CryptoDeleteTransactionBody.Builder> cryptoDelete() {
        var builder = CryptoDeleteTransactionBody.newBuilder()
                .setDeleteAccountID(accountId())
                .setTransferAccountID(accountId());
        return new Builder<>(TransactionType.CRYPTODELETE, builder);
    }

    public Builder<CryptoDeleteAllowanceTransactionBody.Builder> cryptoDeleteAllowance() {
        var builder = CryptoDeleteAllowanceTransactionBody.newBuilder()
                .addNftAllowances(NftRemoveAllowance.newBuilder()
                        .setOwner(accountId())
                        .addSerialNumbers(1L)
                        .addSerialNumbers(2L)
                        .setTokenId(tokenId()))
                .addNftAllowances(NftRemoveAllowance.newBuilder()
                        .setOwner(accountId())
                        .addSerialNumbers(2L)
                        .addSerialNumbers(3L)
                        .setTokenId(tokenId()));
        return new Builder<>(TransactionType.CRYPTODELETEALLOWANCE, builder);
    }

    public Builder<CryptoTransferTransactionBody.Builder> cryptoTransfer() {
        return cryptoTransfer(TransferType.CRYPTO);
    }

    public Builder<CryptoTransferTransactionBody.Builder> cryptoTransfer(TransferType transferType) {
        var body = CryptoTransferTransactionBody.newBuilder();
        var builder = new Builder<>(TransactionType.CRYPTOTRANSFER, body);

        if (transferType == TransferType.ALL || transferType == TransferType.CRYPTO) {
            var transfers = TransferList.newBuilder()
                    .addAccountAmounts(accountAmount(accountId(), -100))
                    .addAccountAmounts(accountAmount(accountId(), 100));
            body.setTransfers(transfers);
            builder.record(r -> r.mergeTransferList(transfers.build()));
        }

        if (transferType == TransferType.ALL || transferType == TransferType.TOKEN) {
            var receiver = accountId();
            var sender = accountId();
            var tokenId = tokenId();
            var tokenTransfers = TokenTransferList.newBuilder()
                    .setToken(tokenId)
                    .addTransfers(accountAmount(sender, -100))
                    .addTransfers(accountAmount(receiver, 100));
            body.addTokenTransfers(tokenTransfers);
            builder.record(r -> r.addTokenTransferLists(tokenTransfers));
            updateState(TokenAssociation.newBuilder()
                    .setAccountId(sender)
                    .setTokenId(tokenId)
                    .build());
            updateState(TokenAssociation.newBuilder()
                    .setAccountId(receiver)
                    .setTokenId(tokenId)
                    .build());
        }

        if (transferType == TransferType.ALL || transferType == TransferType.NFT) {
            var receiver = accountId();
            var sender = accountId();
            var tokenId = tokenId();
            var nftTransfers = TokenTransferList.newBuilder()
                    .setToken(tokenId)
                    .addNftTransfers(NftTransfer.newBuilder()
                            .setSenderAccountID(sender)
                            .setReceiverAccountID(receiver)
                            .setSerialNumber(1));
            body.addTokenTransfers(nftTransfers);
            builder.record(r -> r.addTokenTransferLists(nftTransfers));
            updateState(TokenAssociation.newBuilder()
                    .setAccountId(sender)
                    .setTokenId(tokenId)
                    .build());
            updateState(TokenAssociation.newBuilder()
                    .setAccountId(receiver)
                    .setTokenId(tokenId)
                    .build());
        }

        if (transferType == TransferType.ALL
                || transferType == TransferType.NFT
                || transferType == TransferType.TOKEN) {
            builder.record(r -> r.addAssessedCustomFees(assessedCustomFee()));
        }

        return builder;
    }

    @SuppressWarnings("deprecation")
    public Builder<CryptoUpdateTransactionBody.Builder> cryptoUpdate() {
        var accountId = accountId();
        var builder = CryptoUpdateTransactionBody.newBuilder()
                .setAutoRenewPeriod(duration(30))
                .setAccountIDToUpdate(accountId)
                .setDeclineReward(BoolValue.of(true))
                .setKey(key())
                .setProxyAccountID(accountId())
                .setReceiverSigRequired(false)
                .setStakedNodeId(1L)
                .addHookCreationDetails(hookCreationDetails())
                .addHookIdsToDelete(1L);
        return new Builder<>(TransactionType.CRYPTOUPDATEACCOUNT, builder);
    }

    public CustomFee.Builder customFee(CustomFee.FeeCase feeCase) {
        var accountId = accountId();
        var customFee =
                CustomFee.newBuilder().setFeeCollectorAccountId(accountId).setAllCollectorsAreExempt(false);

        if (Objects.requireNonNull(feeCase) == FIXED_FEE) {
            customFee.setFixedFee(fixedFee());
        } else if (feeCase == FeeCase.ROYALTY_FEE) {
            customFee.setRoyaltyFee(royaltyFee());
        } else if (feeCase == FeeCase.FRACTIONAL_FEE) {
            customFee.setFractionalFee(fractionalFee());
        }

        return customFee;
    }

    public AssessedCustomFee assessedCustomFee() {
        return AssessedCustomFee.newBuilder()
                .setAmount(id())
                .addEffectivePayerAccountId(accountId())
                .setFeeCollectorAccountId(accountId())
                .setTokenId(tokenId())
                .build();
    }

    private List<FixedCustomFee> fixedCustomFees() {
        return List.of(
                FixedCustomFee.newBuilder()
                        .setFeeCollectorAccountId(accountId())
                        .setFixedFee(FixedFee.newBuilder().setAmount(id()))
                        .build(),
                FixedCustomFee.newBuilder()
                        .setFeeCollectorAccountId(accountId())
                        .setFixedFee(FixedFee.newBuilder().setAmount(id()).setDenominatingTokenId(tokenId()))
                        .build());
    }

    private FixedFee.Builder fixedFee() {
        return FixedFee.newBuilder().setAmount(100L).setDenominatingTokenId(tokenId());
    }

    private FractionalFee.Builder fractionalFee() {
        return FractionalFee.newBuilder()
                .setFractionalAmount(Fraction.newBuilder().setNumerator(1).setDenominator(10))
                .setMaximumAmount(1000L)
                .setMinimumAmount(1L)
                .setNetOfTransfers(false);
    }

    public RoyaltyFee.Builder royaltyFee() {
        return RoyaltyFee.newBuilder()
                .setExchangeValueFraction(Fraction.newBuilder().setNumerator(50).setDenominator(100))
                .setFallbackFee(fixedFee());
    }

    public Builder<EthereumTransactionBody.Builder> ethereumTransaction() {
        return ethereumTransaction(false);
    }

    @SneakyThrows
    public Builder<EthereumTransactionBody.Builder> ethereumTransaction(boolean create) {
        EthereumTransactionBody.Builder transactionBody = EthereumTransactionBody.newBuilder()
                .setEthereumData(ByteString.copyFrom(LONDON_RAW_TX))
                .setMaxGasAllowance(10_000L);

        var contractId = contractId();
        var digestedHash = bytes(32);
        var functionResult = contractFunctionResult(contractId);
        var builder = new Builder<>(TransactionType.ETHEREUMTRANSACTION, transactionBody)
                .record(r -> r.setContractCallResult(functionResult)
                        .setEthereumHash(digestedHash)
                        .getReceiptBuilder()
                        .setContractID(contractId))
                .recordItem(r -> r.hapiVersion(new Version(0, 47, 0)))
                .sidecarRecords(r -> r.add(contractStateChanges(contractId)))
                .sidecarRecords(r -> r.add(contractActions()));

        if (create) {
            transactionBody.setCallData(fileId());
            builder.sidecarRecords
                    .get(1)
                    .getActionsBuilder()
                    .getContractActionsBuilder(0)
                    .setCallType(ContractActionType.CREATE);
            builder.record(r -> r.setContractCreateResult(functionResult));
        }

        return builder;
    }

    public Builder<FileAppendTransactionBody.Builder> fileAppend() {
        var builder =
                FileAppendTransactionBody.newBuilder().setContents(bytes(100)).setFileID(fileId());
        return new Builder<>(TransactionType.FILEAPPEND, builder);
    }

    public Builder<FileCreateTransactionBody.Builder> fileCreate() {
        var builder = FileCreateTransactionBody.newBuilder()
                .setContents(bytes(100))
                .setExpirationTime(timestamp())
                .setKeys(KeyList.newBuilder().addKeys(key()))
                .setRealmID(REALM_ID)
                .setShardID(SHARD_ID)
                .setMemo(text(10));
        return new Builder<>(TransactionType.FILECREATE, builder).receipt(b -> b.setFileID(fileId()));
    }

    public Builder<FileDeleteTransactionBody.Builder> fileDelete() {
        var fileId = fileId();
        var builder = FileDeleteTransactionBody.newBuilder().setFileID(fileId);
        return new Builder<>(TransactionType.FILEDELETE, builder);
    }

    public Builder<FileUpdateTransactionBody.Builder> fileUpdate() {
        var builder = FileUpdateTransactionBody.newBuilder()
                .setContents(bytes(100))
                .setExpirationTime(timestamp())
                .setFileID(fileId())
                .setKeys(KeyList.newBuilder().addKeys(key()))
                .setMemo(StringValue.newBuilder().setValue(text(10)));
        return new Builder<>(TransactionType.FILEUPDATE, builder);
    }

    public Builder<FreezeTransactionBody.Builder> freeze() {
        var builder = FreezeTransactionBody.newBuilder()
                .setFileHash(bytes(48))
                .setFreezeType(FreezeType.PREPARE_UPGRADE)
                .setStartTime(timestamp())
                .setUpdateFile(fileId());
        return new Builder<>(TransactionType.FREEZE, builder);
    }

    public Builder<NodeCreateTransactionBody.Builder> nodeCreate() {
        var builder = NodeCreateTransactionBody.newBuilder()
                .setAccountId(accountId())
                .setAdminKey(key())
                .setDeclineReward(false)
                .setDescription("Node create")
                .setGossipCaCertificate(bytes(4))
                .setGrpcProxyEndpoint(serviceEndpoint().toBuilder().setPort(80))
                .addGossipEndpoint(gossipEndpoint())
                .setGrpcCertificateHash(bytes(48))
                .addServiceEndpoint(serviceEndpoint());
        return new Builder<>(TransactionType.NODECREATE, builder).receipt(r -> r.setNodeId(id()));
    }

    public Builder<NodeUpdateTransactionBody.Builder> nodeUpdate() {
        var builder = NodeUpdateTransactionBody.newBuilder()
                .setAccountId(accountId())
                .setAdminKey(key())
                .setDeclineReward(BoolValue.of(false))
                .setDescription(StringValue.of("Node update"))
                .setGossipCaCertificate(BytesValue.of(bytes(4)))
                .setGrpcProxyEndpoint(serviceEndpoint().toBuilder().setPort(80))
                .addGossipEndpoint(gossipEndpoint())
                .setGrpcCertificateHash(BytesValue.of(bytes(48)))
                .setNodeId(id())
                .addServiceEndpoint(serviceEndpoint());
        return new Builder<>(TransactionType.NODEUPDATE, builder);
    }

    public Builder<NodeDeleteTransactionBody.Builder> nodeDelete() {
        var builder = NodeDeleteTransactionBody.newBuilder().setNodeId(id());
        return new Builder<>(TransactionType.NODEDELETE, builder);
    }

    @SuppressWarnings("deprecation")
    public Builder<NodeStakeUpdateTransactionBody.Builder> nodeStakeUpdate() {
        var builder = NodeStakeUpdateTransactionBody.newBuilder()
                .setEndOfStakingPeriod(timestamp())
                .setMaxStakeRewarded(10L)
                .setMaxStakingRewardRatePerHbar(17_808L)
                .setMaxTotalReward(20L)
                .setNodeRewardFeeFraction(Fraction.newBuilder().setNumerator(0L).setDenominator(100L))
                .setReservedStakingRewards(30L)
                .setRewardBalanceThreshold(40L)
                .setStakingPeriod(1440)
                .setStakingPeriodsStored(365)
                .setStakingRewardFeeFraction(
                        Fraction.newBuilder().setNumerator(100L).setDenominator(100L))
                .setStakingRewardRate(100_000_000_000L)
                .setStakingStartThreshold(25_000_000_000_000_000L)
                .setUnreservedStakingRewardBalance(50L)
                .addNodeStake(nodeStake());
        return new Builder<>(TransactionType.NODESTAKEUPDATE, builder);
    }

    public PendingAirdropId.Builder pendingAirdropId() {
        return PendingAirdropId.newBuilder()
                .setReceiverId(accountId())
                .setSenderId(accountId())
                .setFungibleTokenType(tokenId());
    }

    public Builder<UtilPrngTransactionBody.Builder> prng() {
        return prng(0);
    }

    public Builder<UtilPrngTransactionBody.Builder> prng(int range) {
        var builder = UtilPrngTransactionBody.newBuilder().setRange(range);
        var transactionBodyBuilder = new Builder<>(TransactionType.UTILPRNG, builder);

        return transactionBodyBuilder.record(r -> {
            if (range == 0) {
                r.setPrngBytes(ByteString.copyFrom(randomBytes(382)));
            } else if (range > 0) {
                r.setPrngNumber(random.nextInt());
            }
        });
    }

    public void reset() {
        entityId.set(INITIAL_ID);
        id.set(0L);
        now = Instant.now();
        state.clear();
    }

    public Builder<ScheduleCreateTransactionBody.Builder> scheduleCreate() {
        var cryptoTransfer = cryptoTransfer().build().getTransactionBody().getCryptoTransfer();
        var scheduledTransaction = SchedulableTransactionBody.newBuilder()
                .setTransactionFee(1_00_000_000)
                .setMemo(text(16))
                .setCryptoTransfer(cryptoTransfer);
        var builder = ScheduleCreateTransactionBody.newBuilder()
                .setScheduledTransactionBody(scheduledTransaction)
                .setMemo(text(16))
                .setAdminKey(key())
                .setPayerAccountID(accountId())
                .setExpirationTime(timestamp())
                .setWaitForExpiry(true);
        return new Builder<>(TransactionType.SCHEDULECREATE, builder).receipt(r -> r.setScheduleID(scheduleId()));
    }

    public Builder<ScheduleDeleteTransactionBody.Builder> scheduleDelete() {
        var builder = ScheduleDeleteTransactionBody.newBuilder().setScheduleID(scheduleId());
        return new Builder<>(TransactionType.SCHEDULEDELETE, builder);
    }

    public Builder<ScheduleSignTransactionBody.Builder> scheduleSign() {
        var builder = ScheduleSignTransactionBody.newBuilder().setScheduleID(scheduleId());
        return new Builder<>(TransactionType.SCHEDULESIGN, builder);
    }

    public Builder<SystemDeleteTransactionBody.Builder> systemDelete() {
        var builder = SystemDeleteTransactionBody.newBuilder()
                .setFileID(fileId())
                .setExpirationTime(
                        TimestampSeconds.newBuilder().setSeconds(Instant.now().getEpochSecond() + id()));
        return new Builder<>(TransactionType.SYSTEMDELETE, builder);
    }

    public Builder<SystemUndeleteTransactionBody.Builder> systemUndelete() {
        var builder = SystemUndeleteTransactionBody.newBuilder().setFileID(fileId());
        return new Builder<>(TransactionType.SYSTEMUNDELETE, builder);
    }

    public Builder<TokenAirdropTransactionBody.Builder> tokenAirdrop() {
        var fungibleTokenId = tokenId();
        var nftTokenId = tokenId();
        var sender = accountId();
        var receiver = accountId();
        var pendingReceiver = accountId();

        // Airdrops that transfer to the account and do not go into the pending airdrop list
        var tokenTransferList = TokenTransferList.newBuilder()
                .setToken(fungibleTokenId)
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(sender)
                        .setAmount(-100)
                        .build())
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(receiver)
                        .setAmount(100)
                        .build());
        var nftTransferList = TokenTransferList.newBuilder()
                .setToken(nftTokenId)
                .addNftTransfers(NftTransfer.newBuilder()
                        .setSenderAccountID(sender)
                        .setSerialNumber(1L)
                        .setReceiverAccountID(receiver)
                        .build());

        var fungiblePendingAirdropId = PendingAirdropId.newBuilder()
                .setSenderId(sender)
                .setReceiverId(pendingReceiver)
                .setFungibleTokenType(fungibleTokenId);
        var fungiblePendingAirdrop = PendingAirdropRecord.newBuilder()
                .setPendingAirdropId(fungiblePendingAirdropId)
                .setPendingAirdropValue(
                        PendingAirdropValue.newBuilder().setAmount(1000L).build());
        var nftPendingAirdropId = PendingAirdropId.newBuilder()
                .setSenderId(sender)
                .setReceiverId(pendingReceiver)
                .setNonFungibleToken(NftID.newBuilder()
                        .setTokenID(nftTokenId)
                        .setSerialNumber(1L)
                        .build());
        var nftPendingAirdrop = PendingAirdropRecord.newBuilder().setPendingAirdropId(nftPendingAirdropId);

        var body = TokenAirdropTransactionBody.newBuilder();
        var builder = new Builder<>(TransactionType.TOKENAIRDROP, body);
        var tokenTransfers = TokenTransferList.newBuilder()
                .setToken(fungibleTokenId)
                .addTransfers(accountAmount(sender, -100))
                .addTransfers(accountAmount(pendingReceiver, 100));
        body.addTokenTransfers(tokenTransfers);

        var nftTransfers = TokenTransferList.newBuilder()
                .setToken(nftTokenId)
                .addNftTransfers(NftTransfer.newBuilder()
                        .setSenderAccountID(sender)
                        .setReceiverAccountID(pendingReceiver)
                        .setSerialNumber(1));
        body.addTokenTransfers(nftTransfers);

        return builder.record(r -> r.addTokenTransferLists(tokenTransferList)
                .addTokenTransferLists(nftTransferList)
                .addNewPendingAirdrops(fungiblePendingAirdrop)
                .addNewPendingAirdrops(nftPendingAirdrop));
    }

    public Builder<TokenCancelAirdropTransactionBody.Builder> tokenCancelAirdrop() {
        var transactionBody = TokenCancelAirdropTransactionBody.newBuilder().addPendingAirdrops(pendingAirdropId());
        return new Builder<>(TransactionType.TOKENCANCELAIRDROP, transactionBody);
    }

    public Builder<TokenClaimAirdropTransactionBody.Builder> tokenClaimAirdrop() {
        var transactionBody = TokenClaimAirdropTransactionBody.newBuilder().addPendingAirdrops(pendingAirdropId());
        return new Builder<>(TransactionType.TOKENCLAIMAIRDROP, transactionBody);
    }

    public Builder<TokenAssociateTransactionBody.Builder> tokenAssociate() {
        var transactionBody = TokenAssociateTransactionBody.newBuilder()
                .setAccount(accountId())
                .addTokens(tokenId());

        return new Builder<>(TransactionType.TOKENASSOCIATE, transactionBody);
    }

    public Builder<TokenBurnTransactionBody.Builder> tokenBurn() {
        var transactionBody = TokenBurnTransactionBody.newBuilder()
                .setAmount(1L)
                .setToken(tokenId())
                .addSerialNumbers(1L);
        return new Builder<>(TransactionType.TOKENBURN, transactionBody).receipt(b -> b.setNewTotalSupply(2L));
    }

    public Builder<TokenDissociateTransactionBody.Builder> tokenDissociate() {
        var transactionBody = TokenDissociateTransactionBody.newBuilder()
                .setAccount(accountId())
                .addTokens(tokenId());
        return new Builder<>(TransactionType.TOKENDISSOCIATE, transactionBody);
    }

    public Builder<TokenFreezeAccountTransactionBody.Builder> tokenFreeze() {
        var transactionBody = TokenFreezeAccountTransactionBody.newBuilder()
                .setAccount(accountId())
                .setToken(tokenId());
        return new Builder<>(TransactionType.TOKENFREEZE, transactionBody);
    }

    public Builder<TokenGrantKycTransactionBody.Builder> tokenGrantKyc() {
        var transactionBody = TokenGrantKycTransactionBody.newBuilder()
                .setAccount(accountId())
                .setToken(tokenId());
        return new Builder<>(TransactionType.TOKENGRANTKYC, transactionBody);
    }

    public Builder<TokenMintTransactionBody.Builder> tokenMint() {
        return tokenMint(NON_FUNGIBLE_UNIQUE);
    }

    public Builder<TokenMintTransactionBody.Builder> tokenMint(TokenType tokenType) {
        var tokenId = tokenId();
        var tokenTransferList = TokenTransferList.newBuilder().setToken(tokenId);
        var treasury = accountId();
        var transactionBody = TokenMintTransactionBody.newBuilder().setToken(tokenId);
        var builder = new Builder<>(TransactionType.TOKENMINT, transactionBody);

        if (tokenType == FUNGIBLE_COMMON) {
            transactionBody.setAmount(1000L);
            builder.record(r -> r.addTokenTransferLists(tokenTransferList.addTransfers(
                            AccountAmount.newBuilder().setAccountID(treasury).setAmount(1000L))))
                    .receipt(b -> b.setNewTotalSupply(2000L));
        } else {
            // randomly reverse the serial numbers
            var serials = List.of(2L, 3L);
            if (id() % 2 == 0) {
                serials = serials.reversed();
            }
            var nftTransfers = serials.stream()
                    .map(serial -> NftTransfer.newBuilder()
                            .setReceiverAccountID(treasury)
                            .setSerialNumber(serial)
                            .build())
                    .toList();
            transactionBody.addMetadata(bytes(16)).addMetadata(bytes(16));
            builder.record(r -> r.addTokenTransferLists(tokenTransferList.addAllNftTransfers(nftTransfers)));
            builder.receipt(b -> b.addSerialNumbers(2L).addSerialNumbers(3L).setNewTotalSupply(3L));
        }

        return builder;
    }

    public Builder<TokenUpdateNftsTransactionBody.Builder> tokenUpdateNfts() {
        var transactionBody = TokenUpdateNftsTransactionBody.newBuilder()
                .addSerialNumbers(1L)
                .addSerialNumbers(2L)
                .setMetadata(BytesValue.of(bytes(16)))
                .setToken(tokenId());
        return new Builder<>(TransactionType.TOKENUPDATENFTS, transactionBody);
    }

    public Builder<TokenPauseTransactionBody.Builder> tokenPause() {
        var transactionBody = TokenPauseTransactionBody.newBuilder().setToken(tokenId());
        return new Builder<>(TransactionType.TOKENPAUSE, transactionBody);
    }

    public TokenReference.Builder tokenReference(TokenTypeEnum tokenType) {
        var builder = TokenReference.newBuilder();
        if (tokenType == TokenTypeEnum.FUNGIBLE_COMMON) {
            builder.setFungibleToken(tokenId());
        } else {
            var nftId = NftID.newBuilder()
                    .setTokenID(tokenId())
                    .setSerialNumber(id())
                    .build();
            builder.setNft(nftId);
        }

        return builder;
    }

    public Builder<TokenRejectTransactionBody.Builder> tokenReject() {
        var owner = accountId();
        var fungibleTokenReference = tokenReference(TokenTypeEnum.FUNGIBLE_COMMON);
        var nftTokenReference = tokenReference(TokenTypeEnum.NON_FUNGIBLE_UNIQUE);
        var transactionBody = TokenRejectTransactionBody.newBuilder()
                .setOwner(owner)
                .addRejections(fungibleTokenReference)
                .addRejections(nftTokenReference);
        var transferList = TokenTransferList.newBuilder()
                .setToken(fungibleTokenReference.getFungibleToken())
                .addTransfers(AccountAmount.newBuilder().setAccountID(owner).setAmount(-100))
                .addTransfers(
                        AccountAmount.newBuilder().setAccountID(accountId()).setAmount(100));
        var nftTransferList = TokenTransferList.newBuilder()
                .setToken(nftTokenReference.getNft().getTokenID())
                .addNftTransfers(NftTransfer.newBuilder()
                        .setSenderAccountID(owner)
                        .setSerialNumber(nftTokenReference.getNft().getSerialNumber())
                        .setReceiverAccountID(accountId()));

        return new Builder<>(TransactionType.TOKENREJECT, transactionBody)
                .record(r -> r.addTokenTransferLists(transferList).addTokenTransferLists(nftTransferList));
    }

    public Builder<TokenRevokeKycTransactionBody.Builder> tokenRevokeKyc() {
        var transactionBody = TokenRevokeKycTransactionBody.newBuilder()
                .setAccount(accountId())
                .setToken(tokenId());
        return new Builder<>(TransactionType.TOKENREVOKEKYC, transactionBody);
    }

    public Builder<TokenUnfreezeAccountTransactionBody.Builder> tokenUnfreeze() {
        var transactionBody = TokenUnfreezeAccountTransactionBody.newBuilder()
                .setAccount(accountId())
                .setToken(tokenId());
        return new Builder<>(TransactionType.TOKENUNFREEZE, transactionBody);
    }

    public Builder<TokenUnpauseTransactionBody.Builder> tokenUnpause() {
        var transactionBody = TokenUnpauseTransactionBody.newBuilder().setToken(tokenId());
        return new Builder<>(TransactionType.TOKENUNPAUSE, transactionBody);
    }

    public Builder<TokenUpdateTransactionBody.Builder> tokenUpdate() {
        var transactionBody = TokenUpdateTransactionBody.newBuilder()
                .setAdminKey(key())
                .setAutoRenewAccount(accountId())
                .setAutoRenewPeriod(duration(3600))
                .setExpiry(timestamp())
                .setFeeScheduleKey(key())
                .setFreezeKey(key())
                .setKycKey(key())
                .setMemo(StringValue.of(text(16)))
                .setMetadata(BytesValue.of(bytes(64)))
                .setMetadataKey(key())
                .setName(text(4))
                .setPauseKey(key())
                .setSupplyKey(key())
                .setSymbol(text(4))
                .setToken(tokenId())
                .setTreasury(accountId())
                .setWipeKey(key());
        return new Builder<>(TransactionType.TOKENUPDATE, transactionBody)
                .recordItem(r -> r.hapiVersion(HAPI_VERSION_0_49_0));
    }

    public Builder<TokenCreateTransactionBody.Builder> tokenCreate() {
        var tokenId = tokenId();
        var treasury = accountId();
        var transactionBody = TokenCreateTransactionBody.newBuilder()
                .setAdminKey(key())
                .setAutoRenewAccount(accountId())
                .setAutoRenewPeriod(duration(3600))
                .setExpiry(timestamp())
                .setFeeScheduleKey(key())
                .setFreezeKey(key())
                .setKycKey(key())
                .setMemo(String.valueOf(text(16)))
                .setMetadata(bytes(64))
                .setMetadataKey(key())
                .setName(text(4))
                .setPauseKey(key())
                .setSupplyKey(key())
                .setSymbol(text(4))
                .setTreasury(treasury)
                .addCustomFees(customFee(FIXED_FEE))
                .setWipeKey(key());
        return new Builder<>(TransactionType.TOKENCREATION, transactionBody)
                .recordItem(r -> r.hapiVersion(HAPI_VERSION_0_49_0))
                .receipt(r -> r.setTokenID(tokenId))
                .record(r -> r.addAutomaticTokenAssociations(
                        TokenAssociation.newBuilder().setAccountId(treasury).setTokenId(tokenId)));
    }

    public Builder<TokenDeleteTransactionBody.Builder> tokenDelete() {
        var builder = TokenDeleteTransactionBody.newBuilder().setToken(tokenId());
        return new Builder<>(TransactionType.TOKENDELETION, builder);
    }

    public Builder<TokenFeeScheduleUpdateTransactionBody.Builder> tokenFeeScheduleUpdate() {
        var transactionBody = TokenFeeScheduleUpdateTransactionBody.newBuilder()
                .setTokenId(tokenId())
                .addCustomFees(customFee(FIXED_FEE));
        return new Builder<>(TransactionType.TOKENFEESCHEDULEUPDATE, transactionBody);
    }

    public Builder<TokenWipeAccountTransactionBody.Builder> tokenWipe() {
        return tokenWipe(NON_FUNGIBLE_UNIQUE);
    }

    public Builder<TokenWipeAccountTransactionBody.Builder> tokenWipe(TokenType type) {
        var transactionBody = TokenWipeAccountTransactionBody.newBuilder()
                .setAccount(accountId())
                .setToken(tokenId());
        switch (type) {
            case FUNGIBLE_COMMON -> transactionBody.setAmount(1000L);
            case NON_FUNGIBLE_UNIQUE -> transactionBody.addSerialNumbers(1L);
            default -> throw new IllegalArgumentException("Unsupported token type: " + type);
        }
        return new Builder<>(TransactionType.TOKENWIPE, transactionBody).receipt(r -> r.setNewTotalSupply(2L));
    }

    @SuppressWarnings("deprecation")
    public Builder<UncheckedSubmitBody.Builder> uncheckedSubmit() {
        var transactionBody = UncheckedSubmitBody.newBuilder().setTransactionBytes(bytes(32));
        return new Builder<>(TransactionType.UNCHECKEDSUBMIT, transactionBody);
    }

    public AccountID accountId() {
        var accountId = entityId().toAccountID();
        updateState(accountId);
        return accountId;
    }

    public ContractID contractId() {
        var contractId = entityId().toContractID();
        updateState(contractId);
        return contractId;
    }

    public List<? extends Builder<?>> getCreateTransactions() {
        autoCreation.set(true);
        var createTransactions = state.entrySet().stream()
                .filter(e -> !e.getValue().isCreated())
                .map(this::toCreateTransaction)
                .toList();
        autoCreation.set(false);
        return createTransactions;
    }

    private Builder<?> toCreateTransaction(Map.Entry<GeneratedMessage, EntityState> entry) {
        entry.getValue().created.set(true);
        return switch (entry.getKey()) {
            case AccountID accountId -> cryptoCreate().receipt(r -> r.setAccountID(accountId));
            case ContractID contractId -> contractCreate().receipt(r -> r.setContractID(contractId));
            case FileID fileId -> fileCreate().receipt(r -> r.setFileID(fileId));
            case ScheduleID scheduleId -> scheduleCreate().receipt(r -> r.setScheduleID(scheduleId));
            case TokenAssociation ta ->
                tokenAssociate()
                        .transactionBody(b -> b.setAccount(ta.getAccountId()).addTokens(ta.getTokenId()));
            case TokenID tokenId -> tokenCreate().receipt(r -> r.setTokenID(tokenId));
            case TopicID topicId -> consensusCreateTopic().receipt(r -> r.setTopicID(topicId));
            default -> throw new UnsupportedOperationException("ID not supported: " + id);
        };
    }

    public Builder<LambdaSStoreTransactionBody.Builder> lambdaSstore() {
        var slotUpdate = LambdaStorageUpdate.newBuilder()
                .setStorageSlot(LambdaStorageSlot.newBuilder()
                        .setKey(slot())
                        .setValue(bytes(32))
                        .build());

        var slotUpdate2 = LambdaStorageUpdate.newBuilder()
                .setStorageSlot(LambdaStorageSlot.newBuilder()
                        .setKey(slot())
                        .setValue(bytes(32))
                        .build());

        var body = LambdaSStoreTransactionBody.newBuilder()
                .setHookId(HookId.newBuilder()
                        .setHookId(id())
                        .setEntityId(HookEntityId.newBuilder()
                                .setAccountId(accountId())
                                .build())
                        .build())
                .addStorageUpdates(slotUpdate)
                .addStorageUpdates(slotUpdate2);

        return new Builder<>(TransactionType.LAMBDA_SSTORE, body);
    }

    public ByteString bytes(int length) {
        byte[] bytes = randomBytes(length);
        return ByteString.copyFrom(bytes);
    }

    public ByteString nonZeroBytes(int length) {
        byte[] bytes = randomBytes(length);
        for (int i = 0; i < length; i++) {
            if (bytes[i] == 0) {
                bytes[i] = (byte) random.nextInt(1, Byte.MAX_VALUE);
            }
        }
        return ByteString.copyFrom(bytes);
    }

    public byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    public ByteString slot() {
        return fromBytes(trim(randomBytes(32)));
    }

    // Helper methods
    private AccountAmount accountAmount(EntityId accountId, long amount) {
        return accountAmount(accountId.toAccountID(), amount);
    }

    private AccountAmount accountAmount(AccountID accountID, long amount) {
        return AccountAmount.newBuilder()
                .setAccountID(accountID)
                .setAmount(amount)
                .build();
    }

    private ByteString bloomFor(ContractID contractId, List<ByteString> topics) {
        var address = Address.wrap(
                Bytes.wrap(Hex.decode(StringUtils.leftPad(Long.toHexString(contractId.getContractNum()), 40, '0'))));
        var logTopics = topics.stream()
                .map(topic -> LogTopic.wrap(Bytes.wrap(DomainUtils.toBytes(topic))))
                .toList();
        var log = new Log(address, Bytes.EMPTY, logTopics);
        return DomainUtils.fromBytes(
                LogsBloomFilter.builder().insertLog(log).build().toArray());
    }

    private TransactionSidecarRecord.Builder contractActions() {
        return TransactionSidecarRecord.newBuilder()
                .setActions(ContractActions.newBuilder()
                        .addContractActions(contractAction().setCallDepth(0))
                        .addContractActions(contractAction()
                                .setCallDepth(1)
                                .setCallingAccount(accountId())
                                .setError(bytes(10))
                                .setRecipientAccount(accountId()))
                        .addContractActions(contractAction()
                                .setCallDepth(2)
                                .setTargetedAddress(bytes(20))
                                .setRevertReason(bytes(10))));
    }

    private ContractAction.Builder contractAction() {
        return ContractAction.newBuilder()
                .setCallDepth(3)
                .setCallingContract(contractId())
                .setCallOperationType(CallOperationType.OP_CALL)
                .setCallType(ContractActionType.CALL)
                .setGas(100)
                .setGasUsed(50)
                .setInput(bytes(100))
                .setRecipientContract(contractId())
                .setOutput(bytes(256))
                .setValue(20);
    }

    private TransactionSidecarRecord.Builder contractBytecode(ContractID contractId) {
        var contractBytecode = ContractBytecode.newBuilder()
                .setContractId(contractId)
                .setInitcode(bytes(2048))
                .setRuntimeBytecode(bytes(3048));
        return TransactionSidecarRecord.newBuilder().setBytecode(contractBytecode);
    }

    private ContractLoginfo contractLoginfo(ContractID contractId) {
        var topics = IntStream.range(0, 4).mapToObj(x -> bytes(32)).toList();
        return ContractLoginfo.newBuilder()
                .setBloom(bloomFor(contractId, topics))
                .setContractID(contractId)
                .setData(bytes(128))
                .addAllTopic(topics)
                .build();
    }

    private TransactionSidecarRecord.Builder contractStateChanges(ContractID contractId) {
        var contractStateChange = ContractStateChange.newBuilder()
                .setContractId(contractId)
                .addStorageChanges(storageChange())
                .addStorageChanges(storageChange().setValueWritten(BytesValue.of(ByteString.EMPTY)));
        return TransactionSidecarRecord.newBuilder()
                .setStateChanges(ContractStateChanges.newBuilder().addContractStateChanges(contractStateChange));
    }

    private Duration duration(int seconds) {
        return Duration.newBuilder().setSeconds(seconds).build();
    }

    private EntityId entityId() {
        return EntityId.of(commonProperties.getShard(), commonProperties.getRealm(), entityId.getAndIncrement());
    }

    public BytesValue evmAddress() {
        return BytesValue.of(bytes(20));
    }

    public FileID fileId() {
        var fileId = entityId().toFileID();
        updateState(fileId);
        return fileId;
    }

    private long id() {
        return id.incrementAndGet();
    }

    public Key key() {
        if (id() % 2 == 0) {
            return Key.newBuilder().setECDSASecp256K1(bytes(KEY_LENGTH_ECDSA)).build();
        } else {
            return Key.newBuilder().setEd25519(bytes(KEY_LENGTH_ED25519)).build();
        }
    }

    public Key thresholdKey(int count, int threshold) {
        var keyList = KeyList.newBuilder();
        for (int i = 0; i < count; i++) {
            keyList.addKeys(key());
        }
        var thresholdKey = ThresholdKey.newBuilder().setKeys(keyList).setThreshold(threshold);
        return Key.newBuilder().setThresholdKey(thresholdKey).build();
    }

    public NodeStake.Builder nodeStake() {
        long stake = id() * TINYBARS_IN_ONE_HBAR;
        long maxStake = 50_000_000_000L * TINYBARS_IN_ONE_HBAR / 26L;
        long minStake = stake / 2;
        return NodeStake.newBuilder()
                .setMaxStake(maxStake)
                .setMinStake(minStake)
                .setNodeId(id())
                .setRewardRate(id())
                .setStake(stake)
                .setStakeNotRewarded(TINYBARS_IN_ONE_HBAR)
                .setStakeRewarded(stake - TINYBARS_IN_ONE_HBAR);
    }

    private ServiceEndpoint serviceEndpoint() {
        return ServiceEndpoint.newBuilder()
                .setIpAddressV4(ByteString.empty())
                .setPort(50211)
                .setDomainName("node1.hedera.com")
                .build();
    }

    private ServiceEndpoint gossipEndpoint() {
        return ServiceEndpoint.newBuilder()
                .setIpAddressV4(ByteString.copyFrom(new byte[] {127, 0, 0, 5}))
                .setPort(5112)
                .setDomainName("")
                .build();
    }

    public ScheduleID scheduleId() {
        var scheduleId = entityId().toScheduleID();
        updateState(scheduleId);
        return scheduleId;
    }

    private StorageChange.Builder storageChange() {
        return StorageChange.newBuilder()
                .setSlot(bytes(32))
                .setValueRead(bytes(32))
                .setValueWritten(BytesValue.of(bytes(32)));
    }

    public String text(int characters) {
        return RandomStringUtils.secure().nextAlphanumeric(characters);
    }

    public Timestamp timestamp() {
        return timestamp(ChronoUnit.SECONDS);
    }

    public Timestamp timestamp(TemporalUnit unit) {
        return Utility.instantToTimestamp(now.plus(id(), unit));
    }

    public TokenID tokenId() {
        var tokenId = entityId().toTokenID();
        updateState(tokenId);
        return tokenId;
    }

    public TopicID topicId() {
        var topicId = entityId().toTopicID();
        updateState(topicId);
        return topicId;
    }

    private void updateState(GeneratedMessage id) {
        // Don't cascade creations that occur during an auto creation to avoid infinite recursion
        if (!autoCreation.get()) {
            state.computeIfAbsent(id, k -> new EntityState());
        }
    }

    public enum TransferType {
        ALL,
        CRYPTO,
        NFT,
        TOKEN
    }

    @Value
    private class EntityState {
        private final AtomicBoolean created = new AtomicBoolean(false);
        private final AtomicLong sequenceNumber = new AtomicLong(0L);

        long getSequenceNumber() {
            return sequenceNumber.incrementAndGet();
        }

        boolean isCreated() {
            return created.get();
        }
    }

    public class Builder<T extends GeneratedMessage.Builder<T>> {

        private static final BiConsumer<TransactionBody.Builder, TransactionRecord.Builder> NOOP_INCREMENTER =
                (b, r) -> {};
        private final TransactionType type;
        private final T transactionBody;
        private final SignatureMap.Builder signatureMap;
        private final TransactionBody.Builder transactionBodyWrapper;
        private final TransactionRecord.Builder transactionRecord;
        private final List<TransactionSidecarRecord.Builder> sidecarRecords;
        private final AccountID payerAccountId;
        private final RecordItem.RecordItemBuilder recordItemBuilder;

        private Predicate<EntityId> entityTransactionPredicate = persistProperties::shouldPersistEntityTransaction;
        private Predicate<EntityId> contractTransactionPredicate = e -> persistProperties.isContractTransaction();
        private BiConsumer<TransactionBody.Builder, TransactionRecord.Builder> incrementer = NOOP_INCREMENTER;
        private boolean useTransactionBodyBytesAndSigMap;

        private Builder(TransactionType type, T transactionBody) {
            this.payerAccountId = accountId();
            this.recordItemBuilder = RecordItem.builder().hapiVersion(RecordFile.HAPI_VERSION_NOT_SET);
            this.sidecarRecords = new ArrayList<>();
            this.signatureMap = defaultSignatureMap();
            this.type = type;
            this.transactionBody = transactionBody;
            this.transactionBodyWrapper = defaultTransactionBody();
            this.transactionRecord = defaultTransactionRecord();
        }

        public RecordItem build() {
            var field = transactionBodyWrapper.getDescriptorForType().findFieldByNumber(type.getProtoId());
            if (field != null) { // Not UNKNOWN transaction type
                transactionBodyWrapper.setField(field, transactionBody.build());
            }

            // Update the consensus timestamp. Some tests depend upon static values so use it if it's present.
            if (!transactionRecord.hasConsensusTimestamp()) {
                transactionRecord.setConsensusTimestamp(timestamp(ChronoUnit.NANOS));
            }

            // Update the transaction ID if one was not already assigned
            var transactionId = getTransactionID();
            transactionBodyWrapper.setTransactionID(transactionId);
            transactionRecord.setTransactionID(transactionId);

            incrementer.accept(transactionBodyWrapper, transactionRecord);
            var transaction = transaction();
            if (transactionRecord.getTransactionHash() == ByteString.EMPTY) {
                var digest = createSha384Digest();
                byte[] transactionHash = useTransactionBodyBytesAndSigMap
                        ? digest.digest(transaction.toByteArray())
                        : digest.digest(toBytes(transaction.getSignedTransactionBytes()));
                transactionRecord.setTransactionHash(fromBytes(transactionHash));
            }

            var transactionRecordInstance = transactionRecord.build();
            var contractId = transactionRecordInstance.getReceipt().getContractID();

            transactionRecordInstance.getAutomaticTokenAssociationsList().forEach(RecordItemBuilder.this::updateState);

            var sidecars = this.sidecarRecords.stream()
                    .map(r -> {
                        if (r.hasBytecode() && !contractId.equals(ContractID.getDefaultInstance())) {
                            r.getBytecodeBuilder().setContractId(contractId);
                        }
                        return r.setConsensusTimestamp(transactionRecord.getConsensusTimestamp())
                                .build();
                    })
                    .toList();

            // Clear these so that the builder can be reused and get new incremented values.
            transactionRecord.clearTransactionID().clearConsensusTimestamp();
            transactionBodyWrapper.clearTransactionID();

            return recordItemBuilder
                    .contractTransactionPredicate(contractTransactionPredicate)
                    .entityTransactionPredicate(entityTransactionPredicate)
                    .transactionRecord(transactionRecordInstance)
                    .transaction(transaction)
                    .sidecarRecords(sidecars)
                    .build();
        }

        public Builder<T> clearIncrementer() {
            this.incrementer = NOOP_INCREMENTER;
            return this;
        }

        public Builder<T> customize(Consumer<Builder<T>> consumer) {
            consumer.accept(this);
            return this;
        }

        public Builder<T> entityTransactionPredicate(Predicate<EntityId> entityTransactionPredicate) {
            this.entityTransactionPredicate = entityTransactionPredicate;
            return this;
        }

        public Builder<T> contractTransactionPredicate(Predicate<EntityId> contractTransactionPredicate) {
            this.contractTransactionPredicate = contractTransactionPredicate;
            return this;
        }

        public Builder<T> incrementer(BiConsumer<TransactionBody.Builder, TransactionRecord.Builder> incrementer) {
            this.incrementer = incrementer;
            return this;
        }

        public Builder<T> receipt(Consumer<TransactionReceipt.Builder> consumer) {
            consumer.accept(transactionRecord.getReceiptBuilder());
            return this;
        }

        @SuppressWarnings("java:S6213")
        public Builder<T> record(Consumer<TransactionRecord.Builder> consumer) {
            consumer.accept(transactionRecord);
            return this;
        }

        public Builder<T> recordItem(Consumer<RecordItem.RecordItemBuilder> consumer) {
            consumer.accept(recordItemBuilder);
            return this;
        }

        public Builder<T> sidecarRecords(Consumer<List<TransactionSidecarRecord.Builder>> consumer) {
            consumer.accept(sidecarRecords);
            return this;
        }

        public Builder<T> status(ResponseCodeEnum status) {
            transactionRecord.getReceiptBuilder().setStatus(status);
            return this;
        }

        public Builder<T> signatureMap(Consumer<SignatureMap.Builder> consumer) {
            consumer.accept(signatureMap);
            return this;
        }

        public Builder<T> transactionBody(Consumer<T> consumer) {
            consumer.accept(transactionBody);
            return this;
        }

        public Builder<T> transactionBodyWrapper(Consumer<TransactionBody.Builder> consumer) {
            consumer.accept(transactionBodyWrapper);
            return this;
        }

        public Builder<T> useTransactionBodyBytesAndSigMap(boolean value) {
            this.useTransactionBodyBytesAndSigMap = value;
            return this;
        }

        private SignatureMap.Builder defaultSignatureMap() {
            return SignatureMap.newBuilder()
                    .addSigPair(SignaturePair.newBuilder().setEd25519(bytes(32)).setPubKeyPrefix(bytes(16)));
        }

        private TransactionBody.Builder defaultTransactionBody() {
            return TransactionBody.newBuilder()
                    .setMemo(type.name())
                    .setNodeAccountID(accountId())
                    .setTransactionFee(100L)
                    .setTransactionValidDuration(duration(120));
        }

        private TransactionRecord.Builder defaultTransactionRecord() {
            var nodeAccountId = transactionBodyWrapper.getNodeAccountID();
            var transactionRecordBuilder = TransactionRecord.newBuilder()
                    .setMemoBytes(ByteString.copyFromUtf8(transactionBodyWrapper.getMemo()))
                    .setTransactionFee(transactionBodyWrapper.getTransactionFee())
                    .setTransferList(TransferList.newBuilder()
                            .addAccountAmounts(accountAmount(payerAccountId, -6000L))
                            .addAccountAmounts(accountAmount(nodeAccountId, 1000L))
                            .addAccountAmounts(accountAmount(systemEntity.feeCollectorAccount(), 2000L))
                            .addAccountAmounts(accountAmount(systemEntity.stakingRewardAccount(), 3000L))
                            .build());
            transactionRecordBuilder.getReceiptBuilder().setStatus(ResponseCodeEnum.SUCCESS);
            return transactionRecordBuilder;
        }

        private TransactionID getTransactionID() {
            if (transactionRecord.hasTransactionID()) {
                return transactionRecord.getTransactionID();
            }

            if (transactionBodyWrapper.hasTransactionID()) {
                return transactionBodyWrapper.getTransactionID();
            }

            var instant = Utility.convertToInstant(transactionRecord.getConsensusTimestamp());
            var validStart = Utility.instantToTimestamp(instant.minusNanos(10));
            return TransactionID.newBuilder()
                    .setAccountID(payerAccountId)
                    .setTransactionValidStart(validStart)
                    .build();
        }

        @SuppressWarnings("deprecation")
        private Transaction transaction() {
            var bodyBytes = transactionBodyWrapper.build().toByteString();
            var builder = Transaction.newBuilder();
            if (useTransactionBodyBytesAndSigMap) {
                builder.setBodyBytes(bodyBytes).setSigMap(signatureMap);
            } else {
                // use signedTransactionBytes
                builder.setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(bodyBytes)
                        .setSigMap(signatureMap)
                        .build()
                        .toByteString());
            }

            return builder.build();
        }
    }
}
