// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.TestUtils.toEntityTransaction;
import static org.hiero.mirror.importer.TestUtils.toEntityTransactions;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_ALIAS;
import static org.hiero.mirror.importer.util.Utility.HALT_ON_ERROR_PROPERTY;
import static org.hiero.mirror.importer.util.UtilityTest.ALIAS_ECDSA_SECP256K1;
import static org.hiero.mirror.importer.util.UtilityTest.EVM_ADDRESS;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Range;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAddLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.RealmID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ShardID;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Condition;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.IterableAssert;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.common.domain.entity.AbstractCryptoAllowance.Id;
import org.hiero.mirror.common.domain.entity.AbstractEntity;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.hook.AbstractHook;
import org.hiero.mirror.common.domain.hook.HookExtensionPoint;
import org.hiero.mirror.common.domain.hook.HookType;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.common.domain.transaction.CryptoTransfer;
import org.hiero.mirror.common.domain.transaction.ErrataType;
import org.hiero.mirror.common.domain.transaction.ItemizedTransfer;
import org.hiero.mirror.common.domain.transaction.LiveHash;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.StakingRewardTransfer;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.repository.ContractRepository;
import org.hiero.mirror.importer.repository.CryptoAllowanceRepository;
import org.hiero.mirror.importer.repository.HookRepository;
import org.hiero.mirror.importer.repository.NftAllowanceRepository;
import org.hiero.mirror.importer.repository.NftRepository;
import org.hiero.mirror.importer.repository.TokenAllowanceRepository;
import org.hiero.mirror.importer.repository.TokenTransferRepository;
import org.hiero.mirror.importer.util.Utility;
import org.hiero.mirror.importer.util.UtilityTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;

@RequiredArgsConstructor
class EntityRecordItemListenerCryptoTest extends AbstractEntityRecordItemListenerTest {

    private static final long INITIAL_BALANCE = 1000L;
    private static final EntityId accountId1 = DOMAIN_BUILDER.entityNum(1001);
    private static final long[] additionalTransfers = {5000};
    private static final long[] additionalTransferAmounts = {1001, 1002};
    private static final ByteString ALIAS_KEY = DomainUtils.fromBytes(UtilityTest.ALIAS_ECDSA_SECP256K1);

    private final @Qualifier(CACHE_ALIAS) CacheManager cacheManager;
    private final ContractRepository contractRepository;
    private final CryptoAllowanceRepository cryptoAllowanceRepository;
    private final HookRepository hookRepository;
    private final NftAllowanceRepository nftAllowanceRepository;
    private final NftRepository nftRepository;
    private final TokenAllowanceRepository tokenAllowanceRepository;
    private final TokenTransferRepository tokenTransferRepository;

    @BeforeEach
    void before() {
        entityProperties.getPersist().setClaims(true);
        entityProperties.getPersist().setCryptoTransferAmounts(true);
        entityProperties.getPersist().setEntityTransactions(true);
        entityProperties.getPersist().setItemizedTransfers(true);
        entityProperties.getPersist().setTransactionBytes(false);
        entityProperties.getPersist().setTransactionRecordBytes(false);
    }

    @AfterEach
    void after() {
        entityProperties.getPersist().setEntityTransactions(false);
        entityProperties.getPersist().setItemizedTransfers(false);
    }

    @Test
    void cryptoApproveAllowance() {
        // given
        var consensusTimestamp = recordItemBuilder.timestamp();
        var expectedNfts = new ArrayList<Nft>();
        var nftAllowances = customizeNftAllowances(consensusTimestamp, expectedNfts);
        var recordItem = recordItemBuilder
                .cryptoApproveAllowance()
                .transactionBody(b -> b.clearNftAllowances().addAllNftAllowances(nftAllowances))
                .record(r -> r.setConsensusTimestamp(consensusTimestamp))
                .build();
        var body = recordItem.getTransactionBody().getCryptoApproveAllowance();
        var entityIds = body.getCryptoAllowancesList().stream()
                .flatMap(cryptoAllowance -> Stream.of(cryptoAllowance.getOwner(), cryptoAllowance.getSpender())
                        .map(EntityId::of))
                .collect(Collectors.toList());
        entityIds.addAll(body.getNftAllowancesList().stream()
                .flatMap(nftAllowance -> Stream.of(
                        EntityId.of(nftAllowance.getDelegatingSpender()),
                        EntityId.of(nftAllowance.getOwner()),
                        EntityId.of(nftAllowance.getSpender()),
                        EntityId.of(nftAllowance.getTokenId())))
                .toList());
        entityIds.addAll(body.getTokenAllowancesList().stream()
                .flatMap(nftAllowance -> Stream.of(
                        EntityId.of(nftAllowance.getOwner()),
                        EntityId.of(nftAllowance.getSpender()),
                        EntityId.of(nftAllowance.getTokenId())))
                .toList());
        entityIds.add(EntityId.of(recordItem.getTransactionBody().getNodeAccountID()));
        entityIds.add(recordItem.getPayerAccountId());
        var expectedEntityTransactions = toEntityTransactions(recordItem, entityIds.toArray(EntityId[]::new))
                .values();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        assertAllowances(recordItem, expectedNfts);
        assertThat(entityTransactionRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedEntityTransactions);
    }

