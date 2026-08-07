// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A single log entry within a {@link SimulateCallResult}, matching HIP-1485's {@code eth_simulatev1} example shape
 * (not the existing {@code ContractResultLog}/{@code ContractLog} schemas, which use {@code index} instead of
 * {@code log_index} and lack {@code removed}).
 */
public record SimulateLog(
        String address,
        @JsonProperty("block_hash") String blockHash,
        @JsonProperty("block_number") String blockNumber,
        String data,
        @JsonProperty("log_index") String logIndex,
        boolean removed,
        List<String> topics,
        @JsonProperty("transaction_hash") String transactionHash,
        @JsonProperty("transaction_index") String transactionIndex) {}
