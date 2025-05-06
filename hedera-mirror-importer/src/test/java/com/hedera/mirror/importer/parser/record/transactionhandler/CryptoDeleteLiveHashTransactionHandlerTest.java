// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.transactionhandler;

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hederahashgraph.api.proto.java.CryptoDeleteLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

class CryptoDeleteLiveHashTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new CryptoDeleteLiveHashTransactionHandler();
    }

    @SuppressWarnings("deprecation")
    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setCryptoDeleteLiveHash(CryptoDeleteLiveHashTransactionBody.newBuilder()
                        .setAccountOfLiveHash(defaultEntityId.toAccountID()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.ACCOUNT;
    }
}
