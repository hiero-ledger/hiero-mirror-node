// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_TOKENS;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;

@Named
final class TokenCreateTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void updateTransactionRecord(
            BlockItem blockItem, TransactionBody transactionBody, TransactionRecord.Builder transactionRecordBuilder) {
        if (!blockItem.successful()) {
            return;
        }

        for (var stateChanges : blockItem.stateChanges()) {
            for (var stateChange : stateChanges.getStateChangesList()) {
                if (stateChange.getStateId() == STATE_ID_TOKENS.getNumber()
                        && stateChange.hasMapUpdate()
                        && stateChange.getMapUpdate().getKey().hasTokenIdKey()) {
                    var key = stateChange.getMapUpdate().getKey().getTokenIdKey();
                    transactionRecordBuilder.getReceiptBuilder().setTokenID(key);
                    return;
                }
            }
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENCREATION;
    }
}
