// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.rest.model.AccountTrace;
import org.hiero.mirror.web3.service.model.PrestateRequest;

/**
 * Properties for tracing prestate
 */
@Getter
public class PrestateContext {

    /**
     * Include storage information
     */
    private final boolean storage;

    /**
     * Include contract bytecode
     */
    private final boolean code;

    /**
     * Include pre and post account traces. By default only the pre collection will be populated.
     */
    private final boolean diff;

    private final long consensusTimestamp;

    private final Set<EntityId> accounts = new LinkedHashSet<>();

    private final Set<Long> createdContractIds = new HashSet<>();

    private final Map<Long, Map<String, String>> preStorageByContract = new HashMap<>();

    private final Map<Long, Map<String, String>> postStorageByContract = new HashMap<>();

    private final Map<Long, byte[]> preBytecodeByContract = new HashMap<>();

    private final Map<Long, byte[]> postBytecodeByContract = new HashMap<>();

    private final Map<String, AccountTrace> preAccountTraces = new LinkedHashMap<>();

    private final Map<String, AccountTrace> postAccountTraces = new LinkedHashMap<>();

    public PrestateContext(final PrestateRequest prestateRequest, final long consensusTimestamp) {
        this.code = prestateRequest.isCode();
        this.diff = prestateRequest.isDiffMode();
        this.storage = prestateRequest.isStorage();
        this.consensusTimestamp = consensusTimestamp;
    }

    public void addAccount(final EntityId accountId) {
        if (!EntityId.isEmpty(accountId)) {
            accounts.add(accountId);
        }
    }

    public void addCreatedContract(final EntityId contractId) {
        if (!EntityId.isEmpty(contractId)) {
            createdContractIds.add(contractId.getId());
            addAccount(contractId);
        }
    }

    public void addPreStorageSlot(final long contractId, final byte[] slot, final byte[] value) {
        if (value == null) {
            return;
        }
        preStorageByContract
                .computeIfAbsent(contractId, id -> new LinkedHashMap<>())
                .put(Bytes.wrap(slot).toHex(), Bytes.wrap(value).toHex());
    }

    public void addPostStorageSlot(final long contractId, final byte[] slot, final byte[] value) {
        if (value == null) {
            return;
        }
        postStorageByContract
                .computeIfAbsent(contractId, id -> new LinkedHashMap<>())
                .put(Bytes.wrap(slot).toHex(), Bytes.wrap(value).toHex());
    }

    public void addPreBytecode(final long contractId, final byte[] bytecode) {
        if (bytecode != null) {
            preBytecodeByContract.put(contractId, bytecode);
        }
    }

    public void addPostBytecode(final long contractId, final byte[] bytecode) {
        if (bytecode != null) {
            postBytecodeByContract.put(contractId, bytecode);
        }
    }

    public boolean isCreatedContract(final long contractId) {
        return createdContractIds.contains(contractId);
    }
}
