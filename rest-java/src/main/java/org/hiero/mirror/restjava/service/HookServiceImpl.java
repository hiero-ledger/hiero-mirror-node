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
        if (isHistorical(request)) {
            return getHookStorageChange(request);
        }

        final var ownerId = entityService.lookup(request.getOwnerId());

        final var page = pageRequest(request, false);
        final var keys = request.getKeys();
        if (keys.isEmpty()) {
            final var hookStorage = hookStorageRepository.findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
                    ownerId.getId(), request.getHookId(), request.getKeyLowerBound(), request.getKeyUpperBound(), page);

            return new HookStorageResult(ownerId, hookStorage);
        }

        final var keysInRange = getKeysInRange(keys, request.getKeyLowerBound(), request.getKeyUpperBound());

        if (keysInRange.isEmpty()) {
            return new HookStorageResult(ownerId, List.of());
        }

        final var hookStorage = hookStorageRepository.findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
                ownerId.getId(), request.getHookId(), keysInRange, page);

        return new HookStorageResult(ownerId, hookStorage);
    }

    private HookStorageResult getHookStorageChange(HookStorageRequest request) {
        final var page = pageRequest(request, true);

        final var ownerId = entityService.lookup(request.getOwnerId());
        final long hookId = request.getHookId();

        final byte[] keyLowerBound = request.getKeyLowerBound();
        final byte[] keyUpperBound = request.getKeyUpperBound();

        final var keys = request.getKeys();
        final boolean requestHasKeys = !keys.isEmpty();

        final var keysInRange = requestHasKeys ? getKeysInRange(keys, keyLowerBound, keyUpperBound) : List.of();

        if (keysInRange.isEmpty() && requestHasKeys) {
            return new HookStorageResult(ownerId, List.of());
        }

        final var timestamp = request.getTimestamp();
        final long timestampLowerBound = timestamp.getAdjustedLowerRangeValue();
        final long timestampUpperBound = timestamp.adjustUpperBound();

        List<HookStorageChange> changes;

        if (requestHasKeys) {
            changes = hookStorageChangeRepository.findByKeyInAndTimestampBetween(
                    ownerId.getId(), hookId, keys, timestampLowerBound, timestampUpperBound, page);
        } else {
            changes = hookStorageChangeRepository.findByKeyBetweenAndTimestampBetween(
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
                changes.stream().map(c -> new HookStorage().hookStorage(c)).toList());
    }

    /**
     * Checks if request has timestamp parameter. If present - historical call should be made,
     * if not - call to the current state should be initiated.
     */
    private boolean isHistorical(HookStorageRequest request) {
        return !request.getTimestamp().isEmpty();
    }

    private List<byte[]> getKeysInRange(Collection<byte[]> keys, byte[] lower, byte[] upper) {
        return keys.stream()
                .filter(key -> Arrays.compareUnsigned(key, lower) >= 0 && Arrays.compareUnsigned(key, upper) <= 0)
                .toList();
    }

    private PageRequest pageRequest(HookStorageRequest request, boolean historical) {
        Sort sort;

        if (historical) {
            sort = Sort.by(
                    new Sort.Order(request.getOrder(), Constants.KEY),
                    new Sort.Order(Direction.DESC, CONSENSUS_TIMESTAMP));
        } else {
            sort = Sort.by(request.getOrder(), Constants.KEY);
        }

        return PageRequest.of(0, request.getLimit(), sort);
    }
}
