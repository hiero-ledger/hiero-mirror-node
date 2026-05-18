// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
import java.util.Map;
import lombok.Data;
import org.hiero.mirror.web3.validation.Hex;

/**
 * Per-address state override for {@code /api/v1/contracts/call}, matching the {@code eth_call} JSON-RPC state override
 * set. All fields are optional. {@code state} and {@code stateDiff} are mutually exclusive.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StateOverride {

    /** Hex-encoded balance override in tinybars (Hedera's smallest denomination). */
    @Hex
    private String balance;

    /** Hex-encoded Ethereum nonce override. */
    @Hex
    private String nonce;

    /** Hex-encoded runtime bytecode override. */
    @Hex
    private String code;

    /**
     * Full storage replacement: maps hex storage slot to hex value.
     * All existing storage for this address is discarded; only these slots exist.
     * Mutually exclusive with {@link #stateDiff}.
     */
    private Map<@Hex String, @Hex String> state;

    /**
     * Storage patch: maps hex storage slot to hex value.
     * Only the listed slots are overridden; all other slots fall through to the underlying state.
     * Mutually exclusive with {@link #state}.
     */
    private Map<@Hex String, @Hex String> stateDiff;

    @AssertTrue(message = "state and stateDiff are mutually exclusive")
    private boolean hasValidStorage() {
        return state == null || stateDiff == null;
    }
}
