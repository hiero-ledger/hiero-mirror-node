// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.domain.EntityIdService;

@Named
@RequiredArgsConstructor
class TokenRejectTransactionHandler extends AbstractTransactionHandler {

    private final EntityIdService entityIdService;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        var tokenReject = recordItem.getTransactionBody().getTokenReject();
        return tokenReject.hasOwner()
                ? entityIdService.lookup(tokenReject.getOwner()).orElse(EntityId.EMPTY)
                : recordItem.getPayerAccountId();
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENREJECT;
    }
}
