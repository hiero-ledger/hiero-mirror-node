// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.common.domain.transaction.TransactionType.FILECREATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_EXCHANGE_RATES_SYSTEM_FILE;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import jakarta.annotation.Resource;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.cache.CacheManager;

final class ContractCallExchangeRateDriftTest extends AbstractContractCallServiceTest {

    private static final long FAR_FUTURE_SECONDS = 4_102_444_800L;
    private static final int HBAR_EQUIV = 30_000;
    private static final int BASE_CENT_EQUIV = 269_662;
    private static final int DRIFTED_CENT_EQUIV = 272_880;

    @Resource(name = CACHE_MANAGER_EXCHANGE_RATES_SYSTEM_FILE)
    private CacheManager exchangeRatesCacheManager;

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
        persistExchangeRate(HBAR_EQUIV, BASE_CENT_EQUIV, 1L);
        final long baseline =
                gasUsedAfterExecution(getContractExecutionParameters(HEX_PREFIX, receiver, sender, 1L, ETH_CALL));
        persistExchangeRate(HBAR_EQUIV, DRIFTED_CENT_EQUIV, 2L);
        final long afterDrift =
                gasUsedAfterExecution(getContractExecutionParameters(HEX_PREFIX, receiver, sender, 1L, ETH_CALL));

        // Then
        assertThat(afterDrift).isEqualTo(baseline);
    }

    private long hollowCreationGas(
            final Address sender, final int hbarEquiv, final int centEquiv, final long timestamp) {
        persistExchangeRate(hbarEquiv, centEquiv, timestamp);
        final var hollowAlias = Address.wrap(Bytes.wrap(domainBuilder.evmAddress()));
        return gasUsedAfterExecution(getContractExecutionParameters(HEX_PREFIX, hollowAlias, sender, 1L, ETH_CALL));
    }

    private void persistExchangeRate(final int hbarEquiv, final int centEquiv, final long timestamp) {
        final var rate = ExchangeRate.newBuilder()
                .setHbarEquiv(hbarEquiv)
                .setCentEquiv(centEquiv)
                .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(FAR_FUTURE_SECONDS))
                .build();
        final var rates = ExchangeRateSet.newBuilder()
                .setCurrentRate(rate)
                .setNextRate(rate)
                .build()
                .toByteArray();
        domainBuilder
                .fileData()
                .customize(f -> f.entityId(systemEntity.exchangeRateFile())
                        .fileData(rates)
                        .transactionType(FILECREATE.getProtoId())
                        .consensusTimestamp(timestamp))
                .persist();
        resetCacheManager(exchangeRatesCacheManager);
    }
}
