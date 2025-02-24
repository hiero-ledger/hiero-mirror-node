// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.transaction;

import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.mirror.common.domain.StreamItem;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import java.util.List;
import lombok.Builder;

@Builder(toBuilder = true)
public record BlockItem(
        Transaction transaction,
        TransactionResult transactionResult,
        List<TransactionOutput> transactionOutput,
        List<StateChanges> stateChanges,
        BlockItem parent,
        BlockItem previous,
        boolean successful)
        implements StreamItem {

    public BlockItem {
        parent = parseParent(transactionResult, previous);
        successful = parseSuccess(transactionResult, parent);
    }

    private BlockItem parseParent(TransactionResult transactionResult, BlockItem previous) {
        // set parent, parent-child items are assured to exist in sequential order of [Parent, Child1,..., ChildN]
        if (transactionResult.hasParentConsensusTimestamp() && previous != null) {
            var parentTimestamp = transactionResult.getParentConsensusTimestamp();
            if (parentTimestamp.equals(previous.transactionResult.getConsensusTimestamp())) {
                return previous;
            } else if (previous.parent != null
                    && parentTimestamp.equals(previous.parent.transactionResult.getConsensusTimestamp())) {
                // check older siblings parent, if child count is > 1 this prevents having to search to parent
                return previous.parent;
            }
        }
        return this.parent;
    }

    private boolean parseSuccess(TransactionResult transactionResult, BlockItem parent) {
        if (parent != null && !parent.successful()) {
            return false;
        }

        var status = transactionResult.getStatus();
        return status == ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED
                || status == ResponseCodeEnum.SUCCESS
                || status == ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
    }
}
