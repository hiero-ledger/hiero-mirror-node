// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;

public record EvmTransactionResult(ResponseCodeEnum responseCodeEnum, ContractFunctionResult functionResult) {}
