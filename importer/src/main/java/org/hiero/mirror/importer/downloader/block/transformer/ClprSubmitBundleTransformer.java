// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

/**
 * Registers {@code ClprSubmitBundle} for CLPR enrichment; the actual population happens generically in
 * {@link AbstractBlockTransactionTransformer}'s shared {@code transform()} step.
 */
@Named
final class ClprSubmitBundleTransformer extends AbstractBlockTransactionTransformer {

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRSUBMITBUNDLE;
    }
}
