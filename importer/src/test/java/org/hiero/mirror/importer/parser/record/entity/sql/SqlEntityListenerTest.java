// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.common.util.DomainUtils.EMPTY_BYTE_ARRAY;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.contract.ContractState;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.hiero.mirror.common.domain.contract.ContractTransaction;
import org.hiero.mirror.common.domain.entity.CryptoAllowance;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.hiero.mirror.common.domain.entity.TokenAllowance;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.common.domain.hook.HookExtensionPoint;
import org.hiero.mirror.common.domain.hook.HookType;
import org.hiero.mirror.common.domain.node.Node;
import org.hiero.mirror.common.domain.node.ServiceEndpoint;
import org.hiero.mirror.common.domain.schedule.Schedule;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenAirdrop;
import org.hiero.mirror.common.domain.token.TokenAirdropStateEnum;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.token.TokenPauseStatusEnum;
import org.hiero.mirror.common.domain.token.TokenSupplyTypeEnum;
import org.hiero.mirror.common.domain.token.TokenTransfer;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.common.domain.topic.Topic;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionHash;
import org.hiero.mirror.common.domain.transaction.TransactionSignature;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.parser.record.entity.ParserContext;
import org.hiero.mirror.importer.repository.AssessedCustomFeeRepository;
import org.hiero.mirror.importer.repository.ContractActionRepository;
import org.hiero.mirror.importer.repository.ContractLogRepository;
import org.hiero.mirror.importer.repository.ContractRepository;
import org.hiero.mirror.importer.repository.ContractResultRepository;
import org.hiero.mirror.importer.repository.ContractStateChangeRepository;
import org.hiero.mirror.importer.repository.ContractStateRepository;
import org.hiero.mirror.importer.repository.ContractTransactionRepository;
import org.hiero.mirror.importer.repository.CryptoAllowanceRepository;
import org.hiero.mirror.importer.repository.CryptoTransferRepository;
import org.hiero.mirror.importer.repository.CustomFeeRepository;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.hiero.mirror.importer.repository.EntityTransactionRepository;
import org.hiero.mirror.importer.repository.EthereumTransactionRepository;
import org.hiero.mirror.importer.repository.FileDataRepository;
import org.hiero.mirror.importer.repository.HookRepository;
import org.hiero.mirror.importer.repository.LiveHashRepository;
import org.hiero.mirror.importer.repository.NetworkFreezeRepository;
import org.hiero.mirror.importer.repository.NetworkStakeRepository;
import org.hiero.mirror.importer.repository.NftAllowanceRepository;
import org.hiero.mirror.importer.repository.NftRepository;
import org.hiero.mirror.importer.repository.NodeRepository;
import org.hiero.mirror.importer.repository.NodeStakeRepository;
import org.hiero.mirror.importer.repository.PrngRepository;
import org.hiero.mirror.importer.repository.ScheduleRepository;
import org.hiero.mirror.importer.repository.StakingRewardTransferRepository;
import org.hiero.mirror.importer.repository.TokenAccountRepository;
import org.hiero.mirror.importer.repository.TokenAirdropRepository;
import org.hiero.mirror.importer.repository.TokenAllowanceRepository;
import org.hiero.mirror.importer.repository.TokenRepository;
import org.hiero.mirror.importer.repository.TokenTransferRepository;
import org.hiero.mirror.importer.repository.TopicMessageRepository;
import org.hiero.mirror.importer.repository.TopicRepository;
import org.hiero.mirror.importer.repository.TransactionHashRepository;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.hiero.mirror.importer.repository.TransactionSignatureRepository;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor
final class SqlEntityListenerTest extends ImporterIntegrationTest {

    private final AssessedCustomFeeRepository assessedCustomFeeRepository;
    private final ContractActionRepository contractActionRepository;
    private final ContractLogRepository contractLogRepository;
    private final ContractRepository contractRepository;
    private final ContractResultRepository contractResultRepository;
    private final ContractStateChangeRepository contractStateChangeRepository;
    private final ContractStateRepository contractStateRepository;
    private final ContractTransactionRepository contractTransactionRepository;
    private final CryptoAllowanceRepository cryptoAllowanceRepository;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final CustomFeeRepository customFeeRepository;
    private final DomainBuilder domainBuilder;
    private final EntityProperties entityProperties;
    private final EntityRepository entityRepository;
    private final EntityTransactionRepository entityTransactionRepository;
    private final EthereumTransactionRepository ethereumTransactionRepository;
    private final FileDataRepository fileDataRepository;
    private final HookRepository hookRepository;
    private final LiveHashRepository liveHashRepository;
    private final NetworkFreezeRepository networkFreezeRepository;
    private final NetworkStakeRepository networkStakeRepository;
    private final NftRepository nftRepository;
    private final NftAllowanceRepository nftAllowanceRepository;
    private final NodeRepository nodeRepository;
    private final NodeStakeRepository nodeStakeRepository;
    private final ParserContext parserContext;
    private final PrngRepository prngRepository;
    private final ScheduleRepository scheduleRepository;
    private final SqlEntityListener sqlEntityListener;
    private final SqlProperties sqlProperties;
    private final StakingRewardTransferRepository stakingRewardTransferRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenAirdropRepository tokenAirdropRepository;
    private final TokenAllowanceRepository tokenAllowanceRepository;
    private final TokenRepository tokenRepository;
    private final TokenTransferRepository tokenTransferRepository;
    private final TopicRepository topicRepository;
    private final TopicMessageRepository topicMessageRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionHashRepository transactionHashRepository;
    private final TransactionSignatureRepository transactionSignatureRepository;
    private final TransactionTemplate transactionTemplate;

    private Set<TransactionType> defaultTransactionHashTypes;

    @BeforeEach
    void beforeEach() {
        defaultTransactionHashTypes = entityProperties.getPersist().getTransactionHashTypes();

        entityProperties.getPersist().setEntityHistory(true);
        entityProperties.getPersist().setTrackBalance(true);
    }

    @AfterEach
    void afterEach() {
        entityProperties.getPersist().setEntityHistory(true);
        entityProperties.getPersist().setTransactionHashTypes(defaultTransactionHashTypes);
        entityProperties.getPersist().setTransactionHash(true);
        entityProperties.getPersist().setTrackBalance(true);
    }

    @Test
    void executeBatch() {
        // given
        Entity entity1 = domainBuilder.entity().get();
        Entity entity2 = domainBuilder.entity().get();

        // when
        sqlEntityListener.onEntity(entity1);
        sqlEntityListener.onEntity(entity2);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(entity1, entity2);
        assertThat(findHistory(Entity.class)).isEmpty();
    }

    @Test
    void isEnabled() {
        sqlProperties.setEnabled(false);
        assertThat(sqlEntityListener.isEnabled()).isFalse();

        sqlProperties.setEnabled(true);
        assertThat(sqlEntityListener.isEnabled()).isTrue();
    }

    @Test
    void onAssessedCustomFee() {
        // given
        var assessedCustomFee = domainBuilder.assessedCustomFee().get();

        // when
        sqlEntityListener.onAssessedCustomFee(assessedCustomFee);
        completeFileAndCommit();

        // then
        assertThat(assessedCustomFeeRepository.findAll()).containsExactly(assessedCustomFee);
    }

