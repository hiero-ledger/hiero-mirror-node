// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
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
import org.web3j.utils.Numeric;

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
            return new HookStorageResult(
                    ownerId,
                    hookStorageRepository.findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
                            ownerId.getId(),
                            request.getHookId(),
                            request.getKeyLowerBound(),
                            request.getKeyUpperBound(),
                            page));
        }

        final var keyBytesList =
                keys.stream().map(Numeric::hexStringToByteArray).toList();

        final var filteredKeys =
                filterKeysInRange(keyBytesList, request.getKeyLowerBound(), request.getKeyUpperBound());

        if (filteredKeys.isEmpty()) {
            return new HookStorageResult(ownerId, List.of());
        }
        return new HookStorageResult(
                ownerId,
                hookStorageRepository.findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
                        ownerId.getId(), request.getHookId(), filteredKeys, page));
    }

    @Override
    public HookStorageResult getHookStorageChange(HookStorageRequest request) {
        final var sort = Sort.by(request.getOrder(), Constants.KEY);
        final var page = PageRequest.of(0, request.getLimit(), sort);

        final var ownerId = entityService.lookup(request.getOwnerId());
        final long hookId = request.getHookId();

        var keyEqualsList =
                request.getKeys().stream().map(Numeric::hexStringToByteArray).toList();
        final byte[] keyLowerBound = request.getKeyLowerBound();
        final byte[] keyUpperBound = request.getKeyUpperBound();

        final var filteredKeys =
                keyEqualsList.isEmpty() ? List.of() : filterKeysInRange(keyEqualsList, keyLowerBound, keyUpperBound);

        if (filteredKeys.isEmpty() && !keyEqualsList.isEmpty()) {
            return new HookStorageResult(ownerId, List.of());
        }

        final long timestampLowerBound = request.getTimestampLowerBound();
        final long timestampUpperBound = request.getTimestampUpperBound();

        final boolean sortDesc = sort.getOrderFor(Constants.KEY) != null
                && sort.getOrderFor(Constants.KEY).isDescending();

        List<HookStorageChange> results;

        if (!keyEqualsList.isEmpty()) {
            results = fetchLatestByKeySorted(
                    sortDesc,
                    () -> hookStorageChangeRepository.findLatestChangePerKeyInTimestampRangeForKeysOrderByKeyAsc(
                            ownerId.getId(), hookId, keyEqualsList, timestampLowerBound, timestampUpperBound, page),
                    () -> hookStorageChangeRepository.findLatestChangePerKeyInTimestampRangeForKeysOrderByKeyDesc(
                            ownerId.getId(), hookId, keyEqualsList, timestampLowerBound, timestampUpperBound, page));
        } else {
            results = fetchLatestByKeySorted(
                    sortDesc,
                    () -> hookStorageChangeRepository.findLatestChangePerKeyInTimestampRangeForKeyRangeOrderByKeyAsc(
                            ownerId.getId(),
                            hookId,
                            keyLowerBound,
                            keyUpperBound,
                            timestampLowerBound,
                            timestampUpperBound,
                            page),
                    () -> hookStorageChangeRepository.findLatestChangePerKeyInTimestampRangeForKeyRangeOrderByKeyDesc(
                            ownerId.getId(),
                            hookId,
                            keyLowerBound,
                            keyUpperBound,
                            timestampLowerBound,
                            timestampUpperBound,
                            page));
        }

        return new HookStorageResult(
                ownerId,
                results.stream().map(c -> new HookStorage().hookStorage(c)).toList());
    }

    private List<byte[]> filterKeysInRange(List<byte[]> keys, byte[] lower, byte[] upper) {
        final var lowerBound = new BigInteger(1, lower);
        final var upperBound = new BigInteger(1, upper);
        return keys.stream()
                .filter(k -> {
                    final var keyAsBigInt = new BigInteger(1, k);
                    return keyAsBigInt.compareTo(lowerBound) >= 0 && keyAsBigInt.compareTo(upperBound) <= 0;
                })
                .toList();
    }

    private <T extends Comparable<T>> List<T> filterTimestampsInRange(List<T> values, T lower, T upper) {
        return values.stream()
                .filter(v -> v.compareTo(lower) >= 0 && v.compareTo(upper) <= 0)
                .toList();
    }

    private <T> List<T> fetchLatestByKeySorted(
            boolean descending, Supplier<List<T>> ascSupplier, Supplier<List<T>> descSupplier) {
        return descending ? descSupplier.get() : ascSupplier.get();
    }
}
