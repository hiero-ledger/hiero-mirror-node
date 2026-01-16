// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.job.ReconciliationStatus.FAILURE_CRYPTO_TRANSFERS;
import static org.hiero.mirror.common.domain.job.ReconciliationStatus.FAILURE_FIFTY_BILLION;
import static org.hiero.mirror.common.domain.job.ReconciliationStatus.FAILURE_TOKEN_TRANSFERS;
import static org.hiero.mirror.common.domain.job.ReconciliationStatus.SUCCESS;
import static org.hiero.mirror.common.domain.job.ReconciliationStatus.UNKNOWN;
import static org.hiero.mirror.importer.reconciliation.BalanceReconciliationService.FIFTY_BILLION_HBARS;
import static org.hiero.mirror.importer.reconciliation.BalanceReconciliationService.METRIC;
import static org.hiero.mirror.importer.reconciliation.BalanceReconciliationService.TokenAccountId;
import static org.hiero.mirror.importer.reconciliation.ReconciliationProperties.RemediationStrategy.ACCUMULATE;
import static org.hiero.mirror.importer.reconciliation.ReconciliationProperties.RemediationStrategy.FAIL;
import static org.hiero.mirror.importer.reconciliation.ReconciliationProperties.RemediationStrategy.RESET;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ObjectAssert;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.common.domain.balance.TokenBalance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.job.ReconciliationJob;
import org.hiero.mirror.common.domain.job.ReconciliationStatus;
import org.hiero.mirror.common.domain.token.TokenTransfer;
import org.hiero.mirror.common.domain.transaction.ErrataType;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.ReconciliationJobRepository;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.transaction.support.TransactionTemplate;

@DirtiesContext(classMode = ClassMode.BEFORE_CLASS)
@RequiredArgsConstructor
class BalanceReconciliationServiceTest extends ImporterIntegrationTest {

    private final DomainBuilder domainBuilder;
    private final MeterRegistry meterRegistry;
    private final ReconciliationJobRepository reconciliationJobRepository;
    private final ReconciliationProperties reconciliationProperties;
    private final BalanceReconciliationService reconciliationService;
    private final TransactionTemplate transactionTemplate;

    @BeforeEach
    void setup() {
        reconciliationProperties.setDelay(Duration.ZERO);
        reconciliationProperties.setEnabled(true);
        reconciliationProperties.setEndDate(Utility.MAX_INSTANT_LONG);
        reconciliationProperties.setRemediationStrategy(FAIL);
        reconciliationProperties.setStartDate(Instant.EPOCH);
        reconciliationProperties.setToken(true);
        reconciliationService.status.set(UNKNOWN);
    }

    @Override
    protected void reset() {
        // Skip so we don't clear the metric
    }

    @Test
    void cryptoTransfersSuccess() {
        // given
        balance(Map.of(2L, FIFTY_BILLION_HBARS));
        transfer(2, 3, 1000);
        transfer(3, 4, 100);
        balance(Map.of(2L, FIFTY_BILLION_HBARS - 1000L, 3L, 900L, 4L, 100L, 5L, 0L));
        transfer(2, 4, 500);
        transfer(2, 5, 100);
        var last = balance(Map.of(2L, FIFTY_BILLION_HBARS - 1600L, 3L, 900L, 4L, 600L, 5L, 100L));

        // when
        reconcile();

        // then
        assertReconciliationJob(SUCCESS, last).returns(2L, ReconciliationJob::getCount);
    }

    @Test
    void cryptoTransfersFailure() {
        // given
        balance(Map.of(2L, FIFTY_BILLION_HBARS));
        transfer(2, 3, 1000);
        balance(Map.of(2L, FIFTY_BILLION_HBARS - 1010L, 3L, 1010L)); // Missing 10 tinybar transfer

        // when
        reconcile();

        // then
        assertReconciliationJob(FAILURE_CRYPTO_TRANSFERS, null).returns(0L, ReconciliationJob::getCount);
    }

    @Test
    void cryptoTransfersAccumulateStrategy() {
        // given
        reconciliationProperties.setRemediationStrategy(ACCUMULATE);
        balance(Map.of(2L, FIFTY_BILLION_HBARS));
        var balance2 = balance(Map.of(2L, FIFTY_BILLION_HBARS));
        balance(Map.of(2L, FIFTY_BILLION_HBARS - 1000L, 3L, 1000L)); // Missing 1000 tinybar transfer
        transfer(3, 4, 1);
        balance(Map.of(2L, FIFTY_BILLION_HBARS - 1000L, 3L, 999L, 4L, 1L));

        // when
        reconcile();

        // then
        assertReconciliationJob(FAILURE_CRYPTO_TRANSFERS, balance2)
                .returns(3L, ReconciliationJob::getCount)
                .satisfies(r -> assertThat(r.getError()).contains(""));
    }

