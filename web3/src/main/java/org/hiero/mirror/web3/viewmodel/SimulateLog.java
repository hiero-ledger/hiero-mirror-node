// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SimulateLog(
        String address,
        @JsonProperty("block_hash") String blockHash,
        @JsonProperty("block_number") long blockNumber,
        String data,
        @JsonProperty("log_index") long logIndex,
        boolean removed,
        List<String> topics,
        @JsonProperty("transaction_hash") String transactionHash,
        @JsonProperty("transaction_index") long transactionIndex) {}
