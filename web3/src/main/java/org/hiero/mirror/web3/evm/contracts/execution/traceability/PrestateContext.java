// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.hiero.mirror.web3.service.model.PrestateRequest;

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

    public PrestateContext(final PrestateRequest prestateRequest) {
        this.code = prestateRequest.isCode();
        this.diff = prestateRequest.isDiffMode();
        this.storage = prestateRequest.isStorage();
    }
}