    @Test
    void onContract() {
        // given
        Contract contract1 = domainBuilder.contract().get();
        Contract contract2 = domainBuilder.contract().get();

        // when
        sqlEntityListener.onContract(contract1);
        sqlEntityListener.onContract(contract2);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.count()).isZero();
        assertThat(contractRepository.findAll()).containsExactlyInAnyOrder(contract1, contract2);
    }

    @Test
    void onContractAction() {
        // given
        ContractAction contractAction = domainBuilder.contractAction().get();

        // when
        sqlEntityListener.onContractAction(contractAction);
        completeFileAndCommit();

        // then
        assertThat(contractActionRepository.findAll()).containsExactlyInAnyOrder(contractAction);
    }

    @Test
    void onContractLog() {
        // given
        ContractLog contractLog = domainBuilder.contractLog().get();

        // when
        sqlEntityListener.onContractLog(contractLog);
        completeFileAndCommit();

        // then
        assertThat(contractLogRepository.findAll()).containsExactlyInAnyOrder(contractLog);
    }

    @Test
    void onContractResult() {
        // given
        ContractResult contractResult = domainBuilder.contractResult().get();

        // when
        sqlEntityListener.onContractResult(contractResult);
        completeFileAndCommit();

        // then
        assertThat(contractResultRepository.findAll()).containsExactlyInAnyOrder(contractResult);
    }

    @Test
    void onContractStateChange() {
        // given
        ContractStateChange contractStateChange =
                domainBuilder.contractStateChange().get();
        ContractState expectedContractState = ContractState.builder()
                .contractId(contractStateChange.getContractId())
                .createdTimestamp(contractStateChange.getConsensusTimestamp())
                .modifiedTimestamp(contractStateChange.getConsensusTimestamp())
                .slot(DomainUtils.leftPadBytes(contractStateChange.getSlot(), 32))
                .value(contractStateChange.getValueWritten())
                .build();

        // when
        sqlEntityListener.onContractStateChange(contractStateChange);
        completeFileAndCommit();

        // then
        assertThat(contractStateChangeRepository.findAll()).containsExactlyInAnyOrder(contractStateChange);
        assertThat(contractStateRepository.findAll()).containsExactlyInAnyOrder(expectedContractState);
    }

    @Test
    void onContractState() {
        // given
        var builder = domainBuilder.contractStateChange().customize(c -> c.slot(domainBuilder.bytes(15)));
        var contractStateChangeCreate = builder.get();
        var contractStateChangeValueWritten = builder.customize(c -> c.valueWritten(domainBuilder.bytes(32))
                        .consensusTimestamp(contractStateChangeCreate.getConsensusTimestamp() + 1))
                .get();
        var contractStateChangeNoValue = builder.customize(c ->
                        c.valueWritten(null).consensusTimestamp(contractStateChangeCreate.getConsensusTimestamp() + 2))
                .get();

        // when
        sqlEntityListener.onContractStateChange(contractStateChangeCreate);
        sqlEntityListener.onContractStateChange(contractStateChangeValueWritten);
        sqlEntityListener.onContractStateChange(contractStateChangeNoValue);
        completeFileAndCommit();

        var expectedContractState = ContractState.builder()
                .contractId(contractStateChangeCreate.getContractId())
                .createdTimestamp(contractStateChangeCreate.getConsensusTimestamp())
                .modifiedTimestamp(contractStateChangeValueWritten.getConsensusTimestamp())
                .slot(DomainUtils.leftPadBytes(contractStateChangeCreate.getSlot(), 32))
                .value(contractStateChangeValueWritten.getValueWritten())
                .build();

        // then
        assertThat(contractStateRepository.findAll()).containsExactlyInAnyOrder(expectedContractState);
    }

    @Test
    void onContractStateMigrateFalse() {
        // given
        var builder = domainBuilder
                .contractStateChange()
                .customize(c -> c.contractId(1000).consensusTimestamp(1L).valueWritten("a".getBytes()));

        var contractStateChange1Create = builder.get();
        var contractStateChange1Update = builder.customize(
                        c -> c.consensusTimestamp(2L).valueWritten("b".getBytes()))
                .get();
        var contractStateChange2Create = builder.customize(
                        c -> c.contractId(1001).consensusTimestamp(2L).valueWritten("c".getBytes()))
                .get();
        var contractStateChange2Update = builder.customize(
                        c -> c.consensusTimestamp(3L).valueWritten(null))
                .get();
        var contractStateChange1Update2 = builder.customize(
                        c -> c.contractId(1000).consensusTimestamp(4L).valueWritten("d".getBytes()))
                .get();
        var contractStateChange2Update2 = builder.customize(
                        c -> c.contractId(1001).consensusTimestamp(4L).valueWritten("e".getBytes()))
                .get();

        // when
        sqlEntityListener.onContractStateChange(contractStateChange1Create);
        sqlEntityListener.onContractStateChange(contractStateChange2Create);
        completeFileAndCommit();

        sqlEntityListener.onContractStateChange(contractStateChange1Update);
        sqlEntityListener.onContractStateChange(contractStateChange2Update);
        completeFileAndCommit();

        sqlEntityListener.onContractStateChange(contractStateChange1Update2);
        sqlEntityListener.onContractStateChange(contractStateChange2Update2);
        completeFileAndCommit();

        var expected = List.of(
                getContractState(contractStateChange1Update2, 1L), getContractState(contractStateChange2Update2, 2L));

        // then
        assertThat(contractStateRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void onContractStateMigrateTrue() {
        // given
        var builder = domainBuilder.contractStateChange().customize(c -> c.contractId(1000)
                .consensusTimestamp(1L)
                .migration(true)
                .slot(new byte[] {1})
                .valueRead("a".getBytes())
                .valueWritten(null));

        var contractStateChange1Create = builder.get();
        var contractStateChange1Update = builder.customize(
                        c -> c.consensusTimestamp(2L).valueWritten("b".getBytes()))
                .get();
        var contractStateChange2Create = builder.customize(
                        c -> c.contractId(1001).consensusTimestamp(2L).valueRead("c".getBytes()))
                .get();
        var contractStateChange2Update = builder.customize(
                        c -> c.consensusTimestamp(3L).valueWritten(null))
                .get();
        var contractStateChange1Update2 = builder.customize(
                        c -> c.contractId(1000).consensusTimestamp(4L).valueRead("d".getBytes()))
                .get();
        var contractStateChange2Update2 = builder.customize(
                        c -> c.contractId(1001).consensusTimestamp(4L).valueRead("e".getBytes()))
                .get();

        // when
        sqlEntityListener.onContractStateChange(contractStateChange1Create);
        sqlEntityListener.onContractStateChange(contractStateChange2Create);
        completeFileAndCommit();

        sqlEntityListener.onContractStateChange(contractStateChange1Update);
        sqlEntityListener.onContractStateChange(contractStateChange2Update);
        completeFileAndCommit();

        sqlEntityListener.onContractStateChange(contractStateChange1Update2);
        sqlEntityListener.onContractStateChange(contractStateChange2Update2);
        completeFileAndCommit();

        var expected = List.of(
                getContractState(contractStateChange1Update2, 1L), getContractState(contractStateChange2Update2, 2L));

        // then
        assertThat(contractStateRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void onContractTransactionsPersistEnabled() {
        var currentEnabledValue = entityProperties.getPersist().isContractTransaction();
        entityProperties.getPersist().setContractTransaction(true);
        ContractTransaction contractTransaction1 =
                domainBuilder.contractTransaction().get();
        ContractTransaction contractTransaction2 =
                domainBuilder.contractTransaction().get();
        ContractTransaction contractTransaction3 =
                domainBuilder.contractTransaction().get();

        sqlEntityListener.onContractTransactions(
                Arrays.asList(contractTransaction1, contractTransaction2, contractTransaction3));
        completeFileAndCommit();

        assertThat(contractTransactionRepository.findAll())
                .containsExactlyInAnyOrder(contractTransaction1, contractTransaction2, contractTransaction3);
        entityProperties.getPersist().setContractTransaction(currentEnabledValue);
    }

    @Test
    void onContractTransactionsPersistDisabled() {
        var currentEnabledValue = entityProperties.getPersist().isContractTransaction();
        entityProperties.getPersist().setContractTransaction(false);
        ContractTransaction contractTransaction1 =
                domainBuilder.contractTransaction().get();
        ContractTransaction contractTransaction2 =
                domainBuilder.contractTransaction().get();
        ContractTransaction contractTransaction3 =
                domainBuilder.contractTransaction().get();

        sqlEntityListener.onContractTransactions(
                Arrays.asList(contractTransaction1, contractTransaction2, contractTransaction3));
        completeFileAndCommit();

        assertThat(contractTransactionRepository.findAll()).isEmpty();
        entityProperties.getPersist().setContractTransaction(currentEnabledValue);
    }

    @Test
    void onCryptoAllowance() {
        // given
        CryptoAllowance cryptoAllowance1 = domainBuilder.cryptoAllowance().get();
        CryptoAllowance cryptoAllowance2 = domainBuilder.cryptoAllowance().get();

        // when
        sqlEntityListener.onCryptoAllowance(cryptoAllowance1);
        sqlEntityListener.onCryptoAllowance(cryptoAllowance2);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.count()).isZero();
        assertThat(cryptoAllowanceRepository.findAll()).containsExactlyInAnyOrder(cryptoAllowance1, cryptoAllowance2);
        assertThat(findHistory(CryptoAllowance.class)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void onCryptoAllowanceWithApprovedTransfer(int commitIndex) {
        // given
        var amountGranted = 1000L;
        var amountTransferred = -100L;

        var builder = domainBuilder.cryptoAllowance();
        var cryptoAllowanceCreate = builder.customize(
                        c -> c.amountGranted(amountGranted).amount(amountGranted))
                .get();

        // when
        sqlEntityListener.onCryptoAllowance(cryptoAllowanceCreate);

        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(cryptoAllowanceRepository.findAll()).containsExactly(cryptoAllowanceCreate);
            assertThat(findHistory(CryptoAllowance.class)).isEmpty();
        }

        // Approved transfer allowance debit as emitted by EntityRecordItemListener
        var cryptoAllowanceDebitFromTransfer = builder.customize(
                        c -> c.amount(amountTransferred).amountGranted(null).timestampRange(null))
                .get();

        sqlEntityListener.onCryptoAllowance(cryptoAllowanceDebitFromTransfer);
        completeFileAndCommit();

        // then
        cryptoAllowanceCreate.setAmount(amountGranted + amountTransferred);
        assertThat(entityRepository.count()).isZero();
        assertThat(cryptoAllowanceRepository.findAll()).containsExactly(cryptoAllowanceCreate);
        assertThat(findHistory(CryptoAllowance.class)).isEmpty();
    }

    @Test
    void onCryptoAllowanceExistingWithApprovedTransfer() {
        // given
        var amountGranted = 1000L;
        var amountTransferred = -100L;
        var builder = domainBuilder.cryptoAllowance();
        var cryptoAllowanceCreate = builder.customize(
                        c -> c.amountGranted(amountGranted).amount(amountGranted))
                .persist();

        // Approved transfer allowance debit as emitted by EntityRecordItemListener
        var cryptoAllowanceDebitFromTransfer = builder.customize(
                        c -> c.amount(amountTransferred).amountGranted(null).timestampRange(null))
                .get();

        // when
        sqlEntityListener.onCryptoAllowance(cryptoAllowanceDebitFromTransfer);
        completeFileAndCommit();

        // then
        cryptoAllowanceCreate.setAmount(amountGranted + amountTransferred);
        assertThat(cryptoAllowanceRepository.findAll()).containsExactly(cryptoAllowanceCreate);
        assertThat(findHistory(CryptoAllowance.class)).isEmpty();
    }

    // Partial mirror node scenario where the crypto allowance grant does not exist in
    // the database. An approved transfer debit operation should have no affect and not be persisted.
    @Test
    void onCryptoAllowanceAbsentWithApprovedTransfer() {
        // given
        // Approved transfer allowance debit as emitted by EntityRecordItemListener
        var cryptoAllowanceDebitFromTransfer = domainBuilder
                .cryptoAllowance()
                .customize(c -> c.amount(-100L).amountGranted(null).timestampRange(null))
                .get();

        // when
        sqlEntityListener.onCryptoAllowance(cryptoAllowanceDebitFromTransfer);
        completeFileAndCommit();

        // then
        assertThat(cryptoAllowanceRepository.count()).isZero();
        assertThat(findHistory(CryptoAllowance.class)).isEmpty();
    }

    @ValueSource(ints = {1, 2, 3, 4})
    @ParameterizedTest
    void onCryptoAllowanceHistory(int commitIndex) {
        // given
        var cryptoAllowanceCreate1 = domainBuilder.cryptoAllowance().get();
        var cryptoAllowanceUpdate1 = cryptoAllowanceCreate1.toBuilder()
                .amount(-90L)
                .amountGranted(null)
                .timestampRange(null)
                .build();

        long amount = cryptoAllowanceCreate1.getAmount() + 200L;
        var cryptoAllowanceCreate2 = cryptoAllowanceCreate1.toBuilder()
                .amount(amount)
                .amountGranted(amount)
                .timestampRange(domainBuilder.timestampRange())
                .build();
        var cryptoAllowanceRevoke = cryptoAllowanceCreate1.toBuilder()
                .amount(0)
                .amountGranted(0L)
                .timestampRange(domainBuilder.timestampRange())
                .build();

        // when
        sqlEntityListener.onCryptoAllowance(cryptoAllowanceCreate1);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(cryptoAllowanceRepository.findAll()).containsExactly(cryptoAllowanceCreate1);
            assertThat(findHistory(CryptoAllowance.class)).isEmpty();
        }
        var mergedUpdate1 = cryptoAllowanceCreate1.toBuilder()
                .amount(cryptoAllowanceCreate1.getAmount() + cryptoAllowanceUpdate1.getAmount())
                .build();

        sqlEntityListener.onCryptoAllowance(cryptoAllowanceUpdate1);
        if (commitIndex > 2) {
            completeFileAndCommit();
            assertThat(cryptoAllowanceRepository.findAll()).containsExactly(mergedUpdate1);
            assertThat(findHistory(CryptoAllowance.class)).isEmpty();
        }
        mergedUpdate1.setTimestampUpper(cryptoAllowanceCreate2.getTimestampLower());

        sqlEntityListener.onCryptoAllowance(cryptoAllowanceCreate2);
        if (commitIndex > 3) {
            completeFileAndCommit();
            assertThat(cryptoAllowanceRepository.findAll()).containsExactly(cryptoAllowanceCreate2);
            assertThat(findHistory(CryptoAllowance.class)).containsExactly(mergedUpdate1);
        }
        var mergedUpdate2 = cryptoAllowanceCreate2.toBuilder().build();
        mergedUpdate2.setTimestampUpper(cryptoAllowanceRevoke.getTimestampLower());

        sqlEntityListener.onCryptoAllowance(cryptoAllowanceRevoke);
        completeFileAndCommit();

        // then
        assertThat(cryptoAllowanceRepository.findAll()).containsExactly(cryptoAllowanceRevoke);
        assertThat(findHistory(CryptoAllowance.class)).containsExactlyInAnyOrder(mergedUpdate1, mergedUpdate2);
    }

    @Test
    void onCryptoTransfer() {
        // given
        var cryptoTransfer1 = domainBuilder.cryptoTransfer().get();
        var cryptoTransfer2 = domainBuilder.cryptoTransfer().get();

        // when
        sqlEntityListener.onCryptoTransfer(cryptoTransfer1);
        sqlEntityListener.onCryptoTransfer(cryptoTransfer2);
        completeFileAndCommit();

        // then
        assertThat(cryptoTransferRepository.findAll()).containsExactlyInAnyOrder(cryptoTransfer1, cryptoTransfer2);
        assertThat(entityRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"true, 95, 225", "false, 100, 200"})
    void onCryptoTransferWhenEntitiesExist(
            boolean trackBalance, long expectedAccountBalance, long expectedContractBalance) {
        // given
        entityProperties.getPersist().setTrackBalance(trackBalance);
        var account = domainBuilder
                .entity()
                .customize(e -> e.balance(100L).type(ACCOUNT))
                .persist();
        var contract = domainBuilder
                .entity()
                .customize(e -> e.balance(200L).type(CONTRACT))
                .persist();
        var cryptoTransfer1 = domainBuilder
                .cryptoTransfer()
                .customize(c -> c.amount(-15L).entityId(account.getId()))
                .get();
        var cryptoTransfer2 = domainBuilder
                .cryptoTransfer()
                .customize(c -> c.amount(10L).entityId(account.getId()))
                .get();
        var cryptoTransfer3 = domainBuilder
                .cryptoTransfer()
                .customize(c -> c.amount(25L).entityId(contract.getId()))
                .get();
        account.setBalance(expectedAccountBalance);
        contract.setBalance(expectedContractBalance);

        // when
        sqlEntityListener.onCryptoTransfer(cryptoTransfer1);
        sqlEntityListener.onCryptoTransfer(cryptoTransfer2);
        sqlEntityListener.onCryptoTransfer(cryptoTransfer3);
        completeFileAndCommit();

        // then
        if (trackBalance) {
            account.setBalanceTimestamp(cryptoTransfer2.getConsensusTimestamp());
            contract.setBalanceTimestamp(cryptoTransfer3.getConsensusTimestamp());
        }
        assertThat(cryptoTransferRepository.findAll())
                .containsExactlyInAnyOrder(cryptoTransfer1, cryptoTransfer2, cryptoTransfer3);
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, contract);
        assertThat(findHistory(Entity.class)).isEmpty();
    }

    @Test
    void onCryptoTransferBeforeContractCreate() {
        // given
        var contract = domainBuilder
                .entity()
                .customize(e -> e.balance(0L).type(CONTRACT))
                .get();
        var cryptoTransfer = domainBuilder
                .cryptoTransfer()
                .customize(c -> c.amount(10L)
                        .consensusTimestamp(contract.getCreatedTimestamp() - 1L)
                        .entityId(contract.getId()))
                .get();
        var expectedContract = TestUtils.clone(contract);
        expectedContract.setBalance(10L);
        expectedContract.setBalanceTimestamp(cryptoTransfer.getConsensusTimestamp());

        // when
        sqlEntityListener.onCryptoTransfer(cryptoTransfer);
        sqlEntityListener.onEntity(contract);
        completeFileAndCommit();

        // then
        assertThat(cryptoTransferRepository.findAll()).containsOnly(cryptoTransfer);
        assertThat(entityRepository.findAll()).containsOnly(expectedContract);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void onCustomFee(int commitIndex) {
        var nonEmptyCustomFee = domainBuilder.customFee().get();
        sqlEntityListener.onCustomFee(nonEmptyCustomFee);

        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(customFeeRepository.findAll()).containsExactly(nonEmptyCustomFee);
        }

        var emptyCustomFee = domainBuilder
                .customFee()
                .customize(cf -> cf.entityId(nonEmptyCustomFee.getEntityId())
                        .fixedFees(null)
                        .fractionalFees(null)
                        .royaltyFees(null))
                .get();
        sqlEntityListener.onCustomFee(emptyCustomFee);
        completeFileAndCommit();

        nonEmptyCustomFee.setTimestampUpper(emptyCustomFee.getTimestampLower());
        assertThat(customFeeRepository.findAll()).containsExactly(emptyCustomFee);
        assertThat(findHistory(CustomFee.class)).containsExactly(nonEmptyCustomFee);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onEntityWhenTypeIsAccount(boolean hasNonce) {
        // given
        Entity entity1 = domainBuilder
                .entity()
                .customize(e -> e.ethereumNonce(hasNonce ? 1L : null))
                .get();
        Entity entity2 = domainBuilder
                .entity()
                .customize(e -> e.ethereumNonce(hasNonce ? 2L : null))
                .get();

        // when
        sqlEntityListener.onEntity(entity1);
        sqlEntityListener.onEntity(entity2);
        completeFileAndCommit();

        // then
        if (!hasNonce) {
            // the default nonce is 0 for ACCOUNT
            entity1.setEthereumNonce(0L);
            entity2.setEthereumNonce(0L);
        }
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(entity1, entity2);
        assertThat(findHistory(Entity.class)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onEntityTypeIsContract(boolean hasNonce) {
        // given
        Entity entity1 = domainBuilder
                .entity()
                .customize(e -> e.ethereumNonce(hasNonce ? 1L : null).type(CONTRACT))
                .get();
        Entity entity2 = domainBuilder
                .entity()
                .customize(e -> e.ethereumNonce(hasNonce ? 2L : null).type(CONTRACT))
                .get();

        // when
        sqlEntityListener.onEntity(entity1);
        sqlEntityListener.onEntity(entity2);
        completeFileAndCommit();

        // then
        assertThat(contractRepository.count()).isZero();
        // for contract, there shouldn't be a default nonce value
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(entity1, entity2);
        assertThat(findHistory(Entity.class)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void onEntityCreateAndBalanceChange(int commitIndex) {
        // given
        var entityCreate = domainBuilder.entity().customize(e -> e.balance(0L)).get();
        var balanceUpdate1 = Entity.builder()
                .id(entityCreate.getId())
                .balance(25L)
                .balanceTimestamp(domainBuilder.timestamp())
                .build();
        var balanceUpdate2 = Entity.builder()
                .id(entityCreate.getId())
                .balance(15L)
                .balanceTimestamp(domainBuilder.timestamp())
                .build();
        var expectedEntity = TestUtils.clone(entityCreate);

        // when
        sqlEntityListener.onEntity(entityCreate);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(entityRepository.findAll()).containsExactly(expectedEntity);
            assertThat(findHistory(Entity.class)).isEmpty();
        }

        sqlEntityListener.onEntity(balanceUpdate1);
        expectedEntity.setBalance(25L);
        expectedEntity.setBalanceTimestamp(balanceUpdate1.getBalanceTimestamp());
        if (commitIndex > 2) {
            completeFileAndCommit();
            assertThat(entityRepository.findAll()).containsExactly(expectedEntity);
            assertThat(findHistory(Entity.class)).isEmpty();
        }

        sqlEntityListener.onEntity(balanceUpdate2);
        expectedEntity.setBalance(40L);
        expectedEntity.setBalanceTimestamp(balanceUpdate2.getBalanceTimestamp());
        completeFileAndCommit();

        // then
        assertThat(entityRepository.findAll()).containsExactly(expectedEntity);
        assertThat(findHistory(Entity.class)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = EntityType.class,
            mode = Mode.EXCLUDE,
            names = {"ACCOUNT", "CONTRACT"})
    void onEntityBalanceChangeWhenNotPayableAndNoInitialBalance(EntityType type) {
        // given
        // The entity is not payable and there is no balance info
        var entity = domainBuilder
                .entity()
                .customize(e -> e.balance(null).balanceTimestamp(null).type(type))
                .persist();

        // when
        var balanceChange = Entity.builder()
                .balance(domainBuilder.number())
                .balanceTimestamp(domainBuilder.timestamp())
                .id(entity.getId())
                .build();
        sqlEntityListener.onEntity(balanceChange);
        completeFileAndCommit();

        // then
        entity.setBalanceTimestamp(balanceChange.getBalanceTimestamp());
        assertThat(entityRepository.findAll()).containsExactly(entity);
    }

    @ParameterizedTest
    @EnumSource(
            value = EntityType.class,
            mode = Mode.EXCLUDE,
            names = {"ACCOUNT", "CONTRACT"})
    void onEntityBalanceChangeWhenNotPayableAndWithInitialBalance(EntityType type) {
        // given
        // The entity is not payable and there is balance info, initialized from consensus nodes' account balance file
        var entity = domainBuilder.entity().customize(e -> e.type(type)).persist();

        // when
        var balanceChange = Entity.builder()
                .balance(domainBuilder.number())
                .balanceTimestamp(domainBuilder.timestamp())
                .id(entity.getId())
                .build();
        sqlEntityListener.onEntity(balanceChange);
        completeFileAndCommit();

        // then
        entity.setBalance(entity.getBalance() + balanceChange.getBalance());
        entity.setBalanceTimestamp(balanceChange.getBalanceTimestamp());
        assertThat(entityRepository.findAll()).containsExactly(entity);
    }

    @Test
    void onEntityWithNonHistoryUpdates() {
        // given
        // Update to non-history field with existing entity should just update nonce
        Entity existingEntity = domainBuilder.entity().persist();
        Entity existingEntityNonce1 = existingEntity.toEntityId().toEntity();
        existingEntityNonce1.setEthereumNonce(100L);
        existingEntityNonce1.setTimestampRange(null);
        existingEntityNonce1.setType(ACCOUNT);

        Entity existingEntityNonce2 = existingEntity.toEntityId().toEntity();
        existingEntityNonce2.setEthereumNonce(101L);
        existingEntityNonce2.setTimestampRange(null);
        existingEntityNonce2.setType(ACCOUNT);

        Entity existingEntityStakePeriodStart1 = existingEntity.toEntityId().toEntity();
        existingEntityStakePeriodStart1.setStakePeriodStart(2L);
        existingEntityStakePeriodStart1.setTimestampRange(null);
        existingEntityStakePeriodStart1.setType(ACCOUNT);

        Entity existingEntityStakePeriodStart2 = existingEntity.toEntityId().toEntity();
        existingEntityStakePeriodStart2.setStakePeriodStart(5L);
        existingEntityStakePeriodStart2.setTimestampRange(null);
        existingEntityStakePeriodStart2.setType(ACCOUNT);

        // Update to non-history field with partial data should be discarded
        Entity nonExistingEntity = domainBuilder.entity().get();
        Entity nonExistingEntityNonce1 = nonExistingEntity.toEntityId().toEntity();
        nonExistingEntityNonce1.setEthereumNonce(200L);
        nonExistingEntityNonce1.setTimestampRange(null);
        nonExistingEntityNonce1.setType(ACCOUNT);

        Entity nonExistingEntityNonce2 = nonExistingEntity.toEntityId().toEntity();
        nonExistingEntityNonce2.setEthereumNonce(201L);
        nonExistingEntityNonce2.setTimestampRange(null);
        nonExistingEntityNonce2.setType(ACCOUNT);

        Entity nonExistingEntityStakePeriodStart1 =
                nonExistingEntity.toEntityId().toEntity();
        nonExistingEntityStakePeriodStart1.setStakePeriodStart(6L);
        nonExistingEntityStakePeriodStart1.setTimestampRange(null);
        nonExistingEntityStakePeriodStart1.setType(ACCOUNT);

        Entity nonExistingEntityStakePeriodStart2 =
                nonExistingEntity.toEntityId().toEntity();
        nonExistingEntityStakePeriodStart2.setStakePeriodStart(8L);
        nonExistingEntityStakePeriodStart2.setTimestampRange(null);
        nonExistingEntityStakePeriodStart2.setType(ACCOUNT);

        // when
        sqlEntityListener.onEntity(existingEntityNonce1);
        sqlEntityListener.onEntity(existingEntityNonce2);
        sqlEntityListener.onEntity(existingEntityStakePeriodStart1);
        sqlEntityListener.onEntity(existingEntityStakePeriodStart2);
        sqlEntityListener.onEntity(nonExistingEntityNonce1);
        sqlEntityListener.onEntity(nonExistingEntityNonce2);
        sqlEntityListener.onEntity(nonExistingEntityStakePeriodStart1);
        sqlEntityListener.onEntity(nonExistingEntityStakePeriodStart2);
        completeFileAndCommit();

        existingEntity.setEthereumNonce(existingEntityNonce2.getEthereumNonce());
        existingEntity.setStakePeriodStart(existingEntityStakePeriodStart2.getStakePeriodStart());
        assertThat(entityRepository.findAll()).containsExactly(existingEntity);

        Entity existingEntityNonce3 = existingEntity.toEntityId().toEntity();
        existingEntityNonce3.setEthereumNonce(102L);
        existingEntityNonce3.setTimestampRange(null);
        existingEntityNonce3.setType(ACCOUNT);

        Entity existingEntityStakePeriodStart3 = existingEntity.toEntityId().toEntity();
        existingEntityStakePeriodStart3.setStakePeriodStart(10L);
        existingEntityStakePeriodStart3.setTimestampRange(null);
        existingEntityStakePeriodStart3.setType(ACCOUNT);

        Entity nonExistingEntityNonce3 = domainBuilder.entityId().toEntity();
        nonExistingEntityNonce3.setEthereumNonce(202L);
        nonExistingEntityNonce3.setTimestampRange(null);
        nonExistingEntityNonce3.setType(ACCOUNT);

        Entity nonExistingEntityStakePeriodStart3 = domainBuilder.entityId().toEntity();
        nonExistingEntityStakePeriodStart3.setStakePeriodStart(12L);
        nonExistingEntityStakePeriodStart3.setTimestampRange(null);
        nonExistingEntityStakePeriodStart3.setType(ACCOUNT);

        sqlEntityListener.onEntity(existingEntityNonce3);
        sqlEntityListener.onEntity(existingEntityStakePeriodStart3);
        sqlEntityListener.onEntity(nonExistingEntityNonce3);
        sqlEntityListener.onEntity(nonExistingEntityStakePeriodStart3);
        completeFileAndCommit();

        // then
        existingEntity.setEthereumNonce(existingEntityNonce3.getEthereumNonce());
        existingEntity.setStakePeriodStart(existingEntityStakePeriodStart3.getStakePeriodStart());
        assertThat(entityRepository.findAll()).containsExactly(existingEntity);
        assertThat(findHistory(Entity.class)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onEntityWithHistoryAndNonHistoryUpdates(boolean nonHistoryBefore) {
        // given
        Entity entity = domainBuilder.entity().persist();
        Entity entityNonce1 = entity.toEntityId().toEntity();
        entityNonce1.setEthereumNonce(100L);
        entityNonce1.setTimestampRange(null);
        entityNonce1.setType(ACCOUNT);

        Entity entityStakePeriodStart1 = entity.toEntityId().toEntity();
        entityStakePeriodStart1.setStakePeriodStart(2L);
        entityStakePeriodStart1.setTimestampRange(null);
        entityStakePeriodStart1.setType(ACCOUNT);

        Entity entityNonce2 = entity.toEntityId().toEntity();
        entityNonce2.setEthereumNonce(101L);
        entityNonce2.setTimestampRange(null);
        entityNonce2.setType(ACCOUNT);

        Entity entityStakePeriodStart2 = entity.toEntityId().toEntity();
        entityStakePeriodStart2.setStakePeriodStart(7L);
        entityStakePeriodStart2.setTimestampRange(null);
        entityStakePeriodStart2.setType(ACCOUNT);

        Entity entityMemoUpdated = entity.toEntityId().toEntity();
        entityMemoUpdated.setMemo(domainBuilder.text(16));
        entityMemoUpdated.setTimestampLower(domainBuilder.timestamp());
        entityMemoUpdated.setType(ACCOUNT);

        // when
        if (nonHistoryBefore) {
            sqlEntityListener.onEntity(entityNonce1);
            sqlEntityListener.onEntity(entityNonce2);
            sqlEntityListener.onEntity(entityStakePeriodStart1);
            sqlEntityListener.onEntity(entityStakePeriodStart2);
            sqlEntityListener.onEntity(entityMemoUpdated);
        } else {
            sqlEntityListener.onEntity(entityMemoUpdated);
            sqlEntityListener.onEntity(entityStakePeriodStart1);
            sqlEntityListener.onEntity(entityNonce1);
            sqlEntityListener.onEntity(entityStakePeriodStart2);
            sqlEntityListener.onEntity(entityNonce2);
        }
        completeFileAndCommit();

        // then
        Entity entityMerged = TestUtils.clone(entity);
        entityMerged.setMemo(entityMemoUpdated.getMemo());
        entityMerged.setEthereumNonce(entityNonce2.getEthereumNonce());
        entityMerged.setStakePeriodStart(entityStakePeriodStart2.getStakePeriodStart());
        entityMerged.setTimestampRange(entityMemoUpdated.getTimestampRange());
        entity.setTimestampUpper(entityMemoUpdated.getTimestampLower());

        assertThat(entityRepository.findAll()).containsExactly(entityMerged);
        assertThat(findHistory(Entity.class)).containsExactly(entity);
    }

    @Test
    void onNonHistoryUpdateWithIncorrectTypeThenHistoryUpdate() {
        // given
        var entityId = domainBuilder.entityId(); // The entity in fact is a contract
        var nonHistoryUpdate = entityId.toEntity();
        nonHistoryUpdate.setStakePeriodStart(120L);
        nonHistoryUpdate.setTimestampRange(null);
        nonHistoryUpdate.setType(ACCOUNT);
        var historyUpdate = entityId.toEntity();
        historyUpdate.setMemo("Update entity memo");
        historyUpdate.setTimestampRange(domainBuilder.timestampRange());
        historyUpdate.setType(CONTRACT); // Correct type
        var expectedEntity = TestUtils.clone(historyUpdate);
        expectedEntity.setBalance(0L);
        expectedEntity.setDeclineReward(false);
        expectedEntity.setStakedNodeId(-1L);
        expectedEntity.setStakePeriodStart(120L);

        // when
        sqlEntityListener.onEntity(nonHistoryUpdate);
        sqlEntityListener.onEntity(historyUpdate);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.findAll()).containsOnly(expectedEntity);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void onEntityHistory(int commitIndex) {
        // given
        var entityCreate = domainBuilder.entity().get();

        var entityUpdate = entityCreate.toEntityId().toEntity();
        entityUpdate.setAlias(entityCreate.getAlias());
        entityUpdate.setAutoRenewAccountId(101L);
        entityUpdate.setAutoRenewPeriod(30L);
        entityUpdate.setDeclineReward(true);
        entityUpdate.setExpirationTimestamp(500L);
        entityUpdate.setKey(domainBuilder.thresholdKey(2, 1));
        entityUpdate.setMaxAutomaticTokenAssociations(40);
        entityUpdate.setMemo("updated");
        entityUpdate.setTimestampLower(entityCreate.getTimestampLower() + 1);
        entityUpdate.setProxyAccountId(EntityId.of(100L));
        entityUpdate.setReceiverSigRequired(true);
        entityUpdate.setStakedAccountId(domainBuilder.id());
        entityUpdate.setStakedNodeId(-1L);
        entityUpdate.setStakePeriodStart(domainBuilder.number());
        entityUpdate.setType(ACCOUNT);

        var entityDelete = entityCreate.toEntityId().toEntity();
        entityDelete.setAlias(entityCreate.getAlias());
        entityDelete.setDeleted(true);
        entityDelete.setTimestampLower(entityCreate.getTimestampLower() + 2);
        entityDelete.setType(ACCOUNT);

        // Expected merged objects
        var mergedCreate = TestUtils.clone(entityCreate);
        var mergedUpdate = TestUtils.merge(entityCreate, entityUpdate);
        var mergedDelete = TestUtils.merge(mergedUpdate, entityDelete);
        mergedCreate.setTimestampUpper(entityUpdate.getTimestampLower());
        mergedUpdate.setPublicKey(null); // workaround the sentinel public key side effect
        mergedDelete.setPublicKey(null); // workaround the sentinel public key side effect

        // when
        sqlEntityListener.onEntity(entityCreate);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(entityRepository.findAll()).containsExactly(entityCreate);
            assertThat(findHistory(Entity.class)).isEmpty();
        }

        sqlEntityListener.onEntity(entityUpdate);
        if (commitIndex > 2) {
            completeFileAndCommit();
            assertThat(entityRepository.findAll()).containsExactly(mergedUpdate);
            assertThat(findHistory(Entity.class)).containsExactly(mergedCreate);
        }

        sqlEntityListener.onEntity(entityDelete);
        completeFileAndCommit();

        // then
        mergedUpdate.setTimestampUpper(entityDelete.getTimestampLower());
        assertThat(entityRepository.findAll()).containsExactly(mergedDelete);
        assertThat(findHistory(Entity.class)).containsExactly(mergedCreate, mergedUpdate);
    }

    @Test
    void onEntityPersistOriginalHistoryCreatedTimestamp() {
        var entity1 = domainBuilder.entity().get();
        sqlEntityListener.onEntity(entity1);
        completeFileAndCommit();

        long timestamp = entity1.getCreatedTimestamp() + 12345L;
        var entity2 = entity1.toBuilder()
                .createdTimestamp(timestamp)
                .timestampRange(Range.atLeast(timestamp))
                .build();
        var entity3 = entity2.toBuilder()
                .createdTimestamp(null)
                .timestampRange(Range.atLeast(timestamp + 5555L))
                .build();
        sqlEntityListener.onEntity(entity2);
        sqlEntityListener.onEntity(entity3);
        completeFileAndCommit();

        assertThat(entityRepository.findAll())
                .hasSize(1)
                .first()
                .returns(entity1.getCreatedTimestamp(), Entity::getCreatedTimestamp);
        assertThat(findHistory(Entity.class))
                .extracting("createdTimestamp")
                .containsOnly(entity1.getCreatedTimestamp());
    }

    @Test
    void onEntityWhenEntityHistoryDisabled() {
        // given
        entityProperties.getPersist().setEntityHistory(false);
        var entity1 = domainBuilder.entity().persist();
        var entity1Update = entity1.toBuilder()
                .createdTimestamp(null)
                .timestampRange(domainBuilder.timestampRange())
                .build();
        var entity2Create = domainBuilder.entity().get();
        var entity2Update = entity2Create.toBuilder()
                .createdTimestamp(null)
                .timestampRange(domainBuilder.timestampRange())
                .build();

        // when
        sqlEntityListener.onEntity(entity1Update);
        sqlEntityListener.onEntity(entity2Create);
        sqlEntityListener.onEntity(entity2Update);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(entity1, entity2Create);
        assertThat(findHistory(Entity.class)).isEmpty();
    }

    @Test
    void onEntityTransactions() {
        // given
        var entityTransaction1 = domainBuilder.entityTransaction().get();
        var entityTransaction2 = domainBuilder.entityTransaction().get();

        // when
        sqlEntityListener.onEntityTransactions(List.of(entityTransaction1));
        sqlEntityListener.onEntityTransactions(List.of(entityTransaction2));
        completeFileAndCommit();

        // then
        assertThat(entityTransactionRepository.findAll())
                .containsExactlyInAnyOrder(entityTransaction1, entityTransaction2);
    }

    @Test
    void onTopic() {
        // given
        var topic1 = domainBuilder.topic().get();
        var topic2 = domainBuilder.topic().get();

        // when
        sqlEntityListener.onTopic(topic1);
        sqlEntityListener.onTopic(topic2);
        completeFileAndCommit();

        // then
        assertThat(topicRepository.findAll()).containsExactlyInAnyOrder(topic1, topic2);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void onTopicHistory(int commitIndex) {
        var create = domainBuilder.topic().get();
        sqlEntityListener.onTopic(create);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(topicRepository.findAll()).containsExactly(create);
            assertThat(findHistory(Topic.class)).isEmpty();
        }

        var updateAdminKey = Topic.builder()
                .adminKey(domainBuilder.bytes(16))
                .id(create.getId())
                .timestampRange(domainBuilder.timestampRange())
                .build();
        sqlEntityListener.onTopic(updateAdminKey);
        var expectedHistory = new ArrayList<Topic>();
        create.setTimestampUpper(updateAdminKey.getTimestampLower());
        expectedHistory.add(create);
        var current = create.toBuilder()
                .adminKey(updateAdminKey.getAdminKey())
                .timestampRange(updateAdminKey.getTimestampRange())
                .build();
        if (commitIndex > 2) {
            completeFileAndCommit();
            assertThat(topicRepository.findAll()).containsExactly(current);
            assertThat(findHistory(Topic.class)).containsExactlyInAnyOrderElementsOf(expectedHistory);
        }

        var updateFeeScheduleKey = Topic.builder()
                .feeScheduleKey(domainBuilder.bytes(16))
                .id(create.getId())
                .timestampRange(domainBuilder.timestampRange())
                .build();
        sqlEntityListener.onTopic(updateFeeScheduleKey);
        expectedHistory.add(current.toBuilder()
                .timestampRange(Range.closedOpen(current.getTimestampLower(), updateFeeScheduleKey.getTimestampLower()))
                .build());
        current.setFeeScheduleKey(updateFeeScheduleKey.getFeeScheduleKey());
        current.setTimestampLower(updateFeeScheduleKey.getTimestampLower());
        if (commitIndex > 3) {
            completeFileAndCommit();
            assertThat(topicRepository.findAll()).containsExactly(current);
            assertThat(findHistory(Topic.class)).containsExactlyInAnyOrderElementsOf(expectedHistory);
        }

        var updateFeeExemptKeyList = Topic.builder()
                .feeExemptKeyList(domainBuilder.bytes(16))
                .id(create.getId())
                .timestampRange(domainBuilder.timestampRange())
                .build();
        sqlEntityListener.onTopic(updateFeeExemptKeyList);
        expectedHistory.add(current.toBuilder()
                .timestampRange(
                        Range.closedOpen(current.getTimestampLower(), updateFeeExemptKeyList.getTimestampLower()))
                .build());
        current.setFeeExemptKeyList(updateFeeExemptKeyList.getFeeExemptKeyList());
        current.setTimestampLower(updateFeeExemptKeyList.getTimestampLower());
        if (commitIndex > 4) {
            completeFileAndCommit();
            assertThat(topicRepository.findAll()).containsExactly(current);
            assertThat(findHistory(Topic.class)).containsExactlyInAnyOrderElementsOf(expectedHistory);
        }

        var updateSubmitKey = Topic.builder()
                .id(create.getId())
                .submitKey(domainBuilder.bytes(16))
                .timestampRange(domainBuilder.timestampRange())
                .build();
        sqlEntityListener.onTopic(updateSubmitKey);
        expectedHistory.add(current.toBuilder()
                .timestampRange(Range.closedOpen(current.getTimestampLower(), updateSubmitKey.getTimestampLower()))
                .build());
        current.setSubmitKey(updateSubmitKey.getSubmitKey());
        current.setTimestampLower(updateSubmitKey.getTimestampLower());

        completeFileAndCommit();

        assertThat(topicRepository.findAll()).containsExactly(current);
        assertThat(findHistory(Topic.class)).containsExactlyInAnyOrderElementsOf(expectedHistory);
    }

    @Test
    void onTopicMessage() {
        // given
        TopicMessage topicMessage = domainBuilder.topicMessage().get();

        // when
        sqlEntityListener.onTopicMessage(topicMessage);
        completeFileAndCommit();

        // then
        assertThat(topicMessageRepository.findAll()).containsExactlyInAnyOrder(topicMessage);
    }

    @Test
    void onFileData() {
        // given
        var fileData1 = domainBuilder.fileData().get();
        var fileData2 = domainBuilder.fileData().get();

        // when
        sqlEntityListener.onFileData(fileData1);
        sqlEntityListener.onFileData(fileData2);
        completeFileAndCommit();

        // then
        assertThat(fileDataRepository.findAll()).containsExactlyInAnyOrder(fileData1, fileData2);
    }

    @Test
    void onLiveHash() {
        // given
        var liveHash = domainBuilder.liveHash().get();

        // when
        sqlEntityListener.onLiveHash(liveHash);
        completeFileAndCommit();

        // then
        assertThat(liveHashRepository.findAll()).containsExactly(liveHash);
    }

    @Test
    void onNetworkFreeze() {
        // given
        var networkFreeze1 = domainBuilder.networkFreeze().get();
        var networkFreeze2 = domainBuilder.networkFreeze().get();

        // when
        sqlEntityListener.onNetworkFreeze(networkFreeze1);
        sqlEntityListener.onNetworkFreeze(networkFreeze2);
        completeFileAndCommit();

        // then
        assertThat(networkFreezeRepository.findAll()).containsExactlyInAnyOrder(networkFreeze1, networkFreeze2);
    }

    @Test
    void onNetworkStake() {
        // given
        var networkStake1 = domainBuilder.networkStake().get();
        var networkStake2 = domainBuilder.networkStake().get();

        // when
        sqlEntityListener.onNetworkStake(networkStake1);
        sqlEntityListener.onNetworkStake(networkStake2);
        completeFileAndCommit();

        // then
        assertThat(networkStakeRepository.findAll()).containsExactlyInAnyOrder(networkStake1, networkStake2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onTransaction(boolean persistTransactionHash) {
        // given
        entityProperties.getPersist().setTransactionHash(persistTransactionHash);
        var firstTransaction = domainBuilder.transaction().get();
        var secondTransaction = domainBuilder.transaction().get();
        var thirdTransaction = domainBuilder.transaction().get();
        var expectedTransactionHashes = Stream.of(firstTransaction, secondTransaction, thirdTransaction)
                .filter(t -> persistTransactionHash)
                .map(Transaction::toTransactionHash)
                .toList();

        // when
        sqlEntityListener.onTransaction(firstTransaction);
        sqlEntityListener.onTransaction(secondTransaction);
        sqlEntityListener.onTransaction(thirdTransaction);
        completeFileAndCommit();

        // then
        assertThat(transactionRepository.findAll())
                .containsExactlyInAnyOrder(firstTransaction, secondTransaction, thirdTransaction);

        assertThat(transactionRepository.findById(firstTransaction.getConsensusTimestamp()))
                .get()
                .isNotNull()
                .returns(0, Transaction::getIndex)
                .returns(firstTransaction.getItemizedTransfer(), Transaction::getItemizedTransfer);

        assertThat(transactionRepository.findById(secondTransaction.getConsensusTimestamp()))
                .get()
                .isNotNull()
                .returns(1, Transaction::getIndex)
                .returns(secondTransaction.getItemizedTransfer(), Transaction::getItemizedTransfer);

        assertThat(transactionRepository.findById(thirdTransaction.getConsensusTimestamp()))
                .get()
                .isNotNull()
                .returns(2, Transaction::getIndex)
                .returns(thirdTransaction.getItemizedTransfer(), Transaction::getItemizedTransfer);

        assertThat(transactionHashRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTransactionHashes);
    }

    @Test
    void onTransactionWithBatchParent() {
        var batchParent = domainBuilder
                .transaction()
                .customize(builder -> builder.type(TransactionType.ATOMIC_BATCH.getProtoId()))
                .get();
        var firstInner = domainBuilder
                .transaction()
                .customize(builder -> builder.batchKey(domainBuilder.key())
                        .parentConsensusTimestamp(batchParent.getConsensusTimestamp()))
                .get();
        var secondInner = domainBuilder
                .transaction()
                .customize(builder -> builder.batchKey(domainBuilder.key())
                        .parentConsensusTimestamp(batchParent.getConsensusTimestamp()))
                .get();

        sqlEntityListener.onTransaction(batchParent);
        sqlEntityListener.onTransaction(firstInner);
        sqlEntityListener.onTransaction(secondInner);

        completeFileAndCommit();

        assertThat(transactionRepository.findAll()).containsExactlyInAnyOrder(batchParent, firstInner, secondInner);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void onTransactionWithMissingBatchParent(CapturedOutput output) {
        var timestamp = domainBuilder.timestamp();
        var firstInner = domainBuilder
                .transaction()
                .customize(builder -> builder.batchKey(domainBuilder.key()).parentConsensusTimestamp(timestamp))
                .get();

        sqlEntityListener.onTransaction(firstInner);

        completeFileAndCommit();

        assertThat(transactionRepository.findAll()).containsExactlyInAnyOrder(firstInner);
        assertThat(output.getAll()).contains("Recoverable error. Batch parent not found for transaction");
    }

    @ParameterizedTest
    @EnumSource(
            value = TransactionType.class,
            names = {"CRYPTOTRANSFER", "CONSENSUSSUBMITMESSAGE"})
    void onTransactionHashByTransactionType(TransactionType includedTransactionType) {
        // given
        entityProperties.getPersist().setTransactionHashTypes(Set.of(includedTransactionType));
        var consensusSubmitMessage = domainBuilder
                .transaction()
                .customize(t -> t.type(TransactionType.CONSENSUSSUBMITMESSAGE.getProtoId()))
                .get();
        var cryptoTransfer = domainBuilder.transaction().get();
        var expectedTransactionHashes = Stream.of(consensusSubmitMessage, cryptoTransfer)
                .filter(t -> t.getType() == includedTransactionType.getProtoId())
                .map(Transaction::toTransactionHash)
                .toList();

        // when
        sqlEntityListener.onTransaction(cryptoTransfer);
        sqlEntityListener.onTransaction(consensusSubmitMessage);
        completeFileAndCommit();

        // then
        assertThat(transactionRepository.findAll()).containsExactlyInAnyOrder(consensusSubmitMessage, cryptoTransfer);
        assertThat(transactionHashRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTransactionHashes);
    }

    @Test
    void onTransactionHashWhenFilterEmpty() {
        // given
        entityProperties.getPersist().setTransactionHashTypes(Collections.emptySet());
        var consensusSubmitMessage = domainBuilder
                .transaction()
                .customize(t -> t.type(TransactionType.CONSENSUSSUBMITMESSAGE.getProtoId()))
                .get();
        var cryptoTransfer = domainBuilder.transaction().get();
        var expectedTransactionHashes = Stream.of(consensusSubmitMessage, cryptoTransfer)
                .map(Transaction::toTransactionHash)
                .toList();

        // when
        sqlEntityListener.onTransaction(cryptoTransfer);
        sqlEntityListener.onTransaction(consensusSubmitMessage);
        completeFileAndCommit();

        // then
        assertThat(transactionRepository.findAll()).containsExactlyInAnyOrder(consensusSubmitMessage, cryptoTransfer);
        assertThat(transactionHashRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTransactionHashes);
    }

    @Test
    void onTransactionHashInvalidHash() {
        // given
        entityProperties.getPersist().setTransactionHashTypes(Collections.emptySet());
        var cryptoTransfer = domainBuilder.transaction().get();
        cryptoTransfer.setTransactionHash(Arrays.copyOfRange(cryptoTransfer.getTransactionHash(), 0, 31));

        // when
        sqlEntityListener.onTransaction(cryptoTransfer);
        completeFileAndCommit();

        // then
        assertThat(transactionRepository.findAll()).containsExactlyInAnyOrder(cryptoTransfer);
        assertThat(transactionHashRepository.findAll()).isEmpty();
    }

    @Test
    void onNft() {
        // create token first
        EntityId tokenId1 = EntityId.of("0.0.1");
        EntityId tokenId2 = EntityId.of("0.0.3");
        EntityId accountId1 = EntityId.of("0.0.2");
        EntityId accountId2 = EntityId.of("0.0.4");
        EntityId treasuryId = EntityId.of("0.0.98");
        String metadata1 = "nft1";
        String metadata1Update = "update_nft1";
        String metadata2 = "nft2";

        Token token1 = getToken(tokenId1, treasuryId, 1L, 1L);
        Token token2 = getToken(tokenId2, treasuryId, 2L, 2L);
        sqlEntityListener.onToken(token1);
        sqlEntityListener.onToken(token2);
        completeFileAndCommit();

        // create nft 1
        sqlEntityListener.onNft(getNft(tokenId1, 1L, null, 3L, false, metadata1, 3L)); // mint
        sqlEntityListener.onNft(getNft(tokenId1, 1L, accountId1, null, null, null, 3L)); // transfer
        sqlEntityListener.onNft(getNft(tokenId1, 1L, null, null, null, metadata1Update, 4L)); // metadata update

        // create nft 2
        sqlEntityListener.onNft(getNft(tokenId2, 1L, null, 4L, false, metadata2, 4L)); // mint
        sqlEntityListener.onNft(getNft(tokenId2, 1L, accountId2, null, null, null, 4L)); // transfer
        sqlEntityListener.onNft(getNft(tokenId2, 1L, accountId2, null, null, "", 5L)); // metadata clear

        completeFileAndCommit();

        Nft nft1 = getNft(tokenId1, 1L, accountId1, 3L, false, metadata1Update, 4L); // transfer + metadata update
        Nft nft2 = getNft(tokenId2, 1L, accountId2, 4L, false, "", 5L); // transfer + metadata clear

        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
    }

    @Test
    void onNftAllowance() {
        // given
        NftAllowance nftAllowance1 = domainBuilder.nftAllowance().get();
        NftAllowance nftAllowance2 = domainBuilder.nftAllowance().get();

        // when
        sqlEntityListener.onNftAllowance(nftAllowance1);
        sqlEntityListener.onNftAllowance(nftAllowance2);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.count()).isZero();
        assertThat(nftAllowanceRepository.findAll()).containsExactlyInAnyOrder(nftAllowance1, nftAllowance2);
        assertThat(findHistory(NftAllowance.class)).isEmpty();
    }

    @ValueSource(ints = {1, 2})
    @ParameterizedTest
    void onNftAllowanceHistory(int commitIndex) {
        // given
        var builder = domainBuilder.nftAllowance();
        NftAllowance nftAllowanceCreate =
                builder.customize(c -> c.approvedForAll(true)).get();

        NftAllowance nftAllowanceUpdate1 = builder.get();
        nftAllowanceUpdate1.setTimestampLower(nftAllowanceCreate.getTimestampLower() + 1);

        // Expected merged objects
        NftAllowance mergedCreate = TestUtils.clone(nftAllowanceCreate);
        NftAllowance mergedUpdate1 = TestUtils.merge(nftAllowanceCreate, nftAllowanceUpdate1);
        mergedCreate.setTimestampUpper(nftAllowanceUpdate1.getTimestampLower());

        // when
        sqlEntityListener.onNftAllowance(nftAllowanceCreate);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(nftAllowanceRepository.findAll()).containsExactly(nftAllowanceCreate);
            assertThat(findHistory(NftAllowance.class)).isEmpty();
        }

        sqlEntityListener.onNftAllowance(nftAllowanceUpdate1);
        completeFileAndCommit();

        // then
        assertThat(nftAllowanceRepository.findAll()).containsExactly(mergedUpdate1);
        assertThat(findHistory(NftAllowance.class)).containsExactly(mergedCreate);
    }

    @ValueSource(ints = {1, 2})
    @ParameterizedTest
    void onNftWithInstanceAllowance(int commitIndex) {
        // given
        var nft = domainBuilder.nft().persist();

        // grant allowance
        var expectedNft = TestUtils.clone(nft);
        expectedNft.setDelegatingSpender(domainBuilder.entityId().getId());
        expectedNft.setTimestampLower(domainBuilder.timestamp());
        expectedNft.setSpender(domainBuilder.entityId().getId());

        var nftUpdate = TestUtils.clone(expectedNft);
        nftUpdate.setCreatedTimestamp(null);

        sqlEntityListener.onNft(nftUpdate);
        if (commitIndex > 1) {
            // when
            completeFileAndCommit();
            // then
            assertThat(nftRepository.findAll()).containsOnly(expectedNft);
        }

        // revoke allowance
        expectedNft = TestUtils.clone(nft);
        expectedNft.setTimestampLower(domainBuilder.timestamp());

        nftUpdate = TestUtils.clone(expectedNft);
        nftUpdate.setCreatedTimestamp(null);
        sqlEntityListener.onNft(nftUpdate);

        // when
        completeFileAndCommit();

        // then
        assertThat(nftRepository.findAll()).containsOnly(expectedNft);
    }

    @Test
    void onNftMintOutOfOrder() {
        // create token first
        EntityId tokenId1 = EntityId.of("0.0.1");
        EntityId tokenId2 = EntityId.of("0.0.3");
        EntityId accountId1 = EntityId.of("0.0.2");
        EntityId accountId2 = EntityId.of("0.0.4");
        EntityId treasuryId = EntityId.of("0.0.98");
        String metadata1 = "nft1";
        String metadata2 = "nft2";

        // save token entities first
        Token token1 = getToken(tokenId1, treasuryId, 1L, 1L);
        Token token2 = getToken(tokenId2, treasuryId, 2L, 2L);
        sqlEntityListener.onToken(token1);
        sqlEntityListener.onToken(token2);

        // create nft 1 w transfer coming first
        sqlEntityListener.onNft(getNft(tokenId1, 1L, accountId1, null, null, null, 3L)); // transfer
        sqlEntityListener.onNft(getNft(tokenId1, 1L, null, 3L, false, metadata1, 3L)); // mint

        // create nft 2 w transfer coming first
        sqlEntityListener.onNft(getNft(tokenId2, 1L, accountId2, null, null, null, 4L)); // transfer
        sqlEntityListener.onNft(getNft(tokenId2, 1L, null, 4L, false, metadata2, 4L)); // mint

        completeFileAndCommit();

        Nft nft1 = getNft(tokenId1, 1L, accountId1, 3L, false, metadata1, 3L); // transfer
        Nft nft2 = getNft(tokenId2, 1L, accountId2, 4L, false, metadata2, 4L); // transfer

        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
    }

    @Test
    void onNftDomainTransfer() {
        // create token first
        EntityId tokenId1 = EntityId.of("0.0.1");
        EntityId tokenId2 = EntityId.of("0.0.3");
        EntityId accountId1 = EntityId.of("0.0.2");
        EntityId accountId2 = EntityId.of("0.0.4");
        EntityId treasuryId = EntityId.of("0.0.98");
        EntityId accountId3 = EntityId.of("0.0.5");
        EntityId accountId4 = EntityId.of("0.0.6");
        String metadata1 = "nft1";
        String metadata2 = "nft2";

        // save token entities first
        Token token1 = getToken(tokenId1, treasuryId, 1L, 1L);
        Token token2 = getToken(tokenId2, treasuryId, 2L, 2L);
        sqlEntityListener.onToken(token1);
        sqlEntityListener.onToken(token2);

        // create nfts
        Nft nft1Combined = getNft(tokenId1, 1L, accountId1, 3L, false, metadata1, 3L); // mint transfer combined
        Nft nft2Combined = getNft(tokenId2, 1L, accountId2, 4L, false, metadata2, 4L); // mint transfer combined
        sqlEntityListener.onNft(nft1Combined);
        sqlEntityListener.onNft(nft2Combined);
        completeFileAndCommit();
        assertEquals(2, nftRepository.count());

        // nft w transfers
        sqlEntityListener.onNft(getNft(tokenId1, 1L, accountId3, null, null, null, 5L)); // transfer
        sqlEntityListener.onNft(getNft(tokenId2, 1L, accountId4, null, null, null, 6L)); // transfer
        completeFileAndCommit();

        Nft nft1 = getNft(tokenId1, 1L, accountId3, 3L, false, metadata1, 5L); // transfer
        Nft nft2 = getNft(tokenId2, 1L, accountId4, 4L, false, metadata2, 6L); // transfer

        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
    }

    @Test
    void onNftTransferOwnershipAndDelete() {
        // create token first
        EntityId tokenId1 = EntityId.of("0.0.1");
        EntityId accountId1 = EntityId.of("0.0.2");
        EntityId accountId2 = EntityId.of("0.0.3");
        EntityId treasury = EntityId.of("0.0.98");
        String metadata1 = "nft1";
        String metadata2 = "nft2";

        // save token entities first
        Token token1 = getToken(tokenId1, treasury, 1L, 1L);
        sqlEntityListener.onToken(token1);

        // create nfts
        Nft nft1Combined = getNft(tokenId1, 1L, accountId1, 3L, false, metadata1, 3L); // mint transfer combined
        Nft nft2Combined = getNft(tokenId1, 2L, accountId2, 4L, false, metadata2, 4L); // mint transfer combined

        sqlEntityListener.onNft(nft1Combined);
        sqlEntityListener.onNft(nft2Combined);

        completeFileAndCommit();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1Combined, nft2Combined);

        Nft nft1Burn = getNft(tokenId1, 1L, EntityId.EMPTY, null, true, null, 5L); // mint/burn
        sqlEntityListener.onNft(nft1Burn);

        Nft nft2Burn = getNft(tokenId1, 2L, EntityId.EMPTY, null, true, null, 6L); // mint/burn
        sqlEntityListener.onNft(nft2Burn);

        completeFileAndCommit();

        // expected nfts
        Nft nft1 = getNft(tokenId1, 1L, null, 3L, true, metadata1, 5L); // transfer
        Nft nft2 = getNft(tokenId1, 2L, null, 4L, true, metadata2, 6L); // transfer

        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onTransactionWithNftTransfer(boolean trackBalance) {
        // given
        entityProperties.getPersist().setTrackBalance(trackBalance);
        var transferTimestamp = domainBuilder.timestamp();
        var nftTransfer1 = domainBuilder.nftTransfer().get();
        var nftTransfer2 = domainBuilder.nftTransfer().get();
        var nftTransfer3 = domainBuilder.nftTransfer().get();

        var nftTransfers = List.of(nftTransfer1, nftTransfer2, nftTransfer3);
        // token account upsert needs token class in db
        nftTransfers.forEach(transfer -> {
            var tokenId = transfer.getTokenId().getId();
            domainBuilder
                    .token()
                    .customize(t -> t.tokenId(tokenId).type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                    .persist();
        });
        var expectedTokenAccounts = nftTransfers.stream()
                .flatMap(transfer -> {
                    long sender = transfer.getSenderAccountId().getId();
                    long receiver = transfer.getReceiverAccountId().getId();
                    long tokenId = transfer.getTokenId().getId();
                    var tokenAccountSender = domainBuilder
                            .tokenAccount()
                            .customize(ta -> ta.accountId(sender)
                                    .tokenId(tokenId)
                                    .balance(6)
                                    .balanceTimestamp(transferTimestamp))
                            .persist();
                    var tokenAccountReceiver = domainBuilder
                            .tokenAccount()
                            .customize(ta -> ta.accountId(receiver)
                                    .tokenId(tokenId)
                                    .balance(1)
                                    .balanceTimestamp(transferTimestamp))
                            .persist();
                    if (trackBalance) {
                        tokenAccountSender.setBalance(5);
                        tokenAccountReceiver.setBalance(2);
                    }

                    return Stream.of(tokenAccountSender, tokenAccountReceiver);
                })
                .toList();

        // when
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(transferTimestamp).nftTransfer(nftTransfers))
                .get();
        sqlEntityListener.onTransaction(transaction);
        completeFileAndCommit();

        // then
        assertThat(transactionRepository.findAll()).containsExactly(transaction);
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTokenAccounts);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onTransactionWithNftTransferBurn(boolean trackBalance) {
        // given
        entityProperties.getPersist().setTrackBalance(trackBalance);
        var token = domainBuilder
                .token()
                .customize(t -> t.type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();
        var tokenId = token.getTokenId();
        var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.balance(2L).tokenId(tokenId))
                .persist();

        var nftTransfer = domainBuilder
                .nftTransfer()
                .customize(t -> t.receiverAccountId(EntityId.EMPTY)
                        .senderAccountId(EntityId.of(tokenAccount.getAccountId()))
                        .tokenId(EntityId.of(tokenId)))
                .get();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.nftTransfer(List.of(nftTransfer)))
                .get();

        // when
        sqlEntityListener.onTransaction(transaction);
        completeFileAndCommit();

        // then
        nftTransfer.setReceiverAccountId(null);
        if (trackBalance) {
            tokenAccount.setBalance(tokenAccount.getBalance() - 1);
            tokenAccount.setBalanceTimestamp(transaction.getConsensusTimestamp());
        }
        assertThat(transactionRepository.findAll()).containsExactly(transaction);
        assertThat(tokenAccountRepository.findAll()).containsExactly(tokenAccount);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onTransactionWithNftTransferMint(boolean trackBalance) {
        // given
        entityProperties.getPersist().setTrackBalance(trackBalance);
        var token = domainBuilder
                .token()
                .customize(t -> t.type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .persist();
        var tokenId = token.getTokenId();
        var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(tokenId))
                .persist();

        var nftTransfer = domainBuilder
                .nftTransfer()
                .customize(t -> t.receiverAccountId(EntityId.of(tokenAccount.getAccountId()))
                        .senderAccountId(EntityId.EMPTY)
                        .tokenId(EntityId.of(tokenId)))
                .get();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.nftTransfer(List.of(nftTransfer)))
                .get();

        // when
        sqlEntityListener.onTransaction(transaction);
        completeFileAndCommit();

        // then
        nftTransfer.setSenderAccountId(null);
        if (trackBalance) {
            tokenAccount.setBalance(tokenAccount.getBalance() + 1);
            tokenAccount.setBalanceTimestamp(transaction.getConsensusTimestamp());
        }
        assertThat(transactionRepository.findAll()).containsExactly(transaction);
        assertThat(tokenAccountRepository.findAll()).containsExactly(tokenAccount);
    }

    @Test
    void onNode() {
        // given
        var node1 = domainBuilder.node().get();
        var node2 = domainBuilder.node().get();

        // when
        sqlEntityListener.onNode(node1);
        sqlEntityListener.onNode(node2);
        completeFileAndCommit();

        // then
        assertThat(nodeRepository.findAll()).containsExactlyInAnyOrder(node1, node2);
    }

    @Test
    void onNodeMerge() {
        // given
        var nodeCreate = domainBuilder.node().get();
        var nodeUpdate = Node.builder()
                .nodeId(nodeCreate.getNodeId())
                .timestampRange(domainBuilder.timestampRange())
                .build();

        // when
        sqlEntityListener.onNode(nodeCreate);
        sqlEntityListener.onNode(nodeUpdate);
        completeFileAndCommit();

        nodeCreate.setTimestampUpper(nodeUpdate.getTimestampLower());
        var nodeUpdateExpected = nodeCreate.toBuilder()
                .timestampRange(nodeUpdate.getTimestampRange())
                .build();

        // then
        assertThat(nodeRepository.findAll()).containsExactly(nodeUpdateExpected);
        assertThat(findHistory(Node.class)).containsExactly(nodeCreate);
    }

    @Test
    void onNodeMergeWithDelete() {
        // given
        var nodeCreate = domainBuilder.node().get();
        var nodeDelete = Node.builder()
                .deleted(true)
                .nodeId(nodeCreate.getNodeId())
                .timestampRange(domainBuilder.timestampRange())
                .build();

        // when
        sqlEntityListener.onNode(nodeCreate);
        sqlEntityListener.onNode(nodeDelete);
        completeFileAndCommit();

        nodeCreate.setTimestampUpper(nodeDelete.getTimestampLower());
        var nodeDeleteExpected = nodeCreate.toBuilder()
                .deleted(true)
                .timestampRange(nodeDelete.getTimestampRange())
                .build();

        // then
        assertThat(nodeRepository.findAll()).containsExactly(nodeDeleteExpected);
        assertThat(findHistory(Node.class)).containsExactly(nodeCreate);
    }

    @Test
    void onNodeHistory() {
        // given
        var nodeCreate = domainBuilder.node().get();
        var nodeUpdate = nodeCreate.toBuilder()
                .accountId(domainBuilder.entityId())
                .adminKey(domainBuilder.key())
                .createdTimestamp(null)
                .declineReward(true)
                .grpcProxyEndpoint(new ServiceEndpoint("", "127.0.0.1", 8080))
                .timestampRange(domainBuilder.timestampRange())
                .build();
        var nodeDelete = Node.builder()
                .deleted(true)
                .nodeId(nodeCreate.getNodeId())
                .timestampRange(domainBuilder.timestampRange())
                .build();
        var nodeDeleteExpected = nodeUpdate.toBuilder()
                .createdTimestamp(nodeCreate.getCreatedTimestamp())
                .deleted(true)
                .timestampRange(nodeDelete.getTimestampRange())
                .build();

        // when the node is created
        sqlEntityListener.onNode(nodeCreate);
        completeFileAndCommit();

        // then
        assertThat(nodeRepository.findAll()).containsExactly(nodeCreate);
        assertThat(findHistory(Node.class)).isEmpty();

        // when the node is updated
        var mergedUpdate = nodeCreate.toBuilder().build();
        mergedUpdate.setTimestampUpper(nodeUpdate.getTimestampLower());
        sqlEntityListener.onNode(nodeUpdate);
        completeFileAndCommit();

        nodeUpdate.setCreatedTimestamp(nodeCreate.getCreatedTimestamp());

        // then
        assertThat(nodeRepository.findAll()).containsExactly(nodeUpdate);
        assertThat(findHistory(Node.class)).containsExactly(mergedUpdate);

        var mergedUpdate2 = nodeUpdate.toBuilder().build();
        mergedUpdate2.setTimestampUpper(nodeDelete.getTimestampLower());

        // when the node is deleted
        sqlEntityListener.onNode(nodeDelete);
        completeFileAndCommit();

        // then
        assertThat(nodeRepository.findAll()).containsExactlyInAnyOrder(nodeDeleteExpected);
        assertThat(findHistory(Node.class)).containsExactlyInAnyOrder(mergedUpdate, mergedUpdate2);
    }

    @Test
    void onNodePartialUpdate() {
        // given
        var nodeUpdate =
                domainBuilder.node().customize(n -> n.createdTimestamp(null)).get();

        // when
        sqlEntityListener.onNode(nodeUpdate);
        completeFileAndCommit();

        // then
        assertThat(nodeRepository.findAll()).containsExactly(nodeUpdate);
        assertThat(findHistory(Node.class)).isEmpty();
    }

    @Test
    void onNodeStake() {
        // given
        var nodeStake1 = domainBuilder.nodeStake().get();
        var nodeStake2 = domainBuilder.nodeStake().get();

        // when
        sqlEntityListener.onNodeStake(nodeStake1);
        sqlEntityListener.onNodeStake(nodeStake2);
        completeFileAndCommit();

        // then
        assertThat(nodeStakeRepository.findAll()).containsExactlyInAnyOrder(nodeStake1, nodeStake2);
    }

    @Test
    void onPrng() {
        var prng = domainBuilder.prng().get();
        var prng2 = domainBuilder
                .prng()
                .customize(r -> r.range(0).prngNumber(null).prngBytes(domainBuilder.bytes(382)))
                .get();

        sqlEntityListener.onPrng(prng);
        sqlEntityListener.onPrng(prng2);

        // when
        completeFileAndCommit();

        // then
        assertThat(prngRepository.findAll()).containsExactlyInAnyOrder(prng, prng2);
    }

    @Test
    void onStakingRewardTransfer() {
        // given
        var transfer1 = domainBuilder.stakingRewardTransfer().get();
        var transfer2 = domainBuilder.stakingRewardTransfer().get();
        var transfer3 = domainBuilder.stakingRewardTransfer().get();
        var expectedEntities = Stream.of(transfer1, transfer2, transfer3)
                .map(transfer -> {
                    var entity = EntityId.of(transfer.getAccountId()).toEntity();
                    entity.setStakePeriodStart(Utility.getEpochDay(transfer.getConsensusTimestamp()) - 1);
                    entity.setTimestampLower(transfer.getConsensusTimestamp());
                    entity.setType(EntityType.UNKNOWN);
                    return entity;
                })
                .toList();

        // when
        sqlEntityListener.onStakingRewardTransfer(transfer1);
        sqlEntityListener.onStakingRewardTransfer(transfer2);
        sqlEntityListener.onStakingRewardTransfer(transfer3);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "stakePeriodStart", "timestampRange", "type")
                .containsExactlyInAnyOrderElementsOf(expectedEntities);
        assertThat(stakingRewardTransferRepository.findAll())
                .containsExactlyInAnyOrder(transfer1, transfer2, transfer3);
    }

    @Test
    void onStakingRewardTransferWithExistingEntity() {
        // given
        var account = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(1L))
                .persist();
        var contract = domainBuilder
                .entity()
                .customize(e -> e.type(CONTRACT).stakedNodeId(0L).stakePeriodStart(1L))
                .persist();
        var transfer1 = domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(account.getId()).amount(10L))
                .get();
        var transfer2 = domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(contract.getId()).amount(20L))
                .get();

        // when
        sqlEntityListener.onStakingRewardTransfer(transfer1);
        sqlEntityListener.onStakingRewardTransfer(transfer2);
        completeFileAndCommit();

        // then
        var accountHistory = account.toBuilder()
                .timestampRange(Range.closedOpen(account.getTimestampLower(), transfer1.getConsensusTimestamp()))
                .build();
        var contractHistory = contract.toBuilder()
                .timestampRange(Range.closedOpen(contract.getTimestampLower(), transfer2.getConsensusTimestamp()))
                .build();
        account.setStakePeriodStart(Utility.getEpochDay(transfer1.getConsensusTimestamp()) - 1);
        account.setTimestampLower(transfer1.getConsensusTimestamp());
        contract.setStakePeriodStart(Utility.getEpochDay(transfer2.getConsensusTimestamp()) - 1);
        contract.setTimestampLower(transfer2.getConsensusTimestamp());
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(account, contract);
        assertThat(findHistory(Entity.class)).containsExactlyInAnyOrder(accountHistory, contractHistory);
        assertThat(stakingRewardTransferRepository.findAll()).containsExactlyInAnyOrder(transfer1, transfer2);
    }

    @Test
    void onStakingRewardTransferAfterMemoUpdate() {
        // given
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(1L))
                .persist();
        long updateTimestamp = domainBuilder.timestamp();
        long stakingRewardTransferTimestamp = updateTimestamp + 1;
        var entityMemoUpdate = Entity.builder()
                .id(entity.getId())
                .memo(domainBuilder.text(6))
                .timestampRange(Range.atLeast(updateTimestamp))
                .type(entity.getType())
                .build();
        // staking reward transfer after entity memo update
        var transfer = domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(entity.getId()).consensusTimestamp(stakingRewardTransferTimestamp))
                .get();

        // when
        sqlEntityListener.onEntity(entityMemoUpdate);
        sqlEntityListener.onStakingRewardTransfer(transfer);
        completeFileAndCommit();

        // then
        entity.setMemo(entityMemoUpdate.getMemo());
        entity.setStakePeriodStart(Utility.getEpochDay(stakingRewardTransferTimestamp) - 1);
        entity.setTimestampRange(Range.atLeast(stakingRewardTransferTimestamp));
        assertThat(entityRepository.findAll()).containsExactly(entity);
        assertThat(stakingRewardTransferRepository.findAll()).containsExactly(transfer);
    }

    @Test
    void onStakingRewardTransferFromStakingUpdate() {
        // given
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(1L))
                .persist();
        long timestamp = domainBuilder.timestamp();
        var entityStakingUpdate = Entity.builder()
                .id(entity.getId())
                .stakedNodeId(1L)
                .stakePeriodStart(20L)
                .timestampRange(Range.atLeast(timestamp))
                .type(entity.getType())
                .build();
        var transfer = domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(entity.getId()).consensusTimestamp(timestamp))
                .get();

        // when
        sqlEntityListener.onEntity(entityStakingUpdate);
        sqlEntityListener.onStakingRewardTransfer(transfer);
        completeFileAndCommit();

        // then
        entity.setStakedNodeId(1L);
        entity.setStakePeriodStart(20L);
        entity.setTimestampRange(Range.atLeast(timestamp));
        assertThat(entityRepository.findAll()).containsExactly(entity);
        assertThat(stakingRewardTransferRepository.findAll()).containsExactly(transfer);
    }

    @Test
    void onStakingRewardTransferFromMemoUpdate() {
        // given
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(1L))
                .persist();
        long timestamp = domainBuilder.timestamp();
        var entityMemoUpdate = Entity.builder()
                .id(entity.getId())
                .memo(domainBuilder.text(6))
                .timestampRange(Range.atLeast(timestamp))
                .type(entity.getType())
                .build();
        var transfer = domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(entity.getId()).consensusTimestamp(timestamp))
                .get();

        // when
        sqlEntityListener.onEntity(entityMemoUpdate);
        sqlEntityListener.onStakingRewardTransfer(transfer);
        completeFileAndCommit();

        // then
        entity.setMemo(entityMemoUpdate.getMemo());
        entity.setStakePeriodStart(Utility.getEpochDay(timestamp) - 1);
        entity.setTimestampRange(Range.atLeast(timestamp));
        assertThat(entityRepository.findAll()).containsExactly(entity);
        assertThat(stakingRewardTransferRepository.findAll()).containsExactly(transfer);
    }

    @Test
    void onStakingRewardTransferAfterNonHistoryUpdate() {
        // given
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(1L).type(CONTRACT))
                .persist();
        var entityBalanceUpdate = Entity.builder()
                .id(entity.getId())
                .balance(200L)
                .balanceTimestamp(domainBuilder.timestamp())
                .build();
        var transfer = domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(entity.getId()))
                .get();

        // when
        sqlEntityListener.onEntity(entityBalanceUpdate);
        sqlEntityListener.onStakingRewardTransfer(transfer);
        completeFileAndCommit();

        // then
        var current = TestUtils.clone(entity);
        current.setBalance(entity.getBalance() + entityBalanceUpdate.getBalance());
        current.setBalanceTimestamp(entityBalanceUpdate.getBalanceTimestamp());
        current.setStakePeriodStart(Utility.getEpochDay(transfer.getConsensusTimestamp()) - 1);
        current.setTimestampLower(transfer.getConsensusTimestamp());
        entity.setTimestampUpper(transfer.getConsensusTimestamp());
        assertThat(entityRepository.findAll()).containsExactly(current);
        assertThat(findHistory(Entity.class)).containsExactly(entity);
        assertThat(stakingRewardTransferRepository.findAll()).containsExactly(transfer);
    }

    @Test
    void onToken() {
        // given
        var token1 = domainBuilder.token().get();
        var token2 = domainBuilder.token().get();

        // when
        sqlEntityListener.onToken(token1);
        sqlEntityListener.onToken(token2);
        completeFileAndCommit();

        // then
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrder(token1, token2);
        assertThat(findHistory(Token.class)).isEmpty();
    }

    @Test
    void onTokenMerge() {
        // given
        var tokenCreate = domainBuilder.token().get();
        sqlEntityListener.onToken(tokenCreate);

        var tokenUpdate = Token.builder()
                .metadata(domainBuilder.bytes(16))
                .metadataKey(domainBuilder.key())
                .pauseStatus(TokenPauseStatusEnum.PAUSED)
                .timestampRange(domainBuilder.timestampRange())
                .tokenId(tokenCreate.getTokenId())
                .build();
        sqlEntityListener.onToken(tokenUpdate);
        completeFileAndCommit();

        // then
        var tokenMerged = TestUtils.clone(tokenCreate);
        tokenMerged.setMetadata(tokenUpdate.getMetadata());
        tokenMerged.setMetadataKey(tokenUpdate.getMetadataKey());
        tokenMerged.setPauseStatus(tokenUpdate.getPauseStatus());
        tokenMerged.setTimestampRange(tokenUpdate.getTimestampRange());
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrder(tokenMerged);

        var tokenHistory = TestUtils.clone(tokenCreate);
        tokenHistory.setTimestampUpper(tokenUpdate.getTimestampLower());
        assertThat(findHistory(Token.class)).containsExactlyInAnyOrder(tokenHistory);
    }

    @Test
    void onTokenMergeRetainMetadataAndKey() {
        // given
        var tokenCreate = domainBuilder.token().get();
        sqlEntityListener.onToken(tokenCreate);

        var tokenUpdate = Token.builder()
                .timestampRange(domainBuilder.timestampRange())
                .tokenId(tokenCreate.getTokenId())
                .build();
        sqlEntityListener.onToken(tokenUpdate);
        completeFileAndCommit();

        // then
        var tokenMerged = TestUtils.clone(tokenCreate);
        // Metadata and key are unchanged since create
        tokenMerged.setTimestampRange(tokenUpdate.getTimestampRange());
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrder(tokenMerged);

        var tokenHistory = TestUtils.clone(tokenCreate);
        tokenHistory.setTimestampUpper(tokenUpdate.getTimestampLower());
        assertThat(findHistory(Token.class)).containsExactlyInAnyOrder(tokenHistory);
    }

    @Test
    void onTokenMergeWithCreateThenUpdateThenMint() {
        // given
        // Create does not have a history row
        var tokenCreate = domainBuilder.token().get();
        var ts = tokenCreate.getCreatedTimestamp();
        sqlEntityListener.onToken(tokenCreate);

        // Update has a history row
        var tokenUpdate = Token.builder()
                .name("UpdateToken")
                .tokenId(tokenCreate.getTokenId())
                .timestampRange(Range.atLeast(ts + 1))
                .build();
        sqlEntityListener.onToken(tokenUpdate);

        // Token mint does not have history
        var tokenMint = Token.builder()
                .tokenId(tokenCreate.getTokenId())
                .totalSupply(5000L)
                .build();

        sqlEntityListener.onToken(tokenMint);
        completeFileAndCommit();

        // then
        var tokenMerge = TestUtils.clone(tokenCreate);
        tokenMerge.setName(tokenUpdate.getName());
        tokenMerge.setTimestampRange(tokenUpdate.getTimestampRange());
        tokenMerge.setTotalSupply(tokenMint.getTotalSupply());
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrder(tokenMerge);

        var tokenHistory = TestUtils.clone(tokenCreate);
        tokenHistory.setTimestampUpper(tokenUpdate.getTimestampLower());
        assertThat(findHistory(Token.class)).containsExactlyInAnyOrder(tokenHistory);
    }

    @Test
    void onTokenMergeWithCreateThenUpdateThenBurn() {
        // given
        // Create does not have a history row
        var tokenCreate = domainBuilder.token().get();

        // Token mint does not have history
        var tokenMint = Token.builder()
                .tokenId(tokenCreate.getTokenId())
                .totalSupply(5000L)
                .build();

        // Token burn does not have history
        var tokenBurn = Token.builder()
                .tokenId(tokenCreate.getTokenId())
                .totalSupply(-500L)
                .build();

        sqlEntityListener.onToken(tokenCreate);
        sqlEntityListener.onToken(tokenMint);
        sqlEntityListener.onToken(tokenBurn);

        completeFileAndCommit();

        // then
        tokenCreate.setTotalSupply(4500L);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrder(tokenCreate);

        assertThat(findHistory(Token.class)).isEmpty();
    }

    @ValueSource(ints = {1, 2, 3})
    @ParameterizedTest
    void onTokenMergeWithCreateThenMintThenUpdate(int commitIndex) {
        // given
        // Create does not have a history row
        var tokenCreate = domainBuilder.token().get();
        var ts = tokenCreate.getCreatedTimestamp();

        // Token mint does not have history
        var tokenMint = Token.builder()
                .tokenId(tokenCreate.getTokenId())
                .totalSupply(5000L)
                .build();

        // Update has a history row
        var tokenUpdate = Token.builder()
                .name("UpdateToken")
                .tokenId(tokenCreate.getTokenId())
                .timestampRange(Range.atLeast(ts + 1))
                .build();

        sqlEntityListener.onToken(tokenCreate);

        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(tokenRepository.findAll()).containsExactly(tokenCreate);
            assertThat(findHistory(Token.class)).isEmpty();
        }

        sqlEntityListener.onToken(tokenMint);

        tokenCreate.setTotalSupply(tokenMint.getTotalSupply());

        if (commitIndex > 2) {
            completeFileAndCommit();
            assertThat(tokenRepository.findAll()).containsExactly(tokenCreate);
            assertThat(findHistory(Token.class)).isEmpty();
        }

        sqlEntityListener.onToken(tokenUpdate);
        completeFileAndCommit();

        // then
        var tokenMerge = TestUtils.clone(tokenCreate);
        tokenMerge.setName(tokenUpdate.getName());
        tokenMerge.setTimestampRange(tokenUpdate.getTimestampRange());

        var tokenHistory = TestUtils.clone(tokenCreate);
        tokenHistory.setTimestampUpper(tokenUpdate.getTimestampLower());

        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrder(tokenMerge);
        assertThat(findHistory(Token.class)).containsExactlyInAnyOrder(tokenHistory);
    }

    @Test
    void onTokenConsecutiveNegativeTotalSupply() {
        // given
        var tokenCreate = domainBuilder.token().get();
        var tokenId = tokenCreate.getTokenId();
        var ts = tokenCreate.getCreatedTimestamp();
        var tokenUpdate1 = Token.builder()
                .timestampRange(Range.atLeast(ts + 1))
                .tokenId(tokenId)
                .totalSupply(-10L)
                .build();
        var tokenUpdate2 = Token.builder()
                .timestampRange(Range.atLeast(ts + 2))
                .tokenId(tokenId)
                .totalSupply(-15L)
                .build();

        // when
        sqlEntityListener.onToken(tokenCreate);
        completeFileAndCommit();

        sqlEntityListener.onToken(tokenUpdate1);
        sqlEntityListener.onToken(tokenUpdate2);
        completeFileAndCommit();

        // then
        var expected = TestUtils.clone(tokenCreate);
        expected.setTimestampLower(tokenUpdate2.getTimestampLower());
        expected.setTotalSupply(tokenCreate.getTotalSupply() - 25);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrder(expected);

        var history1 = TestUtils.clone(tokenCreate);
        var history2 = TestUtils.clone(tokenCreate);
        history1.setTimestampUpper(tokenUpdate1.getTimestampLower());
        history2.setTimestampLower(tokenUpdate1.getTimestampLower());
        history2.setTimestampUpper(tokenUpdate2.getTimestampLower());
        history2.setTotalSupply(history2.getTotalSupply() - 10);
        assertThat(findHistory(Token.class)).containsExactlyInAnyOrder(history1, history2);
    }

    @Test
    void onTokenMergeNegativeTotalSupply() {
        // given
        var tokenCreate = domainBuilder.token().get();
        var tokenDissociateDeleted = Token.builder()
                .timestampRange(null)
                .tokenId(tokenCreate.getTokenId())
                .totalSupply(-10L)
                .build();

        // when
        sqlEntityListener.onToken(tokenCreate);
        sqlEntityListener.onToken(tokenDissociateDeleted);
        completeFileAndCommit();

        // then
        var expected = TestUtils.clone(tokenCreate);
        expected.setTotalSupply(expected.getTotalSupply());
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrder(expected);

        assertThat(findHistory(Token.class)).isEmpty();
    }

    @Test
    void onTokenAccount() {
        var token1 = domainBuilder.token().get();
        var token2 = domainBuilder.token().get();
        sqlEntityListener.onToken(token1);
        sqlEntityListener.onToken(token2);
        completeFileAndCommit();

        var tokenAccount1 = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(token1.getTokenId()))
                .get();
        var tokenAccount2 = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.tokenId(token2.getTokenId()))
                .get();

        // when
        sqlEntityListener.onTokenAccount(tokenAccount1);
        sqlEntityListener.onTokenAccount(TestUtils.clone(tokenAccount1));
        sqlEntityListener.onTokenAccount(tokenAccount2);
        completeFileAndCommit();

        // then
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrder(tokenAccount1, tokenAccount2);
        assertThat(findHistory(TokenAccount.class)).isEmpty();
    }

    @Test
    void onTokenAccountClaimNoExisting() {
        var tokenAccount =
                domainBuilder.tokenAccount().customize(ta -> ta.claim(true)).get();

        // when
        sqlEntityListener.onTokenAccount(tokenAccount);
        completeFileAndCommit();

        // then
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrder(tokenAccount);
        assertThat(findHistory(TokenAccount.class)).isEmpty();
    }

    @CsvSource(
            nullValues = "null",
            textBlock =
                    """
                            false, false
                            true, false
                            true, null
                            """)
    @ParameterizedTest
    void onTokenAccountClaimNoExistingAssociated(boolean database, Boolean associated) {
        var tokenAccountDissociate = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.associated(associated))
                .get();
        var tokenAccountClaim = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.claim(true)
                        .accountId(tokenAccountDissociate.getAccountId())
                        .tokenId(tokenAccountDissociate.getTokenId()))
                .get();

        sqlEntityListener.onTokenAccount(tokenAccountDissociate);

        if (database) {
            completeFileAndCommit();
        }

        // when
        sqlEntityListener.onTokenAccount(tokenAccountClaim);
        completeFileAndCommit();

        // then
        tokenAccountDissociate.setAssociated(false);
        tokenAccountDissociate.setTimestampUpper(tokenAccountClaim.getTimestampLower());
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrder(tokenAccountClaim);
        assertThat(findHistory(TokenAccount.class)).containsExactly(tokenAccountDissociate);
    }

    @CsvSource(
            nullValues = "null",
            textBlock =
                    """
                            false, true
                            false, null
                            true, true
                            """)
    @ParameterizedTest
    void onTokenAccountClaimExistingAssociated(boolean database, Boolean associated) {
        var tokenAccountAssociate = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.associated(associated))
                .get();
        var tokenAccountClaim = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.claim(true)
                        .accountId(tokenAccountAssociate.getAccountId())
                        .tokenId(tokenAccountAssociate.getTokenId()))
                .get();

        sqlEntityListener.onTokenAccount(tokenAccountAssociate);

        if (database) {
            completeFileAndCommit();
        }

        // when
        sqlEntityListener.onTokenAccount(tokenAccountClaim);
        completeFileAndCommit();

        // then
        if (associated == null) { // db defaults to false
            tokenAccountAssociate.setAssociated(false);
        }
        assertThat(tokenAccountRepository.findAll()).containsExactly(tokenAccountAssociate);
        assertThat(findHistory(TokenAccount.class)).isEmpty();
    }

    @Test
    void onTokenAccountDissociate() {
        EntityId tokenId1 = EntityId.of("0.0.3");

        // save token entities first
        Token token1 = getToken(tokenId1, EntityId.of("0.0.500"), 1L, 1L);
        sqlEntityListener.onToken(token1);
        completeFileAndCommit();

        EntityId accountId1 = EntityId.of("0.0.7");
        TokenAccount associate = getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                true,
                false,
                0,
                5L,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                Range.atLeast(5L));
        TokenAccount dissociate =
                getTokenAccount(tokenId1, accountId1, null, false, null, 0, null, null, null, Range.atLeast(10L));

        // when
        sqlEntityListener.onTokenAccount(associate);
        sqlEntityListener.onTokenAccount(dissociate);
        sqlEntityListener.onTokenAccount(TestUtils.clone(dissociate));
        completeFileAndCommit();

        // then
        assertThat(tokenAccountRepository.findAll())
                .containsExactly(getTokenAccount(
                        tokenId1,
                        accountId1,
                        5L,
                        false,
                        false,
                        0,
                        5L,
                        TokenFreezeStatusEnum.NOT_APPLICABLE,
                        TokenKycStatusEnum.NOT_APPLICABLE,
                        Range.atLeast(10L)));
        assertThat(findHistory(TokenAccount.class)).containsExactly(associate);
    }

    @Test
    void onTokenAccountMerge() {
        var tokenId1 = EntityId.of("0.0.3");

        // save token entities first
        var token = getToken(tokenId1, EntityId.of("0.0.500"), 1L, 1L);
        sqlEntityListener.onToken(token);

        // when
        var accountId1 = EntityId.of("0.0.7");
        var tokenAccountAssociate =
                getTokenAccount(tokenId1, accountId1, 5L, true, false, 0, 5L, null, null, Range.atLeast(5L));
        sqlEntityListener.onTokenAccount(tokenAccountAssociate);
        var expectedTokenAccountAssociate =
                getTokenAccount(tokenId1, accountId1, 5L, true, false, 0, 5L, null, null, Range.closedOpen(5L, 15L));

        var tokenAccountKyc = getTokenAccount(
                tokenId1, accountId1, null, null, null, 0, null, null, TokenKycStatusEnum.GRANTED, Range.atLeast(15L));
        sqlEntityListener.onTokenAccount(tokenAccountKyc);

        completeFileAndCommit();

        // then
        var tokenAccountMerged = getTokenAccount(
                tokenId1, accountId1, 5L, true, false, 0, 5L, null, TokenKycStatusEnum.GRANTED, Range.atLeast(15L));
        assertThat(tokenAccountRepository.findAll()).containsExactly(tokenAccountMerged);
        assertThat(findHistory(TokenAccount.class)).containsExactly(expectedTokenAccountAssociate);
    }

    @Test
    void onTokenAccountReassociate() {
        var expected = new ArrayList<TokenAccount>();
        var tokenId1 = EntityId.of("0.0.3");

        // save token entities first
        var token = getToken(tokenId1, EntityId.of("0.0.500"), 1L, 1L);
        tokenRepository.save(token);

        // token account was associated before this record file
        var accountId1 = EntityId.of("0.0.7");
        var associate = getTokenAccount(tokenId1, accountId1, 5L, true, false, 0, 5L, null, null, Range.atLeast(5L));
        tokenAccountRepository.save(associate);
        associate.setTimestampUpper(6L);
        expected.add(associate);

        // when
        var freeze = getTokenAccount(
                tokenId1, accountId1, null, null, null, 0, null, TokenFreezeStatusEnum.FROZEN, null, Range.atLeast(6L));
        sqlEntityListener.onTokenAccount(freeze);
        expected.add(getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                true,
                false,
                0,
                5L,
                TokenFreezeStatusEnum.FROZEN,
                null,
                Range.closedOpen(6L, 7L)));

        var kycGrant = getTokenAccount(
                tokenId1, accountId1, null, null, null, 0, null, null, TokenKycStatusEnum.GRANTED, Range.atLeast(7L));
        sqlEntityListener.onTokenAccount(kycGrant);
        expected.add(getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                true,
                false,
                0,
                5L,
                TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.GRANTED,
                Range.closedOpen(7L, 8L)));

        var dissociate =
                getTokenAccount(tokenId1, accountId1, null, false, null, 0, null, null, null, Range.atLeast(8L));
        sqlEntityListener.onTokenAccount(dissociate);
        expected.add(getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                false,
                false,
                0,
                5L,
                TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.GRANTED,
                Range.closedOpen(8L, 20L)));

        // associate after dissociate, the token has freeze key with freezeDefault = false, the token also has kyc key,
        // so the new relationship should have UNFROZEN, REVOKED
        var reassociate =
                getTokenAccount(tokenId1, accountId1, 20L, true, false, 0, 20L, null, null, Range.atLeast(20L));
        sqlEntityListener.onTokenAccount(reassociate);

        var expectedToken =
                getTokenAccount(tokenId1, accountId1, 20L, true, false, 0, 20L, null, null, Range.atLeast(20L));
        completeFileAndCommit();

        // then
        assertThat(tokenAccountRepository.findAll()).containsExactly(expectedToken);
        assertThat(findHistory(TokenAccount.class)).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void onTokenAccountMissingToken() {
        var tokenId1 = EntityId.of("0.0.3");
        var accountId1 = EntityId.of("0.0.7");

        // given no token row in db

        // when
        var associate = getTokenAccount(tokenId1, accountId1, 10L, true, false, 0, 10L, null, null, Range.atLeast(10L));
        sqlEntityListener.onTokenAccount(associate);

        var kycGrant = getTokenAccount(
                tokenId1, accountId1, null, null, null, 0, null, null, TokenKycStatusEnum.GRANTED, Range.atLeast(11L));
        sqlEntityListener.onTokenAccount(kycGrant);

        completeFileAndCommit();

        // then
        var expected = getTokenAccount(
                tokenId1, accountId1, 10L, true, false, 0, 10L, null, TokenKycStatusEnum.GRANTED, Range.atLeast(11L));
        var expectedHistory =
                getTokenAccount(tokenId1, accountId1, 10L, true, false, 0, 10L, null, null, Range.closedOpen(10L, 11L));
        assertThat(tokenAccountRepository.findAll()).containsExactly(expected);
        assertThat(findHistory(TokenAccount.class)).containsExactly(expectedHistory);
    }

    @Test
    void onTokenAccountMissingLastAssociation() {
        EntityId tokenId1 = EntityId.of("0.0.3");
        EntityId accountId1 = EntityId.of("0.0.7");

        // given token in db and missing last account token association
        Token token = getToken(tokenId1, EntityId.of("0.0.500"), 1L, 1L);
        tokenRepository.save(token);

        // when
        TokenAccount freeze = getTokenAccount(
                tokenId1,
                accountId1,
                null,
                true,
                null,
                0,
                null,
                TokenFreezeStatusEnum.FROZEN,
                null,
                Range.atLeast(10L));
        sqlEntityListener.onTokenAccount(freeze);

        TokenAccount kycGrant = getTokenAccount(
                tokenId1, accountId1, null, true, null, 0, null, null, TokenKycStatusEnum.GRANTED, Range.atLeast(15L));
        sqlEntityListener.onTokenAccount(kycGrant);

        completeFileAndCommit();

        // then
        kycGrant.setFreezeStatus(TokenFreezeStatusEnum.FROZEN);
        freeze.setTimestampUpper(kycGrant.getTimestampLower());
        assertThat(tokenAccountRepository.findAll()).containsExactly(kycGrant);
        assertThat(findHistory(TokenAccount.class)).containsExactly(freeze);
    }

    @Test
    void onTokenAccountSpanningRecordFiles() {
        var expected = new ArrayList<TokenAccount>();
        var tokenId1 = EntityId.of("0.0.3");
        var accountId1 = EntityId.of("0.0.7");

        // given token in db
        var token = getToken(tokenId1, EntityId.of("0.0.500"), 1L, 1L);
        tokenRepository.save(token);

        // given association in a previous record file
        var associate = getTokenAccount(tokenId1, accountId1, 5L, true, false, 0, 5L, null, null, Range.atLeast(5L));
        sqlEntityListener.onTokenAccount(associate);
        expected.add(
                getTokenAccount(tokenId1, accountId1, 5L, true, false, 0, 5L, null, null, Range.closedOpen(5L, 10L)));

        completeFileAndCommit();

        // when in the next record file we have freeze, kycGrant, dissociate, associate, kycGrant
        var freeze = getTokenAccount(
                tokenId1,
                accountId1,
                null,
                null,
                null,
                0,
                null,
                TokenFreezeStatusEnum.FROZEN,
                null,
                Range.atLeast(10L));
        sqlEntityListener.onTokenAccount(freeze);
        expected.add(getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                true,
                false,
                0,
                5L,
                TokenFreezeStatusEnum.FROZEN,
                null,
                Range.closedOpen(10L, 12L)));

        var kycGrant = getTokenAccount(
                tokenId1, accountId1, null, null, null, 0, null, null, TokenKycStatusEnum.GRANTED, Range.atLeast(12L));
        sqlEntityListener.onTokenAccount(kycGrant);
        expected.add(getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                true,
                false,
                0,
                5L,
                TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.GRANTED,
                Range.closedOpen(12L, 15L)));

        var dissociate =
                getTokenAccount(tokenId1, accountId1, null, false, null, 0, null, null, null, Range.atLeast(15L));
        sqlEntityListener.onTokenAccount(dissociate);
        expected.add(getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                false,
                false,
                0,
                5L,
                TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.GRANTED,
                Range.closedOpen(15L, 20L)));

        associate = getTokenAccount(tokenId1, accountId1, 20L, true, true, 0, 20L, null, null, Range.atLeast(20L));
        sqlEntityListener.onTokenAccount(associate);
        expected.add(
                getTokenAccount(tokenId1, accountId1, 20L, true, true, 0, 20L, null, null, Range.closedOpen(20L, 22L)));

        kycGrant = getTokenAccount(
                tokenId1, accountId1, null, null, null, 0, null, null, TokenKycStatusEnum.GRANTED, Range.atLeast(22L));
        sqlEntityListener.onTokenAccount(kycGrant);

        completeFileAndCommit();

        var expectedTokenAccount = getTokenAccount(
                tokenId1, accountId1, 20L, true, true, 0, 20L, null, TokenKycStatusEnum.GRANTED, Range.atLeast(22L));

        // then
        assertThat(tokenAccountRepository.findAll()).containsExactly(expectedTokenAccount);
        assertThat(findHistory(TokenAccount.class)).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void onTokenAirdrop() {
        // given
        var tokenAirdrop =
                domainBuilder.tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON).get();
        var tokenAirdrop2 =
                domainBuilder.tokenAirdrop(TokenTypeEnum.NON_FUNGIBLE_UNIQUE).get();

        // when
        sqlEntityListener.onTokenAirdrop(tokenAirdrop);
        sqlEntityListener.onTokenAirdrop(tokenAirdrop2);

        // when
        long newAmount = 50000L;
        var updatedAmountAirdrop = domainBuilder
                .tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON)
                .customize(a -> a.amount(newAmount)
                        .receiverAccountId(tokenAirdrop.getReceiverAccountId())
                        .senderAccountId(tokenAirdrop.getSenderAccountId())
                        .tokenId(tokenAirdrop.getTokenId())
                        .timestampRange(domainBuilder.timestampRange()))
                .get();
        sqlEntityListener.onTokenAirdrop(updatedAmountAirdrop);
        completeFileAndCommit();

        // then
        tokenAirdrop.setTimestampRange(
                Range.closedOpen(tokenAirdrop.getTimestampLower(), updatedAmountAirdrop.getTimestampLower()));
        assertThat(findHistory(TokenAirdrop.class)).containsExactly(tokenAirdrop);
        updatedAmountAirdrop.setAmount(newAmount);
        assertThat(tokenAirdropRepository.findAll()).containsExactlyInAnyOrder(updatedAmountAirdrop, tokenAirdrop2);
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                            CANCELLED, 1
                            CANCELLED, 2
                            CLAIMED,   1
                            CLAIMED,   2
                            """)
    void onTokenAirdropUpdate(TokenAirdropStateEnum state, int commitIndex) {
        // given
        var tokenAirdrop =
                domainBuilder.tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON).get();
        var nftAirdrop =
                domainBuilder.tokenAirdrop(TokenTypeEnum.NON_FUNGIBLE_UNIQUE).get();

        sqlEntityListener.onTokenAirdrop(tokenAirdrop);
        sqlEntityListener.onTokenAirdrop(nftAirdrop);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(tokenAirdropRepository.findAll()).containsExactlyInAnyOrder(tokenAirdrop, nftAirdrop);
            assertThat(findHistory(TokenAirdrop.class)).isEmpty();
        }

        // when
        var tokenAirdropUpdateState = domainBuilder
                .tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON)
                .customize(a -> a.state(state)
                        .amount(null) // Record files that change state do not include PendingAirdropValue so remove the
                        // amount here
                        .receiverAccountId(tokenAirdrop.getReceiverAccountId())
                        .senderAccountId(tokenAirdrop.getSenderAccountId())
                        .tokenId(tokenAirdrop.getTokenId())
                        .timestampRange(domainBuilder.timestampRange()))
                .get();
        var nftAirdropUpdateState = domainBuilder
                .tokenAirdrop(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                .customize(a -> a.state(state)
                        .receiverAccountId(nftAirdrop.getReceiverAccountId())
                        .senderAccountId(nftAirdrop.getSenderAccountId())
                        .serialNumber(nftAirdrop.getSerialNumber())
                        .tokenId(nftAirdrop.getTokenId())
                        .timestampRange(domainBuilder.timestampRange()))
                .get();
        sqlEntityListener.onTokenAirdrop(tokenAirdropUpdateState);
        sqlEntityListener.onTokenAirdrop(nftAirdropUpdateState);
        completeFileAndCommit();

        // then
        tokenAirdrop.setTimestampRange(
                Range.closedOpen(tokenAirdrop.getTimestampLower(), tokenAirdropUpdateState.getTimestampLower()));
        nftAirdrop.setTimestampRange(
                Range.closedOpen(nftAirdrop.getTimestampLower(), nftAirdropUpdateState.getTimestampLower()));
        assertThat(findHistory(TokenAirdrop.class)).containsExactly(tokenAirdrop, nftAirdrop);
        tokenAirdropUpdateState.setAmount(tokenAirdrop.getAmount());
        assertThat(tokenAirdropRepository.findAll())
                .containsExactlyInAnyOrder(tokenAirdropUpdateState, nftAirdropUpdateState);
    }

    @ParameterizedTest
    @EnumSource(
            value = TokenAirdropStateEnum.class,
            names = {"CANCELLED", "CLAIMED"})
    void onTokenAirdropPartialUpdate(TokenAirdropStateEnum state) {
        // given
        var tokenAirdrop =
                domainBuilder.tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON).get();
        var nftAirdrop =
                domainBuilder.tokenAirdrop(TokenTypeEnum.NON_FUNGIBLE_UNIQUE).get();

        // when
        var tokenAirdropUpdateState = domainBuilder
                .tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON)
                .customize(a -> a.state(state)
                        .amount(null) // Record files that change state do not include PendingAirdropValue so remove the
                        // amount here
                        .receiverAccountId(tokenAirdrop.getReceiverAccountId())
                        .senderAccountId(tokenAirdrop.getSenderAccountId())
                        .tokenId(tokenAirdrop.getTokenId())
                        .timestampRange(domainBuilder.timestampRange()))
                .get();
        var nftAirdropUpdateState = domainBuilder
                .tokenAirdrop(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                .customize(a -> a.state(state)
                        .receiverAccountId(nftAirdrop.getReceiverAccountId())
                        .senderAccountId(nftAirdrop.getSenderAccountId())
                        .serialNumber(nftAirdrop.getSerialNumber())
                        .tokenId(nftAirdrop.getTokenId())
                        .timestampRange(domainBuilder.timestampRange()))
                .get();
        sqlEntityListener.onTokenAirdrop(tokenAirdropUpdateState);
        sqlEntityListener.onTokenAirdrop(nftAirdropUpdateState);
        completeFileAndCommit();

        // then
        tokenAirdrop.setTimestampRange(
                Range.closedOpen(tokenAirdrop.getTimestampLower(), tokenAirdropUpdateState.getTimestampLower()));
        nftAirdrop.setTimestampRange(
                Range.closedOpen(nftAirdrop.getTimestampLower(), nftAirdropUpdateState.getTimestampLower()));
        assertThat(findHistory(TokenAirdrop.class)).isEmpty();
        assertThat(tokenAirdropRepository.findAll())
                .containsExactlyInAnyOrder(tokenAirdropUpdateState, nftAirdropUpdateState);
    }

    @Test
    void onTokenAllowance() {
        // given
        TokenAllowance tokenAllowance1 = domainBuilder.tokenAllowance().get();
        TokenAllowance tokenAllowance2 = domainBuilder.tokenAllowance().get();

        // when
        sqlEntityListener.onTokenAllowance(tokenAllowance1);
        sqlEntityListener.onTokenAllowance(tokenAllowance2);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.count()).isZero();
        assertThat(tokenAllowanceRepository.findAll()).containsExactlyInAnyOrder(tokenAllowance1, tokenAllowance2);
        assertThat(findHistory(TokenAllowance.class)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void onTokenAllowanceWithApprovedTransfer(int commitIndex) {
        // given
        var amountGranted = 1000L;
        var amountTransferred = -100L;

        var builder = domainBuilder.tokenAllowance();
        var tokenAllowanceCreate = builder.customize(
                        c -> c.amountGranted(amountGranted).amount(amountGranted))
                .get();

        // when
        sqlEntityListener.onTokenAllowance(tokenAllowanceCreate);

        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(tokenAllowanceRepository.findAll()).containsExactly(tokenAllowanceCreate);
            assertThat(findHistory(TokenAllowance.class)).isEmpty();
        }

        // Approved transfer allowance debit as emitted by EntityRecordItemListener
        var tokenAllowanceDebitFromTransfer = builder.customize(
                        c -> c.amount(amountTransferred).amountGranted(null).timestampRange(null))
                .get();

        sqlEntityListener.onTokenAllowance(tokenAllowanceDebitFromTransfer);
        completeFileAndCommit();

        // then
        tokenAllowanceCreate.setAmount(amountGranted + amountTransferred);
        assertThat(entityRepository.count()).isZero();
        assertThat(tokenAllowanceRepository.findAll()).containsExactly(tokenAllowanceCreate);
        assertThat(findHistory(TokenAllowance.class)).isEmpty();
    }

    @Test
    void onTokenAllowanceExistingWithApprovedTransfer() {
        // given
        var amountGranted = 1000L;
        var amountTransferred = -100L;

        var builder = domainBuilder.tokenAllowance();
        var tokenAllowanceCreate = builder.customize(
                        c -> c.amountGranted(amountGranted).amount(amountGranted))
                .persist();

        // Approved transfer allowance debit as emitted by EntityRecordItemListener
        var tokenAllowanceDebitFromTransfer = builder.customize(
                        c -> c.amount(amountTransferred).amountGranted(null).timestampRange(null))
                .get();

        sqlEntityListener.onTokenAllowance(tokenAllowanceDebitFromTransfer);
        completeFileAndCommit();

        // then
        tokenAllowanceCreate.setAmount(amountGranted + amountTransferred);
        assertThat(entityRepository.count()).isZero();
        assertThat(tokenAllowanceRepository.findAll()).containsExactly(tokenAllowanceCreate);
        assertThat(findHistory(TokenAllowance.class)).isEmpty();
    }

    // Partial mirror node scenario where the token allowance grant does not exist in
    // the database. An approved transfer debit operation should have no affect and not be persisted.
    @Test
    void onTokenAllowanceAbsentWithApprovedTransfer() {
        // given
        var amountTransferred = -100L;
        var builder = domainBuilder.tokenAllowance();

        // Approved transfer allowance debit as emitted by EntityRecordItemListener
        var tokenAllowanceDebitFromTransfer = builder.customize(
                        c -> c.amount(amountTransferred).amountGranted(null).timestampRange(null))
                .get();

        sqlEntityListener.onTokenAllowance(tokenAllowanceDebitFromTransfer);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.count()).isZero();
        assertThat(tokenAllowanceRepository.count()).isZero();
        assertThat(findHistory(TokenAllowance.class)).isEmpty();
    }

    @ValueSource(ints = {1, 2, 3, 4})
    @ParameterizedTest
    void onTokenAllowanceHistory(int commitIndex) {
        // given
        var tokenAllowanceCreate1 = domainBuilder.tokenAllowance().get();
        var tokenAllowanceUpdate1 = tokenAllowanceCreate1.toBuilder()
                .amount(-80)
                .amountGranted(null)
                .timestampRange(null)
                .build();
        long amount = tokenAllowanceCreate1.getAmount() + 500;
        var tokenAllowanceCreate2 = tokenAllowanceCreate1.toBuilder()
                .amount(amount)
                .amountGranted(amount)
                .timestampRange(domainBuilder.timestampRange())
                .build();
        var tokenAllowanceRevoke = tokenAllowanceCreate1.toBuilder()
                .amount(0)
                .amountGranted(0L)
                .timestampRange(domainBuilder.timestampRange())
                .build();

        // when
        sqlEntityListener.onTokenAllowance(tokenAllowanceCreate1);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(tokenAllowanceRepository.findAll()).containsExactly(tokenAllowanceCreate1);
            assertThat(findHistory(TokenAllowance.class)).isEmpty();
        }
        var mergedUpdate1 = tokenAllowanceCreate1.toBuilder()
                .amount(tokenAllowanceCreate1.getAmount() + tokenAllowanceUpdate1.getAmount())
                .build();

        sqlEntityListener.onTokenAllowance(tokenAllowanceUpdate1);
        if (commitIndex > 2) {
            completeFileAndCommit();
            assertThat(tokenAllowanceRepository.findAll()).containsExactly(mergedUpdate1);
            assertThat(findHistory(TokenAllowance.class)).isEmpty();
        }
        mergedUpdate1.setTimestampUpper(tokenAllowanceCreate2.getTimestampLower());

        sqlEntityListener.onTokenAllowance(tokenAllowanceCreate2);
        if (commitIndex > 3) {
            completeFileAndCommit();
            assertThat(tokenAllowanceRepository.findAll()).containsExactly(tokenAllowanceCreate2);
            assertThat(findHistory(TokenAllowance.class)).containsExactly(mergedUpdate1);
        }
        var mergedUpdate2 = tokenAllowanceCreate2.toBuilder().build();
        mergedUpdate2.setTimestampUpper(tokenAllowanceRevoke.getTimestampLower());

        sqlEntityListener.onTokenAllowance(tokenAllowanceRevoke);
        completeFileAndCommit();

        // then
        assertThat(tokenAllowanceRepository.findAll()).containsExactly(tokenAllowanceRevoke);
        assertThat(findHistory(TokenAllowance.class)).containsExactlyInAnyOrder(mergedUpdate1, mergedUpdate2);
    }

    @Test
    void onTokenTransfer() {
        TokenTransfer tokenTransfer1 = domainBuilder.tokenTransfer().get();
        TokenTransfer tokenTransfer2 = domainBuilder.tokenTransfer().get();
        TokenTransfer tokenTransfer3 = domainBuilder.tokenTransfer().get();

        // when
        sqlEntityListener.onTokenTransfer(tokenTransfer1);
        sqlEntityListener.onTokenTransfer(tokenTransfer2);
        sqlEntityListener.onTokenTransfer(tokenTransfer3);
        completeFileAndCommit();

        // then
        assertThat(tokenTransferRepository.findAll())
                .containsExactlyInAnyOrder(tokenTransfer1, tokenTransfer2, tokenTransfer3);
    }

    @ValueSource(ints = {1, 2, 3, 4, 5})
    @ParameterizedTest
    void onTokenTransferTokenAccountBalance(int commitIndex) {
        // given
        var tokenId1 = EntityId.of("0.0.3");

        // save token entities first
        var token1 = domainBuilder
                .token()
                .customize(c -> c.createdTimestamp(1L)
                        .timestampRange(Range.atLeast(1L))
                        .tokenId(tokenId1.getId())
                        .totalSupply(1_000_000_000L)
                        .treasuryAccountId(EntityId.of("0.0.500"))
                        .type(TokenTypeEnum.FUNGIBLE_COMMON))
                .get();

        sqlEntityListener.onToken(token1);
        completeFileAndCommit();

        var accountId1 = EntityId.of("0.0.7");
        var tokenAccount = getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                true,
                false,
                0,
                5L,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                Range.atLeast(5L));

        // when
        sqlEntityListener.onTokenAccount(tokenAccount);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(tokenAccountRepository.findAll()).containsExactly(tokenAccount);
            assertThat(findHistory(TokenAccount.class)).isEmpty();
            assertThat(tokenTransferRepository.findAll()).isEmpty();
        }

        var tokenTransferId = new TokenTransfer.Id();
        tokenTransferId.setAccountId(EntityId.of(tokenAccount.getAccountId()));
        tokenTransferId.setTokenId(EntityId.of(tokenAccount.getTokenId()));
        tokenTransferId.setConsensusTimestamp(tokenAccount.getCreatedTimestamp() + 1);
        TokenTransfer tokenTransfer1 = domainBuilder
                .tokenTransfer()
                .customize(t -> t.id(tokenTransferId))
                .get();
        var expected = getTokenAccount(
                tokenId1,
                accountId1,
                5L,
                true,
                false,
                tokenTransfer1.getAmount(),
                tokenTransfer1.getId().getConsensusTimestamp(),
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                Range.atLeast(5L));

        sqlEntityListener.onTokenTransfer(tokenTransfer1);
        if (commitIndex > 2) {
            completeFileAndCommit();
            tokenAccount.setBalance(tokenTransfer1.getAmount());
            tokenAccount.setBalanceTimestamp(tokenTransfer1.getId().getConsensusTimestamp());
            assertThat(tokenAccountRepository.findAll()).containsExactly(expected);
            assertThat(findHistory(TokenAccount.class)).isEmpty();
            assertThat(tokenTransferRepository.findAll()).containsExactly(tokenTransfer1);
        }

        TokenAccount freeze = getTokenAccount(
                tokenId1, accountId1, null, null, null, 0, null, TokenFreezeStatusEnum.FROZEN, null, Range.atLeast(6L));
        expected.setFreezeStatus(TokenFreezeStatusEnum.FROZEN);
        expected.setTimestampRange(Range.atLeast(6L));
        tokenAccount.setTimestampRange(Range.closedOpen(5L, 6L));

        sqlEntityListener.onTokenAccount(freeze);
        if (commitIndex > 3) {
            completeFileAndCommit();
            assertThat(tokenAccountRepository.findAll()).containsExactly(expected);
            assertThat(findHistory(TokenAccount.class)).containsExactly(tokenAccount);
            assertThat(tokenTransferRepository.findAll()).containsExactly(tokenTransfer1);
        }

        var tokenTransferId2 = new TokenTransfer.Id();
        tokenTransferId2.setAccountId(EntityId.of(tokenAccount.getAccountId()));
        tokenTransferId2.setTokenId(EntityId.of(tokenAccount.getTokenId()));
        tokenTransferId2.setConsensusTimestamp(tokenAccount.getCreatedTimestamp() + 2);
        var tokenTransfer2 = domainBuilder
                .tokenTransfer()
                .customize(t -> t.id(tokenTransferId2).amount(-50L))
                .get();
        expected.setBalance(tokenTransfer1.getAmount() + tokenTransfer2.getAmount());
        expected.setBalanceTimestamp(tokenTransferId2.getConsensusTimestamp());

        sqlEntityListener.onTokenTransfer(tokenTransfer2);
        if (commitIndex > 4) {
            completeFileAndCommit();
            assertThat(tokenAccountRepository.findAll()).containsExactly(expected);
            assertThat(findHistory(TokenAccount.class)).containsExactly(tokenAccount);
            assertThat(tokenTransferRepository.findAll()).containsExactlyInAnyOrder(tokenTransfer1, tokenTransfer2);
        }

        var tokenTransferId3 = new TokenTransfer.Id();
        tokenTransferId3.setAccountId(EntityId.of(tokenAccount.getAccountId()));
        tokenTransferId3.setTokenId(EntityId.of(tokenAccount.getTokenId()));
        tokenTransferId3.setConsensusTimestamp(tokenAccount.getCreatedTimestamp() + 3);
        var tokenTransfer3 = domainBuilder
                .tokenTransfer()
                .customize(t -> t.id(tokenTransferId3).amount(20L))
                .get();

        sqlEntityListener.onTokenTransfer(tokenTransfer3);
        completeFileAndCommit();
        expected.setBalance(tokenTransfer1.getAmount() + tokenTransfer2.getAmount() + tokenTransfer3.getAmount());
        expected.setBalanceTimestamp(tokenTransfer3.getId().getConsensusTimestamp());

        // then
        assertThat(tokenAccountRepository.findAll()).containsExactly(expected);
        assertThat(findHistory(TokenAccount.class)).containsExactly(tokenAccount);
        assertThat(tokenTransferRepository.findAll())
                .containsExactlyInAnyOrder(tokenTransfer1, tokenTransfer2, tokenTransfer3);
    }

    @ValueSource(ints = {1, 2})
    @ParameterizedTest
    void onSchedule(int commitIndex) {
        var schedule = domainBuilder.schedule().get();
        var expected = TestUtils.clone(schedule);

        sqlEntityListener.onSchedule(schedule);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(scheduleRepository.findAll()).containsOnly(expected);
        }

        var scheduleUpdate = new Schedule();
        scheduleUpdate.setExecutedTimestamp(domainBuilder.timestamp());
        scheduleUpdate.setScheduleId(schedule.getScheduleId());
        expected.setExecutedTimestamp(scheduleUpdate.getExecutedTimestamp());

        sqlEntityListener.onSchedule(scheduleUpdate);
        completeFileAndCommit();

        assertThat(scheduleRepository.findAll()).containsOnly(expected);
    }

    @Test
    void onScheduleExecutedWithoutScheduleCreate() {
        // For partial mirrornode which can miss a schedulecreate tx for an executed scheduled tx
        var schedule = new Schedule();
        schedule.setExecutedTimestamp(domainBuilder.timestamp());
        schedule.setScheduleId(domainBuilder.entityId().getId());
        sqlEntityListener.onSchedule(schedule);
        assertThat(scheduleRepository.findAll()).isEmpty();
    }

    @Test
    void onScheduleSignature() {
        TransactionSignature transactionSignature1 =
                domainBuilder.transactionSignature().get();
        TransactionSignature transactionSignature2 =
                domainBuilder.transactionSignature().get();
        TransactionSignature transactionSignature3 =
                domainBuilder.transactionSignature().get();

        // when
        sqlEntityListener.onTransactionSignature(transactionSignature1);
        sqlEntityListener.onTransactionSignature(transactionSignature2);
        sqlEntityListener.onTransactionSignature(transactionSignature3);
        completeFileAndCommit();

        // then
        assertThat(transactionSignatureRepository.findAll())
                .containsExactlyInAnyOrder(transactionSignature1, transactionSignature2, transactionSignature3);
    }

    @Test
    void onScheduleMerge() {
        Schedule schedule = domainBuilder.schedule().get();
        sqlEntityListener.onSchedule(schedule);

        Schedule scheduleUpdated = new Schedule();
        scheduleUpdated.setScheduleId(schedule.getScheduleId());
        scheduleUpdated.setExecutedTimestamp(5L);
        sqlEntityListener.onSchedule(scheduleUpdated);

        // when
        completeFileAndCommit();

        // then
        schedule.setExecutedTimestamp(5L);
        assertThat(scheduleRepository.findAll()).containsExactlyInAnyOrder(schedule);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onEthereumTransactionWInitCode(boolean persistTransactionHash) {
        entityProperties.getPersist().setTransactionHash(persistTransactionHash);
        var ethereumTransaction = domainBuilder.ethereumTransaction(true).get();
        sqlEntityListener.onEthereumTransaction(ethereumTransaction);
        var expectedTransactionHash = persistTransactionHash
                ? List.of(ethereumTransaction.toTransactionHash())
                : Collections.<TransactionHash>emptyList();

        // when
        completeFileAndCommit();

        // then
        assertThat(ethereumTransactionRepository.findAll()).containsExactly(ethereumTransaction);
        assertThat(transactionHashRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTransactionHash);
    }

    @Test
    void onEthereumTransactionWithEmptyHash() {
        var ethereumTransaction = domainBuilder
                .ethereumTransaction(false)
                .customize(e -> e.hash(EMPTY_BYTE_ARRAY))
                .get();
        sqlEntityListener.onEthereumTransaction(ethereumTransaction);

        // when
        completeFileAndCommit();

        // then
        assertThat(ethereumTransactionRepository.findAll()).containsExactly(ethereumTransaction);
        assertThat(transactionHashRepository.findAll()).isEmpty();
    }

    @Test
    void onEthereumTransactionWFileId() {
        var ethereumTransaction = domainBuilder.ethereumTransaction(false).get();
        sqlEntityListener.onEthereumTransaction(ethereumTransaction);

        // when
        completeFileAndCommit();

        // then
        assertThat(ethereumTransactionRepository.findAll()).containsExactly(ethereumTransaction);
    }

    private void completeFileAndCommit() {
        RecordFile recordFile =
                domainBuilder.recordFile().customize(r -> r.sidecars(List.of())).get();
        transactionTemplate.executeWithoutResult(status -> sqlEntityListener.onEnd(recordFile));
        parserContext.clear();
    }

    private ContractState getContractState(ContractStateChange contractStateChange, long createdTimestamp) {
        var value = contractStateChange.getValueWritten() == null
                ? contractStateChange.getValueRead()
                : contractStateChange.getValueWritten();
        return ContractState.builder()
                .contractId(contractStateChange.getContractId())
                .createdTimestamp(createdTimestamp)
                .modifiedTimestamp(contractStateChange.getConsensusTimestamp())
                .slot(DomainUtils.leftPadBytes(contractStateChange.getSlot(), 32))
                .value(value)
                .build();
    }

    @SneakyThrows
    private Token getToken(EntityId tokenId, EntityId accountId, Long createdTimestamp, long modifiedTimestamp) {
        var instr = "0011223344556677889900aabbccddeeff0011223344556677889900aabbccddeeff";
        var hexKey = Key.newBuilder()
                .setEd25519(ByteString.copyFrom(Hex.decodeHex(instr)))
                .build();
        return getToken(
                tokenId,
                accountId,
                createdTimestamp,
                modifiedTimestamp,
                1000,
                false,
                hexKey,
                TokenFreezeStatusEnum.UNFROZEN,
                1_000_000_000L,
                hexKey,
                TokenKycStatusEnum.REVOKED,
                domainBuilder.bytes(16),
                hexKey,
                "FOO COIN TOKEN",
                hexKey,
                "FOOTOK",
                hexKey,
                hexKey,
                TokenPauseStatusEnum.UNPAUSED);
    }

    private Token getToken(
            EntityId tokenId,
            EntityId accountId,
            Long createdTimestamp,
            long modifiedTimestamp,
            Integer decimals,
            Boolean freezeDefault,
            Key freezeKey,
            TokenFreezeStatusEnum freezeStatus,
            Long initialSupply,
            Key kycKey,
            TokenKycStatusEnum kycStatus,
            byte[] metadata,
            Key metadataKey,
            String name,
            Key supplyKey,
            String symbol,
            Key wipeKey,
            Key pauseKey,
            TokenPauseStatusEnum pauseStatus) {
        Token token = new Token();
        token.setCreatedTimestamp(createdTimestamp);
        token.setDecimals(decimals);
        token.setFreezeDefault(freezeDefault);
        token.setFreezeKey(freezeKey != null ? freezeKey.toByteArray() : null);
        token.setFreezeStatus(freezeStatus);
        token.setInitialSupply(initialSupply);
        token.setKycKey(kycKey != null ? kycKey.toByteArray() : null);
        token.setKycStatus(kycStatus);
        token.setMaxSupply(0L);
        token.setMetadata(metadata);
        token.setMetadataKey(metadataKey != null ? metadataKey.toByteArray() : null);
        token.setName(name);
        token.setPauseKey(pauseKey != null ? pauseKey.toByteArray() : null);
        token.setPauseStatus(pauseStatus);
        token.setSupplyKey(supplyKey != null ? supplyKey.toByteArray() : null);
        token.setSupplyType(TokenSupplyTypeEnum.INFINITE);
        token.setSymbol(symbol);
        token.setTimestampLower(modifiedTimestamp);
        token.setTokenId(tokenId.getId());
        token.setTotalSupply(initialSupply);
        token.setType(TokenTypeEnum.FUNGIBLE_COMMON);
        token.setTreasuryAccountId(accountId);
        token.setWipeKey(wipeKey != null ? wipeKey.toByteArray() : null);

        return token;
    }

    private Nft getNft(
            EntityId tokenId,
            long serialNumber,
            EntityId accountId,
            Long createdTimestamp,
            Boolean deleted,
            String metadata,
            long modifiedTimestamp) {
        Nft nft = new Nft();
        nft.setAccountId(accountId);
        nft.setCreatedTimestamp(createdTimestamp);
        nft.setDeleted(deleted);
        nft.setMetadata(metadata == null ? null : metadata.getBytes(StandardCharsets.UTF_8));
        nft.setSerialNumber(serialNumber);
        nft.setTimestampLower(modifiedTimestamp);
        nft.setTokenId(tokenId.getId());

        return nft;
    }

    @Test
    void onHook() {
        // given
        var hook1 = domainBuilder.hook().get();
        var hook2 = domainBuilder.hook().get();

        // when
        sqlEntityListener.onHook(hook1);
        sqlEntityListener.onHook(hook2);
        completeFileAndCommit();

        // then
        assertThat(hookRepository.findAll()).containsExactlyInAnyOrder(hook1, hook2);
        assertThat(findHistory(Hook.class)).isEmpty();
    }

    @Test
    void onHookMergeWithoutPrevious() {
        // given
        var hook = domainBuilder.hook().get();

        // when
        sqlEntityListener.onHook(hook);
        completeFileAndCommit();

        // then
        assertThat(hookRepository.findAll()).containsExactly(hook);
        assertThat(findHistory(Hook.class)).isEmpty();
    }

    @Test
    void onHookMergeWithDeletedPrevious() {
        // given
        var hookCreate = domainBuilder.hook().get();
        var hookDelete = Hook.builder()
                .deleted(true)
                .ownerId(hookCreate.getOwnerId())
                .hookId(hookCreate.getHookId())
                .timestampRange(Range.atLeast(hookCreate.getCreatedTimestamp()))
                .build();

        // when
        sqlEntityListener.onHook(hookDelete);
        sqlEntityListener.onHook(hookCreate);
        completeFileAndCommit();

        // then
        var expectedPrevious = hookDelete.toBuilder().build();
        expectedPrevious.setTimestampUpper(hookCreate.getTimestampLower() + 1);
        expectedPrevious.setExtensionPoint(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK);
        expectedPrevious.setType(HookType.LAMBDA);
        assertThat(hookRepository.findAll()).containsExactly(hookCreate);
        assertThat(findHistory(Hook.class)).containsExactly(expectedPrevious);
    }

    @Test
    void onHookCreateAndDeleteLifecycle() {
        // given
        var ownerId = EntityId.of(domainBuilder.id()).getId();
        var hookId = 1L;
        var contractId = EntityId.of(domainBuilder.id());
        var adminKey = domainBuilder.bytes(32);
        var createdTimestamp = domainBuilder.timestamp();
        var deletedTimestamp = createdTimestamp + 100;

        var hookCreate = Hook.builder()
                .adminKey(adminKey)
                .contractId(contractId)
                .createdTimestamp(createdTimestamp)
                .deleted(false)
                .extensionPoint(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK)
                .hookId(hookId)
                .ownerId(ownerId)
                .timestampRange(Range.atLeast(createdTimestamp))
                .type(HookType.LAMBDA)
                .build();

        var hookDelete = Hook.builder()
                .deleted(true)
                .hookId(hookId)
                .createdTimestamp(createdTimestamp)
                .ownerId(ownerId)
                .timestampRange(Range.atLeast(deletedTimestamp))
                .build();

        // when
        sqlEntityListener.onHook(hookCreate);
        completeFileAndCommit();
        assertThat(hookRepository.findAll()).containsExactly(hookCreate);

        sqlEntityListener.onHook(hookDelete);
        completeFileAndCommit();

        var expectedDeleteHistory = hookDelete.toBuilder()
                .adminKey(adminKey) // merged from creation
                .contractId(contractId) // merged from creation
                .createdTimestamp(createdTimestamp) // merged from creation
                .extensionPoint(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK) // merged from creation
                .type(HookType.LAMBDA) // merged from creation
                .build();

        assertThat(hookRepository.findAll()).containsExactly(expectedDeleteHistory);

        Collection<Hook> history = findHistory(Hook.class);
        Optional<Hook> actualOptional = history.stream().findFirst();
        assertThat(actualOptional).isPresent();

        assertAll(
                () -> assertEquals(hookId, actualOptional.get().getHookId()),
                () -> assertEquals(contractId, actualOptional.get().getContractId()),
                () -> assertEquals(
                        HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK,
                        actualOptional.get().getExtensionPoint()),
                () -> assertEquals(HookType.LAMBDA, actualOptional.get().getType()),
                () -> assertEquals(
                        EntityId.of(ownerId).getId(), actualOptional.get().getOwnerId()),
                () -> assertEquals(createdTimestamp, actualOptional.get().getCreatedTimestamp()),
                () -> assertNotNull(actualOptional.get().getTimestampUpper()),
                () -> assertFalse(actualOptional.get().getDeleted()));
    }

    @Test
    void onHookCreateDeleteRecreateWithoutAdminKey() {
        // given
        var ownerId = EntityId.of(domainBuilder.id()).getId();
        var hookId = 1L;
        var contractId = EntityId.of(domainBuilder.id());
        var createdTimestamp = domainBuilder.timestamp();
        var adminKey = domainBuilder.bytes(32);

        var hookCreate = Hook.builder()
                .adminKey(adminKey)
                .contractId(contractId)
                .createdTimestamp(createdTimestamp)
                .deleted(false)
                .extensionPoint(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK)
                .hookId(hookId)
                .ownerId(ownerId)
                .timestampRange(Range.atLeast(createdTimestamp))
                .type(HookType.LAMBDA)
                .build();

        var hookDelete = Hook.builder()
                .deleted(true)
                .hookId(hookId)
                .ownerId(ownerId)
                .timestampRange(Range.atLeast(createdTimestamp))
                .build();

        // when
        sqlEntityListener.onHook(hookDelete);
        sqlEntityListener.onHook(hookCreate);
        completeFileAndCommit();

        // then
        var expectedCreateHistory = hookCreate.toBuilder().build();
        expectedCreateHistory.setTimestampUpper(createdTimestamp);

        var expectedDeleteHistory = hookDelete.toBuilder()
                .contractId(contractId) // merged from creation
                .createdTimestamp(createdTimestamp) // merged from creation
                .extensionPoint(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK) // merged from creation
                .type(HookType.LAMBDA) // merged from creation
                .build();
        expectedDeleteHistory.setTimestampUpper(createdTimestamp + 1);

        assertThat(hookRepository.findAll()).containsExactly(hookCreate);
        assertThat(findHistory(Hook.class)).containsExactly(expectedDeleteHistory);

        // when
        var hookRecreate = hookCreate.toBuilder()
                .createdTimestamp(createdTimestamp + 100)
                .adminKey(null)
                .build();
        sqlEntityListener.onHook(hookRecreate);
        completeFileAndCommit();

        assertThat(hookRepository.findAll()).containsExactly(hookRecreate);
    }

    @Test
    void onHookCreateDeleteRecreateWithoutAdminKeyInSeparateRecordFile() {
        // given
        var ownerId = EntityId.of(domainBuilder.id()).getId();
        var hookId = 1L;
        var contractId = EntityId.of(domainBuilder.id());
        var createdTimestamp = domainBuilder.timestamp();

        var hookCreate = Hook.builder()
                .contractId(contractId)
                .createdTimestamp(createdTimestamp)
                .deleted(false)
                .extensionPoint(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK)
                .hookId(hookId)
                .ownerId(ownerId)
                .timestampRange(Range.atLeast(createdTimestamp))
                .type(HookType.LAMBDA)
                .build();

        var hookDelete = Hook.builder()
                .deleted(true)
                .createdTimestamp(createdTimestamp)
                .hookId(hookId)
                .ownerId(ownerId)
                .timestampRange(Range.atLeast(createdTimestamp))
                .build();

        // when
        sqlEntityListener.onHook(hookCreate);
        completeFileAndCommit();
        sqlEntityListener.onHook(hookDelete);
        completeFileAndCommit();

        // then
        var expectedCreateHistory = hookCreate.toBuilder().build();
        expectedCreateHistory.setTimestampUpper(createdTimestamp);

        var expectedDelete = hookDelete.toBuilder()
                .contractId(contractId) // merged from creation
                .createdTimestamp(createdTimestamp) // merged from creation
                .extensionPoint(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK) // merged from creation
                .type(HookType.LAMBDA) // merged from creation
                .build();
        expectedCreateHistory.setTimestampRange(Range.openClosed(createdTimestamp, createdTimestamp));

        assertThat(hookRepository.findAll()).containsExactly(expectedDelete);
        Collection<Hook> history = findHistory(Hook.class);
        Optional<Hook> actualOptional = history.stream().findFirst();
        assertThat(actualOptional).isPresent();

        assertAll(
                () -> assertEquals(hookId, actualOptional.get().getHookId()),
                () -> assertEquals(contractId, actualOptional.get().getContractId()),
                () -> assertEquals(
                        HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK,
                        actualOptional.get().getExtensionPoint()),
                () -> assertEquals(HookType.LAMBDA, actualOptional.get().getType()),
                () -> assertEquals(
                        EntityId.of(ownerId).getId(), actualOptional.get().getOwnerId()),
                () -> assertEquals(createdTimestamp, actualOptional.get().getCreatedTimestamp()),
                () -> assertNotNull(actualOptional.get().getTimestampUpper()),
                () -> assertFalse(actualOptional.get().getDeleted()));
    }

    @Test
    void onHookCreateDeleteInSameTransaction() {
        // given
        var ownerId = EntityId.of(domainBuilder.id()).getId();
        var hookId = 1L;
        var contractId = EntityId.of(domainBuilder.id());
        var adminKey = domainBuilder.bytes(32);
        var timestamp = domainBuilder.timestamp();

        var hookCreate = Hook.builder()
                .adminKey(adminKey)
                .contractId(contractId)
                .createdTimestamp(timestamp)
                .deleted(false)
                .extensionPoint(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK)
                .hookId(hookId)
                .ownerId(ownerId)
                .timestampRange(Range.atLeast(timestamp))
                .type(HookType.LAMBDA)
                .build();

        var hookDelete = Hook.builder()
                .deleted(true)
                .hookId(hookId)
                .ownerId(ownerId)
                .timestampRange(Range.atLeast(timestamp)) // different timestamp
                .build();

        // when
        sqlEntityListener.onHook(hookDelete);
        sqlEntityListener.onHook(hookCreate);

        completeFileAndCommit();

        // then - delete should merge properties and override deleted flag
        var expectedDeleted = hookDelete.toBuilder()
                .adminKey(adminKey)
                .contractId(contractId)
                .createdTimestamp(timestamp)
                .extensionPoint(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK)
                .type(HookType.LAMBDA)
                .build();

        assertThat(hookRepository.findAll()).containsExactly(hookCreate);
        assertThat(findHistory(Hook.class)).containsExactly(expectedDeleted);
    }

    @Test
    void onHookDeleteWithoutCreation() {
        // given - delete a hook that was never created
        var ownerId = EntityId.of(domainBuilder.id()).getId();
        var hookId = 1L;
        var timestamp = domainBuilder.timestamp();

        var hookDelete = Hook.builder()
                .deleted(true)
                .hookId(hookId)
                .ownerId(ownerId)
                .timestampRange(Range.atLeast(timestamp))
                .build();

        // when
        sqlEntityListener.onHook(hookDelete);
        completeFileAndCommit();

        // then - hook should be persisted with default values for missing fields
        var expectedHook = Hook.builder()
                .deleted(true)
                .extensionPoint(HookExtensionPoint.ACCOUNT_ALLOWANCE_HOOK) // database default
                .hookId(hookId)
                .ownerId(ownerId)
                .timestampRange(Range.atLeast(timestamp))
                .type(HookType.LAMBDA) // database default
                .build();

        assertThat(hookRepository.findAll()).containsExactly(expectedHook);
        assertThat(findHistory(Hook.class)).isEmpty(); // no history for new record
    }

    private TokenAccount getTokenAccount(
            EntityId tokenId,
            EntityId accountId,
            Long createdTimestamp,
            Boolean associated,
            Boolean autoAssociated,
            long balance,
            Long balanceTimestamp,
            TokenFreezeStatusEnum freezeStatus,
            TokenKycStatusEnum kycStatus,
            Range<Long> timestampRange) {
        TokenAccount tokenAccount = new TokenAccount();
        tokenAccount.setAccountId(accountId.getId());
        tokenAccount.setAssociated(associated);
        tokenAccount.setAutomaticAssociation(autoAssociated);
        tokenAccount.setBalance(balance);
        tokenAccount.setBalanceTimestamp(balanceTimestamp);
        tokenAccount.setCreatedTimestamp(createdTimestamp);
        tokenAccount.setFreezeStatus(freezeStatus);
        tokenAccount.setKycStatus(kycStatus);
        tokenAccount.setTimestampRange(timestampRange);
        tokenAccount.setTokenId(tokenId.getId());
        return tokenAccount;
    }
}
