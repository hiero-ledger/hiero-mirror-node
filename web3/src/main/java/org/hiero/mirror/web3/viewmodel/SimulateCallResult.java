// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SimulateCallResult(
        SimulateCallError error,
        @JsonProperty("gas_used") long gasUsed,
        List<SimulateLog> logs,
        @JsonProperty("return_data") String returnData,
        boolean success) {}
