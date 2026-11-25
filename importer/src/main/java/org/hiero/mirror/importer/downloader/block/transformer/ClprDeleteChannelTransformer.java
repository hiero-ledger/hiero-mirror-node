// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

/**
 * Reads the resulting {@code clpr_channel_value} state change (the {@code CLOSING}/{@code DRAINED}/{@code CLOSED}
 * status transition and queue state) off {@code StateChangeContext}. A successful deletion is expected to
 * include a channel state change.
 */
@Named
final class ClprDeleteChannelTransformer extends AbstractClprChannelTransformer {

    @Override
    protected ByteString getChannelId(TransactionBody transactionBody) {
        return transactionBody.getClprDeleteChannel().getChannelId();
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRDELETECHANNEL;
    }
}
