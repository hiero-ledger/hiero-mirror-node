// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.hiero.mirror.restjava.config.NetworkProperties;
import org.hiero.mirror.restjava.dto.NetworkSupply;
import org.hiero.mirror.restjava.dto.NetworkSupplyProjection;
import org.hiero.mirror.restjava.repository.EntityRepository;
import org.hiero.mirror.restjava.repository.NetworkStakeRepository;

@Named
@RequiredArgsConstructor
final class NetworkServiceImpl implements NetworkService {

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
        final var unreleasedSupplyAccountIds = networkProperties.getUnreleasedSupplyAccountIds();
        NetworkSupplyProjection projection;

        if (timestamp.isEmpty()) {
            projection = entityRepository.getSupply(unreleasedSupplyAccountIds);
        } else {
            var minTimestamp = timestamp.getAdjustedLowerRangeValue();
            final var maxTimestamp = timestamp.adjustUpperBound();

            final var optimalLowerBound = getFirstDayOfMonth(maxTimestamp, -1);
            minTimestamp = Math.max(minTimestamp, optimalLowerBound);

            projection = entityRepository.getSupplyHistory(unreleasedSupplyAccountIds, minTimestamp, maxTimestamp);
        }

        if (projection.getConsensusTimestamp() == 0L) {
            throw new EntityNotFoundException("Network supply not found");
        }

        final var releasedSupply = NetworkSupply.TOTAL_SUPPLY - projection.getUnreleasedSupply();
        return new NetworkSupply(releasedSupply, projection.getConsensusTimestamp(), NetworkSupply.TOTAL_SUPPLY);
    }

    private long getFirstDayOfMonth(long timestamp, int monthOffset) {
        final var instant = Instant.ofEpochSecond(timestamp / 1_000_000_000);
        final var dateTime = instant.atZone(ZoneOffset.UTC);
        final var firstDay = dateTime.plusMonths(monthOffset).withDayOfMonth(1);

        return firstDay.toLocalDate().atStartOfDay(ZoneOffset.UTC).toEpochSecond() * 1_000_000_000L;
    }
}
