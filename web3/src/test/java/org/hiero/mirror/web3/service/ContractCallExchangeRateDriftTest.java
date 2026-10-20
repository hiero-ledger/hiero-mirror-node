// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import jakarta.annotation.Resource;
import java.time.Instant;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

final class ContractCallExchangeRateDriftTest extends AbstractContractCallServiceTest {

    private static final int HBAR_EQUIV = 30_000;
    private static final int BASE_CENT_EQUIV = 269_662;
    private static final int DRIFTED_CENT_EQUIV = 272_880;

    @Resource
    private ExchangeRateUpdater exchangeRateUpdater;

    @Resource
    private TransactionExecutorFactory transactionExecutorFactory;

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Native secp256k1 DLL not available on Windows")
    void lazyCreationGasIsIndependentOfExchangeRate() {
        // Given
        final var sender = getAliasAddressFromEntity(accountEntityWithEvmAddressAndSufficientBalancePersist());

        // When
        final long baseline = hollowCreationGas(sender, HBAR_EQUIV, BASE_CENT_EQUIV, 1L);
        final long afterDrift = hollowCreationGas(sender, HBAR_EQUIV, DRIFTED_CENT_EQUIV, 2L);

        // Then
        // Integer truncation in the tinycent to tinybar conversion can differ by one unit between rates
        assertThat(afterDrift).isCloseTo(baseline, within(1L));
    }

    @Test
    void plainTransferIsIndependentOfExchangeRate() {
        // Given
        final var sender = getAliasAddressFromEntity(accountEntityWithEvmAddressAndSufficientBalancePersist());
        final var receiver = getAliasAddressFromEntity(accountEntityWithEvmAddressPersist());

        // When
        persistAndUpdateExchangeRate(HBAR_EQUIV, BASE_CENT_EQUIV, 1L);
        final long baseline =
                gasUsedAfterExecution(getContractExecutionParameters(HEX_PREFIX, receiver, sender, 1L, ETH_CALL));
        persistAndUpdateExchangeRate(HBAR_EQUIV, DRIFTED_CENT_EQUIV, 2L);
        final long afterDrift =
                gasUsedAfterExecution(getContractExecutionParameters(HEX_PREFIX, receiver, sender, 1L, ETH_CALL));

        // Then
        assertThat(afterDrift).isEqualTo(baseline);
    }

    @Test
    void executorsKeepStaleRatesUntilRefreshed() {
        // Given an executor created while the base rates are in the database
        final var sender = getAliasAddressFromEntity(accountEntityWithEvmAddressAndSufficientBalancePersist());
        final var receiver = getAliasAddressFromEntity(accountEntityWithEvmAddressPersist());
        persistAndUpdateExchangeRate(HBAR_EQUIV, BASE_CENT_EQUIV, 1L);
        gasUsedAfterExecution(getContractExecutionParameters(HEX_PREFIX, receiver, sender, 1L, ETH_CALL));
        final var exchangeRateManagers = transactionExecutorFactory.getExchangeRateManagers();
        assertThat(exchangeRateManagers).isNotEmpty();

        // When the importer persists drifted rates
        persistExchangeRate(HBAR_EQUIV, DRIFTED_CENT_EQUIV, 2L);

        // Then the in-memory rates stay stale until the next refresh
        assertThat(exchangeRateManagers)
                .allSatisfy(
                        m -> assertThat(m.exchangeRates().currentRateOrThrow().centEquiv())
                                .isEqualTo(BASE_CENT_EQUIV));
        updateExchangeRate();
        assertThat(exchangeRateManagers)
                .allSatisfy(
                        m -> assertThat(m.exchangeRates().currentRateOrThrow().centEquiv())
                                .isEqualTo(DRIFTED_CENT_EQUIV));
    }

    private long hollowCreationGas(
            final Address sender, final int hbarEquiv, final int centEquiv, final long timestamp) {
        persistAndUpdateExchangeRate(hbarEquiv, centEquiv, timestamp);
        final var hollowAlias = Address.wrap(Bytes.wrap(domainBuilder.evmAddress()));
        return gasUsedAfterExecution(getContractExecutionParameters(HEX_PREFIX, hollowAlias, sender, 1L, ETH_CALL));
    }

    private void persistAndUpdateExchangeRate(final int hbarEquiv, final int centEquiv, final long timestamp) {
        persistExchangeRate(hbarEquiv, centEquiv, timestamp);
        updateExchangeRate();
    }

    private void updateExchangeRate() {
        exchangeRateUpdater.refresh(Instant.now());
    }
}
