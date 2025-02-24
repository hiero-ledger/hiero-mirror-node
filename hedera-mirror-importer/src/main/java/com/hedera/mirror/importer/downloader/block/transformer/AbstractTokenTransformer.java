// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_TOKENS;

import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.List;

abstract class AbstractTokenTransformer extends AbstractBlockItemTransformer {

    void updateTotalSupply(List<StateChanges> stateChangesList, TransactionRecord.Builder transactionRecordBuilder) {
        for (var stateChanges : stateChangesList) {
            for (var stateChange : stateChanges.getStateChangesList()) {
                if (stateChange.getStateId() == STATE_ID_TOKENS.getNumber()
                        && stateChange.hasMapUpdate()
                        && stateChange.getMapUpdate().hasValue()
                        && stateChange.getMapUpdate().getValue().hasTokenValue()) {
                    var value = stateChange
                            .getMapUpdate()
                            .getValue()
                            .getTokenValue()
                            .getTotalSupply();
                    transactionRecordBuilder.getReceiptBuilder().setNewTotalSupply(value);
                    return;
                }
            }
        }
    }
}