    @Test
    void cryptoTransfersResetStrategy() {
        // given
        reconciliationProperties.setRemediationStrategy(RESET);
        balance(Map.of(2L, FIFTY_BILLION_HBARS));
        var balance2 = balance(Map.of(2L, FIFTY_BILLION_HBARS));
        balance(Map.of(2L, FIFTY_BILLION_HBARS - 1000L, 3L, 1000L)); // Missing 1000 tinybar transfer
        transfer(3, 4, 1);
        balance(Map.of(2L, FIFTY_BILLION_HBARS - 1000L, 3L, 999L, 4L, 1L));

        // when
        reconcile();

        // then
        assertReconciliationJob(FAILURE_CRYPTO_TRANSFERS, balance2).returns(3L, ReconciliationJob::getCount);
    }

    @Test
    void cryptoTransfersZeroBalances() {
        // given
        balance(Map.of(2L, FIFTY_BILLION_HBARS, 3L, 0L));
        var balance2 = balance(Map.of(2L, FIFTY_BILLION_HBARS, 4L, 0L));

        // when
        reconcile();

        // then
        assertReconciliationJob(SUCCESS, balance2).returns(1L, ReconciliationJob::getCount);
    }

    @Test
    void delay() {
        // given
        long delay = 1000L;
        reconciliationProperties.setDelay(Duration.ofMillis(delay));
        balance(Map.of(2L, FIFTY_BILLION_HBARS, 3L, 0L));
        var balance2 = balance(Map.of(2L, FIFTY_BILLION_HBARS, 4L, 0L));

        // when
        long start = System.currentTimeMillis();
        reconcile();
        long end = System.currentTimeMillis();

        // then
        assertReconciliationJob(SUCCESS, balance2).returns(1L, ReconciliationJob::getCount);
        assertThat(end - start).isGreaterThanOrEqualTo(delay);
    }

    @Test
    void tokenTransfersSuccess() {
        // given
        tokenBalance(Map.of());
        tokenTransfer(2, 100, 1000);
        tokenTransfer(2, 100, -100);
        tokenTransfer(3, 100, 100);
        var balance2 = tokenBalance(Map.of(new TokenAccountId(2, 100), 900L, new TokenAccountId(3, 100), 100L));

        // when
        reconcile();

        // then
        assertReconciliationJob(SUCCESS, balance2).returns(1L, ReconciliationJob::getCount);
    }

    @Test
    void tokenTransfersFailure() {
        // given
        tokenBalance(Map.of(new TokenAccountId(2, 100), 1L));
        tokenBalance(Map.of(new TokenAccountId(2, 100), 2L)); // Missing transfer

        // when
        reconcile();

        // then
        assertReconciliationJob(FAILURE_TOKEN_TRANSFERS, null).returns(0L, ReconciliationJob::getCount);
    }

    @Test
    void tokenTransfersResetStrategy() {
        // given
        reconciliationProperties.setRemediationStrategy(RESET);
        tokenBalance(Map.of(new TokenAccountId(2, 100), 100L));
        var balance2 = tokenBalance(Map.of(new TokenAccountId(2, 100), 100L));
        tokenBalance(Map.of(new TokenAccountId(2, 100), 101L)); // Missing transfer
        tokenTransfer(2, 100, -10);
        tokenTransfer(3, 100, 10);
        tokenBalance(Map.of(new TokenAccountId(2, 100), 91L, new TokenAccountId(3, 100), 10L));

        // when
        reconcile();

        // then
        assertReconciliationJob(FAILURE_TOKEN_TRANSFERS, balance2).returns(3L, ReconciliationJob::getCount);
    }

    @Test
    void tokensNotEnabled() {
        // given
        reconciliationProperties.setToken(false);
        tokenBalance(Map.of(new TokenAccountId(2, 100), 1L));
        var balance2 = tokenBalance(Map.of(new TokenAccountId(2, 100), 2L)); // Missing transfer

        // when
        reconcile();

        // then
        assertReconciliationJob(SUCCESS, balance2).returns(1L, ReconciliationJob::getCount);
    }

