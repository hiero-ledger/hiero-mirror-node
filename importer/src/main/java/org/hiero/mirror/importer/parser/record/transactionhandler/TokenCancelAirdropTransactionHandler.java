// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.hedera.mirror.common.domain.token.TokenAirdropStateEnum;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import jakarta.inject.Named;
import java.util.List;
import java.util.function.Function;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;

@Named
class TokenCancelAirdropTransactionHandler extends AbstractTokenUpdateAirdropTransactionHandler {

    private static final Function<RecordItem, List<PendingAirdropId>> airdropExtractor =
            r -> r.getTransactionBody().getTokenCancelAirdrop().getPendingAirdropsList();

    public TokenCancelAirdropTransactionHandler(
            EntityIdService entityIdService, EntityListener entityListener, EntityProperties entityProperties) {
        super(
                entityIdService,
                entityListener,
                entityProperties,
                airdropExtractor,
                TokenAirdropStateEnum.CANCELLED,
                TransactionType.TOKENCANCELAIRDROP);
    }
}
