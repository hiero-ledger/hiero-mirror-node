// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.config.NetworkProperties;
import org.hiero.mirror.restjava.dto.NetworkNodeDto;
import org.hiero.mirror.restjava.dto.NetworkNodeRequest;
import org.hiero.mirror.restjava.dto.NetworkSupply;
import org.hiero.mirror.restjava.repository.AccountBalanceRepository;
import org.hiero.mirror.restjava.repository.EntityRepository;
import org.hiero.mirror.restjava.repository.NetworkNodeRepository;
import org.hiero.mirror.restjava.repository.NetworkStakeRepository;

@Named
@RequiredArgsConstructor
final class NetworkServiceImpl implements NetworkService {

    private final AccountBalanceRepository accountBalanceRepository;
    private final EntityRepository entityRepository;
    private final NetworkStakeRepository networkStakeRepository;
    private final NetworkProperties networkProperties;
    private final NetworkNodeRepository networkNodeRepository;

    @Override
    public NetworkStake getLatestNetworkStake() {
        return networkStakeRepository
                .findLatest()
                .orElseThrow(() -> new EntityNotFoundException("No network stake data found"));
    }

    @Override
    public NetworkSupply getSupply(Bound timestamp) {
        final NetworkSupply networkSupply;

        final var bounds = networkProperties.getUnreleasedSupplyRangeBounds();
        final var lowerBounds = bounds.lowerBounds();
        final var upperBounds = bounds.upperBounds();

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

    @Override
    public List<NetworkNodeDto> getNetworkNodes(NetworkNodeRequest request) {
        final var fileId = request.getFileId().value();
        final var limit = request.getEffectiveLimit();
        final var nodeIdParams = request.getNodeIds();
        final var orderDirection = request.getOrder().name();

        final Set<Long> nodeIds = new HashSet<>();
        long lowerBound = 0L;
        long upperBound = Long.MAX_VALUE;

        for (final var nodeIdParam : nodeIdParams) {
            if (nodeIdParam.operator() == RangeOperator.EQ) {
                nodeIds.add(nodeIdParam.value());
            } else if (nodeIdParam.hasLowerBound()) {
                lowerBound = Math.max(lowerBound, nodeIdParam.getInclusiveValue());
            } else if (nodeIdParam.hasUpperBound()) {
                upperBound = Math.min(upperBound, nodeIdParam.getInclusiveValue());
            }
        }

        if (lowerBound > upperBound) {
            throw new IllegalArgumentException("Invalid range for : node.id");
        }

        final Long[] nodeIdArray;
        if (!nodeIds.isEmpty()) {
            if (lowerBound > 0L || upperBound < Long.MAX_VALUE) {
                final var filteredNodeIds = new HashSet<Long>();
                for (final var nodeId : nodeIds) {
                    if (nodeId >= lowerBound && nodeId <= upperBound) {
                        filteredNodeIds.add(nodeId);
                    }
                }
                if (filteredNodeIds.isEmpty()) {
                    return List.of();
                }
                nodeIdArray = filteredNodeIds.toArray(Long[]::new);
            } else {
                nodeIdArray = nodeIds.toArray(Long[]::new);
            }
        } else {
            nodeIdArray = new Long[0];
        }

        return networkNodeRepository.findNetworkNodes(
                fileId, nodeIdArray, lowerBound, upperBound, orderDirection, limit);
    }
}
