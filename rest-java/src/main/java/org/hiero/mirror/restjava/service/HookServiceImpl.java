// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.hiero.mirror.restjava.common.Constants;
import org.hiero.mirror.restjava.dto.HookStorageRequest;
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
                return Collections.emptyList();
            }

            return hookRepository.findByOwnerIdAndHookIdIn(id.getId(), idsInRange, page);
        }
    }

    @Override
    public Collection<HookStorage> getHookStorage(HookStorageRequest request) {
        final var sort = Sort.by(request.getOrder(), Constants.KEY);
        final var page = PageRequest.of(0, request.getLimit(), sort);

        final var id = entityService.lookup(request.getOwnerId());
        final var keys = request.getKeys();

        if (keys.isEmpty()) {
            return hookStorageRepository.findByOwnerIdAndHookIdAndKeyBetween(
                    id.getId(), request.getHookId(), request.getKeyLowerBound(), request.getKeyUpperBound(), page);
        } else {
            final BigInteger lowerBound = new BigInteger(1, request.getKeyLowerBound());
            final BigInteger upperBound = new BigInteger(1, request.getKeyUpperBound());

            final var idsInRange = keys.stream()
                    .filter(key -> {
                        final var keyAsBigInt = new BigInteger(key, 16);
                        return keyAsBigInt.compareTo(lowerBound) >= 0 && keyAsBigInt.compareTo(upperBound) <= 0;
                    })
                    .map(Numeric::hexStringToByteArray)
                    .toList();

            if (idsInRange.isEmpty()) {
                return Collections.emptyList();
            } else {
                return hookStorageRepository.findByOwnerIdAndHookIdAndKeyIn(
                        id.getId(), request.getHookId(), idsInRange, page);
            }
        }
    }

    @Override
    public Collection<HookStorage> getHookStorageChange(HookStorageRequest request) {

        final var sort = Sort.by(request.getOrder(), Constants.KEY);
        final var page = PageRequest.of(0, request.getLimit(), sort);

        final long ownerId = entityService.lookup(request.getOwnerId()).getId();
        final long hookId = request.getHookId();

        var keyEqualsList =
                request.getKeys().stream().map(Numeric::hexStringToByteArray).toList();

        final byte[] keyLowerBound = request.getKeyLowerBound();
        final byte[] keyUpperBound = request.getKeyUpperBound();

        // Filter keys that are within range
        if (!keyEqualsList.isEmpty()) {
            final var lowerBound = new BigInteger(1, keyLowerBound);
            final var upperBound = new BigInteger(1, keyUpperBound);

            keyEqualsList = keyEqualsList.stream()
                    .filter(key -> {
                        final var keyAsBigInt = new BigInteger(1, key);
                        return keyAsBigInt.compareTo(lowerBound) >= 0 && keyAsBigInt.compareTo(upperBound) <= 0;
                    })
                    .toList();

            if (keyEqualsList.isEmpty()) {
                return List.of();
            }
        }

        var timestampEqualsList = request.getTimestamp().stream().toList();
        final long timestampLowerBound = request.getTimestampLowerBound();
        final long timestampUpperBound = request.getTimestampUpperBound();

        // Filter timestamps that are within range
        if (!timestampEqualsList.isEmpty()) {
            timestampEqualsList = timestampEqualsList.stream()
                    .filter(timestamp -> timestamp >= timestampLowerBound && timestamp <= timestampUpperBound)
                    .toList();

            if (timestampEqualsList.isEmpty()) {
                return List.of();
            }
        }

        List<HookStorageChange> results;

        if (!keyEqualsList.isEmpty()) {
            if (!timestampEqualsList.isEmpty()) {
                results = hookStorageChangeRepository.findByOwnerIdAndHookIdAndKeyInAndConsensusTimestampIn(
                        ownerId, hookId, keyEqualsList, timestampEqualsList, page);
            } else {
                results = hookStorageChangeRepository.findByOwnerIdAndHookIdAndKeyInAndConsensusTimestampBetween(
                        ownerId, hookId, keyEqualsList, timestampLowerBound, timestampUpperBound, page);
            }
        } else {
            if (!timestampEqualsList.isEmpty()) {
                results = hookStorageChangeRepository.findByOwnerIdAndHookIdAndKeyBetweenAndConsensusTimestampIn(
                        ownerId, hookId, keyLowerBound, keyUpperBound, timestampEqualsList, page);
            } else {
                results = hookStorageChangeRepository.findByOwnerIdAndHookIdAndKeyBetweenAndConsensusTimestampBetween(
                        ownerId, hookId, keyLowerBound, keyUpperBound, timestampLowerBound, timestampUpperBound, page);
            }
        }

        return results.stream().map(c -> new HookStorage().hookStorage(c)).toList();
    }
}
