// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.transaction;

import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.mirror.common.domain.StreamItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import java.util.List;
import java.util.Map;
import lombok.Builder;

@Builder(toBuilder = true)
public record BlockItem(
        long consensusTimestamp,
        Transaction transaction,
        TransactionResult transactionResult,
        Map<TransactionCase, TransactionOutput> transactionOutputs,
        List<StateChanges> stateChanges,
        BlockItem parent,
        Long parentConsensusTimestamp,
        BlockItem previous,
        boolean successful)
        implements StreamItem {

    public BlockItem {
        consensusTimestamp = DomainUtils.timestampInNanosMax(transactionResult.getConsensusTimestamp());
        parentConsensusTimestamp = transactionResult.hasParentConsensusTimestamp()
                ? DomainUtils.timestampInNanosMax(transactionResult.getParentConsensusTimestamp())
                : null;
        parent = parseParent(parentConsensusTimestamp, previous);
        successful = parseSuccess(transactionResult, parent);
    }

    private BlockItem parseParent(Long parentConsensusTimestamp, BlockItem previous) {
        // set parent, parent-child items are assured to exist in sequential order of [Parent, Child1,..., ChildN]
        if (parentConsensusTimestamp != null && previous != null) {
            if (parentConsensusTimestamp == previous.consensusTimestamp()) {
                return previous;
            } else if (previous.parent != null && parentConsensusTimestamp == previous.parent.consensusTimestamp()) {
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
