// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
public class TransactionHandlerFactory {

    private final Map<TransactionType, TransactionHandler> transactionHandlers;
    private final TransactionHandler defaultTransactionHandler;

    TransactionHandlerFactory(List<TransactionHandler> transactionHandlers) {
        this.transactionHandlers = transactionHandlers.stream()
                .collect(Collectors.toUnmodifiableMap(TransactionHandler::getType, Function.identity()));
        this.defaultTransactionHandler = this.transactionHandlers.get(TransactionType.UNKNOWN);
    }

    public TransactionHandler get(TransactionType transactionType) {
        return transactionHandlers.getOrDefault(transactionType, defaultTransactionHandler);
    }
}
