// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.StringJoiner;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.restjava.config.NetworkProperties;
import org.hiero.mirror.restjava.dto.NetworkSupply;
import org.hiero.mirror.restjava.repository.AccountBalanceRepository;
import org.hiero.mirror.restjava.repository.EntityRepository;
import org.hiero.mirror.restjava.repository.NetworkStakeRepository;

@Named
@RequiredArgsConstructor
final class NetworkServiceImpl implements NetworkService {

    private final AccountBalanceRepository accountBalanceRepository;
    private final EntityRepository entityRepository;
    private final NetworkStakeRepository networkStakeRepository;
    private final NetworkProperties networkProperties;

    @Override
    public NetworkStake getLatestNetworkStake() {
        return networkStakeRepository
                .findLatest()
                .orElseThrow(() -> new EntityNotFoundException("No network stake data found"));
    }

    @Override
    public NetworkSupply getSupply(Bound timestamp) {
        final NetworkSupply networkSupply;

        final var commonProperties = CommonProperties.getInstance();
        final long shard = commonProperties.getShard();
        final long realm = commonProperties.getRealm();

        final var ranges = networkProperties.getUnreleasedSupplyAccounts();
        final var lowerBoundJoiner = new StringJoiner(",");
        final var upperBoundJoiner = new StringJoiner(",");

        for (final var range : ranges) {
            final long from = EntityId.of(shard, realm, range.from()).getId();
            final long to = EntityId.of(shard, realm, range.to()).getId();

            lowerBoundJoiner.add(String.valueOf(from));
            upperBoundJoiner.add(String.valueOf(to));
        }

        final var lowerBounds = lowerBoundJoiner.toString();
        final var upperBounds = upperBoundJoiner.toString();

        if (timestamp.isEmpty()) {
            networkSupply = entityRepository.getSupply(lowerBounds, upperBounds);
        } else {
            var minTimestamp = timestamp.getAdjustedLowerRangeValue();
            final var maxTimestamp = timestamp.adjustUpperBound();

            // Validate timestamp range
            if (minTimestamp > maxTimestamp) {
                throw new IllegalArgumentException("Invalid range provided for timestamp");
            }

            final var optimalLowerBound = getFirstDayOfMonth(maxTimestamp, -1);
            minTimestamp = Math.max(minTimestamp, optimalLowerBound);

            networkSupply =
                    accountBalanceRepository.getSupplyHistory(lowerBounds, upperBounds, minTimestamp, maxTimestamp);
        }

        if (networkSupply.consensusTimestamp() == 0L) {
            throw new EntityNotFoundException("Network supply not found");
        }

        return networkSupply;
    }

    private long getFirstDayOfMonth(long timestamp, int monthOffset) {
        final var instant = Instant.ofEpochSecond(0, timestamp);
        final var dateTime = instant.atZone(ZoneOffset.UTC);
        final var firstDay = dateTime.plusMonths(monthOffset).withDayOfMonth(1);

        return firstDay.toLocalDate().atStartOfDay(ZoneOffset.UTC).toEpochSecond() * DomainUtils.NANOS_PER_SECOND;
    }
}
