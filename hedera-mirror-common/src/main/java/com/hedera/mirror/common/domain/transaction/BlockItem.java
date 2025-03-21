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
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import org.springframework.util.CollectionUtils;

@AllArgsConstructor(access = AccessLevel.NONE)
@Value
public class BlockItem implements StreamItem {

    private final long consensusTimestamp;
    private final BlockItem parent;
    private final Long parentConsensusTimestamp;
    private final BlockItem previous;
    private final List<StateChanges> stateChanges;
    private final boolean successful;
    private final Transaction transaction;

    @Getter(value = AccessLevel.NONE)
    private final Map<TransactionCase, TransactionOutput> transactionOutputs;

    private final TransactionResult transactionResult;

    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    private final StateChangeContext stateChangeContext = createStateChangeContext();

    @Builder(toBuilder = true)
    public BlockItem(
            Transaction transaction,
            TransactionResult transactionResult,
            Map<TransactionCase, TransactionOutput> transactionOutputs,
            List<StateChanges> stateChanges,
            BlockItem previous) {
        this.transaction = transaction;
        this.transactionResult = transactionResult;
        this.transactionOutputs = transactionOutputs;
        this.stateChanges = stateChanges;
        this.previous = previous;

        consensusTimestamp = DomainUtils.timestampInNanosMax(transactionResult.getConsensusTimestamp());
        parentConsensusTimestamp = transactionResult.hasParentConsensusTimestamp()
                ? DomainUtils.timestampInNanosMax(transactionResult.getParentConsensusTimestamp())
                : null;
        parent = parseParent();
        successful = parseSuccess();
    }

    public Optional<TransactionOutput> getTransactionOutput(TransactionCase transactionCase) {
        return Optional.ofNullable(transactionOutputs.get(transactionCase));
    }

    private StateChangeContext createStateChangeContext() {
        if (parent != null) {
            return parent.getStateChangeContext();
        }

        return !CollectionUtils.isEmpty(stateChanges)
                ? new StateChangeContext(stateChanges)
                : StateChangeContext.EMPTY_CONTEXT;
    }

    private BlockItem parseParent() {
        // set parent, parent-child items are assured to exist in sequential order of [Parent, Child1,..., ChildN]
        if (parentConsensusTimestamp != null && previous != null) {
            if (parentConsensusTimestamp == previous.consensusTimestamp) {
                return previous;
            } else if (previous.parent != null && parentConsensusTimestamp == previous.parent.consensusTimestamp) {
                // check older siblings parent, if child count is > 1 this prevents having to search to parent
                return previous.parent;
            }
        }

        return this.parent;
    }

    private boolean parseSuccess() {
        if (parent != null && !parent.successful) {
            return false;
        }

        var status = transactionResult.getStatus();
        return status == ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED
                || status == ResponseCodeEnum.SUCCESS
                || status == ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
    }
}
