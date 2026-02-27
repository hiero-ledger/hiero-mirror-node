// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain;

import static org.hiero.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.common.domain.entity.EntityType.TOPIC;
import static org.hiero.mirror.common.util.DomainUtils.TINYBARS_IN_ONE_HBAR;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hedera.services.stream.proto.CallOperationType;
import com.hedera.services.stream.proto.ContractAction.ResultDataCase;
import com.hedera.services.stream.proto.ContractActionType;
import com.hederahashgraph.api.proto.java.FeeExemptKeyList;
import com.hederahashgraph.api.proto.java.FreezeType;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Key.KeyCase;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import jakarta.persistence.EntityManager;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import org.apache.commons.lang3.RandomStringUtils;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.aggregator.LogsBloomAggregator;
import org.hiero.mirror.common.domain.addressbook.AddressBook;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.hiero.mirror.common.domain.addressbook.AddressBookServiceEndpoint;
import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.hiero.mirror.common.domain.addressbook.NodeStake;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.common.domain.balance.TokenBalance;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.contract.ContractState;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.hiero.mirror.common.domain.contract.ContractTransaction;
import org.hiero.mirror.common.domain.contract.ContractTransactionHash;
import org.hiero.mirror.common.domain.entity.CryptoAllowance;
import org.hiero.mirror.common.domain.entity.CryptoAllowanceHistory;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityHistory;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityStake;
import org.hiero.mirror.common.domain.entity.EntityStakeHistory;
import org.hiero.mirror.common.domain.entity.EntityTransaction;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.hiero.mirror.common.domain.entity.NftAllowanceHistory;
import org.hiero.mirror.common.domain.entity.TokenAllowance;
import org.hiero.mirror.common.domain.entity.TokenAllowanceHistory;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.common.domain.hook.HookExtensionPoint;
import org.hiero.mirror.common.domain.hook.HookHistory;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.hiero.mirror.common.domain.hook.HookType;
import org.hiero.mirror.common.domain.job.ReconciliationJob;
import org.hiero.mirror.common.domain.job.ReconciliationStatus;
import org.hiero.mirror.common.domain.node.Node;
import org.hiero.mirror.common.domain.node.NodeHistory;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.hiero.mirror.common.domain.node.ServiceEndpoint;
import org.hiero.mirror.common.domain.schedule.Schedule;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.hiero.mirror.common.domain.token.CustomFeeHistory;
import org.hiero.mirror.common.domain.token.FallbackFee;
import org.hiero.mirror.common.domain.token.FixedFee;
import org.hiero.mirror.common.domain.token.FractionalFee;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.common.domain.token.NftHistory;
import org.hiero.mirror.common.domain.token.NftTransfer;
import org.hiero.mirror.common.domain.token.RoyaltyFee;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenAccountHistory;
import org.hiero.mirror.common.domain.token.TokenAirdrop;
import org.hiero.mirror.common.domain.token.TokenAirdropHistory;
import org.hiero.mirror.common.domain.token.TokenAirdropStateEnum;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenHistory;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.token.TokenPauseStatusEnum;
import org.hiero.mirror.common.domain.token.TokenSupplyTypeEnum;
import org.hiero.mirror.common.domain.token.TokenTransfer;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.common.domain.topic.Topic;
import org.hiero.mirror.common.domain.topic.TopicHistory;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.domain.topic.TopicMessageLookup;
import org.hiero.mirror.common.domain.transaction.AssessedCustomFee;
import org.hiero.mirror.common.domain.transaction.Authorization;
import org.hiero.mirror.common.domain.transaction.CryptoTransfer;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.common.domain.transaction.ItemizedTransfer;
import org.hiero.mirror.common.domain.transaction.LiveHash;
import org.hiero.mirror.common.domain.transaction.NetworkFreeze;
import org.hiero.mirror.common.domain.transaction.Prng;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.SidecarFile;
import org.hiero.mirror.common.domain.transaction.StakingRewardTransfer;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionHash;
import org.hiero.mirror.common.domain.transaction.TransactionSignature;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.domain.tss.Ledger;
import org.hiero.mirror.common.domain.tss.LedgerNodeContribution;
import org.hiero.mirror.common.util.DomainUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

