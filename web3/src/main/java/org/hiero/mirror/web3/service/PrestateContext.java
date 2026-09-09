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
import org.hiero.mirror.web3.service.model.PrestateRequest;

/**
 * Properties for tracing prestate
 */
@RequiredArgsConstructor
@Getter
final class PrestateContext {

    private final Set<Long> accounts = new HashSet<>();
    private final Map<Long, Long> balanceTransfers = new HashMap<>();

    private final long consensusTimestamp;

    private final PrestateRequest prestateRequest;
    private final Map<Long, Map<String, String>> preStorageByContract = new TreeMap<>();
    private final Map<Long, Map<String, String>> postStorageByContract = new TreeMap<>();

    public void addAccount(final EntityId accountId) {
        if (!EntityId.isEmpty(accountId)) {
            accounts.add(accountId.getId());
        }
    }

    public void addBalanceTransfer(final long accountId, final long value) {
        balanceTransfers.merge(accountId, value, Long::sum);
    }

    public void addPreStorageSlot(final long contractId, final byte[] slot, final byte[] value) {
        if (value == null) {
            return;
        }
        preStorageByContract
                .computeIfAbsent(contractId, id -> new TreeMap<>())
                .put(wrapToWordSize(slot), wrapToWordSize(value));
    }

    public void addPostStorageSlot(final long contractId, final byte[] slot, final byte[] value) {
        if (value == null) {
            return;
        }
        postStorageByContract
                .computeIfAbsent(contractId, id -> new TreeMap<>())
                .put(wrapToWordSize(slot), wrapToWordSize(value));
    }
}
