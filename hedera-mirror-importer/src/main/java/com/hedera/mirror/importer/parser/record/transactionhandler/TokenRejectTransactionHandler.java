// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.transactionhandler;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.domain.EntityIdService;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

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
