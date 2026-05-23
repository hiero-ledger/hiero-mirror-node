// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Internal, normalized representation of a per-address state override.
 * Produced from a {@link StateOverride} after address normalization and slot-key normalization.
 * {@code state} and {@code stateDiff} are Maps for efficient slot lookups by the KV state layer.
 */
@Value
@Builder
public class NormalizedStateOverride {

    String balance;
    String nonce;
    String code;

    /**
     * Full storage replacement map (normalized slot → value). Mutually exclusive with {@link #stateDiff}.
     */
    Map<String, String> state;

    /**
     * Storage patch map (normalized slot → value). Mutually exclusive with {@link #state}.
     */
    Map<String, String> stateDiff;
}
