// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.common.domain.entity.EntityType;

class UnknownDataTransactionHandlerTest extends AbstractTransactionHandlerTest {
    // TransactionBody containing an unknown field with a field id = 9999
    private static final String TRANSACTION_BODY_BYTES_HEX =
            "0a120a0c08eb88d6ee0510e8eff7ab01120218021202180318c280de1922020878321043727970746f2074657374206d656d6ffaf004050a03666f6f";

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new UnknownDataTransactionHandler();
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        try {
            return TransactionBody.parseFrom(Hex.decodeHex(TRANSACTION_BODY_BYTES_HEX)).toBuilder();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }
}