    @Test
    void cryptoCreateWithInitialBalance() {
        Transaction transaction = cryptoCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        CryptoCreateTransactionBody cryptoCreateTransactionBody = transactionBody.getCryptoCreateAccount();
        long initialBalance = cryptoCreateTransactionBody.getInitialBalance();

        var transfer1 = accountAmount(accountId1.getId(), initialBalance);
        var transfer2 = accountAmount(EntityId.of(PAYER).getId(), -initialBalance);
        TransactionRecord txnRecord = transactionRecordSuccess(
                transactionBody,
                recordBuilder -> groupCryptoTransfersByAccountId(recordBuilder, List.of(transfer1, transfer2)));

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        var consensusTimestamp = DomainUtils.timeStampInNanos(txnRecord.getConsensusTimestamp());
        var dbTransaction = getDbTransaction(txnRecord.getConsensusTimestamp());
        Optional<CryptoTransfer> initialBalanceTransfer = cryptoTransferRepository.findById(
                new CryptoTransfer.Id(initialBalance, consensusTimestamp, accountId1.getId()));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(accountId1),
                () -> assertCryptoTransfers(4)
                        .areAtMost(1, isAccountAmountReceiverAccountAmount(transfer1.build()))
                        .areAtMost(1, isAccountAmountReceiverAccountAmount(transfer2.build())),
                () -> assertCryptoTransaction(transactionBody, txnRecord),
                () -> assertCryptoEntity(
                        cryptoCreateTransactionBody, initialBalance, txnRecord.getConsensusTimestamp()),
                () -> assertEquals(initialBalance, dbTransaction.getInitialBalance()),
                () -> assertThat(initialBalanceTransfer).isPresent());
    }

    @Test
    void cryptoCreateWithZeroInitialBalance() {
        CryptoCreateTransactionBody.Builder cryptoCreateBuilder =
                cryptoCreateAccountBuilderWithDefaults().setInitialBalance(0L);
        Transaction transaction = cryptoCreateTransaction(cryptoCreateBuilder);
        TransactionBody transactionBody = getTransactionBody(transaction);
        CryptoCreateTransactionBody cryptoCreateTransactionBody = transactionBody.getCryptoCreateAccount();
        TransactionRecord txnRecord = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        var consensusTimestamp = DomainUtils.timeStampInNanos(txnRecord.getConsensusTimestamp());
        var dbTransaction = getDbTransaction(txnRecord.getConsensusTimestamp());
        Optional<CryptoTransfer> initialBalanceTransfer =
                cryptoTransferRepository.findById(new CryptoTransfer.Id(0L, consensusTimestamp, accountId1.getId()));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(accountId1),
                () -> assertCryptoTransfers(3),
                () -> assertCryptoTransaction(transactionBody, txnRecord),
                () -> assertCryptoEntity(cryptoCreateTransactionBody, 0L, txnRecord.getConsensusTimestamp()),
                () -> assertThat(dbTransaction.getInitialBalance()).isZero(),
                () -> assertThat(initialBalanceTransfer).isEmpty());
    }

    @Test
    void cryptoCreateFailedTransaction() {
        Transaction transaction = cryptoCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        CryptoCreateTransactionBody cryptoCreateTransactionBody = transactionBody.getCryptoCreateAccount();
        // Clear receipt.accountID since transaction is failure.
        TransactionRecord.Builder recordBuilder =
                transactionRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE).toBuilder();
        recordBuilder.getReceiptBuilder().clearAccountID();
        TransactionRecord txnRecord = recordBuilder.build();

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        var dbTransaction = getDbTransaction(txnRecord.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(),
                () -> assertCryptoTransfers(3),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertNull(dbTransaction.getEntityId()),
                () -> assertEquals(cryptoCreateTransactionBody.getInitialBalance(), dbTransaction.getInitialBalance()));
    }

    @Test
    void cryptoCreateInitialBalanceInTransferList() {
        Transaction transaction = cryptoCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        CryptoCreateTransactionBody cryptoCreateTransactionBody = transactionBody.getCryptoCreateAccount();

        // add initial balance to transfer list
        long initialBalance = cryptoCreateTransactionBody.getInitialBalance();
        var transfer1 = accountAmount(accountId1.toAccountID(), initialBalance);
        var transfer2 = accountAmount(EntityId.of(PAYER).getId(), -initialBalance);
        TransactionRecord txnRecord = transactionRecordSuccess(
                transactionBody,
                recordBuilder -> groupCryptoTransfersByAccountId(recordBuilder, List.of(transfer1, transfer2)));

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        var dbTransaction = getDbTransaction(txnRecord.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(accountId1),
                () -> assertCryptoTransfers(4)
                        .areAtMost(1, isAccountAmountReceiverAccountAmount(transfer1.build()))
                        .areAtMost(1, isAccountAmountReceiverAccountAmount(transfer2.build())),
                () -> assertCryptoTransaction(transactionBody, txnRecord),
                () -> assertCryptoEntity(
                        cryptoCreateTransactionBody, initialBalance, txnRecord.getConsensusTimestamp()),
                () -> assertEquals(cryptoCreateTransactionBody.getInitialBalance(), dbTransaction.getInitialBalance()));
    }

    @Test
    void cryptoCreateAccountAlias() {
        Transaction transaction = cryptoCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        CryptoCreateTransactionBody cryptoCreateTransactionBody = transactionBody.getCryptoCreateAccount();
        TransactionRecord txnRecord = buildTransactionRecord(
                recordBuilder ->
                        recordBuilder.setAlias(ALIAS_KEY).getReceiptBuilder().setAccountID(accountId1.toAccountID()),
                transactionBody,
                ResponseCodeEnum.SUCCESS.getNumber());

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        var consensusTimestamp = DomainUtils.timeStampInNanos(txnRecord.getConsensusTimestamp());
        var dbTransaction = getDbTransaction(txnRecord.getConsensusTimestamp());
        Optional<CryptoTransfer> initialBalanceTransfer =
                cryptoTransferRepository.findById(new CryptoTransfer.Id(0, consensusTimestamp, accountId1.getId()));

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(accountId1),
                () -> assertCryptoTransfers(3),
                () -> assertCryptoTransaction(transactionBody, txnRecord),
                () -> assertCryptoEntity(cryptoCreateTransactionBody, 0L, txnRecord.getConsensusTimestamp()),
                () -> assertEquals(cryptoCreateTransactionBody.getInitialBalance(), dbTransaction.getInitialBalance()),
                () -> assertThat(initialBalanceTransfer).isEmpty(),
                () -> assertThat(entityRepository.findByAlias(ALIAS_KEY.toByteArray()))
                        .get()
                        .isEqualTo(accountId1.getId()));
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            false, true
                            false, false
                            # clear cache after the first record file to test the scenario the evm address is looked up from db
                            true, false
                            """)
    void cryptoCreateHollowAccountThenTransferToPublicKeyAlias(boolean clearCache, boolean singleRecordFile) {
        entityProperties.getPersist().setCryptoTransferAmounts(true);
        entityProperties.getPersist().setItemizedTransfers(true);

        var evmAddress = DomainUtils.fromBytes(EVM_ADDRESS);
        var cryptoCreate = recordItemBuilder
                .cryptoCreate()
                .transactionBody(b -> b.setAlias(evmAddress).setInitialBalance(0))
                .record(r -> r.setEvmAddress(evmAddress))
                .incrementer((b, r) -> {
                    b.getTransactionIDBuilder().setNonce(1);
                    r.getTransactionIDBuilder().setNonce(1);
                })
                .build();
        var transactionRecord = cryptoCreate.getTransactionRecord();
        var hollowAccountId = transactionRecord.getReceipt().getAccountID();
        // The triggering crypto transfer tx's transaction id has nonce 0
        var transactionId =
                transactionRecord.getTransactionID().toBuilder().setNonce(0).build();
        var payerAccountId = transactionId.getAccountID();
        var cryptoTransfer = recordItemBuilder
                .cryptoTransfer()
                .transactionBody(b -> b.setTransfers(TransferList.newBuilder()
                        .addAccountAmounts(AccountAmount.newBuilder()
                                .setAccountID(payerAccountId)
                                .setAmount(-1000))
                        .addAccountAmounts(AccountAmount.newBuilder()
                                .setAccountID(AccountID.newBuilder()
                                        .setShardNum(COMMON_PROPERTIES.getShard())
                                        .setRealmNum(COMMON_PROPERTIES.getRealm())
                                        .setAlias(evmAddress))
                                .setAmount(1000))))
                .transactionBodyWrapper(w -> w.setTransactionID(transactionId))
                .record(r -> r.setTransactionID(transactionId)
                        // For simplicity, only add transfer from payer to hollow account in transaction record
                        .setTransferList(TransferList.newBuilder()
                                .addAccountAmounts(AccountAmount.newBuilder()
                                        .setAccountID(payerAccountId)
                                        .setAmount(-1000))
                                .addAccountAmounts(AccountAmount.newBuilder()
                                        .setAccountID(hollowAccountId)
                                        .setAmount(1000L))))
                .build();
        var recordItems = new ArrayList<RecordItem>();
        // crypto transfer to evm address and hollow account create always happen in the same record file
        recordItems.add(cryptoCreate);
        recordItems.add(cryptoTransfer);

        if (!singleRecordFile) {
            parseRecordItemsAndCommit(recordItems);
            recordItems.clear();
        }

        if (clearCache) {
            resetCacheManager(cacheManager);
        }

        // Crypto transfer to public key alias
        var cryptoTransferToAlias = recordItemBuilder
                .cryptoTransfer()
                .transactionBody(b -> b.setTransfers(TransferList.newBuilder()
                        .addAccountAmounts(AccountAmount.newBuilder()
                                .setAccountID(payerAccountId)
                                .setAmount(-200))
                        .addAccountAmounts(AccountAmount.newBuilder()
                                .setAccountID(AccountID.newBuilder()
                                        .setShardNum(COMMON_PROPERTIES.getShard())
                                        .setRealmNum(COMMON_PROPERTIES.getRealm())
                                        .setAlias(DomainUtils.fromBytes(ALIAS_ECDSA_SECP256K1)))
                                .setAmount(200))))
                .record(r -> r.setTransferList(TransferList.newBuilder()
                        .addAccountAmounts(AccountAmount.newBuilder()
                                .setAccountID(payerAccountId)
                                .setAmount(-200))
                        .addAccountAmounts(AccountAmount.newBuilder()
                                .setAccountID(hollowAccountId)
                                .setAmount(200))))
                .build();
        recordItems.add(cryptoTransferToAlias);
        parseRecordItemsAndCommit(recordItems);

        // then
        var hollowAccount = EntityId.of(hollowAccountId);
        var payerAccount = EntityId.of(payerAccountId);
        var expectedItemizedTransfers = new ArrayList<List<ItemizedTransfer>>();
        // From hollow account create tx
        expectedItemizedTransfers.add(null);
        // From crypto transfer tx which triggers hollow account creation
        expectedItemizedTransfers.add(List.of(
                ItemizedTransfer.builder()
                        .amount(-1000L)
                        .entityId(payerAccount)
                        .isApproval(false)
                        .build(),
                ItemizedTransfer.builder()
                        .amount(1000L)
                        .entityId(hollowAccount)
                        .isApproval(false)
                        .build()));
        // From the last crypto transfer to public key alias
        expectedItemizedTransfers.add(List.of(
                ItemizedTransfer.builder()
                        .amount(-200L)
                        .entityId(payerAccount)
                        .isApproval(false)
                        .build(),
                ItemizedTransfer.builder()
                        .amount(200L)
                        .entityId(hollowAccount)
                        .isApproval(false)
                        .build()));
        assertAll(
                () -> assertEquals(3, transactionRepository.count()),
                () -> assertEntities(hollowAccount),
                () -> assertCryptoTransfers(8),
                () -> assertThat(entityRepository.findByAlias(EVM_ADDRESS)).hasValue(hollowAccount.getId()),
                () -> assertThat(transactionRepository.findAll())
                        .map(org.hiero.mirror.common.domain.transaction.Transaction::getItemizedTransfer)
                        .containsExactlyInAnyOrderElementsOf(expectedItemizedTransfers));
    }

    @Test
    void cryptoDeleteAllowance() {
        // given
        var delegatingSpender = EntityId.of(recordItemBuilder.accountId()).getId();
        var ownerAccountId = EntityId.of(recordItemBuilder.accountId());
        var spender1 = EntityId.of(recordItemBuilder.accountId()).getId();
        var spender2 = EntityId.of(recordItemBuilder.accountId()).getId();
        var tokenId1 = EntityId.of(recordItemBuilder.tokenId());
        var tokenId2 = EntityId.of(recordItemBuilder.tokenId());
        List<NftRemoveAllowance> nftRemoveAllowances = List.of(
                NftRemoveAllowance.newBuilder()
                        .setOwner(ownerAccountId.toAccountID())
                        .setTokenId(tokenId1.toTokenID())
                        .addSerialNumbers(1L)
                        .addSerialNumbers(2L)
                        .build(),
                NftRemoveAllowance.newBuilder()
                        .setOwner(ownerAccountId.toAccountID())
                        .setTokenId(tokenId2.toTokenID())
                        .addSerialNumbers(1L)
                        .addSerialNumbers(2L)
                        .addSerialNumbers(2L)
                        .build());
        RecordItem recordItem = recordItemBuilder
                .cryptoDeleteAllowance()
                .transactionBody(b -> b.clearNftAllowances().addAllNftAllowances(nftRemoveAllowances))
                .build();
        var timestampRange = Range.atLeast(recordItem.getConsensusTimestamp());
        var nft1 = Nft.builder()
                .accountId(ownerAccountId)
                .createdTimestamp(10L)
                .deleted(false)
                .serialNumber(1)
                .timestampRange(timestampRange)
                .tokenId(tokenId1.getId())
                .build();
        var nft2 = Nft.builder()
                .accountId(ownerAccountId)
                .createdTimestamp(11L)
                .deleted(false)
                .serialNumber(2)
                .timestampRange(timestampRange)
                .tokenId(tokenId1.getId())
                .build();
        var nft3 = Nft.builder()
                .accountId(ownerAccountId)
                .createdTimestamp(12L)
                .deleted(false)
                .serialNumber(1)
                .timestampRange(timestampRange)
                .tokenId(tokenId2.getId())
                .build();
        var nft4 = Nft.builder()
                .accountId(ownerAccountId)
                .createdTimestamp(13L)
                .deleted(false)
                .serialNumber(2)
                .timestampRange(timestampRange)
                .tokenId(tokenId2.getId())
                .build();
        List<Nft> nftsWithAllowance = Stream.of(
                        nft1.toBuilder()
                                .delegatingSpender(delegatingSpender)
                                .spender(spender1)
                                .timestampRange(Range.atLeast(15L)),
                        nft2.toBuilder().spender(spender2).timestampRange(Range.atLeast(16L)),
                        nft3.toBuilder().spender(spender1).timestampRange(Range.atLeast(17L)),
                        nft4.toBuilder().spender(spender2).timestampRange(Range.atLeast(18L)))
                .map(Nft.NftBuilder::build)
                .collect(Collectors.toList());
        nftRepository.saveAll(nftsWithAllowance);

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        assertAll(
                () -> assertEquals(0, entityRepository.count()),
                () -> assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord()),
                () -> assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2, nft3, nft4));
    }

    @SuppressWarnings("deprecation")
    @Test
    void cryptoUpdateSuccessfulTransaction() {
        createAccount();

        // now update
        var newKey = recordItemBuilder.thresholdKey(2, 1);
        var transaction = cryptoUpdateTransaction(accountId1.toAccountID(), b -> b.setKey(newKey));
        var transactionBody = getTransactionBody(transaction);
        var cryptoUpdate = transactionBody.getCryptoUpdateAccount();
        var txnRecord = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .hapiVersion(RecordFile.HAPI_VERSION_0_27_0)
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());
        var dbAccountEntity = getTransactionEntity(txnRecord.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEntities(accountId1),
                () -> assertEquals(6, cryptoTransferRepository.count()),
                () -> assertCryptoTransaction(transactionBody, txnRecord),
                // transaction body inputs
                () -> assertThat(dbAccountEntity)
                        .returns(cryptoUpdate.getAutoRenewPeriod().getSeconds(), Entity::getAutoRenewPeriod)
                        .returns(null, Entity::getPublicKey)
                        .returns(EntityId.of(cryptoUpdate.getProxyAccountID()), Entity::getProxyAccountId)
                        .returns(newKey.toByteArray(), Entity::getKey)
                        .returns(
                                cryptoUpdate.getMaxAutomaticTokenAssociations().getValue(),
                                Entity::getMaxAutomaticTokenAssociations)
                        .returns(cryptoUpdate.getMemo().getValue(), Entity::getMemo)
                        .returns(
                                DomainUtils.timeStampInNanos(cryptoUpdate.getExpirationTime()),
                                Entity::getExpirationTimestamp)
                        .returns(
                                DomainUtils.timestampInNanosMax(txnRecord.getConsensusTimestamp()),
                                Entity::getTimestampLower)
                        .returns(false, Entity::getReceiverSigRequired)
                        .returns(false, Entity::getDeclineReward)
                        .returns(cryptoUpdate.getStakedNodeId(), Entity::getStakedNodeId)
                        .returns(AbstractEntity.ACCOUNT_ID_CLEARED, Entity::getStakedAccountId)
                        .returns(
                                Utility.getEpochDay(DomainUtils.timestampInNanosMax(txnRecord.getConsensusTimestamp())),
                                Entity::getStakePeriodStart));
    }

    @Test
    void cryptoTransferWithPaidStakingRewards() {
        // given
        var receiver1 = domainBuilder.entity().customize(e -> e.balance(100L)).persist();
        var receiver2 = domainBuilder.entity().customize(e -> e.balance(200L)).persist();
        var sender = domainBuilder.entity().customize(e -> e.balance(300L)).persist();

        var recordItem = recordItemBuilder
                .cryptoTransfer()
                .transactionBody(b -> b.setTransfers(TransferList.newBuilder()
                        .addAccountAmounts(accountAmount(sender.getId(), -20L))
                        .addAccountAmounts(accountAmount(receiver1.getId(), 5L))
                        .addAccountAmounts(accountAmount(receiver2.getId(), -15L))))
                .record(r -> {
                    // preserve the tx fee paid by the payer and received by the node and the fee collector
                    // sender only gets deducted 15, since it gets a 5 reward payout
                    // receiver1 gets 9, since it gets a 4 reward payout
                    // receiver2 gets no reward
                    var paidStakingRewards = List.of(
                            accountAmount(sender.getId(), 5L).build(),
                            accountAmount(receiver1.getId(), 4L).build());

                    r.clearTransferList()
                            .getTransferListBuilder()
                            .addAccountAmounts(accountAmount(sender.getId(), -15L))
                            .addAccountAmounts(accountAmount(receiver1.getId(), 9L))
                            .addAccountAmounts(accountAmount(receiver2.getId(), 15L))
                            .addAccountAmounts(accountAmount(systemEntity.stakingRewardAccount(), -9L));
                    r.addAllPaidStakingRewards(paidStakingRewards);
                })
                .build();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        long expectedStakePeriodStart = Utility.getEpochDay(consensusTimestamp) - 1;
        sender.setBalance(285L);
        sender.setBalanceTimestamp(consensusTimestamp);
        sender.setStakePeriodStart(expectedStakePeriodStart);
        sender.setTimestampLower(consensusTimestamp);
        receiver1.setBalance(109L);
        receiver1.setBalanceTimestamp(consensusTimestamp);
        receiver1.setStakePeriodStart(expectedStakePeriodStart);
        receiver1.setTimestampLower(consensusTimestamp);
        receiver2.setBalance(215L);
        receiver2.setBalanceTimestamp(consensusTimestamp);

        var payerAccountId = recordItem.getPayerAccountId();
        var expectedStakingRewardTransfer1 = new StakingRewardTransfer();
        expectedStakingRewardTransfer1.setAccountId(sender.getId());
        expectedStakingRewardTransfer1.setAmount(5L);
        expectedStakingRewardTransfer1.setConsensusTimestamp(consensusTimestamp);
        expectedStakingRewardTransfer1.setPayerAccountId(payerAccountId);
        var expectedStakingRewardTransfer2 = new StakingRewardTransfer();
        expectedStakingRewardTransfer2.setAccountId(receiver1.getId());
        expectedStakingRewardTransfer2.setAmount(4L);
        expectedStakingRewardTransfer2.setConsensusTimestamp(consensusTimestamp);
        expectedStakingRewardTransfer2.setPayerAccountId(payerAccountId);

        assertAll(
                () -> assertEquals(0, contractRepository.count()),
                // 3 for hbar transfers, and 1 for reward payout from 0.0.800
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(sender, receiver1, receiver2),
                () -> assertThat(stakingRewardTransferRepository.findAll())
                        .containsExactlyInAnyOrder(expectedStakingRewardTransfer1, expectedStakingRewardTransfer2),
                () -> assertEquals(1, transactionRepository.count()));
    }

    @Test
    void cryptoTransferFailedWithPaidStakingRewards() {
        // given
        var payer = domainBuilder.entity().customize(e -> e.balance(5000L)).persist();
        var receiver = domainBuilder.entity().customize(e -> e.balance(0L)).persist();
        var sender = domainBuilder.entity().customize(e -> e.balance(5L)).persist();

        // Transaction failed with INSUFFICIENT_ACCOUNT_BALANCE because sender's balance is less than the intended
        // transfer amount. However, the transaction payer has a balance change and there is pending reward for the
        // payer account, so there will be a reward payout for the transaction payer.
        var transactionId = transactionId(payer, domainBuilder.timestamp());
        var recordItem = recordItemBuilder
                .cryptoTransfer()
                .transactionBody(b -> b.setTransfers(TransferList.newBuilder()
                        .addAccountAmounts(accountAmount(sender.getId(), -20L))
                        .addAccountAmounts(accountAmount(receiver.getId(), 20L))))
                .transactionBodyWrapper(b -> b.setTransactionID(transactionId))
                .record(r -> r.setTransactionID(transactionId)
                        .setTransferList(TransferList.newBuilder()
                                .addAccountAmounts(accountAmount(payer.getId(), -2800L))
                                .addAccountAmounts(accountAmount(NODE, 1000L))
                                .addAccountAmounts(accountAmount(systemEntity.feeCollectorAccount(), 2000L))
                                .addAccountAmounts(accountAmount(systemEntity.stakingRewardAccount(), -200L)))
                        .addPaidStakingRewards(accountAmount(payer.getId(), 200L))
                        .getReceiptBuilder()
                        .setStatus(ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE))
                .build();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        long expectedStakePeriodStart = Utility.getEpochDay(consensusTimestamp) - 1;
        payer.setBalance(2200L);
        payer.setBalanceTimestamp(consensusTimestamp);
        payer.setStakePeriodStart(expectedStakePeriodStart);
        payer.setTimestampLower(consensusTimestamp);

        var expectedStakingRewardTransfer = new StakingRewardTransfer();
        expectedStakingRewardTransfer.setAccountId(payer.getId());
        expectedStakingRewardTransfer.setAmount(200L);
        expectedStakingRewardTransfer.setConsensusTimestamp(consensusTimestamp);
        expectedStakingRewardTransfer.setPayerAccountId(payer.toEntityId());

        assertAll(
                () -> assertEquals(0, contractRepository.count()),
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(payer, sender, receiver),
                () -> assertThat(stakingRewardTransferRepository.findAll()).containsOnly(expectedStakingRewardTransfer),
                () -> assertEquals(1, transactionRepository.count()));
    }

    /**
     * Github issue #483
     */
    @Test
    void samePayerAndUpdateAccount() {
        Transaction transaction = cryptoUpdateTransaction(accountId1.toAccountID());
        TransactionBody transactionBody = getTransactionBody(transaction);
        transactionBody = TransactionBody.newBuilder()
                .mergeFrom(transactionBody)
                .setTransactionID(Utility.getTransactionId(accountId1.toAccountID()))
                .build();
        transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(transactionBody.toByteString())
                        .build()
                        .toByteString())
                .build();
        TransactionRecord txnRecord = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertThat(transactionRepository.findById(DomainUtils.timestampInNanosMax(txnRecord.getConsensusTimestamp())))
                .get()
                .extracting(
                        org.hiero.mirror.common.domain.transaction.Transaction::getPayerAccountId,
                        org.hiero.mirror.common.domain.transaction.Transaction::getEntityId)
                .containsOnly(accountId1);
    }

    // Transactions in production have proxyAccountID explicitly set to '0.0.0'. Test is to prevent code regression
    // in handling this weird case.
    @SuppressWarnings("deprecation")
    @Test
    void proxyAccountIdSetTo0() {
        // given
        Transaction transaction = cryptoUpdateTransaction(accountId1.toAccountID());
        TransactionBody transactionBody = getTransactionBody(transaction);
        var bodyBuilder = transactionBody.toBuilder();
        bodyBuilder.getCryptoUpdateAccountBuilder().setProxyAccountID(AccountID.getDefaultInstance());
        transactionBody = bodyBuilder.build();
        transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(transactionBody.toByteString())
                        .build()
                        .toByteString())
                .build();
        TransactionRecord txnRecord = transactionRecordSuccess(transactionBody);

        // then: process the transaction without throwing NPE
        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertThat(transactionRepository.count()).isEqualTo(1L);
        assertThat(entityRepository.findById(accountId1.getId()))
                .get()
                .extracting(Entity::getProxyAccountId)
                .isNull();
    }

    @DisplayName("update account such that expiration timestamp overflows nanos_timestamp")
    @ParameterizedTest(name = "with seconds {0} and expectedNanosTimestamp {1}")
    @CsvSource({
        "9223372036854775807, 9223372036854775807",
        "31556889864403199, 9223372036854775807",
        "-9223372036854775808, -9223372036854775808",
        "-1000000000000000000, -9223372036854775808"
    })
    void cryptoUpdateExpirationOverflow(long seconds, long expectedNanosTimestamp) {
        createAccount();

        // now update
        var updateTransaction = buildTransaction(builder -> builder.getCryptoUpdateAccountBuilder()
                .setAccountIDToUpdate(accountId1.toAccountID())
                // *** THIS IS THE OVERFLOW WE WANT TO TEST ***
                // This should result in the entity having a Long.MAX_VALUE or Long.MIN_VALUE expirations
                // (the results of overflows).
                .setExpirationTime(Timestamp.newBuilder().setSeconds(seconds))
                .setDeclineReward(BoolValue.of(true))
                .setStakedAccountId(domainBuilder.entityNum(1L).toAccountID()));
        var transactionBody = getTransactionBody(updateTransaction);

        var txnRecord = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .hapiVersion(RecordFile.HAPI_VERSION_0_27_0)
                .transactionRecord(txnRecord)
                .transaction(updateTransaction)
                .build());

        var dbAccountEntity = getTransactionEntity(txnRecord.getConsensusTimestamp());
        var stakedAccountId = EntityId.of(
                        transactionBody.getCryptoUpdateAccount().getStakedAccountId())
                .getId();

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEquals(expectedNanosTimestamp, dbAccountEntity.getExpirationTimestamp()),
                () -> assertTrue(dbAccountEntity.getDeclineReward()),
                () -> assertEquals(stakedAccountId, dbAccountEntity.getStakedAccountId()),
                () -> assertEquals(-1L, dbAccountEntity.getStakedNodeId()));
    }

    @Test
    void cryptoUpdateFailedTransaction() {
        Transaction createTransaction = cryptoCreateTransaction();
        TransactionRecord createRecord = transactionRecordSuccess(getTransactionBody(createTransaction));
        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(createRecord)
                .transaction(createTransaction)
                .build());

        // now update
        Transaction transaction = cryptoUpdateTransaction(accountId1.toAccountID());
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        Entity dbAccountEntityBefore = getTransactionEntity(createRecord.getConsensusTimestamp());
        Entity dbAccountEntity = getTransactionEntity(txnRecord.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEntities(accountId1),
                () -> assertCryptoTransfers(6), // 3 + 3 fee transfers with one transfer per account
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertAccount(txnRecord.getReceipt().getAccountID(), dbAccountEntity),
                () -> assertEquals(dbAccountEntityBefore, dbAccountEntity) // no changes to entity
                );
    }

    @Test
    void cryptoUpdateSuccessfulTransactionWithPaidStakingRewards() {
        // given
        var account = domainBuilder
                .entity()
                .customize(e -> e.declineReward(false).stakedNodeId(1L).stakePeriodStart(1L))
                .persist();
        long newStakedNodeId = 5L;
        var protoAccountId = account.toEntityId().toAccountID();

        // when
        var transactionId = transactionId(account.toEntityId(), domainBuilder.timestamp());
        var recordItem = recordItemBuilder
                .cryptoUpdate()
                .recordItem(r -> r.hapiVersion(RecordFile.HAPI_VERSION_0_27_0))
                .transactionBody(b -> b.setStakedNodeId(newStakedNodeId)
                        .setAccountIDToUpdate(protoAccountId)
                        .setMaxAutomaticTokenAssociations(Int32Value.of(-1)))
                .transactionBodyWrapper(w -> w.setTransactionID(transactionId))
                .record(r -> r.addPaidStakingRewards(accountAmount(account.getId(), 200L))
                        .setTransactionID(transactionId)
                        .setTransferList(TransferList.newBuilder()
                                .addAccountAmounts(accountAmount(systemEntity.stakingRewardAccount(), -200L))
                                .addAccountAmounts(accountAmount(account.getId(), 180L))
                                .addAccountAmounts(accountAmount(NODE, 5L))
                                .addAccountAmounts(accountAmount(systemEntity.feeCollectorAccount(), 15L))))
                .build();
        parseRecordItemAndCommit(recordItem);

        // then
        long expectedStakePeriodStart = Utility.getEpochDay(recordItem.getConsensusTimestamp());
        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(account.toEntityId()),
                () -> assertCryptoTransfers(4),
                () -> assertRecordItem(recordItem),
                () -> assertThat(entityRepository.findById(account.getId()))
                        .get()
                        .returns(newStakedNodeId, Entity::getStakedNodeId)
                        .returns(expectedStakePeriodStart, Entity::getStakePeriodStart)
                        .returns(-1, Entity::getMaxAutomaticTokenAssociations));
    }

    @Test
    void cryptoUpdateMemoSuccessfulTransactionWithPaidStakingRewards() {
        // given
        var account = domainBuilder
                .entity()
                .customize(e -> e.declineReward(false).stakedNodeId(1L).stakePeriodStart(1L))
                .persist();
        var protoAccountId = account.toEntityId().toAccountID();

        // when
        var transactionId = transactionId(account.toEntityId(), domainBuilder.timestamp());
        var recordItem = recordItemBuilder
                .cryptoUpdate()
                .transactionBody(b -> b.clearDeclineReward()
                        .clearStakedAccountId()
                        .clearStakedNodeId()
                        .setAccountIDToUpdate(protoAccountId)
                        .setMemo(StringValue.of("new memo")))
                .transactionBodyWrapper(w -> w.setTransactionID(transactionId))
                .record(r -> r.addPaidStakingRewards(accountAmount(account.getId(), 200L))
                        .setTransactionID(transactionId)
                        .setTransferList(TransferList.newBuilder()
                                .addAccountAmounts(accountAmount(systemEntity.stakingRewardAccount(), -200L))
                                .addAccountAmounts(accountAmount(account.getId(), 180L))
                                .addAccountAmounts(accountAmount(NODE, 5L))
                                .addAccountAmounts(accountAmount(systemEntity.feeCollectorAccount(), 15L))))
                .build();
        parseRecordItemAndCommit(recordItem);

        // then
        var expectedStakePeriodStart = Utility.getEpochDay(recordItem.getConsensusTimestamp()) - 1;
        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(account.toEntityId()),
                () -> assertCryptoTransfers(4),
                () -> assertRecordItem(recordItem),
                () -> assertThat(entityRepository.findById(account.getId()))
                        .get()
                        .returns(1L, Entity::getStakedNodeId)
                        .returns(expectedStakePeriodStart, Entity::getStakePeriodStart));
    }

    @Test
    void cryptoUpdateFailedTransactionWithPaidStakingRewards() {
        // given
        var account = domainBuilder
                .entity()
                .customize(e -> e.declineReward(false).stakedNodeId(1L).stakePeriodStart(1L))
                .persist();
        var protoAccountId = account.toEntityId().toAccountID();

        // when
        var transactionId = transactionId(account.toEntityId(), domainBuilder.timestamp());
        var recordItem = recordItemBuilder
                .cryptoUpdate()
                .transactionBody(b -> b.setStakedNodeId(5L).setAccountIDToUpdate(protoAccountId))
                .transactionBodyWrapper(w -> w.setTransactionID(transactionId))
                .record(r -> r.addPaidStakingRewards(accountAmount(account.getId(), 200L))
                        .setTransactionID(transactionId)
                        .setTransferList(TransferList.newBuilder()
                                .addAccountAmounts(accountAmount(systemEntity.stakingRewardAccount(), -200L))
                                .addAccountAmounts(accountAmount(account.getId(), 180L))
                                .addAccountAmounts(accountAmount(NODE, 5L))
                                .addAccountAmounts(accountAmount(systemEntity.feeCollectorAccount(), 15L))))
                .receipt(r -> r.setStatus(ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE))
                .build();
        parseRecordItemAndCommit(recordItem);

        // then
        long expectedStakePeriodStart = Utility.getEpochDay(recordItem.getConsensusTimestamp()) - 1;
        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(account.toEntityId()),
                () -> assertCryptoTransfers(4),
                () -> assertRecordItem(recordItem),
                () -> assertThat(entityRepository.findById(account.getId()))
                        .get()
                        .returns(1L, Entity::getStakedNodeId)
                        .returns(expectedStakePeriodStart, Entity::getStakePeriodStart));
    }

    @Test
    void cryptoDeleteSuccessfulTransaction() {
        // first create the account
        createAccount();
        Entity dbAccountEntityBefore = getEntity(accountId1);

        // now delete
        Transaction transaction = cryptoDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        Entity dbAccountEntity = getTransactionEntity(txnRecord.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEntities(accountId1),
                () -> assertCryptoTransfers(6), // 3 + 3 fee transfers with one transfer per account
                () -> assertCryptoTransaction(transactionBody, txnRecord),
                () -> assertThat(dbAccountEntity)
                        .isNotNull()
                        .returns(true, Entity::getDeleted)
                        .returns(
                                DomainUtils.timestampInNanosMax(txnRecord.getConsensusTimestamp()),
                                Entity::getTimestampLower)
                        .usingRecursiveComparison()
                        .ignoringFields("deleted", "timestampRange")
                        .isEqualTo(dbAccountEntityBefore));
    }

    @Test
    void cryptoDeleteFailedTransaction() {
        createAccount();

        // now delete
        Transaction transaction = cryptoDeleteTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = transactionRecord(
                transactionBody,
                ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE.getNumber(),
                recordBuilder -> groupCryptoTransfersByAccountId(recordBuilder, List.of()));

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        Entity dbAccountEntity = getTransactionEntity(txnRecord.getConsensusTimestamp());

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEntities(accountId1),
                () -> assertCryptoTransfers(6), // 3 + 3 fee transfers with only one transfer per account
                () -> assertCryptoTransaction(transactionBody, txnRecord),
                () -> assertThat(dbAccountEntity).isNotNull().returns(false, Entity::getDeleted));
    }

    @SuppressWarnings("deprecation")
    @Test
    void cryptoAddLiveHashPersist() {
        Transaction transaction = cryptoAddLiveHashTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        CryptoAddLiveHashTransactionBody cryptoAddLiveHashTransactionBody = transactionBody.getCryptoAddLiveHash();
        TransactionRecord txnRecord = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        var dbTransaction = getDbTransaction(txnRecord.getConsensusTimestamp());
        LiveHash dbLiveHash = liveHashRepository
                .findById(dbTransaction.getConsensusTimestamp())
                .get();

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(),
                () -> assertCryptoTransfers(3),
                () -> assertEquals(1, liveHashRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertArrayEquals(
                        cryptoAddLiveHashTransactionBody.getLiveHash().getHash().toByteArray(),
                        dbLiveHash.getLivehash()));
    }

    @SuppressWarnings("deprecation")
    @Test
    void cryptoAddLiveHashDoNotPersist() {
        entityProperties.getPersist().setClaims(false);
        Transaction transaction = cryptoAddLiveHashTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(),
                () -> assertCryptoTransfers(3),
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, txnRecord));
    }

    @SuppressWarnings("deprecation")
    @Test
    void cryptoDeleteLiveHash() {
        Transaction transactionAddLiveHash = cryptoAddLiveHashTransaction();
        var recordLiveHash = transactionRecordSuccess(getTransactionBody(transactionAddLiveHash));
        var recordItem = RecordItem.builder()
                .transactionRecord(recordLiveHash)
                .transaction(transactionAddLiveHash)
                .build();
        parseRecordItemAndCommit(recordItem);

        // now delete the live hash
        Transaction transaction = cryptoDeleteLiveHashTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        transactionBody.getCryptoDeleteLiveHash();
        TransactionRecord txnRecord = transactionRecordSuccess(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                this::assertEntities,
                () -> assertCryptoTransfers(6),
                () -> assertEquals(1, liveHashRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, txnRecord));
    }

    @Test
    void cryptoTransferWithPersistence() {
        entityProperties.getPersist().setCryptoTransferAmounts(true);
        // make the transfers
        var transaction = cryptoTransferTransaction();
        var transactionBody = getTransactionBody(transaction);
        var txnRecord = transactionRecordSuccess(transactionBody);
        var recordItem = RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build();
        var entityIds = txnRecord.getTransferList().getAccountAmountsList().stream()
                .map(aa -> EntityId.of(aa.getAccountID()))
                .collect(Collectors.toList());
        entityIds.add(EntityId.of(transactionBody.getNodeAccountID()));
        entityIds.add(recordItem.getPayerAccountId());
        var expectedEntityTransactions = toEntityTransactions(
                        recordItem, entityIds, entityProperties.getPersist().getEntityTransactionExclusion())
                .values();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(),
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, txnRecord));
        assertThat(entityTransactionRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedEntityTransactions);
    }

    @Test
    void cryptoTransferWithoutPersistence() {
        entityProperties.getPersist().setCryptoTransferAmounts(false);
        // make the transfers
        var transaction = cryptoTransferTransaction();
        var transactionBody = getTransactionBody(transaction);
        var txnRecord = transactionRecordSuccess(transactionBody);
        var recordItem = RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build();
        var expectedEntityTransactions = toEntityTransactions(
                recordItem, EntityId.of(transactionBody.getNodeAccountID()), recordItem.getPayerAccountId());
        for (var aa : transactionBody.getCryptoTransfer().getTransfers().getAccountAmountsList()) {
            var accountId = EntityId.of(aa.getAccountID());
            expectedEntityTransactions.putIfAbsent(accountId.getId(), toEntityTransaction(accountId, recordItem));
        }

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertCryptoTransfers(0),
                () -> assertTransactionAndRecord(transactionBody, txnRecord));
        assertThat(entityTransactionRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedEntityTransactions.values());
    }

    @Test
    void cryptoTransferWithEntityTransactionDisabled() {
        entityProperties.getPersist().setEntityTransactions(false);
        // make the transfers
        var transaction = cryptoTransferTransaction();
        var transactionBody = getTransactionBody(transaction);
        var txnRecord = transactionRecordSuccess(transactionBody);
        var recordItem = RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(),
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, txnRecord));
        assertThat(entityTransactionRepository.findAll()).isEmpty();
    }

    @Test
    void cryptoTransferFailedTransaction() {
        entityProperties.getPersist().setCryptoTransferAmounts(true);
        // make the transfers
        Transaction transaction = cryptoTransferTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = transactionRecord(transactionBody, ResponseCodeEnum.INVALID_ACCOUNT_ID);

        var recordItem = RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build();
        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(),
                () -> assertCryptoTransfers(3),
                () -> assertThat(transactionRepository.findById(recordItem.getConsensusTimestamp()))
                        .get()
                        .extracting(org.hiero.mirror.common.domain.transaction.Transaction::getItemizedTransfer)
                        .isNull(),
                () -> assertTransactionAndRecord(transactionBody, txnRecord));
    }

    @Test
    void cryptoTransferFailedTransactionErrata() {
        entityProperties.getPersist().setCryptoTransferAmounts(true);
        Transaction transaction = cryptoTransferTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        var tokenId = EntityId.of(1020L);
        long amount = 100L;

        TransactionRecord txnRecord = buildTransactionRecord(
                r -> {
                    r.setConsensusTimestamp(TestUtils.toTimestamp(1577836799000000000L - 1));
                    for (int i = 0; i < additionalTransfers.length; i++) {
                        // Add non-fee transfers to record
                        var accountAmount = accountAmount(additionalTransfers[i], additionalTransferAmounts[i]);
                        r.getTransferListBuilder().addAccountAmounts(accountAmount);
                    }
                    r.addTokenTransferLists(TokenTransferList.newBuilder()
                            .setToken(tokenId.toTokenID())
                            .addTransfers(AccountAmount.newBuilder()
                                    .setAccountID(accountId1.toAccountID())
                                    .setAmount(amount)));
                },
                transactionBody,
                ResponseCodeEnum.FAIL_INVALID.getNumber());

        var recordItem = RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build();
        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEntities(),
                () -> assertEquals(4, cryptoTransferRepository.count(), "Node, network fee & errata"),
                () -> assertThat(transactionRepository.findById(recordItem.getConsensusTimestamp()))
                        .get()
                        .extracting(org.hiero.mirror.common.domain.transaction.Transaction::getItemizedTransfer)
                        .isNull(),
                () -> assertThat(tokenTransferRepository.findAll())
                        .hasSize(1)
                        .first()
                        .returns(tokenId, t -> t.getId().getTokenId())
                        .returns(amount, t -> t.getAmount())
                        .returns(accountId1, t -> t.getId().getAccountId()),
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> {
                    for (int i = 0; i < additionalTransfers.length; i++) {
                        var id = new CryptoTransfer.Id(
                                additionalTransferAmounts[i],
                                recordItem.getConsensusTimestamp(),
                                additionalTransfers[i]);
                        assertThat(cryptoTransferRepository.findById(id))
                                .get()
                                .extracting(CryptoTransfer::getErrata)
                                .isEqualTo(ErrataType.DELETE);
                    }
                });
    }

    @Test
    void cryptoTransferHasCorrectIsApprovalValue() {
        final long[] accountIds = {
            EntityId.of(PAYER).getId(),
            EntityId.of(PAYER2).getId(),
            EntityId.of(PAYER3).getId()
        };
        final long[] amounts = {210, -300, 15};
        final boolean[] isApprovals = {false, true, false};
        Transaction transaction = buildTransaction(r -> {
            for (int i = 0; i < accountIds.length; i++) {
                var accountAmount = accountAmount(accountIds[i], amounts[i])
                        .setIsApproval(isApprovals[i])
                        .build();
                r.getCryptoTransferBuilder().getTransfersBuilder().addAccountAmounts(accountAmount);
            }
        });
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = buildTransactionRecordWithNoTransactions(
                builder -> {
                    for (int i = 0; i < accountIds.length; i++) {
                        var accountAmount = accountAmount(accountIds[i], amounts[i])
                                .setIsApproval(false)
                                .build();
                        builder.getTransferListBuilder().addAccountAmounts(accountAmount);
                    }
                },
                transactionBody,
                ResponseCodeEnum.SUCCESS.getNumber());

        var recordItem = RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build();
        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                // Approved transfer allowance debit emitted to listener must not have resulted in created allowance
                () -> assertEquals(0, cryptoAllowanceRepository.count()),
                () -> assertEquals(amounts.length, cryptoTransferRepository.count()),
                () -> {
                    for (var cryptoTransfer : cryptoTransferRepository.findAll()) {
                        for (int i = 0; i < isApprovals.length; i++) {
                            if (cryptoTransfer.getEntityId() != accountIds[i]) {
                                continue;
                            }
                            assertThat(cryptoTransfer)
                                    .extracting(CryptoTransfer::getIsApproval)
                                    .isEqualTo(isApprovals[i]);
                        }
                    }
                });
    }

    @Test
    void cryptoTransferUpdatesAllowanceAmount() {
        entityProperties.getPersist().setTrackAllowance(true);
        var allowanceAmountGranted = 1000L;

        var payerAccount = EntityId.of(PAYER);

        // Persist the now pre-existing crypto allowance to be debited by the approved transfers below
        var cryptoAllowance = domainBuilder
                .cryptoAllowance()
                .customize(ca -> {
                    ca.amountGranted(allowanceAmountGranted).amount(allowanceAmountGranted);
                    ca.spender(payerAccount.getId());
                })
                .persist();

        var ownerAccountId = EntityId.of(cryptoAllowance.getOwner()).toAccountID();
        var spenderAccountId = EntityId.of(cryptoAllowance.getSpender()).toAccountID();
        var cryptoTransfers = List.of(
                AccountAmount.newBuilder()
                        .setAmount(-100)
                        .setAccountID(ownerAccountId)
                        .setIsApproval(true)
                        .build(),
                AccountAmount.newBuilder()
                        .setAmount(-200)
                        .setAccountID(ownerAccountId)
                        .setIsApproval(true)
                        .build(),
                AccountAmount.newBuilder()
                        .setAmount(-500)
                        .setAccountID(recordItemBuilder.accountId()) // Some other owner
                        .setIsApproval(true)
                        .build());

        Transaction transaction = buildTransaction(
                r -> r.getCryptoTransferBuilder().getTransfersBuilder().addAllAccountAmounts(cryptoTransfers));

        TransactionBody transactionBody = getTransactionBody(transaction);
        var recordCryptoTransfers = cryptoTransfers.stream()
                .map(transfer -> transfer.toBuilder().setIsApproval(false).build())
                .toList();
        TransactionRecord txnRecord = buildTransactionRecordWithNoTransactions(
                builder -> builder.getTransferListBuilder().addAllAccountAmounts(recordCryptoTransfers),
                transactionBody,
                ResponseCodeEnum.SUCCESS.getNumber());

        var recordItem = RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(1, cryptoAllowanceRepository.count()),
                () -> assertEquals(cryptoTransfers.size(), cryptoTransferRepository.count()),
                () -> {
                    var cryptoAllowanceId = new Id();
                    cryptoAllowanceId.setOwner(EntityId.of(ownerAccountId).getId());
                    cryptoAllowanceId.setSpender(EntityId.of(spenderAccountId).getId());

                    var cryptoAllowanceDbOpt = cryptoAllowanceRepository.findById(cryptoAllowanceId);
                    assertThat(cryptoAllowanceDbOpt).isNotEmpty();

                    var cryptoAllowanceDb = cryptoAllowanceDbOpt.get();
                    assertThat(cryptoAllowanceDb.getAmountGranted()).isEqualTo(allowanceAmountGranted);
                    var amountTransferred = cryptoTransfers.get(0).getAmount()
                            + cryptoTransfers.get(1).getAmount();
                    assertThat(cryptoAllowanceDb.getAmount()).isEqualTo(allowanceAmountGranted + amountTransferred);
                });
    }

    @Test
    void cryptoTransferWithAlias() {
        var haltOnError = System.getProperty(HALT_ON_ERROR_PROPERTY, "false");
        System.setProperty(HALT_ON_ERROR_PROPERTY, "true");
        entityProperties.getPersist().setCryptoTransferAmounts(true);
        entityProperties.getPersist().setItemizedTransfers(true);
        Entity entity = domainBuilder.entity().persist();
        var newAccount = domainBuilder.entityId().toAccountID();
        assertThat(entityRepository.findByAlias(entity.getAlias())).get().isEqualTo(entity.getId());
        assertThat(entityRepository.findByAlias(ALIAS_KEY.toByteArray())).isNotPresent();

        // Crypto create alias account
        Transaction accountCreateTransaction = cryptoCreateTransaction();
        TransactionBody accountCreateTransactionBody = getTransactionBody(accountCreateTransaction);
        TransactionRecord recordCreate = buildTransactionRecord(
                recordBuilder ->
                        recordBuilder.setAlias(ALIAS_KEY).getReceiptBuilder().setAccountID(newAccount),
                accountCreateTransactionBody,
                ResponseCodeEnum.SUCCESS.getNumber());

        var transfer1 = accountAliasAmount(ALIAS_KEY, 1003).build();
        var transfer2 = accountAliasAmount(DomainUtils.fromBytes(entity.getAlias()), 1004)
                .build();
        var transfer3 = accountAliasAmount(DomainUtils.fromBytes(entity.getEvmAddress()), 1005)
                .build();
        // Crypto transfer to both existing alias and newly created alias accounts
        Transaction transaction = buildTransaction(builder -> builder.getCryptoTransferBuilder()
                .getTransfersBuilder()
                .addAccountAmounts(transfer1)
                .addAccountAmounts(transfer2)
                .addAccountAmounts(transfer3));
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord recordTransfer = transactionRecordSuccess(
                transactionBody, builder -> groupCryptoTransfersByAccountId(builder, List.of()));
        var recordItem1 = RecordItem.builder()
                .transactionRecord(recordCreate)
                .transaction(accountCreateTransaction)
                .build();
        var recordItem2 = RecordItem.builder()
                .transactionRecord(recordTransfer)
                .transaction(transaction)
                .build();
        parseRecordItemsAndCommit(List.of(recordItem1, recordItem2));

        assertAll(
                () -> assertEquals(2, transactionRepository.count()),
                () -> assertEntities(EntityId.of(newAccount), entity.toEntityId()),
                () -> assertCryptoTransfers(6)
                        .areAtMost(1, isAccountAmountReceiverAccountAmount(transfer1))
                        .areAtMost(1, isAccountAmountReceiverAccountAmount(transfer2)),
                () -> assertTransactionAndRecord(transactionBody, recordTransfer),
                () -> assertThat(transactionRepository.findById(recordItem1.getConsensusTimestamp()))
                        .get()
                        .extracting(org.hiero.mirror.common.domain.transaction.Transaction::getItemizedTransfer)
                        .isNull(),
                () -> assertThat(transactionRepository.findById(recordItem2.getConsensusTimestamp()))
                        .get()
                        .extracting(org.hiero.mirror.common.domain.transaction.Transaction::getItemizedTransfer)
                        .asInstanceOf(InstanceOfAssertFactories.LIST)
                        .map(transfer ->
                                ((ItemizedTransfer) transfer).getEntityId().getId())
                        .asInstanceOf(InstanceOfAssertFactories.LIST)
                        .containsExactly(EntityId.of(newAccount).getId(), entity.getId(), entity.getId()));
        System.setProperty(HALT_ON_ERROR_PROPERTY, haltOnError);
    }

    @Test
    void cryptoTransferWithEvmAddressAlias() {
        Entity contract = domainBuilder.entity().persist();
        assertThat(entityRepository.findByEvmAddress(contract.getEvmAddress())).isPresent();

        entityProperties.getPersist().setItemizedTransfers(true);

        long transferAmount = 123;
        var transfer1 = accountAliasAmount(DomainUtils.fromBytes(contract.getEvmAddress()), transferAmount)
                .build();
        Transaction transaction = buildTransaction(builder ->
                builder.getCryptoTransferBuilder().getTransfersBuilder().addAccountAmounts(transfer1));
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord transactionRecord = transactionRecordSuccess(transactionBody);
        RecordItem recordItem = RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build();
        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, transactionRecord),
                () -> assertThat(transactionRepository.findById(recordItem.getConsensusTimestamp()))
                        .get()
                        .extracting(org.hiero.mirror.common.domain.transaction.Transaction::getItemizedTransfer)
                        .asInstanceOf(InstanceOfAssertFactories.LIST)
                        .hasSize(1)
                        .allSatisfy(transfer -> {
                            assertThat(((ItemizedTransfer) transfer).getEntityId())
                                    .isEqualTo(contract.toEntityId());
                            assertThat(((ItemizedTransfer) transfer).getAmount())
                                    .isEqualTo(transferAmount);
                        }));
    }

    @Test
    void cryptoTransferWithUnknownAlias() {
        // given
        // both accounts have alias, and only account2's alias is in db
        entityProperties.getPersist().setCryptoTransferAmounts(true);
        entityProperties.getPersist().setItemizedTransfers(true);

        Entity account1 = domainBuilder.entity().get();
        Entity account2 = domainBuilder.entity().persist();

        // crypto transfer from unknown account1 alias to account2 alias
        Transaction transaction = buildTransaction(builder -> builder.getCryptoTransferBuilder()
                .getTransfersBuilder()
                .addAccountAmounts(accountAliasAmount(DomainUtils.fromBytes(account1.getAlias()), 100))
                .addAccountAmounts(accountAliasAmount(DomainUtils.fromBytes(account2.getAlias()), -100)));
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord transactionRecord = buildTransactionRecord(
                r -> r.getTransferListBuilder()
                        .addAccountAmounts(accountAmount(account1.getNum(), 100))
                        .addAccountAmounts(accountAmount(account2.getNum(), -100)),
                transactionBody,
                ResponseCodeEnum.SUCCESS.getNumber());
        List<EntityId> expectedEntityIds = List.of(account2.toEntityId());
        var recordItem = RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build();
        // when
        parseRecordItemAndCommit(recordItem);

        // then
        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(5, cryptoTransferRepository.count()),
                () -> assertTransactionAndRecord(transactionBody, transactionRecord),
                () -> assertThat(transactionRepository.findById(recordItem.getConsensusTimestamp()))
                        .get()
                        .extracting(org.hiero.mirror.common.domain.transaction.Transaction::getItemizedTransfer)
                        .asInstanceOf(InstanceOfAssertFactories.LIST)
                        .map(transfer -> ((ItemizedTransfer) transfer).getEntityId())
                        .asInstanceOf(InstanceOfAssertFactories.LIST)
                        .containsExactlyInAnyOrderElementsOf(expectedEntityIds));
    }

    @Test
    void cryptoTransferPersistRawBytesDefault() {
        // Use the default properties for record parsing - the raw bytes should NOT be stored in the db
        Transaction transaction = cryptoTransferTransaction();
        testRawBytes(transaction, null);
    }

    @Test
    void cryptoTransferPersistRawBytesTrue() {
        // Explicitly persist the transaction bytes
        entityProperties.getPersist().setTransactionBytes(true);
        Transaction transaction = cryptoTransferTransaction();
        testRawBytes(transaction, transaction.toByteArray());
    }

    @Test
    void cryptoTransferPersistRawBytesFalse() {
        // Explicitly DO NOT persist the transaction bytes
        entityProperties.getPersist().setTransactionBytes(false);
        Transaction transaction = cryptoTransferTransaction();
        testRawBytes(transaction, null);
    }

    @SuppressWarnings("deprecation")
    @Test
    void finalizeHollowAccountToContract() {
        // given
        var accountId = recordItemBuilder.accountId();
        var contractId = EntityId.of(accountId).toContractID();
        var evmAddress = recordItemBuilder.evmAddress();
        var cryptoCreate = recordItemBuilder
                .cryptoCreate()
                .transactionBody(b -> b.clearAlias().clearKey().setAlias(evmAddress.getValue()))
                .receipt(r -> r.setAccountID(accountId))
                .build();
        var contractCreate = recordItemBuilder
                .contractCreate()
                .receipt(r -> r.setContractID(contractId))
                .record(r -> r.getContractCreateResultBuilder()
                        .clearCreatedContractIDs()
                        .setContractID(contractId)
                        .setEvmAddress(evmAddress))
                .build();

        // when
        parseRecordItemsAndCommit(List.of(cryptoCreate, contractCreate));

        // then
        long createdTimestamp = cryptoCreate.getConsensusTimestamp();
        var expectedAccount = Entity.builder()
                .createdTimestamp(createdTimestamp)
                .evmAddress(DomainUtils.toBytes(evmAddress.getValue()))
                .id(EntityId.of(accountId).getId())
                .timestampRange(Range.closedOpen(createdTimestamp, contractCreate.getConsensusTimestamp()))
                .type(EntityType.ACCOUNT)
                .build();
        var expectedContract = expectedAccount.toBuilder()
                .timestampRange(Range.atLeast(contractCreate.getConsensusTimestamp()))
                .type(EntityType.CONTRACT)
                .build();
        var expectedFileId = EntityId.of(
                contractCreate.getTransactionBody().getContractCreateInstance().getFileID());
        String[] fields = new String[] {"createdTimestamp", "evmAddress", "id", "timestampRange", "type"};
        assertThat(entityRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields(fields)
                .containsExactly(expectedContract);
        assertThat(findHistory(Entity.class))
                .usingRecursiveFieldByFieldElementComparatorOnFields(fields)
                .containsExactly(expectedAccount);
        assertThat(contractRepository.findById(expectedContract.getId()))
                .get()
                .returns(expectedFileId, Contract::getFileId);
    }

    @SuppressWarnings("deprecation")
    @Test
    void finalizeHollowAccountToContractInTwoRecordFiles() {
        // given
        var accountId = recordItemBuilder.accountId();
        var evmAddress = recordItemBuilder.evmAddress();
        var cryptoCreate = recordItemBuilder
                .cryptoCreate()
                .transactionBody(b -> b.clearAlias()
                        .clearKey()
                        .setAlias(evmAddress.getValue())
                        .setMaxAutomaticTokenAssociations(-1))
                .receipt(r -> r.setAccountID(accountId))
                .build();

        // when
        parseRecordItemAndCommit(cryptoCreate);

        // then
        var expectedAccount = Entity.builder()
                .createdTimestamp(cryptoCreate.getConsensusTimestamp())
                .evmAddress(DomainUtils.toBytes(evmAddress.getValue()))
                .id(EntityId.of(accountId).getId())
                .timestampRange(Range.atLeast(cryptoCreate.getConsensusTimestamp()))
                .type(EntityType.ACCOUNT)
                .build();
        String[] fields = new String[] {"createdTimestamp", "evmAddress", "id", "timestampRange", "type"};
        assertThat(entityRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields(fields)
                .containsExactly(expectedAccount)
                .map(e -> e.getMaxAutomaticTokenAssociations())
                .containsOnly(-1);
        assertThat(findHistory(Entity.class)).isEmpty();

        // when
        var contractId = EntityId.of(accountId).toContractID();
        var contractCreate = recordItemBuilder
                .contractCreate()
                .receipt(r -> r.setContractID(contractId))
                .record(r -> r.getContractCreateResultBuilder()
                        .clearCreatedContractIDs()
                        .setContractID(contractId)
                        .setEvmAddress(evmAddress))
                .build();
        parseRecordItemAndCommit(contractCreate);

        // then
        expectedAccount.setTimestampUpper(contractCreate.getConsensusTimestamp());
        var expectedContract = expectedAccount.toBuilder()
                .timestampRange(Range.atLeast(contractCreate.getConsensusTimestamp()))
                .type(EntityType.CONTRACT)
                .build();
        var expectedFileId = EntityId.of(
                contractCreate.getTransactionBody().getContractCreateInstance().getFileID());
        assertThat(entityRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields(fields)
                .containsExactly(expectedContract);
        assertThat(findHistory(Entity.class))
                .usingRecursiveFieldByFieldElementComparatorOnFields(fields)
                .containsExactly(expectedAccount);
        assertThat(contractRepository.findById(expectedContract.getId()))
                .get()
                .returns(expectedFileId, Contract::getFileId);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void persistTransactionRecordBytes(boolean persist) {
        // given
        entityProperties.getPersist().setTransactionRecordBytes(persist);
        var recordItem = recordItemBuilder.cryptoTransfer().build();
        var transactionRecordBytes = persist ? recordItem.getTransactionRecord().toByteArray() : null;

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        assertThat(transactionRepository.findAll())
                .hasSize(1)
                .extracting(org.hiero.mirror.common.domain.transaction.Transaction::getTransactionRecordBytes)
                .containsOnly(transactionRecordBytes);
    }

    @Test
    void twoStakingRewardPayoutsInSameBlockAcrossDayBoundary() {
        // given
        // an account started to staked to node 1 5 days ago
        long currentDay = Utility.getEpochDay(domainBuilder.timestamp());
        long stakePeriodStart = currentDay - 5; // started to stake to a node 5 days ago
        long createdTimestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(stakePeriodStart));
        var account = domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(createdTimestamp)
                        .stakedNodeId(1L)
                        .stakePeriodStart(stakePeriodStart)
                        .timestampRange(Range.atLeast(createdTimestamp)))
                .persist();

        // when
        // 5 ns before the next UTC midnight
        var beforeNextMidnight = TestUtils.asStartOfEpochDay(currentDay + 1).minusNanos(5);
        // for simplicity, use a ConsensusSubmitMessage transaction to trigger the staking reward payout
        var consensusSubmitMessage1 = recordItemBuilder
                .consensusSubmitMessage()
                .record(r -> r.setConsensusTimestamp(Utility.instantToTimestamp(beforeNextMidnight))
                        .addPaidStakingRewards(
                                accountAmount(account.toEntityId().getId(), 200))
                        .setTransferList(TransferList.newBuilder()
                                .addAccountAmounts(
                                        accountAmount(account.toEntityId().getId(), 100))
                                .addAccountAmounts(accountAmount(2, 20))
                                .addAccountAmounts(accountAmount(systemEntity.feeCollectorAccount(), 80))
                                .addAccountAmounts(accountAmount(systemEntity.stakingRewardAccount(), -200))))
                .build();
        var afterNextMidnight = TestUtils.asStartOfEpochDay(currentDay + 1).plusNanos(5);
        var consensusSubmitMessage2 = recordItemBuilder
                .consensusSubmitMessage()
                .record(r -> r.setConsensusTimestamp(Utility.instantToTimestamp(afterNextMidnight))
                        .addPaidStakingRewards(
                                accountAmount(account.toEntityId().getId(), 60))
                        .setTransferList(TransferList.newBuilder()
                                .addAccountAmounts(
                                        accountAmount(account.toEntityId().getId(), -40))
                                .addAccountAmounts(accountAmount(2, 20))
                                .addAccountAmounts(accountAmount(systemEntity.feeCollectorAccount(), 80))
                                .addAccountAmounts(accountAmount(systemEntity.stakingRewardAccount(), -60))))
                .build();
        parseRecordItemsAndCommit(List.of(consensusSubmitMessage1, consensusSubmitMessage2));

        // then
        // expect two account history rows: the first has the original stake period start, the second has the stake
        // period start set to currentDay - 1 as a result of the staking reward transfer before midnight
        // the current account row should have stake period start set to currentDay, i.e., nextDay - 1, as a result of
        // the stake reward transfer after midnight
        var expectedAccountHistory1 = account.toBuilder()
                .timestampRange(Range.closedOpen(
                        account.getCreatedTimestamp(), consensusSubmitMessage1.getConsensusTimestamp()))
                .build();
        var expectedAccountHistory2 = account.toBuilder()
                .balance(account.getBalance() + 100 - 40)
                .balanceTimestamp(consensusSubmitMessage2.getConsensusTimestamp())
                .stakePeriodStart(currentDay - 1)
                .timestampRange(Range.closedOpen(
                        consensusSubmitMessage1.getConsensusTimestamp(),
                        consensusSubmitMessage2.getConsensusTimestamp()))
                .build();
        var expectedAccount = account.toBuilder()
                .balance(account.getBalance() + 100 - 40)
                .balanceTimestamp(consensusSubmitMessage2.getConsensusTimestamp())
                .stakePeriodStart(currentDay)
                .timestampRange(Range.atLeast(consensusSubmitMessage2.getConsensusTimestamp()))
                .build();
        var expectedStakingRewardTransfers = List.of(
                StakingRewardTransfer.builder()
                        .accountId(account.getId())
                        .consensusTimestamp(consensusSubmitMessage1.getConsensusTimestamp())
                        .amount(200)
                        .payerAccountId(consensusSubmitMessage1.getPayerAccountId())
                        .build(),
                StakingRewardTransfer.builder()
                        .accountId(account.getId())
                        .consensusTimestamp(consensusSubmitMessage2.getConsensusTimestamp())
                        .amount(60)
                        .payerAccountId(consensusSubmitMessage2.getPayerAccountId())
                        .build());
        assertAll(
                () -> assertEquals(0, contractRepository.count()),
                () -> assertEquals(8, cryptoTransferRepository.count()),
                () -> assertThat(entityRepository.findAll()).containsExactly(expectedAccount),
                () -> assertThat(findHistory(Entity.class))
                        .containsExactlyInAnyOrder(expectedAccountHistory1, expectedAccountHistory2),
                () -> assertThat(stakingRewardTransferRepository.findAll())
                        .containsExactlyInAnyOrderElementsOf(expectedStakingRewardTransfers),
                () -> assertEquals(2, topicMessageRepository.count()),
                () -> assertEquals(2, transactionRepository.count()));
    }

    @Test
    void unknownTransactionResult() {
        int unknownResult = -1000;
        Transaction transaction = cryptoCreateTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord transactionRecord = transactionRecord(transactionBody, unknownResult);

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());

        assertThat(transactionRepository.findAll())
                .hasSize(1)
                .extracting(org.hiero.mirror.common.domain.transaction.Transaction::getResult)
                .containsOnly(unknownResult);
    }

    @Test
    void cryptoCreateWithHooks() {
        // given
        var recordItem = recordItemBuilder.cryptoCreate().build();

        var accountId = recordItem.getTransactionRecord().getReceipt().getAccountID();
        var cryptoCreateBody = recordItem.getTransactionBody().getCryptoCreateAccount();
        var hookCreationDetails = cryptoCreateBody.getHookCreationDetails(0);

        // Extract hook details from the transaction
        var hookId = hookCreationDetails.getHookId();
        var contractId =
                EntityId.of(hookCreationDetails.getLambdaEvmHook().getSpec().getContractId());
        var adminKey = hookCreationDetails.getAdminKey();

        // when
        parseRecordItemAndCommit(recordItem);

        // Find and verify hook from database
        var hookOptional = hookRepository.findById(
                new AbstractHook.Id(hookId, EntityId.of(accountId).getId()));
        assertAll(
                () -> assertEntities(EntityId.of(accountId)),
                () -> assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord()));

        var dbHook = hookOptional.get();
        assertAll(
                () -> assertEquals(hookId, dbHook.getHookId()),
                () -> assertEquals(contractId, dbHook.getContractId()),
                () -> assertArrayEquals(adminKey.toByteArray(), dbHook.getAdminKey()),
                () -> assertEquals(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK, dbHook.getExtensionPoint()),
                () -> assertEquals(HookType.LAMBDA, dbHook.getType()),
                () -> assertEquals(EntityId.of(accountId).getId(), dbHook.getOwnerId()),
                () -> assertEquals(recordItem.getConsensusTimestamp(), dbHook.getCreatedTimestamp()),
                () -> assertFalse(dbHook.getDeleted()));
    }

    @Test
    void cryptoUpdateWithHooksSequentialOperations() {
        // Step 1: Create an account with hooks
        var createAccountWithHooksRecordItem = recordItemBuilder.cryptoCreate().build();
        parseRecordItemAndCommit(createAccountWithHooksRecordItem);

        var accountId = createAccountWithHooksRecordItem
                .getTransactionRecord()
                .getReceipt()
                .getAccountID();
        var cryptoCreateBody =
                createAccountWithHooksRecordItem.getTransactionBody().getCryptoCreateAccount();
        var hookCreationDetails = cryptoCreateBody.getHookCreationDetails(0);

        // Extract hook details from the transaction
        var hookId = hookCreationDetails.getHookId();
        var contractId =
                EntityId.of(hookCreationDetails.getLambdaEvmHook().getSpec().getContractId());
        var adminKey = hookCreationDetails.getAdminKey();

        // Assert after creation
        final var hookOptional = hookRepository.findById(
                new AbstractHook.Id(hookId, EntityId.of(accountId).getId()));
        assertAll(
                () -> assertTrue(hookOptional.isPresent(), "Hook should exist after creation"),
                () -> assertFalse(hookOptional.get().getDeleted(), "Hook should not be deleted after creation"),
                () -> assertEquals(1, hookRepository.count(), "Should have 1 hook after creation"));
        var dbHook = hookOptional.get();
        assertAll(
                () -> assertEquals(hookId, dbHook.getHookId()),
                () -> assertEquals(contractId, dbHook.getContractId()),
                () -> assertArrayEquals(adminKey.toByteArray(), dbHook.getAdminKey()),
                () -> assertEquals(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK, dbHook.getExtensionPoint()),
                () -> assertEquals(HookType.LAMBDA, dbHook.getType()),
                () -> assertEquals(EntityId.of(accountId).getId(), dbHook.getOwnerId()),
                () -> assertEquals(
                        createAccountWithHooksRecordItem.getConsensusTimestamp(), dbHook.getCreatedTimestamp()),
                () -> assertFalse(dbHook.getDeleted()));

        // Step 2: Delete and create hook in same transaction
        var deletionAndCreationRecordItem = recordItemBuilder
                .cryptoUpdate()
                .transactionBody(b -> b.addHookIdsToDelete(1L).setAccountIDToUpdate(accountId))
                .build();
        parseRecordItemAndCommit(deletionAndCreationRecordItem);

        // Assert after deletion and creation in same transaction
        final var hookOptional2 = hookRepository.findById(
                new AbstractHook.Id(hookId, EntityId.of(accountId).getId()));
        assertAll(
                () -> assertTrue(hookOptional2.isPresent(), "Hook should exist after deletion and creation"),
                () -> assertFalse(hookOptional2.get().getDeleted(), "Hook should not be deleted after recreation"),
                () -> assertEquals(
                        deletionAndCreationRecordItem.getConsensusTimestamp(),
                        hookOptional2.get().getCreatedTimestamp(),
                        "Hook should have creation timestamp from the transaction"),
                () -> assertEquals(
                        1, hookRepository.count(), "Should have 1 hook record (upsert merges delete and create)"));

        // Step 3: Final deletion
        var finalDeleteRecordItem = recordItemBuilder
                .cryptoUpdate()
                .transactionBody(b -> b.clearHookCreationDetails()
                        .addHookIdsToDelete(1L)
                        .setAccountIDToUpdate(accountId)
                        .clearHookCreationDetails())
                .build();
        parseRecordItemAndCommit(finalDeleteRecordItem);

        // Assert after final deletion
        final var hookOptional3 = hookRepository.findById(
                new AbstractHook.Id(hookId, EntityId.of(accountId).getId()));
        assertAll(
                () -> assertTrue(hookOptional3.isPresent(), "Hook should still exist after final soft delete"),
                () -> assertTrue(
                        hookOptional3.get().getDeleted(), "Hook should be marked as deleted after final deletion"),
                () -> assertEquals(1, hookRepository.count(), "Hook count should remain same for soft delete"),
                () -> assertTrue(hookOptional3.get().getDeleted()));
    }

    private void assertAllowances(RecordItem recordItem, Collection<Nft> expectedNfts) {
        assertAll(
                () -> assertEquals(1, cryptoAllowanceRepository.count()),
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(3, nftAllowanceRepository.count()),
                () -> assertEquals(1, tokenAllowanceRepository.count()),
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord()),
                () -> assertThat(cryptoAllowanceRepository.findAll())
                        .allSatisfy(a -> assertThat(a.getAmount()).isPositive())
                        .allSatisfy(a -> assertThat(a.getOwner()).isNotZero())
                        .allSatisfy(a -> assertThat(a.getSpender()).isNotZero())
                        .allMatch(a -> recordItem.getConsensusTimestamp() == a.getTimestampLower())
                        .allMatch(a -> recordItem.getPayerAccountId().equals(a.getPayerAccountId())),
                () -> assertThat(nftAllowanceRepository.findAll())
                        .allSatisfy(a -> assertThat(a.getOwner()).isNotZero())
                        .allSatisfy(a -> assertThat(a.getSpender()).isNotZero())
                        .allSatisfy(a -> assertThat(a.getTokenId()).isNotZero())
                        .allMatch(a -> recordItem.getConsensusTimestamp() == a.getTimestampLower())
                        .allMatch(a -> recordItem.getPayerAccountId().equals(a.getPayerAccountId())),
                () -> assertThat(nftRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedNfts),
                () -> assertThat(tokenAllowanceRepository.findAll())
                        .allSatisfy(a -> assertThat(a.getAmount()).isPositive())
                        .allSatisfy(a -> assertThat(a.getOwner()).isNotZero())
                        .allSatisfy(a -> assertThat(a.getSpender()).isNotZero())
                        .allSatisfy(a -> assertThat(a.getTokenId()).isNotZero())
                        .allMatch(a -> recordItem.getConsensusTimestamp() == a.getTimestampLower())
                        .allMatch(a -> recordItem.getPayerAccountId().equals(a.getPayerAccountId())));
    }

    private void assertCryptoTransaction(TransactionBody transactionBody, TransactionRecord txnRecord) {
        Entity actualAccount = getTransactionEntity(txnRecord.getConsensusTimestamp());
        assertAll(
                () -> assertTransactionAndRecord(transactionBody, txnRecord),
                () -> assertAccount(txnRecord.getReceipt().getAccountID(), actualAccount),
                () -> assertEntity(actualAccount));
    }

    @SuppressWarnings("deprecation")
    private void assertCryptoEntity(
            CryptoCreateTransactionBody expected, long expectedBalance, Timestamp consensusTimestamp) {
        Entity actualAccount = getTransactionEntity(consensusTimestamp);
        long timestamp = DomainUtils.timestampInNanosMax(consensusTimestamp);
        assertAll(
                () -> assertEquals(expected.getAutoRenewPeriod().getSeconds(), actualAccount.getAutoRenewPeriod()),
                () -> assertEquals(expectedBalance, actualAccount.getBalance()),
                () -> assertEquals(timestamp, actualAccount.getCreatedTimestamp()),
                () -> assertEquals(false, actualAccount.getDeleted()),
                () -> assertNull(actualAccount.getExpirationTimestamp()),
                () -> assertArrayEquals(expected.getKey().toByteArray(), actualAccount.getKey()),
                () -> assertEquals(0, actualAccount.getMaxAutomaticTokenAssociations()),
                () -> assertEquals(expected.getMemo(), actualAccount.getMemo()),
                () -> assertEquals(timestamp, actualAccount.getTimestampLower()),
                () -> assertEquals(
                        DomainUtils.getPublicKey(expected.getKey().toByteArray()), actualAccount.getPublicKey()),
                () -> assertEquals(EntityId.of(expected.getProxyAccountID()), actualAccount.getProxyAccountId()),
                () -> assertEquals(expected.getReceiverSigRequired(), actualAccount.getReceiverSigRequired()));
    }

    protected IterableAssert<CryptoTransfer> assertCryptoTransfers(int expectedNumberOfCryptoTransfers) {
        return assertThat(cryptoTransferRepository.findAll())
                .hasSize(expectedNumberOfCryptoTransfers)
                .allSatisfy(a -> assertThat(a.getId().getAmount()).isNotZero());
    }

    private List<NftAllowance> customizeNftAllowances(Timestamp consensusTimestamp, List<Nft> expectedNfts) {
        var delegatingSpender = recordItemBuilder.accountId();
        var owner = recordItemBuilder.accountId();
        var spender1 = recordItemBuilder.accountId();
        var spender2 = recordItemBuilder.accountId();
        var tokenId = recordItemBuilder.tokenId();
        var tokenEntityId = EntityId.of(tokenId).getId();
        var nft1 = Nft.builder()
                .accountId(EntityId.of(owner))
                .createdTimestamp(101L)
                .deleted(false)
                .serialNumber(1)
                .timestampRange(Range.atLeast(101L))
                .tokenId(tokenEntityId)
                .build();
        var nft2 = Nft.builder()
                .accountId(EntityId.of(owner))
                .createdTimestamp(102L)
                .deleted(false)
                .serialNumber(2)
                .timestampRange(Range.atLeast(102L))
                .tokenId(tokenEntityId)
                .build();
        var nft3 = Nft.builder()
                .accountId(EntityId.of(owner))
                .createdTimestamp(103L)
                .deleted(false)
                .serialNumber(3)
                .timestampRange(Range.atLeast(103L))
                .tokenId(tokenEntityId)
                .build();
        var timestamp = DomainUtils.timeStampInNanos(consensusTimestamp);
        List<NftAllowance> nftAllowances = new ArrayList<>();

        nftAllowances.add(NftAllowance.newBuilder()
                .setDelegatingSpender(delegatingSpender)
                .setOwner(owner)
                .addSerialNumbers(1L)
                .addSerialNumbers(2L)
                .setSpender(spender1)
                .setTokenId(tokenId)
                .build());
        expectedNfts.add(nft1.toBuilder()
                .delegatingSpender(EntityId.of(delegatingSpender).getId())
                .spender(EntityId.of(spender1).getId())
                .timestampRange(Range.atLeast(timestamp))
                .build());

        nftAllowances.add(NftAllowance.newBuilder()
                .setApprovedForAll(BoolValue.of(false))
                .setOwner(recordItemBuilder.accountId())
                .setSpender(recordItemBuilder.accountId())
                .setTokenId(recordItemBuilder.tokenId())
                .build());
        nftAllowances.add(NftAllowance.newBuilder()
                .setApprovedForAll(BoolValue.of(true))
                .setOwner(recordItemBuilder.accountId())
                .setSpender(recordItemBuilder.accountId())
                .setTokenId(recordItemBuilder.tokenId())
                .build());

        nftAllowances.add(NftAllowance.newBuilder()
                .setApprovedForAll(BoolValue.of(true))
                .setOwner(owner)
                .addSerialNumbers(2L)
                .addSerialNumbers(3L)
                .setSpender(spender2)
                .setTokenId(tokenId)
                .build());

        // duplicate nft allowance
        nftAllowances.add(nftAllowances.get(nftAllowances.size() - 1));

        // serial number 2's allowance is granted twice, the allowance should be granted to spender2 since it appears
        // after the nft allowance to spender1
        expectedNfts.add(nft2.toBuilder()
                .spender(EntityId.of(spender2).getId())
                .timestampRange(Range.atLeast(timestamp))
                .build());
        expectedNfts.add(nft3.toBuilder()
                .spender(EntityId.of(spender2).getId())
                .timestampRange(Range.atLeast(timestamp))
                .build());

        nftRepository.saveAll(List.of(nft1, nft2, nft3));

        return nftAllowances;
    }

    private void createAccount() {
        Transaction createTransaction = cryptoCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(createTransaction);
        TransactionRecord createRecord = transactionRecordSuccess(createTransactionBody);
        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(createRecord)
                .transaction(createTransaction)
                .build());
    }

    @SuppressWarnings("deprecation")
    private CryptoCreateTransactionBody.Builder cryptoCreateAccountBuilderWithDefaults() {
        return CryptoCreateTransactionBody.newBuilder()
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(1500L))
                .setInitialBalance(INITIAL_BALANCE)
                .setKey(keyFromString(KEY))
                .setMemo("CryptoCreateAccount memo")
                .setNewRealmAdminKey(keyFromString(KEY2))
                .setProxyAccountID(PROXY)
                .setRealmID(RealmID.newBuilder().setShardNum(0).setRealmNum(0).build())
                .setShardID(ShardID.newBuilder().setShardNum(0))
                .setReceiverSigRequired(true);
    }

    private Transaction cryptoCreateTransaction() {
        return cryptoCreateTransaction(cryptoCreateAccountBuilderWithDefaults());
    }

    private Transaction cryptoCreateTransaction(CryptoCreateTransactionBody.Builder cryptoCreateBuilder) {
        return buildTransaction(builder -> builder.setCryptoCreateAccount(cryptoCreateBuilder));
    }

    @SuppressWarnings("deprecation")
    private Transaction cryptoAddLiveHashTransaction() {
        return buildTransaction(builder -> builder.getCryptoAddLiveHashBuilder()
                .getLiveHashBuilder()
                .setAccountId(accountId1.toAccountID())
                .setDuration(Duration.newBuilder().setSeconds(10000L))
                .setHash(ByteString.copyFromUtf8("live hash"))
                .setKeys(KeyList.newBuilder().addKeys(keyFromString(KEY))));
    }

    @SuppressWarnings("deprecation")
    private Transaction cryptoDeleteLiveHashTransaction() {
        return buildTransaction(builder -> builder.getCryptoDeleteLiveHashBuilder()
                .setAccountOfLiveHash(accountId1.toAccountID())
                .setLiveHashToDelete(ByteString.copyFromUtf8("live hash")));
    }

    private Transaction cryptoDeleteTransaction() {
        return buildTransaction(
                builder -> builder.getCryptoDeleteBuilder().setDeleteAccountID(accountId1.toAccountID()));
    }

    private Transaction cryptoUpdateTransaction(AccountID accountNum) {
        return cryptoUpdateTransaction(accountNum, b -> {});
    }

    @SuppressWarnings("deprecation")
    private Transaction cryptoUpdateTransaction(
            AccountID accountNum, Consumer<CryptoUpdateTransactionBody.Builder> custom) {
        return buildTransaction(builder -> {
            var cryptoBuilder = builder.getCryptoUpdateAccountBuilder()
                    .setAccountIDToUpdate(accountNum)
                    .setAutoRenewPeriod(Duration.newBuilder().setSeconds(1500L))
                    .setExpirationTime(Utility.instantToTimestamp(Instant.now()))
                    .setKey(keyFromString(KEY))
                    .setMaxAutomaticTokenAssociations(Int32Value.of(10))
                    .setMemo(StringValue.of("CryptoUpdateAccount memo"))
                    .setProxyAccountID(PROXY_UPDATE)
                    .setReceiverSigRequiredWrapper(BoolValue.of(false))
                    .setStakedNodeId(1L);
            custom.accept(cryptoBuilder);
        });
    }

    private Transaction cryptoTransferTransaction() {
        return buildTransaction(builder -> {
            for (int i = 0; i < additionalTransfers.length; i++) {
                builder.getCryptoTransferBuilder()
                        .getTransfersBuilder()
                        .addAccountAmounts(accountAmount(additionalTransfers[i], additionalTransferAmounts[i]));
            }
        });
    }

    private void groupCryptoTransfersByAccountId(
            TransactionRecord.Builder recordBuilder, List<AccountAmount.Builder> amountsToBeAdded) {
        var accountAmounts = recordBuilder.getTransferListBuilder().getAccountAmountsBuilderList();

        var transfers = new HashMap<AccountID, Long>();
        Stream.concat(accountAmounts.stream(), amountsToBeAdded.stream())
                .forEach(accountAmount -> transfers.compute(accountAmount.getAccountID(), (k, v) -> {
                    long currentValue = (v == null) ? 0 : v;
                    return currentValue + accountAmount.getAmount();
                }));

        TransferList.Builder transferListBuilder = TransferList.newBuilder();
        transfers.entrySet().forEach(entry -> {
            AccountAmount accountAmount = AccountAmount.newBuilder()
                    .setAccountID(entry.getKey())
                    .setAmount(entry.getValue())
                    .build();
            transferListBuilder.addAccountAmounts(accountAmount);
        });
        recordBuilder.setTransferList(transferListBuilder);
    }

    private Condition<CryptoTransfer> isAccountAmountReceiverAccountAmount(AccountAmount receiver) {
        return new Condition<>(
                cryptoTransfer -> isAccountAmountReceiverAccountAmount(cryptoTransfer, receiver),
                format("Is %s the receiver account amount.", receiver));
    }

    private void testRawBytes(Transaction transaction, byte[] expectedBytes) {
        // given
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord txnRecord = transactionRecordSuccess(transactionBody);

        // when
        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(txnRecord)
                .transaction(transaction)
                .build());

        // then
        var dbTransaction = getDbTransaction(txnRecord.getConsensusTimestamp());
        assertArrayEquals(expectedBytes, dbTransaction.getTransactionBytes());
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody, ResponseCodeEnum responseCode) {
        return transactionRecord(transactionBody, responseCode.getNumber(), recordBuilder -> {});
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody, int responseCode) {
        return transactionRecord(transactionBody, responseCode, recordBuilder -> {});
    }

    private TransactionRecord transactionRecord(
            TransactionBody transactionBody, int status, Consumer<TransactionRecord.Builder> builderConsumer) {
        return buildTransactionRecord(
                recordBuilder -> {
                    recordBuilder.getReceiptBuilder().setAccountID(accountId1.toAccountID());
                    builderConsumer.accept(recordBuilder);
                },
                transactionBody,
                status);
    }

    private TransactionRecord transactionRecordSuccess(TransactionBody transactionBody) {
        return transactionRecord(transactionBody, ResponseCodeEnum.SUCCESS);
    }

    private TransactionRecord transactionRecordSuccess(
            TransactionBody transactionBody, Consumer<TransactionRecord.Builder> customBuilder) {
        return transactionRecord(transactionBody, ResponseCodeEnum.SUCCESS.getNumber(), customBuilder);
    }
}
