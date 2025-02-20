/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
