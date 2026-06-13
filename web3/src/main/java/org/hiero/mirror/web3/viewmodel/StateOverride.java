// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.ADDRESS_LENGTH;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;
import org.hiero.mirror.web3.validation.Hex;

/**
 * Per-address state override for {@code /api/v1/contracts/call}. All fields are optional except {@code address}.
 * {@code state} and {@code state_diff} are mutually exclusive.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StateOverride {

    /** EVM address (40 hex characters, optional {@code 0x} prefix) of the account to override. */
    @NotNull
    @Hex(minLength = ADDRESS_LENGTH, maxLength = ADDRESS_LENGTH)
    private String address;

    /** Hex-encoded balance override in tinybars (Hedera's smallest denomination). */
    @Hex
    private String balance;

    /** Hex-encoded runtime bytecode override. */
    @Hex
    private String code;

    /** Hex-encoded Ethereum nonce override. */
    @Hex
    private String nonce;

    /**
     * Full storage replacement: list of slot key-value pairs.
     * All existing storage for this address is discarded; only these slots exist.
     * Mutually exclusive with {@link #stateDiff}.
     */
    @Valid
    private List<@Valid StorageEntry> state;

    /**
     * Storage patch: list of slot key-value pairs.
     * Only the listed slots are overridden; all other slots fall through to the underlying state.
     * Mutually exclusive with {@link #state}.
     */
    @JsonProperty("state_diff")
    @Valid
    private List<@Valid StorageEntry> stateDiff;

    @AssertTrue(message = "state and state_diff are mutually exclusive")
    private boolean hasValidStorage() {
        return isEmpty(state) || isEmpty(stateDiff);
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
