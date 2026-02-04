// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.config.NetworkProperties;
import org.hiero.mirror.restjava.dto.NetworkNodeData;
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
    private final ObjectMapper objectMapper;

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
    public List<NetworkNodeData> getNetworkNodes(NetworkNodeRequest request) {
        // fileId has a default value of 102, so it's always present
        final var fileId = request.getFileId().entityId().getId();
        final var order = request.getOrder().name();
        // Use effective limit (capped at MAX_LIMIT) to match rest module behavior
        final var limit = request.getEffectiveLimit();
        final var nodeIdParams = request.getNodeId();

        // Create two sets: equality operators and range operators
        // Use empty array when there are no parameters - SQL handles this with array_length check
        final var equalitySet = (nodeIdParams == null || nodeIdParams.isEmpty())
                ? new Long[0]
                : nodeIdParams.stream()
                        .filter(x -> RangeOperator.EQ.equals(x.operator()))
                        .map(EntityIdRangeParameter::value)
                        .toArray(Long[]::new);

        final var rangeSet = (nodeIdParams == null || nodeIdParams.isEmpty())
                ? List.<EntityIdRangeParameter>of()
                : nodeIdParams.stream()
                        .filter(x -> !RangeOperator.EQ.equals(x.operator()))
                        .collect(Collectors.toSet());

        // Always calculate range bounds (defaults to 0L and Long.MAX_VALUE if no range parameters)
        var rangeBounds = combineOverlappingRanges(rangeSet);

        // Query for exact limit (not limit+1) to match Node.js behavior
        // Pagination link is generated when results.size() == limit (optimistic pagination)
        var results = networkNodeRepository.findNetworkNodes(
                fileId, equalitySet, rangeBounds.getLeft(), rangeBounds.getRight(), order, limit);

        return results.stream()
                .map(row -> NetworkNodeData.from(row, objectMapper))
                .toList();
    }

    private Pair<Long, Long> combineOverlappingRanges(Collection<EntityIdRangeParameter> rangeSet) {

        if (rangeSet.isEmpty()) {
            return Pair.of(0L, Long.MAX_VALUE);
        }

        // Calculate lower bound: min of (value+1 for GT, value for GTE)
        Long lowerBound = rangeSet.stream()
                .filter(x -> x.operator() == RangeOperator.GTE || x.operator() == RangeOperator.GT)
                .map(x -> x.operator() == RangeOperator.GT ? x.value() + 1 : x.value())
                .min(Comparator.naturalOrder())
                .orElse(0L);

        // Calculate upper bound: max of (value-1 for LT, value for LTE)
        Long upperBound = rangeSet.stream()
                .filter(x -> x.operator() == RangeOperator.LTE || x.operator() == RangeOperator.LT)
                .map(x -> x.operator() == RangeOperator.LT ? x.value() - 1 : x.value())
                .max(Comparator.naturalOrder())
                .orElse(Long.MAX_VALUE);

        return Pair.of(lowerBound, upperBound);
    }
}
