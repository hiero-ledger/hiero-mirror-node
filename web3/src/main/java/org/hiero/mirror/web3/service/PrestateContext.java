// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.utils.ByteUtils.wrapToWordSize;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.service.model.PrestateRequest;

/**
 * Properties for tracing prestate
 */
@RequiredArgsConstructor
@Getter
public final class PrestateContext {

    private final Set<Long> accounts = new HashSet<>();

    private final long consensusTimestamp;

    private final PrestateRequest prestateRequest;
    private final Map<Long, Map<String, String>> preStorageByContract = new TreeMap<>();
    private final Map<Long, Map<String, String>> postStorageByContract = new TreeMap<>();

    @Setter
    private List<ContractAction> actions = new ArrayList<>();

    public void addAccount(final EntityId accountId) {
        if (accountId != null) {
            accounts.add(accountId.getId());
        }
    }

    public void addAccount(final Long accountId) {
        if (accountId != null) {
            accounts.add(accountId);
        }
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
