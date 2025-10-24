// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.accessor;

import static java.util.Objects.requireNonNullElse;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.EMPTY_EVM_ADDRESS;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.FixedFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.FractionalFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.RoyaltyFee;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.evm.store.DatabaseBackedStateFrame.DatabaseAccessIncorrectKeyTypeException;
import org.hiero.mirror.web3.repository.CustomFeeRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.util.CollectionUtils;

@Named
@RequiredArgsConstructor
public class CustomFeeDatabaseAccessor extends DatabaseAccessor<Object, List<CustomFee>> {

    private final CustomFeeRepository customFeeRepository;
    private final EntityDatabaseAccessor entityDatabaseAccessor;

    @Override
    public @NonNull Optional<List<CustomFee>> get(@NonNull Object key, final Optional<Long> timestamp) {
        if (key instanceof Long tokenId) {

            return timestamp
                    .map(t -> customFeeRepository.findByTokenIdAndTimestamp(tokenId, t))
                    .orElseGet(() -> customFeeRepository.findById(tokenId))
                    .map(customFee -> mapCustomFee(customFee, timestamp));
        }
        throw new DatabaseAccessIncorrectKeyTypeException("Accessor for class %s failed to fetch by key of type %s"
                .formatted(CustomFee.class.getTypeName(), key.getClass().getTypeName()));
    }

    private List<CustomFee> mapCustomFee(
            org.hiero.mirror.common.domain.token.CustomFee customFee, final Optional<Long> timestamp) {
        var customFeesConstructed = new ArrayList<CustomFee>();
        customFeesConstructed.addAll(mapFixedFees(customFee, timestamp));
        customFeesConstructed.addAll(mapFractionalFees(customFee, timestamp));
        customFeesConstructed.addAll(mapRoyaltyFees(customFee, timestamp));
        return customFeesConstructed;
    }

    private List<CustomFee> mapFixedFees(
            org.hiero.mirror.common.domain.token.CustomFee customFee, final Optional<Long> timestamp) {
        if (CollectionUtils.isEmpty(customFee.getFixedFees())) {
            return Collections.emptyList();
        }

        var fixedFees = new ArrayList<CustomFee>();
        customFee.getFixedFees().forEach(f -> {
            final var collector = entityDatabaseAccessor.evmAddressFromId(f.getCollectorAccountId(), timestamp);
            final var denominatingTokenId = f.getDenominatingTokenId();
            final var denominatingTokenAddress =
                    denominatingTokenId == null ? EMPTY_EVM_ADDRESS : toAddress(denominatingTokenId);
            final var fixedFee = new FixedFee(
                    requireNonNullElse(f.getAmount(), 0L),
                    denominatingTokenAddress,
                    denominatingTokenId == null,
                    false,
                    collector);
            var constructed = new CustomFee();
            constructed.setFixedFee(fixedFee);
            fixedFees.add(constructed);
        });

        return fixedFees;
    }

    private List<CustomFee> mapFractionalFees(
            org.hiero.mirror.common.domain.token.CustomFee customFee, final Optional<Long> timestamp) {
        if (CollectionUtils.isEmpty(customFee.getFractionalFees())) {
            return Collections.emptyList();
        }

        var fractionalFees = new ArrayList<CustomFee>();
        customFee.getFractionalFees().forEach(f -> {
            final var collector = entityDatabaseAccessor.evmAddressFromId(f.getCollectorAccountId(), timestamp);
            final var fractionFee = new FractionalFee(
                    requireNonNullElse(f.getNumerator(), 0L),
                    requireNonNullElse(f.getDenominator(), 0L),
                    requireNonNullElse(f.getMinimumAmount(), 0L),
                    requireNonNullElse(f.getMaximumAmount(), 0L),
                    requireNonNullElse(f.isNetOfTransfers(), false),
                    collector);
            var constructed = new CustomFee();
            constructed.setFractionalFee(fractionFee);
            fractionalFees.add(constructed);
        });

        return fractionalFees;
    }

    private List<CustomFee> mapRoyaltyFees(
            org.hiero.mirror.common.domain.token.CustomFee customFee, final Optional<Long> timestamp) {
        if (CollectionUtils.isEmpty(customFee.getRoyaltyFees())) {
            return Collections.emptyList();
        }

        var royaltyFees = new ArrayList<CustomFee>();
        customFee.getRoyaltyFees().forEach(f -> {
            final var collector = entityDatabaseAccessor.evmAddressFromId(f.getCollectorAccountId(), timestamp);
            final var fallbackFee = f.getFallbackFee();

            long amount = 0;
            EntityId denominatingTokenId = null;
            var denominatingTokenAddress = EMPTY_EVM_ADDRESS;
            if (fallbackFee != null) {
                amount = fallbackFee.getAmount();
                denominatingTokenId = fallbackFee.getDenominatingTokenId();
                if (denominatingTokenId != null) {
                    denominatingTokenAddress = toAddress(denominatingTokenId);
                }
            }

            final var royaltyFee = new RoyaltyFee(
                    requireNonNullElse(f.getNumerator(), 0L),
                    requireNonNullElse(f.getDenominator(), 0L),
                    amount,
                    denominatingTokenAddress,
                    denominatingTokenId == null,
                    collector);
            var constructed = new CustomFee();
            constructed.setRoyaltyFee(royaltyFee);
            royaltyFees.add(constructed);
        });

        return royaltyFees;
    }
}
