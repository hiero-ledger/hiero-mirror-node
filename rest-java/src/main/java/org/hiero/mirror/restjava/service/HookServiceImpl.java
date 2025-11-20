// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.hiero.mirror.restjava.common.Constants.CONSENSUS_TIMESTAMP;

import jakarta.inject.Named;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.hiero.mirror.restjava.common.Constants;
import org.hiero.mirror.restjava.dto.HookStorageRequest;
import org.hiero.mirror.restjava.dto.HookStorageResult;
import org.hiero.mirror.restjava.dto.HooksRequest;
import org.hiero.mirror.restjava.repository.HookRepository;
import org.hiero.mirror.restjava.repository.HookStorageChangeRepository;
import org.hiero.mirror.restjava.repository.HookStorageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@Named
@RequiredArgsConstructor
final class HookServiceImpl implements HookService {

    private static final String HOOK_ID = "hookId";

    private final HookRepository hookRepository;
    private final HookStorageRepository hookStorageRepository;
    private final HookStorageChangeRepository hookStorageChangeRepository;
    private final EntityService entityService;

    @Override
    public Collection<Hook> getHooks(HooksRequest request) {
        final var sort = Sort.by(request.getOrder(), HOOK_ID);
        final var page = PageRequest.of(0, request.getLimit(), sort);
        final var id = entityService.lookup(request.getOwnerId());
        final long lowerBound = request.getLowerBound();
        final long upperBound = request.getUpperBound();

        if (request.getHookIds().isEmpty()) {
            return hookRepository.findByOwnerIdAndHookIdBetween(id.getId(), lowerBound, upperBound, page);
        } else {
            // Both equal and range filters are present.
            final var idsInRange = request.getHookIds().stream()
                    .filter(hookId -> hookId >= lowerBound && hookId <= upperBound)
                    .toList();

            if (idsInRange.isEmpty()) {
                return List.of();
            }

            return hookRepository.findByOwnerIdAndHookIdIn(id.getId(), idsInRange, page);
        }
    }

    @Override
    public HookStorageResult getHookStorage(HookStorageRequest request) {
        if (!request.getTimestamp().isEmpty()) {
            return getHookStorageChange(request);
        }

        final var sort = Sort.by(request.getOrder(), Constants.KEY);
        final var page = PageRequest.of(0, request.getLimit(), sort);

        final var ownerId = entityService.lookup(request.getOwnerId());
        final var keys = request.getKeys();
        if (keys.isEmpty()) {
            final var queryResult = hookStorageRepository.findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
                    ownerId.getId(), request.getHookId(), request.getKeyLowerBound(), request.getKeyUpperBound(), page);

            return new HookStorageResult(ownerId, queryResult);
        }

        final var filteredKeys = filterKeysInRange(keys, request.getKeyLowerBound(), request.getKeyUpperBound());

        if (filteredKeys.isEmpty()) {
            return new HookStorageResult(ownerId, List.of());
        }

        final var queryResult = hookStorageRepository.findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
                ownerId.getId(), request.getHookId(), filteredKeys, page);

        return new HookStorageResult(ownerId, queryResult);
    }

    private HookStorageResult getHookStorageChange(HookStorageRequest request) {
        final var sort = Sort.by(
                new Sort.Order(request.getOrder(), Constants.KEY), new Sort.Order(Direction.DESC, CONSENSUS_TIMESTAMP));

        final var page = PageRequest.of(0, request.getLimit(), sort);

        final var ownerId = entityService.lookup(request.getOwnerId());
        final long hookId = request.getHookId();

        final byte[] keyLowerBound = request.getKeyLowerBound();
        final byte[] keyUpperBound = request.getKeyUpperBound();

        final var keys = request.getKeys();
        final var filteredKeys = keys.isEmpty() ? List.of() : filterKeysInRange(keys, keyLowerBound, keyUpperBound);

        if (filteredKeys.isEmpty() && !keys.isEmpty()) {
            return new HookStorageResult(ownerId, List.of());
        }

        final long timestampLowerBound = request.getTimestamp().getAdjustedLowerRangeValue();
        final long timestampUpperBound = request.getTimestamp().adjustUpperBound();

        List<HookStorageChange> results;

        if (!keys.isEmpty()) {
            results = hookStorageChangeRepository.findByKeyInAndTimestampBetween(
                    ownerId.getId(), hookId, keys, timestampLowerBound, timestampUpperBound, page);
        } else {
            results = hookStorageChangeRepository.findByKeyBetweenAndTimestampBetween(
                    ownerId.getId(),
                    hookId,
                    keyLowerBound,
                    keyUpperBound,
                    timestampLowerBound,
                    timestampUpperBound,
                    page);
        }

        return new HookStorageResult(
                ownerId,
                results.stream().map(c -> new HookStorage().hookStorage(c)).toList());
    }

    private List<byte[]> filterKeysInRange(Collection<byte[]> keys, byte[] lower, byte[] upper) {
        return keys.stream()
                .filter(key -> Arrays.compareUnsigned(key, lower) >= 0 && Arrays.compareUnsigned(key, upper) <= 0)
                .toList();
    }
}
