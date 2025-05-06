// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import lombok.Value;
import org.hiero.mirror.importer.parser.serializer.ProtoJsonSerializer;
import org.hiero.mirror.importer.parser.serializer.PubSubEntityIdSerializer;

@Value
public class PubSubMessage {
    private final Long consensusTimestamp;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(using = PubSubEntityIdSerializer.class)
    private final EntityId entity;

    private final int transactionType;

    private final Transaction transaction;

    @JsonSerialize(using = ProtoJsonSerializer.class)
    private final TransactionRecord transactionRecord;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(contentUsing = ProtoJsonSerializer.class)
    private final Iterable<AccountAmount> nonFeeTransfers;

    @Value
    // This is a pojo version of the Transaction proto, needed to get around protobuf serializing body as raw bytes
    public static class Transaction {
        @JsonSerialize(using = ProtoJsonSerializer.class)
        private final TransactionBody body;

        @JsonSerialize(using = ProtoJsonSerializer.class)
        private final SignatureMap sigMap;
    }
}
