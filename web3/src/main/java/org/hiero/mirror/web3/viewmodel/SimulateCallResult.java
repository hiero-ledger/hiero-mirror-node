// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The result of a single simulated call. {@code status} is {@code "0x1"} on success or {@code "0x0"} on revert; a
 * reverted call does not abort the rest of the batch.
 */
public record SimulateCallResult(
        @JsonProperty("gas_used") String gasUsed,
        List<SimulateLog> logs,
        @JsonProperty("return_data") String returnData,
        String status) {}