    @Test
    void tokenTransfersZeroBalances() {
        // given
        tokenBalance(Map.of(new TokenAccountId(2, 100), 0L, new TokenAccountId(3, 100), 0L));
        var balance2 = tokenBalance(Map.of(new TokenAccountId(2, 100), 0L, new TokenAccountId(3, 100), 0L));

        // when
        reconcile();

        // then
        assertReconciliationJob(SUCCESS, balance2).returns(1L, ReconciliationJob::getCount);
    }

    @Test
    void errata() {
        // given
        balance(Map.of(2L, FIFTY_BILLION_HBARS));
        transfer(2, 3, 1);
        domainBuilder
                .cryptoTransfer()
                .customize(c -> c.amount(100).entityId(4).errata(ErrataType.DELETE))
                .persist();
        domainBuilder
                .cryptoTransfer()
                .customize(c -> c.amount(-100).entityId(2).errata(ErrataType.DELETE))
                .persist();
        var balance2 = balance(Map.of(2L, FIFTY_BILLION_HBARS - 1L, 3L, 1L)); // Errata rows not present

        // when
        reconcile();

        // then
        assertMetric(SUCCESS);
        assertReconciliationJob(SUCCESS, balance2).returns(1L, ReconciliationJob::getCount);
    }

    @Test
    void notEnabled() {
        // given
        reconciliationProperties.setEnabled(false);
        balance(Map.of(2L, 1L)); // Would fail if checked

        // when
        reconcile();

        // then
        assertMetric(UNKNOWN);
        assertThat(reconciliationJobRepository.count()).isZero();
    }

    @Test
    void noBalanceFiles() {
        // when
        reconcile();

        // then
        assertMetric(UNKNOWN);
        assertReconciliationJob(UNKNOWN, null).returns(0L, ReconciliationJob::getCount);
    }

    @Test
    void singleBalanceFile() {
        // given
        balance(Map.of(2L, FIFTY_BILLION_HBARS));

        // when
        reconcile();

        // then
        assertReconciliationJob(SUCCESS, null).returns(0L, ReconciliationJob::getCount);
    }

    @Test
    void balanceNotFiftyBillion() {
        // given
        balance(Map.of(2L, FIFTY_BILLION_HBARS));
        transfer(2, 3, 100);
        balance(Map.of(2L, FIFTY_BILLION_HBARS, 3L, 100L));

        // when
        reconcile();

        // then
        assertReconciliationJob(FAILURE_FIFTY_BILLION, null).returns(0L, ReconciliationJob::getCount);
    }

    @Test
    void startDate() {
        // given
        balance(Map.of(2L, 1L)); // Would fail if checked
        var balanceFile2 = balance(Map.of(2L, FIFTY_BILLION_HBARS));
        var balanceFile3 = balance(Map.of(2L, FIFTY_BILLION_HBARS));
        reconciliationProperties.setStartDate(Instant.ofEpochSecond(0L, balanceFile2.getConsensusTimestamp()));

        // when
        reconcile();

        // then
        assertReconciliationJob(SUCCESS, balanceFile3).returns(1L, ReconciliationJob::getCount);
    }

    @Test
    void startDateAfterLastRun() {
        // given
        var balanceFile1 = balance(Map.of(2L, 1L)); // Would fail if checked
        var balanceFile2 = balance(Map.of(2L, FIFTY_BILLION_HBARS));
        var balanceFile3 = balance(Map.of(2L, FIFTY_BILLION_HBARS));
        domainBuilder
                .reconciliationJob()
                .customize(r -> r.consensusTimestamp(balanceFile1.getConsensusTimestamp())
                        .timestampStart(Instant.EPOCH)
                        .timestampEnd(Instant.EPOCH.plusSeconds(1)))
                .persist();
        reconciliationProperties.setStartDate(Instant.ofEpochSecond(0L, balanceFile2.getConsensusTimestamp()));

        // when
        reconcile();

        // then
        assertReconciliationJob(SUCCESS, balanceFile3).returns(1L, ReconciliationJob::getCount);
    }

    @Test
    void endDate() {
        // given
        balance(Map.of(2L, FIFTY_BILLION_HBARS));
        balance(Map.of(2L, FIFTY_BILLION_HBARS));
        var balanceFile3 = balance(Map.of(2L, FIFTY_BILLION_HBARS));
        balance(Map.of(2L, 1L)); // Would fail if checked
        reconciliationProperties.setEndDate(Instant.ofEpochSecond(0L, balanceFile3.getConsensusTimestamp()));

        // when
        reconcile();

        // then
        assertReconciliationJob(SUCCESS, balanceFile3).returns(2L, ReconciliationJob::getCount);
    }

