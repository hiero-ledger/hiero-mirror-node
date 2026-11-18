// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/** Transactions that share one read/write cache. */
@Builder
@Value
public class TraceBundle {

    @Builder.Default
    private List<ContractExecutionParameters> transactions = List.of();
}
