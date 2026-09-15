// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import static org.hiero.mirror.common.util.DomainUtils.NANOS_PER_SECOND;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Constants {
    public static final String BALANCE_OPERATION_NAME = "BALANCE";
    public static final String CALL_URI = "/api/v1/contracts/call";
    public static final String OPCODES_URI = "/api/v1/contracts/results/{transactionIdOrHash}/opcodes";

    /**
     * Maximum window (in nanoseconds) between a transaction's {@code valid_start_ns} and its {@code consensus_timestamp}.
     * Sized as 35 minutes to comfortably cover the network's max transaction valid duration (3 minutes) plus buffer.
     */
    public static final long MAX_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS = 35 * 60 * NANOS_PER_SECOND;
}
