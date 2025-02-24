// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.contracts.execution;

import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;

public interface MirrorEvmTxProcessor {

    HederaEvmTransactionProcessingResult execute(final CallServiceParameters params, final long estimatedGas);
}
