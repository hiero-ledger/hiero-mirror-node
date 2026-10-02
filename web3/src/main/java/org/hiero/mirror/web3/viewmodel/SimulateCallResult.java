// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SimulateCallResult(
        @JsonProperty("gas_used") String gasUsed,
        List<SimulateLog> logs,
        @JsonProperty("return_data") String returnData,
        String status) {}
