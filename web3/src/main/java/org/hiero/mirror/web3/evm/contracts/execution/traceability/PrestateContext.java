// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.hiero.mirror.web3.service.model.PrestateRequest;
import org.hyperledger.besu.datatypes.Address;

/**
 * Properties for tracing prestate
 */
@Data
@Builder(toBuilder = true)
@AllArgsConstructor
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

    /**
     * The account addresses that were referenced during the transaction execution
     */
    private Set<Address> touchedAccounts = new HashSet<>();

    /**
     * The contract storage keys per contract that were referenced during the transaction execution
     */
    private Map<Address, Set<String>> touchedStorageKeys = new HashMap<>();

    public PrestateContext(final PrestateRequest prestateRequest) {
        this.code = prestateRequest.isCode();
        this.diff = prestateRequest.isDiffMode();
        this.storage = prestateRequest.isStorage();
    }

    public void setTouchedStorage(final Address contract, final String slotKey) {
        var touchedStorages = touchedStorageKeys.get(contract);

        if (touchedStorages == null) {
            touchedStorages = new HashSet<>();
            touchedStorages.add(slotKey);
        } else {
            touchedStorages.add(slotKey);
        }

        touchedStorageKeys.put(contract, touchedStorages);
    }
}