@Component
@CustomLog
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DomainBuilder {

    public static final int KEY_LENGTH_ECDSA = 33;
    public static final int KEY_LENGTH_ED25519 = 32;

    private static final long LAST_RESERVED_ID = 1000;

    private final CommonProperties commonProperties;
    private final EntityManager entityManager;
    private final TransactionOperations transactionOperations;
    private final AtomicLong num = new AtomicLong(0L);
    private final AtomicInteger transactionIndex = new AtomicInteger(0);
    private final Instant now = Instant.now();
    private final SecureRandom random = new SecureRandom();

    private long timestampOffset = 0;

    // Intended for use by unit tests that don't need persistence
    public DomainBuilder() {
        this(CommonProperties.getInstance(), null, null);
    }

    public DomainWrapper<AccountBalance, AccountBalance.AccountBalanceBuilder> accountBalance() {
        var builder = AccountBalance.builder().balance(10L).id(new AccountBalance.Id(timestamp(), entityId()));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<AccountBalanceFile, AccountBalanceFile.AccountBalanceFileBuilder> accountBalanceFile() {
        long timestamp = timestamp();
        var name = Instant.ofEpochSecond(0L, timestamp).toString().replace(':', '_') + "_Balances.pb.gz";
        var builder = AccountBalanceFile.builder()
                .bytes(bytes(16))
                .consensusTimestamp(timestamp)
                .count(1L)
                .fileHash(text(96))
                .loadEnd(timestamp + 1)
                .loadStart(timestamp)
                .name(name)
                .nodeId(number())
                .timeOffset(0);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<AddressBook, AddressBook.AddressBookBuilder> addressBook() {
        var builder = AddressBook.builder()
                .fileData(bytes(10))
                .fileId(entityNum(102))
                .nodeCount(6)
                .startConsensusTimestamp(timestamp())
                .endConsensusTimestamp(timestamp());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<AddressBookEntry, AddressBookEntry.AddressBookEntryBuilder> addressBookEntry() {
        return addressBookEntry(0);
    }

    public DomainWrapper<AddressBookEntry, AddressBookEntry.AddressBookEntryBuilder> addressBookEntry(int endpoints) {
        long consensusTimestamp = timestamp();
        long nodeId = number();
        var builder = AddressBookEntry.builder()
                .consensusTimestamp(consensusTimestamp)
                .description(text(10))
                .memo(text(10))
                .nodeId(nodeId)
                .nodeAccountId(entityNum(nodeId + 3))
                .nodeCertHash(bytes(96))
                .publicKey(text(64))
                .stake(0L);

        var serviceEndpoints = new HashSet<AddressBookServiceEndpoint>();
        builder.serviceEndpoints(serviceEndpoints);

        for (int i = 0; i < endpoints; ++i) {
            var endpoint = addressBookServiceEndpoint()
                    .customize(a -> a.consensusTimestamp(consensusTimestamp).nodeId(nodeId))
                    .get();
            serviceEndpoints.add(endpoint);
        }

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<AddressBookServiceEndpoint, AddressBookServiceEndpoint.AddressBookServiceEndpointBuilder>
            addressBookServiceEndpoint() {
        String ipAddress = "";
        try {
            ipAddress = InetAddress.getByAddress(bytes(4)).getHostAddress();
        } catch (UnknownHostException e) {
            // This shouldn't happen
        }

        var builder = AddressBookServiceEndpoint.builder()
                .consensusTimestamp(timestamp())
                .ipAddressV4(ipAddress)
                .nodeId(number())
                .domainName("")
                .port(50211);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<AssessedCustomFee, AssessedCustomFee.AssessedCustomFeeBuilder> assessedCustomFee() {
        var builder = AssessedCustomFee.builder()
                .amount(100L)
                .collectorAccountId(entityId().getId())
                .consensusTimestamp(timestamp())
                .effectivePayerAccountIds(List.of(id(), id()))
                .payerAccountId(entityId())
                .tokenId(entityId());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Contract, Contract.ContractBuilder<?, ?>> contract() {
        var builder = Contract.builder()
                .fileId(entityId())
                .id(id())
                .initcode(null) // Mutually exclusive with fileId
                .runtimeBytecode(bytes(256));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<ContractAction, ContractAction.ContractActionBuilder> contractAction() {
        var builder = ContractAction.builder()
                .callDepth(1)
                .caller(entityId())
                .callerType(CONTRACT)
                .callOperationType(CallOperationType.OP_CALL.getNumber())
                .callType(ContractActionType.CALL.getNumber())
                .consensusTimestamp(timestamp())
                .gas(100L)
                .gasUsed(50L)
                .index((int) number())
                .input(bytes(256))
                .payerAccountId(entityId())
                .recipientAccount(entityId())
                .resultData(bytes(256))
                .resultDataType(ResultDataCase.OUTPUT.getNumber())
                .value(300L);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<ContractLog, ContractLog.ContractLogBuilder> contractLog() {
        var builder = ContractLog.builder()
                .bloom(bytes(256))
                .consensusTimestamp(timestamp())
                .contractId(entityId())
                .data(bytes(128))
                .index((int) number())
                .payerAccountId(entityId())
                .topic0(bytes(64))
                .topic1(bytes(64))
                .topic2(bytes(64))
                .topic3(bytes(64))
                .transactionHash(bytes(48))
                .transactionIndex(transactionIndex());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<ContractResult, ContractResult.ContractResultBuilder<?, ?>> contractResult() {
        var builder = ContractResult.builder()
                .amount(1000L)
                .bloom(bytes(256))
                .callResult(bytes(512))
                .consensusTimestamp(timestamp())
                .contractId(entityId().getId())
                .createdContractIds(List.of(entityId().getId()))
                .errorMessage("")
                .functionParameters(bytes(64))
                .functionResult(bytes(128))
                .gasConsumed(80L)
                .gasLimit(200L)
                .gasUsed(100L)
                .payerAccountId(entityId())
                .senderId(entityId())
                .transactionHash(bytes(32))
                .transactionIndex(1)
                .transactionNonce(0)
                .transactionResult(ResponseCodeEnum.SUCCESS_VALUE);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<ContractState, ContractState.ContractStateBuilder> contractState() {
        var createdTimestamp = timestamp();
        var builder = ContractState.builder()
                .contractId(id())
                .createdTimestamp(createdTimestamp)
                .modifiedTimestamp(createdTimestamp)
                .slot(bytes(32))
                .value(bytes(32));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<ContractStateChange, ContractStateChange.ContractStateChangeBuilder> contractStateChange() {
        var builder = ContractStateChange.builder()
                .consensusTimestamp(timestamp())
                .contractId(entityId().getId())
                .payerAccountId(entityId())
                .slot(bytes(128))
                .valueRead(bytes(64))
                .valueWritten(bytes(64));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<ContractTransaction, ContractTransaction.ContractTransactionBuilder> contractTransaction() {
        var payerAccountId = entityId().getId();
        var contractId = entityId().getId();
        var builder = ContractTransaction.builder()
                .consensusTimestamp(timestamp())
                .entityId(contractId)
                .payerAccountId(payerAccountId)
                .contractIds(Arrays.asList(payerAccountId, contractId));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<ContractTransactionHash, ContractTransactionHash.ContractTransactionHashBuilder>
            contractTransactionHash() {
        var builder = ContractTransactionHash.builder()
                .consensusTimestamp(timestamp())
                .entityId(id())
                .payerAccountId(id())
                .transactionResult(ResponseCodeEnum.SUCCESS_VALUE)
                .hash(bytes(32));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<CryptoAllowance, CryptoAllowance.CryptoAllowanceBuilder<?, ?>> cryptoAllowance() {
        long amount = number() + 1000;
        var spender = entityId();
        var builder = CryptoAllowance.builder()
                .amount(amount)
                .amountGranted(amount)
                .owner(id())
                .payerAccountId(spender)
                .spender(spender.getId())
                .timestampRange(timestampRange());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<CryptoAllowanceHistory, CryptoAllowanceHistory.CryptoAllowanceHistoryBuilder<?, ?>>
            cryptoAllowanceHistory() {
        long amount = number() + 1000;
        var spender = entityId();
        var builder = CryptoAllowanceHistory.builder()
                .amount(amount)
                .amountGranted(amount)
                .owner(id())
                .payerAccountId(spender)
                .spender(spender.getId())
                .timestampRange(Range.closedOpen(timestamp(), timestamp()));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<CryptoTransfer, CryptoTransfer.CryptoTransferBuilder> cryptoTransfer() {
        var builder = CryptoTransfer.builder()
                .amount(10L)
                .consensusTimestamp(timestamp())
                .entityId(entityId().getId())
                .isApproval(false)
                .payerAccountId(entityId());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<CustomFee, CustomFee.CustomFeeBuilder<?, ?>> customFee() {
        var builder = CustomFee.builder()
                .fixedFees(List.of(fixedFee()))
                .fractionalFees(List.of(fractionalFee()))
                .royaltyFees(List.of(royaltyFee()))
                .timestampRange(timestampRange())
                .entityId(entityId().getId());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<CustomFeeHistory, CustomFeeHistory.CustomFeeHistoryBuilder<?, ?>> customFeeHistory() {
        long timestamp = timestamp();
        var builder = CustomFeeHistory.builder()
                .fixedFees(List.of(fixedFee()))
                .fractionalFees(List.of(fractionalFee()))
                .royaltyFees(List.of(royaltyFee()))
                .timestampRange(Range.closedOpen(timestamp, timestamp + 10))
                .entityId(entityId().getId());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Entity, Entity.EntityBuilder<?, ?>> entity(EntityId entityId, long createdTimestamp) {
        var builder = Entity.builder()
                .alias(key())
                .autoRenewAccountId(id())
                .autoRenewPeriod(8_000_000L)
                .balance(tinybar())
                .balanceTimestamp(createdTimestamp)
                .createdTimestamp(createdTimestamp)
                .declineReward(false)
                .deleted(false)
                .delegationAddress(bytes(20))
                .ethereumNonce(1L)
                .evmAddress(evmAddress())
                .expirationTimestamp(createdTimestamp + 30_000_000L)
                .id(entityId.getId())
                .key(key())
                .maxAutomaticTokenAssociations(1)
                .memo(text(16))
                .obtainerId(entityId())
                .permanentRemoval(false)
                .proxyAccountId(entityId())
                .num(entityId.getNum())
                .realm(entityId.getRealm())
                .receiverSigRequired(true)
                .shard(entityId.getShard())
                .stakedNodeId(-1L)
                .stakePeriodStart(-1L)
                .timestampRange(Range.atLeast(createdTimestamp))
                .type(ACCOUNT);

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Entity, Entity.EntityBuilder<?, ?>> entity(long id, long createdTimestamp) {
        return entity(EntityId.of(id), createdTimestamp);
    }

    public DomainWrapper<Entity, Entity.EntityBuilder<?, ?>> entity(EntityId entityId) {
        return entity(entityId, timestamp());
    }

    public DomainWrapper<Entity, Entity.EntityBuilder<?, ?>> entity() {
        return entity(entityId(), timestamp());
    }

    public DomainWrapper<EntityHistory, EntityHistory.EntityHistoryBuilder<?, ?>> entityHistory(
            EntityId entityId, long createdTimestamp) {
        var builder = EntityHistory.builder()
                .alias(key())
                .autoRenewAccountId(id())
                .autoRenewPeriod(8_000_000L)
                .balance(number())
                .balanceTimestamp(createdTimestamp)
                .createdTimestamp(createdTimestamp)
                .declineReward(false)
                .deleted(false)
                .delegationAddress(bytes(20))
                .ethereumNonce(1L)
                .evmAddress(evmAddress())
                .expirationTimestamp(createdTimestamp + 30_000_000L)
                .id(entityId.getId())
                .key(key())
                .maxAutomaticTokenAssociations(1)
                .memo(text(16))
                .obtainerId(entityId())
                .permanentRemoval(false)
                .proxyAccountId(entityId())
                .num(entityId.getNum())
                .realm(entityId.getRealm())
                .receiverSigRequired(true)
                .shard(entityId.getShard())
                .stakedNodeId(-1L)
                .stakePeriodStart(-1L)
                .timestampRange(Range.closedOpen(createdTimestamp, createdTimestamp + 10))
                .type(ACCOUNT);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<EntityHistory, EntityHistory.EntityHistoryBuilder<?, ?>> entityHistory(EntityId entityId) {
        return entityHistory(entityId, timestamp());
    }

    public DomainWrapper<EntityHistory, EntityHistory.EntityHistoryBuilder<?, ?>> entityHistory() {
        return entityHistory(entityId(), timestamp());
    }

    public DomainWrapper<EntityStake, EntityStake.EntityStakeBuilder<?, ?>> entityStake() {
        var builder = EntityStake.builder()
                .endStakePeriod(0L)
                .id(id())
                .pendingReward(0L)
                .stakedNodeIdStart(-1L)
                .stakedToMe(0L)
                .stakeTotalStart(0L)
                .timestampRange(timestampRange());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<EntityStakeHistory, EntityStakeHistory.EntityStakeHistoryBuilder<?, ?>> entityStakeHistory() {
        long timestamp = timestamp();
        var builder = EntityStakeHistory.builder()
                .endStakePeriod(0L)
                .id(id())
                .pendingReward(0L)
                .stakedNodeIdStart(-1L)
                .stakedToMe(0L)
                .stakeTotalStart(0L)
                .timestampRange(Range.closedOpen(timestamp, timestamp + 10));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<EntityTransaction, EntityTransaction.EntityTransactionBuilder> entityTransaction() {
        var builder = EntityTransaction.builder()
                .consensusTimestamp(timestamp())
                .entityId(id())
                .payerAccountId(entityId())
                .type(TransactionType.CRYPTOCREATEACCOUNT.getProtoId())
                .result(ResponseCodeEnum.SUCCESS_VALUE);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<EthereumTransaction, EthereumTransaction.EthereumTransactionBuilder> ethereumTransaction(
            boolean hasInitCode) {
        var builder = EthereumTransaction.builder()
                .accessList(bytes(100))
                .authorizationList(authorizationList())
                .chainId(bytes(1))
                .consensusTimestamp(timestamp())
                .data(bytes(100))
                .gasLimit(Long.MAX_VALUE)
                .gasPrice(bytes(32))
                .hash(bytes(32))
                .maxGasAllowance(Long.MAX_VALUE)
                .maxFeePerGas(bytes(32))
                .maxPriorityFeePerGas(bytes(32))
                .nonce(1234L)
                .payerAccountId(entityId())
                .recoveryId(3)
                .signatureR(bytes(32))
                .signatureS(bytes(32))
                .signatureV(bytes(1))
                .toAddress(bytes(20))
                .type(2)
                .value(bytes(32));

        if (hasInitCode) {
            builder.callData(bytes(100));
        } else {
            builder.callDataId(entityId());
        }

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<FileData, FileData.FileDataBuilder> fileData() {
        var builder = FileData.builder()
                .consensusTimestamp(timestamp())
                .fileData(bytes(128))
                .entityId(entityId())
                .transactionType(TransactionType.FILECREATE.getProtoId());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Hook, Hook.HookBuilder<?, ?>> hook() {
        var createdTimestamp = timestamp();
        var builder = Hook.builder()
                .adminKey(key())
                .contractId(entityId())
                .createdTimestamp(createdTimestamp)
                .deleted(false)
                .extensionPoint(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK)
                .hookId(number())
                .ownerId(id())
                .timestampRange(Range.atLeast(createdTimestamp))
                .type(HookType.EVM);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<HookHistory, HookHistory.HookHistoryBuilder<?, ?>> hookHistory() {
        var createdTimestamp = timestamp();
        var builder = HookHistory.builder()
                .adminKey(key())
                .contractId(entityId())
                .createdTimestamp(createdTimestamp)
                .deleted(false)
                .extensionPoint(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK)
                .hookId(number())
                .ownerId(id())
                .timestampRange(Range.closedOpen(createdTimestamp, createdTimestamp + 10))
                .type(HookType.EVM);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<HookStorage, HookStorage.HookStorageBuilder> hookStorage() {
        var createdTimestamp = timestamp();
        var modifiedTimestamp = timestamp();
        var builder = HookStorage.builder()
                .createdTimestamp(createdTimestamp)
                .hookId(number())
                .key(bytes(32))
                .modifiedTimestamp(modifiedTimestamp)
                .ownerId(id())
                .value(bytes(32));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<HookStorageChange, HookStorageChange.HookStorageChangeBuilder> hookStorageChange() {
        final var value = bytes(32);
        final var builder = HookStorageChange.builder()
                .consensusTimestamp(timestamp())
                .hookId(number())
                .key(bytes(32))
                .ownerId(id())
                .valueRead(value)
                .valueWritten(value);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Ledger, Ledger.LedgerBuilder<?, ?>> ledger() {
        final var nodeContributions = new ArrayList<LedgerNodeContribution>(List.of(
                LedgerNodeContribution.builder()
                        .historyProofKey(bytes(48))
                        .nodeId(number())
                        .weight(number())
                        .build(),
                LedgerNodeContribution.builder()
                        .historyProofKey(bytes(48))
                        .nodeId(number())
                        .weight(number())
                        .build()));
        final var builder = Ledger.builder()
                .consensusTimestamp(timestamp())
                .historyProofVerificationKey(bytes(64))
                .ledgerId(bytes(32))
                .nodeContributions(nodeContributions);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<LiveHash, LiveHash.LiveHashBuilder> liveHash() {
        var builder = LiveHash.builder().consensusTimestamp(timestamp()).livehash(bytes(64));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<NetworkFreeze, NetworkFreeze.NetworkFreezeBuilder<?, ?>> networkFreeze() {
        var builder = NetworkFreeze.builder()
                .consensusTimestamp(timestamp())
                .endTime(timestamp())
                .fileHash(bytes(48))
                .fileId(entityId())
                .payerAccountId(entityId())
                .startTime(timestamp())
                .type(FreezeType.FREEZE_UPGRADE_VALUE);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<NetworkStake, NetworkStake.NetworkStakeBuilder> networkStake() {
        var timestamp = timestamp();
        var builder = NetworkStake.builder()
                .consensusTimestamp(timestamp)
                .epochDay(getEpochDay(timestamp))
                .maxStakingRewardRatePerHbar(17_808L)
                .nodeRewardFeeDenominator(0L)
                .nodeRewardFeeNumerator(100L)
                .stakeTotal(number())
                .stakingPeriod(timestamp - 1L)
                .stakingPeriodDuration(1440)
                .stakingPeriodsStored(365)
                .stakingRewardFeeDenominator(100L)
                .stakingRewardFeeNumerator(100L)
                .stakingRewardRate(100_000_000_000L)
                .stakingStartThreshold(25_000_000_000_000_000L);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Nft, Nft.NftBuilder<?, ?>> nft() {
        var createdTimestamp = timestamp();
        var builder = Nft.builder()
                .accountId(entityId())
                .createdTimestamp(createdTimestamp)
                .deleted(false)
                .metadata(bytes(16))
                .serialNumber(number())
                .timestampRange(Range.atLeast(createdTimestamp))
                .tokenId(id());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<NftHistory, NftHistory.NftHistoryBuilder<?, ?>> nftHistory() {
        long timestamp = timestamp();
        var builder = NftHistory.builder()
                .accountId(entityId())
                .createdTimestamp(timestamp())
                .deleted(false)
                .metadata(bytes(16))
                .serialNumber(number())
                .timestampRange(Range.closedOpen(timestamp, timestamp + 10))
                .tokenId(id());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<NftAllowance, NftAllowance.NftAllowanceBuilder<?, ?>> nftAllowance() {
        var builder = NftAllowance.builder()
                .approvedForAll(false)
                .owner(entityId().getId())
                .payerAccountId(entityId())
                .spender(entityId().getId())
                .timestampRange(timestampRange())
                .tokenId(entityId().getId());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<NftAllowanceHistory, NftAllowanceHistory.NftAllowanceHistoryBuilder<?, ?>>
            nftAllowanceHistory() {
        long timestamp = timestamp();
        var builder = NftAllowanceHistory.builder()
                .approvedForAll(false)
                .owner(entityId().getId())
                .payerAccountId(entityId())
                .spender(entityId().getId())
                .timestampRange(Range.closedOpen(timestamp, timestamp + 10))
                .tokenId(entityId().getId());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<NftTransfer, NftTransfer.NftTransferBuilder> nftTransfer() {
        var builder = NftTransfer.builder()
                .isApproval(false)
                .receiverAccountId(entityId())
                .senderAccountId(entityId())
                .serialNumber(1L)
                .tokenId(entityId());

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Node, Node.NodeBuilder<?, ?>> node() {
        long timestamp = timestamp();

        var builder = Node.builder()
                .accountId(entityId())
                .adminKey(key())
                .createdTimestamp(timestamp)
                .declineReward(false)
                .deleted(false)
                .grpcProxyEndpoint(ServiceEndpoint.builder()
                        .ipAddressV4("127.0.0.1")
                        .port(443)
                        .build())
                .nodeId(number())
                .timestampRange(Range.atLeast(timestamp));

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<NodeHistory, NodeHistory.NodeHistoryBuilder<?, ?>> nodeHistory() {
        long timestamp = timestamp();
        var builder = NodeHistory.builder()
                .accountId(entityId())
                .adminKey(key())
                .createdTimestamp(timestamp)
                .declineReward(false)
                .deleted(false)
                .grpcProxyEndpoint(ServiceEndpoint.builder()
                        .ipAddressV4("127.0.0.1")
                        .port(443)
                        .build())
                .nodeId(number())
                .timestampRange(Range.closedOpen(timestamp, timestamp + 10));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<RegisteredNode, RegisteredNode.RegisteredNodeBuilder<?, ?>> registeredNode() {
        final long timestamp = timestamp();
        final var builder = RegisteredNode.builder()
                .adminKey(key())
                .createdTimestamp(timestamp)
                .deleted(false)
                .description("node-" + number())
                .registeredNodeId(number())
                .serviceEndpoints(List.of(RegisteredServiceEndpoint.builder()
                        .ipAddress("127.0.0.1")
                        .port(443)
                        .requiresTls(true)
                        .build()))
                .timestampRange(Range.atLeast(timestamp));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<NodeStake, NodeStake.NodeStakeBuilder> nodeStake() {
        long maxStake = 50_000_000_000L * TINYBARS_IN_ONE_HBAR / 26L;
        long stake = number() * TINYBARS_IN_ONE_HBAR;
        long timestamp = timestamp();

        var builder = NodeStake.builder()
                .consensusTimestamp(timestamp)
                .epochDay(getEpochDay(timestamp))
                .maxStake(maxStake)
                .minStake(maxStake / 2L)
                .nodeId(number())
                .rewardRate(number())
                .stake(stake)
                .stakeNotRewarded(TINYBARS_IN_ONE_HBAR)
                .stakeRewarded(stake - TINYBARS_IN_ONE_HBAR)
                .stakingPeriod(timestamp());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Prng, Prng.PrngBuilder> prng() {
        var builder = Prng.builder()
                .consensusTimestamp(timestamp())
                .payerAccountId(id())
                .range(Integer.MAX_VALUE)
                .prngNumber(random.nextInt(Integer.MAX_VALUE));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<ReconciliationJob, ReconciliationJob.ReconciliationJobBuilder> reconciliationJob() {
        var builder = ReconciliationJob.builder()
                .consensusTimestamp(timestamp())
                .error("")
                .status(ReconciliationStatus.SUCCESS)
                .timestampStart(instant())
                .timestampEnd(instant());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<RecordFile, RecordFile.RecordFileBuilder> recordFile() {
        // reset transaction index
        transactionIndex.set(0);

        long timestamp = timestamp();
        long consensusEnd = timestamp + 1;
        var instantString = now.toString().replace(':', '_');
        var blockNumber = number();
        var round = blockNumber + 1;
        var builder = RecordFile.builder()
                .bytes(bytes(128))
                .consensusStart(timestamp)
                .consensusEnd(consensusEnd)
                .count(1L)
                .digestAlgorithm(DigestAlgorithm.SHA_384)
                .fileHash(hash(96))
                .gasUsed(100L)
                .hapiVersionMajor(0)
                .hapiVersionMinor(28)
                .hapiVersionPatch(0)
                .hash(hash(96))
                .index(blockNumber)
                .logsBloom(bloomFilter())
                .loadEnd(now.toEpochMilli() + 1000L)
                .loadStart(now.toEpochMilli())
                .name(instantString + ".rcd.gz")
                .previousHash(hash(96))
                .roundEnd(round)
                .roundStart(round)
                .sidecarCount(1)
                .sidecars(List.of(sidecarFile()
                        .customize(s -> s.consensusEnd(consensusEnd).name(instantString + "_01.rcd.gz"))
                        .get()))
                .size(256 * 1024)
                .softwareVersionMajor(0)
                .softwareVersionMinor(28)
                .softwareVersionPatch(0)
                .version(6);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Schedule, Schedule.ScheduleBuilder> schedule() {
        var builder = Schedule.builder()
                .consensusTimestamp(timestamp())
                .creatorAccountId(entityId())
                .expirationTime(timestamp())
                .payerAccountId(entityId())
                .scheduleId(entityId().getId())
                .transactionBody(bytes(64))
                .waitForExpiry(true);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<SidecarFile, SidecarFile.SidecarFileBuilder> sidecarFile() {
        var data = bytes(256);
        var builder = SidecarFile.builder()
                .bytes(data)
                .consensusEnd(timestamp())
                .hash(bytes(DigestAlgorithm.SHA_384.getSize()))
                .hashAlgorithm(DigestAlgorithm.SHA_384)
                .index(1)
                .name(now.toString().replace(':', '_') + "_01.rcd.gz")
                .records(Collections.emptyList())
                .size(data.length)
                .types(List.of(1, 2));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<StakingRewardTransfer, StakingRewardTransfer.StakingRewardTransferBuilder>
            stakingRewardTransfer() {
        var accountId = entityId();
        var builder = StakingRewardTransfer.builder()
                .accountId(accountId.getId())
                .amount(number())
                .consensusTimestamp(timestamp())
                .payerAccountId(accountId);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Token, Token.TokenBuilder<?, ?>> token() {
        long timestamp = timestamp();
        var builder = Token.builder()
                .createdTimestamp(timestamp)
                .decimals((int) number())
                .feeScheduleKey(key())
                .freezeDefault(false)
                .freezeKey(key())
                .freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                .initialSupply(1_000_000_000L + number())
                .kycKey(key())
                .kycStatus(TokenKycStatusEnum.REVOKED)
                .metadata(bytes(16))
                .metadataKey(key())
                .name(text(8))
                .pauseKey(key())
                .pauseStatus(TokenPauseStatusEnum.UNPAUSED)
                .supplyKey(key())
                .supplyType(TokenSupplyTypeEnum.INFINITE)
                .symbol(text(8))
                .timestampRange(Range.atLeast(timestamp))
                .tokenId(entityId().getId())
                .totalSupply(1_000_000_000L + number())
                .treasuryAccountId(entityId())
                .type(TokenTypeEnum.FUNGIBLE_COMMON)
                .wipeKey(key());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TokenHistory, TokenHistory.TokenHistoryBuilder<?, ?>> tokenHistory() {
        long timestamp = timestamp();
        var builder = TokenHistory.builder()
                .createdTimestamp(timestamp)
                .decimals((int) number())
                .feeScheduleKey(key())
                .freezeDefault(false)
                .freezeKey(key())
                .freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                .initialSupply(1_000_000_000L + number())
                .kycKey(key())
                .kycStatus(TokenKycStatusEnum.REVOKED)
                .metadata(bytes(16))
                .metadataKey(key())
                .name(text(8))
                .pauseKey(key())
                .pauseStatus(TokenPauseStatusEnum.UNPAUSED)
                .supplyKey(key())
                .supplyType(TokenSupplyTypeEnum.INFINITE)
                .symbol(text(8))
                .timestampRange(Range.closedOpen(timestamp, timestamp + 10))
                .tokenId(entityId().getId())
                .totalSupply(1_000_000_000L + number())
                .treasuryAccountId(entityId())
                .type(TokenTypeEnum.FUNGIBLE_COMMON)
                .wipeKey(key());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TokenAccount, TokenAccount.TokenAccountBuilder<?, ?>> tokenAccount() {
        long timestamp = timestamp();
        var builder = TokenAccount.builder()
                .accountId(id())
                .automaticAssociation(false)
                .associated(true)
                .balance(number())
                .balanceTimestamp(timestamp)
                .createdTimestamp(timestamp)
                .freezeStatus(null)
                .kycStatus(null)
                .timestampRange(Range.atLeast(timestamp))
                .tokenId(id());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TokenAccountHistory, TokenAccountHistory.TokenAccountHistoryBuilder<?, ?>>
            tokenAccountHistory() {
        long timestamp = timestamp();
        var builder = TokenAccountHistory.builder()
                .accountId(id())
                .automaticAssociation(false)
                .associated(true)
                .balance(number())
                .balanceTimestamp(timestamp)
                .createdTimestamp(timestamp)
                .freezeStatus(null)
                .kycStatus(null)
                .timestampRange(Range.closedOpen(timestamp, timestamp + 10))
                .tokenId(id());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TokenAirdrop, TokenAirdrop.TokenAirdropBuilder<?, ?>> tokenAirdrop(TokenTypeEnum type) {
        long timestamp = timestamp();
        var builder = TokenAirdrop.builder()
                .receiverAccountId(id())
                .senderAccountId(id())
                .state(TokenAirdropStateEnum.PENDING)
                .timestampRange(Range.atLeast(timestamp))
                .tokenId(id());
        if (type == TokenTypeEnum.NON_FUNGIBLE_UNIQUE) {
            builder.serialNumber(number());
        } else {
            long amount = number() + 1000;
            builder.amount(amount);
        }

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TokenAirdropHistory, TokenAirdropHistory.TokenAirdropHistoryBuilder<?, ?>> tokenAirdropHistory(
            TokenTypeEnum type) {
        long timestamp = timestamp();
        var builder = TokenAirdropHistory.builder()
                .receiverAccountId(id())
                .senderAccountId(id())
                .state(TokenAirdropStateEnum.PENDING)
                .timestampRange(Range.closedOpen(timestamp, timestamp + 10))
                .tokenId(id());
        if (type == TokenTypeEnum.NON_FUNGIBLE_UNIQUE) {
            builder.serialNumber(number());
        } else {
            long amount = number() + 1000;
            builder.amount(amount);
        }
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TokenAllowance, TokenAllowance.TokenAllowanceBuilder<?, ?>> tokenAllowance() {
        long amount = number() + 1000;
        var spender = entityId();
        var builder = TokenAllowance.builder()
                .amount(amount)
                .amountGranted(amount)
                .owner(id())
                .payerAccountId(spender)
                .spender(spender.getId())
                .timestampRange(timestampRange())
                .tokenId(id());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TokenAllowanceHistory, TokenAllowanceHistory.TokenAllowanceHistoryBuilder<?, ?>>
            tokenAllowanceHistory() {
        long amount = number() + 1000;
        var spender = entityId();
        long timestamp = timestamp();
        var builder = TokenAllowanceHistory.builder()
                .amount(amount)
                .amountGranted(amount)
                .owner(id())
                .payerAccountId(spender)
                .spender(spender.getId())
                .timestampRange(Range.closedOpen(timestamp, timestamp + 10))
                .tokenId(id());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TokenBalance, TokenBalance.TokenBalanceBuilder> tokenBalance() {
        var builder = TokenBalance.builder().balance(1L).id(new TokenBalance.Id(timestamp(), entityId(), entityId()));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TokenTransfer, TokenTransfer.TokenTransferBuilder> tokenTransfer() {
        var builder = TokenTransfer.builder()
                .amount(100L)
                .id(new TokenTransfer.Id(timestamp(), entityId(), entityId()))
                .payerAccountId(entityId());

        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Topic, Topic.TopicBuilder<?, ?>> topic() {
        long timestamp = timestamp();
        var builder = Topic.builder()
                .adminKey(bytes(32))
                .createdTimestamp(timestamp)
                .id(id())
                .feeExemptKeyList(feeExemptKeyList())
                .feeScheduleKey(bytes(32))
                .submitKey(bytes(32))
                .timestampRange(Range.atLeast(timestamp));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TopicHistory, TopicHistory.TopicHistoryBuilder<?, ?>> topicHistory() {
        long timestamp = timestamp();
        var builder = TopicHistory.builder()
                .adminKey(bytes(32))
                .createdTimestamp(timestamp)
                .id(id())
                .feeExemptKeyList(bytes(32))
                .feeScheduleKey(bytes(32))
                .submitKey(bytes(32))
                .timestampRange(Range.closedOpen(timestamp, timestamp + 100));
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Entity, Entity.EntityBuilder<?, ?>> topicEntity() {
        return entity().customize(e -> e.alias(null)
                .balance(null)
                .balanceTimestamp(null)
                .receiverSigRequired(null)
                .ethereumNonce(null)
                .evmAddress(null)
                .key(null)
                .maxAutomaticTokenAssociations(null)
                .proxyAccountId(null)
                .publicKey(null)
                .type(TOPIC));
    }

    public DomainWrapper<TopicMessage, TopicMessage.TopicMessageBuilder> topicMessage() {
        var payer = entityId();
        var transactionId = TransactionID.newBuilder()
                .setAccountID(payer.toAccountID())
                .setTransactionValidStart(protoTimestamp())
                .build()
                .toByteArray();
        var builder = TopicMessage.builder()
                .chunkNum(1)
                .chunkTotal(1)
                .consensusTimestamp(timestamp())
                .initialTransactionId(transactionId)
                .message(bytes(128))
                .payerAccountId(payer)
                .runningHashVersion(2)
                .runningHash(bytes(48))
                .sequenceNumber(number())
                .topicId(entityId())
                .validStartTimestamp(timestamp());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TopicMessageLookup, TopicMessageLookup.TopicMessageLookupBuilder> topicMessageLookup() {
        long timestamp = timestamp();
        long sequenceNumber = number();
        var builder = TopicMessageLookup.builder()
                .partition(String.format("topic_message_%d", number()))
                .sequenceNumberRange(Range.closedOpen(sequenceNumber, sequenceNumber + 1))
                .timestampRange(Range.closedOpen(timestamp, timestamp + 10))
                .topicId(id());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<Transaction, Transaction.TransactionBuilder> transaction() {
        var builder = Transaction.builder()
                .chargedTxFee(10000000L)
                .consensusTimestamp(timestamp())
                .entityId(entityId())
                .highVolume(false)
                .index(transactionIndex())
                .initialBalance(10000000L)
                .itemizedTransfer(List.of(ItemizedTransfer.builder()
                        .amount(100L)
                        .entityId(entityId())
                        .isApproval(false)
                        .build()))
                .maxCustomFees(new byte[][] {bytes(6), bytes(8)})
                .maxFee(100000000L)
                .memo(bytes(10))
                .nodeAccountId(entityId())
                .nonce(0)
                .parentConsensusTimestamp(timestamp())
                .payerAccountId(entityId())
                .result(ResponseCodeEnum.SUCCESS.getNumber())
                .scheduled(false)
                .transactionHash(bytes(48))
                .type(TransactionType.CRYPTOTRANSFER.getProtoId())
                .validStartNs(timestamp())
                .validDurationSeconds(120L);
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TransactionHash, TransactionHash.TransactionHashBuilder> transactionHash() {
        var builder = TransactionHash.builder()
                .consensusTimestamp(timestamp())
                .hash(bytes(48))
                .payerAccountId(id());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public DomainWrapper<TransactionSignature, TransactionSignature.TransactionSignatureBuilder>
            transactionSignature() {
        var builder = TransactionSignature.builder()
                .consensusTimestamp(timestamp())
                .entityId(entityId())
                .publicKeyPrefix(bytes(16))
                .signature(bytes(32))
                .type(SignaturePair.SignatureCase.ED25519.getNumber());
        return new DomainWrapperImpl<>(builder, builder::build);
    }

    public <T, B> DomainWrapper<T, B> wrap(B builder, Supplier<T> supplier) {
        return new DomainWrapperImpl<>(builder, supplier);
    }

    public byte[] bloomFilter() {
        return bytes(LogsBloomAggregator.BYTE_SIZE);
    }

    // Helper methods
    public byte[] bytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    public EntityId entityId() {
        long nextNum = number() + LAST_RESERVED_ID;
        return entityNum(nextNum);
    }

    public EntityId entityNum(long num) {
        return EntityId.of(commonProperties.getShard(), commonProperties.getRealm(), num);
    }

    public byte[] evmAddress() {
        return bytes(20);
    }

    public List<Authorization> authorizationList() {
        return List.of(Authorization.builder()
                .address("0x" + hash(40))
                .chainId("0x1")
                .nonce(number())
                .r("0x" + hash(64))
                .s("0x" + hash(64))
                .yParity(0x01)
                .build());
    }

    public FixedFee fixedFee() {
        return FixedFee.builder()
                .allCollectorsAreExempt(true)
                .amount(number())
                .collectorAccountId(entityId())
                .denominatingTokenId(entityId())
                .build();
    }

    public String hash(int characters) {
        return RandomStringUtils.secure().next(characters, "0123456789abcdef");
    }

    /**
     * Generates an encoded entity id above the max reserved id 1000. Use {@link #number()} instead for a number
     * starting from 1.
     *
     * @return The generated encoded entity id.
     */
    public long id() {
        return entityId().getId();
    }

    public byte[] key() {
        return num.get() % 2 == 0 ? key(KeyCase.ECDSA_SECP256K1) : key(KeyCase.ED25519);
    }

    public byte[] keyList(int count) {
        var keyList = KeyList.newBuilder();
        for (int i = 0; i < count; i++) {
            var keyCase = number() % 2 == 0 ? KeyCase.ECDSA_SECP256K1 : KeyCase.ED25519;
            keyList.addKeys(protobufKey(keyCase));
        }

        return Key.newBuilder().setKeyList(keyList).build().toByteArray();
    }

    public byte[] key(KeyCase keyCase) {
        return protobufKey(keyCase).toByteArray();
    }

    public byte[] nonZeroBytes(int length) {
        var bytes = bytes(length);
        for (int i = 0; i < length; i++) {
            if (bytes[i] == 0) {
                bytes[i] = (byte) random.nextInt(1, Byte.MAX_VALUE);
            }
        }
        return bytes;
    }

    public long number() {
        return num.incrementAndGet();
    }

    public Key protobufKey(KeyCase keyCase) {
        return switch (keyCase) {
            case ECDSA_SECP256K1 ->
                Key.newBuilder().setECDSASecp256K1(generateSecp256k1Key()).build();
            case ED25519 ->
                Key.newBuilder()
                        .setEd25519(ByteString.copyFrom(bytes(KEY_LENGTH_ED25519)))
                        .build();
            default -> throw new UnsupportedOperationException("Key type not supported");
        };
    }

    public Timestamp protoTimestamp() {
        long timestamp = timestamp();
        return Timestamp.newBuilder()
                .setSeconds(timestamp / DomainUtils.NANOS_PER_SECOND)
                .setNanos((int) (timestamp % DomainUtils.NANOS_PER_SECOND))
                .build();
    }

    /**
     * Reset the timestamp, so next call of timestamp() will return value + 1
     *
     * @param value The timestamp to reset to
     */
    public void resetTimestamp(long value) {
        timestampOffset = value - timestampNoOffset();
    }

    public String text(int characters) {
        return RandomStringUtils.secure().nextAlphanumeric(characters);
    }

    public byte[] thresholdKey(int count, int threshold) {
        var keyList = KeyList.newBuilder();
        for (int i = 0; i < count; i++) {
            keyList.addKeys(protobufKey(i % 2 == 0 ? KeyCase.ECDSA_SECP256K1 : KeyCase.ED25519));
        }
        var thresholdKey = ThresholdKey.newBuilder()
                .setKeys(keyList)
                .setThreshold(threshold)
                .build();
        return Key.newBuilder().setThresholdKey(thresholdKey).build().toByteArray();
    }

    public long timestamp() {
        return timestampNoOffset() + timestampOffset;
    }

    public Range<Long> timestampRange() {
        return Range.atLeast(timestamp());
    }

    private long tinybar() {
        return number() * TINYBARS_IN_ONE_HBAR;
    }

    private FallbackFee fallbackFee() {
        return FallbackFee.builder()
                .amount(number())
                .denominatingTokenId(entityId())
                .build();
    }

    private byte[] feeExemptKeyList() {
        return FeeExemptKeyList.newBuilder()
                .addKeys(protobufKey(KeyCase.ECDSA_SECP256K1))
                .addKeys(protobufKey(KeyCase.ED25519))
                .build()
                .toByteArray();
    }

    private FractionalFee fractionalFee() {
        return FractionalFee.builder()
                .allCollectorsAreExempt(true)
                .collectorAccountId(entityId())
                .denominator(number())
                .maximumAmount(number())
                .minimumAmount(1L)
                .numerator(number())
                .netOfTransfers(true)
                .build();
    }

    @SneakyThrows
    private ByteString generateSecp256k1Key() {
        var keyPair = Keys.createEcKeyPair();
        var publicKey = keyPair.getPublicKey();

        // Convert BigInteger public key to a full 65-byte uncompressed key
        var fullPublicKey = Numeric.hexStringToByteArray(Numeric.toHexStringWithPrefixZeroPadded(publicKey, 130));

        // Convert to compressed format (33 bytes)
        var prefix = (byte) (fullPublicKey[64] % 2 == 0 ? 0x02 : 0x03); // 0x02 for even Y, 0x03 for odd Y
        var compressedKey = new byte[33];
        compressedKey[0] = prefix;
        System.arraycopy(fullPublicKey, 1, compressedKey, 1, 32); // Copy only X coordinate
        return ByteString.copyFrom(compressedKey);
    }

    private long getEpochDay(long timestamp) {
        return LocalDate.ofInstant(Instant.ofEpochSecond(0, timestamp), ZoneId.of("UTC"))
                .atStartOfDay()
                .toLocalDate()
                .toEpochDay();
    }

    // SQL timestamp type only supports up to microsecond granularity
    private Instant instant() {
        return now.truncatedTo(ChronoUnit.MILLIS).plusMillis(number());
    }

    private RoyaltyFee royaltyFee() {
        return RoyaltyFee.builder()
                .allCollectorsAreExempt(true)
                .collectorAccountId(entityId())
                .denominator(number())
                .fallbackFee(fallbackFee())
                .numerator(number())
                .build();
    }

    private long timestampNoOffset() {
        return DomainUtils.convertToNanosMax(now.getEpochSecond(), now.getNano()) + number();
    }

    private int transactionIndex() {
        return transactionIndex.getAndIncrement();
    }

    @Value
    private class DomainWrapperImpl<T, B> implements DomainWrapper<T, B> {

        private final B builder;
        private final Supplier<T> supplier;

        @Override
        public DomainWrapper<T, B> customize(Consumer<B> customizer) {
            customizer.accept(builder);
            return this;
        }

        @Override
        public T get() {
            return supplier.get();
        }

        // The DomainBuilder can be used without an active ApplicationContext. If so, this method shouldn't be used.
        @Override
        public T persist() {
            T entity = get();

            if (entityManager == null) {
                throw new IllegalStateException("Unable to persist entity without an EntityManager");
            }

            transactionOperations.executeWithoutResult(t -> entityManager.persist(entity));
            log.trace("Inserted {}", entity);
            return entity;
        }
    }
}
