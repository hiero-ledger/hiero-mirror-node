// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

/**
 * Reads the {@code clpr_channel_value} state change off {@code StateChangeContext} and populates
 * {@link org.hiero.mirror.common.domain.transaction.RecordItem#getClprContext()}'s
 * {@link org.hiero.mirror.common.domain.transaction.ClprContext#getClprChannel()}.
 */
@Named
final class ClprCreateChannelTransformer extends AbstractClprChannelTransformer {

    @Override
    protected ByteString getChannelId(TransactionBody transactionBody) {
        return transactionBody.getClprCreateChannel().getChannelId();
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRCREATECHANNEL;
    }
}