    @Test
    void recovers() {
        // given
        balance(Map.of(2L, FIFTY_BILLION_HBARS));
        domainBuilder.cryptoTransfer().customize(c -> c.amount(100).entityId(3)).persist();
        var missingTransfer =
                domainBuilder.cryptoTransfer().customize(c -> c.amount(-100).entityId(2));
        var last = balance(Map.of(2L, FIFTY_BILLION_HBARS - 100, 3L, 100L));

        // when
        reconcile();

        // then
        assertReconciliationJob(FAILURE_CRYPTO_TRANSFERS, null)
                .returns(0L, ReconciliationJob::getCount)
                .extracting(ReconciliationJob::getError)
                .asInstanceOf(InstanceOfAssertFactories.STRING)
                .contains("not equal: value differences={2=(5000000000000000000, 4999999999999999900)}");

        // given
        missingTransfer.persist();

        // when
        reconciliationService.reconcile();

        // then
        assertReconciliationJob(SUCCESS, last).returns(1L, ReconciliationJob::getCount);
        assertThat(reconciliationJobRepository.count()).isEqualTo(2);
    }

    private void assertMetric(ReconciliationStatus status) {
        assertThat(meterRegistry.find(METRIC).gauges())
                .hasSize(1)
                .first()
                .extracting(Gauge::value)
                .isEqualTo((double) status.ordinal());
    }

    private ObjectAssert<ReconciliationJob> assertReconciliationJob(
            ReconciliationStatus status, AccountBalanceFile accountBalanceFile) {
        assertMetric(status);

        var consensusTimestamp = accountBalanceFile != null ? accountBalanceFile.getConsensusTimestamp() : 0L;
        var jobAssert = assertThat(reconciliationJobRepository.findLatest())
                .get()
                .returns(consensusTimestamp, ReconciliationJob::getConsensusTimestamp)
                .satisfies(rj -> assertThat(rj.getCount()).isNotNegative())
                .satisfies(rj -> assertThat(rj.getTimestampEnd()).isNotNull())
                .satisfies(rj -> assertThat(rj.getTimestampStart()).isNotNull().isBeforeOrEqualTo(rj.getTimestampEnd()))
                .asInstanceOf(InstanceOfAssertFactories.type(ReconciliationJob.class));

        if (status == SUCCESS) {
            jobAssert.returns("", ReconciliationJob::getError);
        }

        return jobAssert;
    }

    private void reconcile() {
        transactionTemplate.executeWithoutResult(t -> reconciliationService.reconcile());
    }

    private AccountBalanceFile balance(Map<Long, Long> balances) {
        var accountBalanceFile = domainBuilder.accountBalanceFile().persist();
        long timestamp = accountBalanceFile.getConsensusTimestamp();

        balances.forEach((accountId, balance) -> {
            var entityId = EntityId.of(accountId);
            domainBuilder
                    .accountBalance()
                    .customize(a -> a.balance(balance).id(new AccountBalance.Id(timestamp, entityId)))
                    .persist();
        });

        domainBuilder.recordFile().customize(r -> r.hapiVersionMinor(20)).persist();
        return accountBalanceFile;
    }

    private AccountBalanceFile tokenBalance(Map<TokenAccountId, Long> balances) {
        var accountBalanceFile = balance(Map.of(2L, FIFTY_BILLION_HBARS));
        long timestamp = accountBalanceFile.getConsensusTimestamp();

        balances.forEach((id, balance) -> {
            var accountId = EntityId.of(id.getAccountId());
            var tokenId = EntityId.of(id.getTokenId());
            domainBuilder
                    .tokenBalance()
                    .customize(a -> a.balance(balance).id(new TokenBalance.Id(timestamp, accountId, tokenId)))
                    .persist();
        });

        return accountBalanceFile;
    }

    private void transfer(long from, long to, long amount) {
        domainBuilder
                .cryptoTransfer()
                .customize(c -> c.amount(amount).entityId(to))
                .persist();
        domainBuilder
                .cryptoTransfer()
                .customize(c -> c.amount(-amount).entityId(from))
                .persist();
    }

    private void tokenTransfer(long accountNum, long tokenNum, long amount) {
        long timestamp = domainBuilder.timestamp();
        EntityId accountId = EntityId.of(accountNum);
        EntityId tokenId = EntityId.of(tokenNum);
        domainBuilder
                .tokenTransfer()
                .customize(c -> c.amount(amount).id(new TokenTransfer.Id(timestamp, tokenId, accountId)))
                .persist();
    }
}
