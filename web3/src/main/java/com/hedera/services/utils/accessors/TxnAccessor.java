// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils.accessors;

import com.hedera.services.hapi.fees.usage.BaseTransactionMeta;
import com.hedera.services.hapi.fees.usage.SigUsage;
import com.hedera.services.hapi.fees.usage.crypto.CryptoTransferMeta;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.Map;

/**
 * Defines a type that gives access to several commonly referenced parts of a Hedera Services gRPC
 * {@link Transaction}.
 */
public interface TxnAccessor {

    SubType getSubType();

    AccountID getPayer();

    byte[] getMemoUtf8Bytes();

    byte[] getTxnBytes();

    SignatureMap getSigMap();

    TransactionID getTxnId();

    HederaFunctionality getFunction();

    SigUsage usageGiven(int numPayerKeys);

    TransactionBody getTxn();

    String getMemo();

    byte[] getHash();

    Transaction getSignedTxnWrapper();

    Map<String, Object> getSpanMap();

    ExpandHandleSpanMapAccessor getSpanMapAccessor();

    BaseTransactionMeta baseUsageMeta();

    CryptoTransferMeta availXferUsageMeta();
}
