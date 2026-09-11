// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.utils.ByteUtils.wrapToWordSize;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.controller.PrestateProperties;
import org.hiero.mirror.web3.service.model.PrestateRequest;
import org.jspecify.annotations.Nullable;

/**
 * Scratch state while collecting touched accounts and emitting a prestate trace.
 */
@RequiredArgsConstructor
@Getter
final class PrestateContext {

    private final PrestateProperties prestateProperties;
    private final Set<Long> accounts = new HashSet<>();
    private final Set<Long> createdIds = new HashSet<>();
    private final Map<Long, Long> balanceTransfers = new HashMap<>();
    private final Map<Long, Long> nonceDeltas = new HashMap<>();
    private final Map<Long, Long> postNonces = new HashMap<>();

    private final long consensusTimestamp;

    private final PrestateRequest prestateRequest;
    private final Map<Long, Map<String, String>> preStorageByContract = new TreeMap<>();
    private final Map<Long, Map<String, String>> postStorageByContract = new TreeMap<>();

    boolean isFull() {
        return accounts.size() >= prestateProperties.getMaxTouchedAccounts();
    }

    public void addAccount(@Nullable final EntityId accountId) {
        if (EntityId.isEmpty(accountId) || isFull()) {
            return;
        }
        accounts.add(accountId.getId());
    }

    public void markCreated(final long accountId) {
        if (accountId == 0L) {
            return;
        }
        if (!accounts.contains(accountId) && isFull()) {
            return;
        }
        accounts.add(accountId);
        createdIds.add(accountId);
        postNonces.put(accountId, 0L);
    }

    public void addBalanceTransfer(final long accountId, final long value) {
        balanceTransfers.merge(accountId, value, Long::sum);
    }

    public void addNonceDelta(final long accountId, final long delta) {
        nonceDeltas.merge(accountId, delta, Long::sum);
    }

    public void putPostNonce(final long accountId, final long nonce) {
        postNonces.put(accountId, nonce);
    }

    long postNonce(final long accountId) {
        return postNonces.getOrDefault(accountId, 0L);
    }

    long preNonce(final long accountId) {
        return Math.max(0L, postNonce(accountId) - nonceDeltas.getOrDefault(accountId, 0L));
    }

    public void addPreStorageSlot(final long contractId, final byte[] slot, final byte @Nullable [] value) {
        addStorageSlot(preStorageByContract, contractId, slot, value);
    }

    public void addPostStorageSlot(final long contractId, final byte[] slot, final byte @Nullable [] value) {
        addStorageSlot(postStorageByContract, contractId, slot, value);
    }

    private static void addStorageSlot(
            final Map<Long, Map<String, String>> storageByContract,
            final long contractId,
            final byte[] slot,
            final byte @Nullable [] value) {
        if (isEmptyStorageValue(value)) {
            return;
        }
        storageByContract
                .computeIfAbsent(contractId, id -> new TreeMap<>())
                .put(wrapToWordSize(slot), wrapToWordSize(value));
    }

    private static boolean isEmptyStorageValue(final byte @Nullable [] value) {
        if (value == null || value.length == 0) {
            return true;
        }
        for (final byte element : value) {
            if (element != 0) {
                return false;
            }
        }
        return true;
    }
}
