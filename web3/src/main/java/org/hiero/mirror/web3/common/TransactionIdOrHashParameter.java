// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.common;

import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.springframework.util.StringUtils;

public sealed interface TransactionIdOrHashParameter permits TransactionHashParameter, TransactionIdParameter {

    static TransactionIdOrHashParameter valueOf(String transactionIdOrHash) throws InvalidParametersException {
        if (!StringUtils.hasText(transactionIdOrHash)) {
            throw new InvalidParametersException("Missing transaction ID or hash");
        }

        TransactionIdOrHashParameter parameter;
        if ((parameter = TransactionHashParameter.valueOf(transactionIdOrHash)) != null) {
            return parameter;
        } else if ((parameter = TransactionIdParameter.valueOf(transactionIdOrHash)) != null) {
            return parameter;
        } else {
            throw new InvalidParametersException("Unsupported ID format: '%s'".formatted(transactionIdOrHash));
        }
    }
}
