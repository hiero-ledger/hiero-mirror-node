// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain;

import com.hedera.services.stream.proto.ContractBytecode;
import org.hiero.mirror.common.domain.transaction.RecordItem;

public interface ContractInitcodeService {

    /**
     * Retrieves the contract's initcode.
     *
     * @param contractBytecode The {@link ContractBytecode} in the transaction's sidecar record
     * @param recordItem The transaction's {@link RecordItem}
     * @return The contract's initcode, if found
     */
    byte[] get(ContractBytecode contractBytecode, RecordItem recordItem);
}
