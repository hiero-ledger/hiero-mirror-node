// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.accessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.EMPTY_EVM_ADDRESS;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.CustomFee;
import com.hedera.mirror.common.domain.token.FallbackFee;
import com.hedera.mirror.common.domain.token.FixedFee;
import com.hedera.mirror.common.domain.token.FractionalFee;
import com.hedera.mirror.common.domain.token.RoyaltyFee;
import java.util.List;
import java.util.Optional;
import org.hiero.mirror.web3.repository.CustomFeeRepository;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomFeeDatabaseAccessorTest {
    @InjectMocks
    private CustomFeeDatabaseAccessor customFeeDatabaseAccessor;

    @Mock
    private CustomFeeRepository customFeeRepository;

    @Mock
    private EntityDatabaseAccessor entityDatabaseAccessor;

    private final long tokenId = 123L;
    private static final Optional<Long> timestamp = Optional.of(1234L);

    private final EntityId collectorId = EntityId.of(1L, 2L, 3L);

    private final Address collectorAddress = toAddress(collectorId);

    private final EntityId denominatingTokenId = EntityId.of(11L, 12L, 13L);

    private CustomFee customFee;

    @BeforeEach
    void setup() {
        customFee = new CustomFee();
    }

    @Test
    void royaltyFee() {
        var fallbackFeeBuilder = FallbackFee.builder().denominatingTokenId(denominatingTokenId);
        var fallbackFee = fallbackFeeBuilder.amount(20L).build();

        var royaltyFeeBuilder = RoyaltyFee.builder().collectorAccountId(collectorId);
        var royaltyFee = royaltyFeeBuilder
                .numerator(15L)
                .denominator(10L)
                .fallbackFee(fallbackFee)
                .build();
        var royaltyFee2 = royaltyFeeBuilder.numerator(16L).denominator(11L).build();
        var royaltyFees = List.of(royaltyFee, royaltyFee2);
        customFee.setRoyaltyFees(royaltyFees);

        when(customFeeRepository.findById(tokenId)).thenReturn(Optional.of(customFee));
        when(entityDatabaseAccessor.evmAddressFromId(collectorId, Optional.empty()))
                .thenReturn(collectorAddress);

        var results = customFeeDatabaseAccessor.get(tokenId, Optional.empty()).get();
        var listAssert = assertThat(results).hasSize(2);
        for (var domainFee : royaltyFees) {
            listAssert.anySatisfy(fee -> {
                assertThat(fee.getFixedFee()).isNull();
                assertThat(fee.getFractionalFee()).isNull();
                var resultRoyaltyFee = fee.getRoyaltyFee();
                assertThat(resultRoyaltyFee.getDenominator()).isEqualTo(domainFee.getDenominator());
                assertThat(resultRoyaltyFee.getNumerator()).isEqualTo(domainFee.getNumerator());
                assertThat(resultRoyaltyFee.getAmount())
                        .isEqualTo(domainFee.getFallbackFee().getAmount());
                assertThat(resultRoyaltyFee.getDenominatingTokenId()).isEqualTo(toAddress(denominatingTokenId));
                assertThat(resultRoyaltyFee.isUseHbarsForPayment()).isFalse();
                assertThat(resultRoyaltyFee.getFeeCollector()).isEqualTo(collectorAddress);
            });
        }
    }

    @Test
    void royaltyFeeHistorical() {
        var fallbackFeeBuilder = FallbackFee.builder().denominatingTokenId(denominatingTokenId);
        var fallbackFee = fallbackFeeBuilder.amount(20L).build();

        var royaltyFeeBuilder = RoyaltyFee.builder().collectorAccountId(collectorId);
        var royaltyFee = royaltyFeeBuilder
                .numerator(15L)
                .denominator(10L)
                .fallbackFee(fallbackFee)
                .build();
        var royaltyFee2 = royaltyFeeBuilder.numerator(16L).denominator(11L).build();
        var royaltyFees = List.of(royaltyFee, royaltyFee2);
        customFee.setRoyaltyFees(royaltyFees);

        when(customFeeRepository.findByTokenIdAndTimestamp(tokenId, timestamp.get()))
                .thenReturn(Optional.of(customFee));
        when(entityDatabaseAccessor.evmAddressFromId(collectorId, timestamp)).thenReturn(collectorAddress);

        var results = customFeeDatabaseAccessor.get(tokenId, timestamp).get();
        var listAssert = assertThat(results).hasSize(2);
        for (var domainFee : royaltyFees) {
            listAssert.anySatisfy(fee -> {
                assertThat(fee.getFixedFee()).isNull();
                assertThat(fee.getFractionalFee()).isNull();
                var resultRoyaltyFee = fee.getRoyaltyFee();
                assertThat(resultRoyaltyFee.getDenominator()).isEqualTo(domainFee.getDenominator());
                assertThat(resultRoyaltyFee.getNumerator()).isEqualTo(domainFee.getNumerator());
                assertThat(resultRoyaltyFee.getAmount())
                        .isEqualTo(domainFee.getFallbackFee().getAmount());
                assertThat(resultRoyaltyFee.getDenominatingTokenId()).isEqualTo(toAddress(denominatingTokenId));
                assertThat(resultRoyaltyFee.isUseHbarsForPayment()).isFalse();
                assertThat(resultRoyaltyFee.getFeeCollector()).isEqualTo(collectorAddress);
            });
        }
    }

    @Test
    void royaltyFeeNoFallback() {
        var royaltyFee = RoyaltyFee.builder()
                .numerator(15L)
                .denominator(10L)
                .fallbackFee(null)
                .collectorAccountId(collectorId)
                .build();
        var royaltyFees = List.of(royaltyFee);
        customFee.setRoyaltyFees(royaltyFees);

        when(customFeeRepository.findById(tokenId)).thenReturn(Optional.of(customFee));
        when(entityDatabaseAccessor.evmAddressFromId(collectorId, Optional.empty()))
                .thenReturn(collectorAddress);

        var results = customFeeDatabaseAccessor.get(tokenId, Optional.empty()).get();
        var resultFee = results.get(0).getRoyaltyFee();
        assertEquals(royaltyFee.getNumerator(), resultFee.getNumerator());
        assertEquals(royaltyFee.getDenominator(), resultFee.getDenominator());
        assertEquals(0L, resultFee.getAmount());
        assertEquals(collectorAddress, resultFee.getFeeCollector());
        assertEquals(EMPTY_EVM_ADDRESS, resultFee.getDenominatingTokenId());
    }

    @Test
    void royaltyFeeNoFallbackHistorical() {
        var royaltyFee = RoyaltyFee.builder()
                .numerator(15L)
                .denominator(10L)
                .fallbackFee(null)
                .collectorAccountId(collectorId)
                .build();
        var royaltyFees = List.of(royaltyFee);
        customFee.setRoyaltyFees(royaltyFees);

        when(customFeeRepository.findByTokenIdAndTimestamp(tokenId, timestamp.get()))
                .thenReturn(Optional.of(customFee));
        when(entityDatabaseAccessor.evmAddressFromId(collectorId, timestamp)).thenReturn(collectorAddress);

        var results = customFeeDatabaseAccessor.get(tokenId, timestamp).get();
        var resultFee = results.get(0).getRoyaltyFee();
        assertEquals(royaltyFee.getNumerator(), resultFee.getNumerator());
        assertEquals(royaltyFee.getDenominator(), resultFee.getDenominator());
        assertEquals(0L, resultFee.getAmount());
        assertEquals(collectorAddress, resultFee.getFeeCollector());
        assertEquals(EMPTY_EVM_ADDRESS, resultFee.getDenominatingTokenId());
    }

    @Test
    void fractionFee() {
        var fractionalFeeBuilder = FractionalFee.builder().collectorAccountId(collectorId);
        var fractionalFee = fractionalFeeBuilder
                .numerator(20L)
                .denominator(2L)
                .maximumAmount(100L)
                .minimumAmount(5L)
                .netOfTransfers(true)
                .build();
        var fractionalFee2 = fractionalFeeBuilder
                .numerator(21L)
                .denominator(3L)
                .maximumAmount(101L)
                .minimumAmount(6L)
                .netOfTransfers(false)
                .build();
        var fractionalFees = List.of(fractionalFee, fractionalFee2);
        customFee.setFractionalFees(fractionalFees);

        when(customFeeRepository.findById(tokenId)).thenReturn(Optional.of(customFee));
        when(entityDatabaseAccessor.evmAddressFromId(collectorId, Optional.empty()))
                .thenReturn(collectorAddress);

        var results = customFeeDatabaseAccessor.get(tokenId, Optional.empty()).get();
        var listAssert = assertThat(results).hasSize(2);
        for (var domainFee : fractionalFees) {
            listAssert.anySatisfy(fee -> {
                assertThat(fee.getFixedFee()).isNull();
                assertThat(fee.getRoyaltyFee()).isNull();
                var resultFractionalFee = fee.getFractionalFee();
                assertThat(resultFractionalFee.getNumerator()).isEqualTo(domainFee.getNumerator());
                assertThat(resultFractionalFee.getDenominator()).isEqualTo(domainFee.getDenominator());
                assertThat(resultFractionalFee.getMaximumAmount()).isEqualTo(domainFee.getMaximumAmount());
                assertThat(resultFractionalFee.getMinimumAmount()).isEqualTo(domainFee.getMinimumAmount());
                assertThat(resultFractionalFee.getNetOfTransfers()).isEqualTo(domainFee.isNetOfTransfers());
                assertThat(resultFractionalFee.getFeeCollector()).isEqualTo(collectorAddress);
            });
        }
    }

    @Test
    void fractionFeeHistorical() {
        var fractionalFeeBuilder = FractionalFee.builder().collectorAccountId(collectorId);
        var fractionalFee = fractionalFeeBuilder
                .numerator(20L)
                .denominator(2L)
                .maximumAmount(100L)
                .minimumAmount(5L)
                .netOfTransfers(true)
                .build();
        var fractionalFee2 = fractionalFeeBuilder
                .numerator(21L)
                .denominator(3L)
                .maximumAmount(101L)
                .minimumAmount(6L)
                .netOfTransfers(false)
                .build();
        var fractionalFees = List.of(fractionalFee, fractionalFee2);
        customFee.setFractionalFees(fractionalFees);

        when(customFeeRepository.findByTokenIdAndTimestamp(tokenId, timestamp.get()))
                .thenReturn(Optional.of(customFee));
        when(entityDatabaseAccessor.evmAddressFromId(collectorId, timestamp)).thenReturn(collectorAddress);

        var results = customFeeDatabaseAccessor.get(tokenId, timestamp).get();
        var listAssert = assertThat(results).hasSize(2);
        for (var domainFee : fractionalFees) {
            listAssert.anySatisfy(fee -> {
                assertThat(fee.getFixedFee()).isNull();
                assertThat(fee.getRoyaltyFee()).isNull();
                var resultFractionalFee = fee.getFractionalFee();
                assertThat(resultFractionalFee.getNumerator()).isEqualTo(domainFee.getNumerator());
                assertThat(resultFractionalFee.getDenominator()).isEqualTo(domainFee.getDenominator());
                assertThat(resultFractionalFee.getMaximumAmount()).isEqualTo(domainFee.getMaximumAmount());
                assertThat(resultFractionalFee.getMinimumAmount()).isEqualTo(domainFee.getMinimumAmount());
                assertThat(resultFractionalFee.getNetOfTransfers()).isEqualTo(domainFee.isNetOfTransfers());
                assertThat(resultFractionalFee.getFeeCollector()).isEqualTo(collectorAddress);
            });
        }
    }

    @Test
    void fixedFee() {
        var fixedFeeBuilder =
                FixedFee.builder().collectorAccountId(collectorId).denominatingTokenId(denominatingTokenId);
        var fixedFee = fixedFeeBuilder.amount(20L).build();
        var fixedFee2 = fixedFeeBuilder.amount(21L).build();
        var fixedFees = List.of(fixedFee, fixedFee2);
        customFee.setFixedFees(fixedFees);

        when(customFeeRepository.findById(tokenId)).thenReturn(Optional.of(customFee));
        when(entityDatabaseAccessor.evmAddressFromId(collectorId, Optional.empty()))
                .thenReturn(collectorAddress);

        var results = customFeeDatabaseAccessor.get(tokenId, Optional.empty()).get();
        var listAssert = assertThat(results).hasSize(2);
        for (var domainFee : fixedFees) {
            listAssert.anySatisfy(fee -> {
                assertThat(fee.getFractionalFee()).isNull();
                assertThat(fee.getRoyaltyFee()).isNull();
                var resultFixedFee = fee.getFixedFee();
                assertThat(resultFixedFee.getAmount()).isEqualTo(domainFee.getAmount());
                assertThat(resultFixedFee.isUseCurrentTokenForPayment()).isFalse();
                assertThat(resultFixedFee.getDenominatingTokenId()).isEqualTo(toAddress(denominatingTokenId));
                assertThat(resultFixedFee.isUseHbarsForPayment()).isFalse();
                assertThat(resultFixedFee.getFeeCollector()).isEqualTo(collectorAddress);
            });
        }
    }

    @Test
    void fixedFeeHistorical() {
        var fixedFeeBuilder =
                FixedFee.builder().collectorAccountId(collectorId).denominatingTokenId(denominatingTokenId);
        var fixedFee = fixedFeeBuilder.amount(20L).build();
        var fixedFee2 = fixedFeeBuilder.amount(21L).build();
        var fixedFees = List.of(fixedFee, fixedFee2);
        customFee.setFixedFees(fixedFees);

        when(customFeeRepository.findByTokenIdAndTimestamp(tokenId, timestamp.get()))
                .thenReturn(Optional.of(customFee));
        when(entityDatabaseAccessor.evmAddressFromId(collectorId, timestamp)).thenReturn(collectorAddress);

        var results = customFeeDatabaseAccessor.get(tokenId, timestamp).get();
        var listAssert = assertThat(results).hasSize(2);
        for (var domainFee : fixedFees) {
            listAssert.anySatisfy(fee -> {
                assertThat(fee.getFractionalFee()).isNull();
                assertThat(fee.getRoyaltyFee()).isNull();
                var resultFixedFee = fee.getFixedFee();
                assertThat(resultFixedFee.getAmount()).isEqualTo(domainFee.getAmount());
                assertThat(resultFixedFee.isUseCurrentTokenForPayment()).isFalse();
                assertThat(resultFixedFee.getDenominatingTokenId()).isEqualTo(toAddress(denominatingTokenId));
                assertThat(resultFixedFee.isUseHbarsForPayment()).isFalse();
                assertThat(resultFixedFee.getFeeCollector()).isEqualTo(collectorAddress);
            });
        }
    }

    @Test
    void mapOnlyFeesWithCollectorAccountId() {
        final var noCollectorCustomFee = new CustomFee();
        when(customFeeRepository.findById(tokenId)).thenReturn(Optional.of(noCollectorCustomFee));
        assertThat(customFeeDatabaseAccessor.get(tokenId, Optional.empty()))
                .hasValueSatisfying(customFees -> assertThat(customFees).isEmpty());
    }

    @Test
    void mapOnlyFeesWithCollectorAccountIdHistorical() {
        final var noCollectorCustomFee = new CustomFee();
        when(customFeeRepository.findByTokenIdAndTimestamp(tokenId, timestamp.get()))
                .thenReturn(Optional.of(noCollectorCustomFee));
        assertThat(customFeeDatabaseAccessor.get(tokenId, timestamp))
                .hasValueSatisfying(customFees -> assertThat(customFees).isEmpty());
    }
}
