// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Range;
import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.config.NetworkProperties;
import org.hiero.mirror.restjava.dto.NetworkNodeDto;
import org.hiero.mirror.restjava.dto.NetworkNodeRequest;
import org.hiero.mirror.restjava.dto.NetworkSupply;
import org.hiero.mirror.restjava.parameter.EntityIdRangeParameter;
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
        // fileId has a default value of 102, so it's always present
        final var fileId = request.getFileId().value();
        // Use effective limit (capped at MAX_LIMIT) to match rest module behavior
        final var limit = request.getEffectiveLimit();
        final var nodeIdParams = request.getNodeId();

        final var equalitySet = new HashSet<Long>();
        final var rangeSet = new HashSet<EntityIdRangeParameter>();
        nodeIdParams.forEach(p -> {
            if (RangeOperator.EQ.equals(p.operator())) {
                equalitySet.add(p.value());
            } else {
                rangeSet.add(p);
            }
        });

        final var rangeBounds = combineOverlappingRanges(rangeSet);
        final var orderDirection = request.getOrder().name();

        // If both equality and range filters are present, validate overlap
        final Long[] nodeIds;
        if (!equalitySet.isEmpty() && !rangeSet.isEmpty()) {
            // Both equal and range filters are present - filter equality IDs to those within range
            final var idsInRange = equalitySet.stream()
                    .filter(nodeId -> nodeId >= rangeBounds.getMinimum() && nodeId <= rangeBounds.getMaximum())
                    .toArray(Long[]::new);

            if (idsInRange.length == 0) {
                return List.of(); // No overlap between equality and range filters
            }
            nodeIds = idsInRange;
        } else {
            nodeIds = equalitySet.isEmpty() ? new Long[0] : equalitySet.toArray(Long[]::new);
        }

        return networkNodeRepository.findNetworkNodes(
                fileId, nodeIds, rangeBounds.getMinimum(), rangeBounds.getMaximum(), orderDirection, limit);
    }

    private Range<Long> combineOverlappingRanges(Collection<EntityIdRangeParameter> rangeSet) {

        if (rangeSet.isEmpty()) {
            return Range.of(0L, Long.MAX_VALUE);
        }

        // Calculate lower bound: max of (value+1 for GT, value for GTE) — most restrictive lower bound
        Long lowerBound = rangeSet.stream()
                .filter(x -> x.operator() == RangeOperator.GTE || x.operator() == RangeOperator.GT)
                .map(x -> x.operator() == RangeOperator.GT ? x.value() + 1 : x.value())
                .max(Comparator.naturalOrder())
                .orElse(0L);

        // Calculate upper bound: min of (value-1 for LT, value for LTE) — most restrictive upper bound
        Long upperBound = rangeSet.stream()
                .filter(x -> x.operator() == RangeOperator.LTE || x.operator() == RangeOperator.LT)
                .map(x -> x.operator() == RangeOperator.LT ? x.value() - 1 : x.value())
                .min(Comparator.naturalOrder())
                .orElse(Long.MAX_VALUE);

        // Validate that the range is not empty (e.g., gt:4 AND lt:5)
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException("Invalid range for : node.id");
        }

        return Range.of(lowerBound, upperBound);
    }
}
