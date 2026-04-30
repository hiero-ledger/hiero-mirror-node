// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.converter.ObjectToStringSerializer.OBJECT_MAPPER;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TopicMessageTest {

    // Test serialization to JSON to verify contract with PostgreSQL listen/notify
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void toJson(boolean nullVersion) throws Exception {
        TopicMessage topicMessage = new TopicMessage();
        topicMessage.setChunkNum(1);
        topicMessage.setChunkTotal(2);
        topicMessage.setConsensusTimestamp(1594401417000000000L);
        topicMessage.setMessage(new byte[] {1, 2, 3});
        topicMessage.setPayerAccountId(EntityId.of("0.1.1000"));
        topicMessage.setRunningHash(new byte[] {4, 5, 6});
        topicMessage.setRunningHashVersion(nullVersion ? null : 2);
        topicMessage.setSequenceNumber(1L);
        topicMessage.setTopicId(EntityId.of("0.0.1001"));
        topicMessage.setValidStartTimestamp(1594401416000000000L);

        TransactionID transactionID = TransactionID.newBuilder()
                .setAccountID(
                        AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(10))
                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(20).setNanos(20))
                .setNonce(1)
                .setScheduled(true)
                .build();
        topicMessage.setInitialTransactionId(transactionID.toByteArray());
        var expectedVersion = nullVersion ? "\"running_hash_version\":null," : "\"running_hash_version\":2,";

        String json = OBJECT_MAPPER.writeValueAsString(topicMessage);
        assertThat(json)
                .isEqualTo("{" + "\"@type\":\"TopicMessage\","
                        + "\"chunk_num\":1,"
                        + "\"chunk_total\":2,"
                        + "\"consensus_timestamp\":1594401417000000000,"
                        + "\"initial_transaction_id\":\"CgQIFBAUEgIYChgBIAE=\","
                        + "\"message\":\"AQID\","
                        + "\"payer_account_id\":274877907944,"
                        + "\"running_hash\":\"BAUG\","
                        + expectedVersion
                        + "\"sequence_number\":1,"
                        + "\"topic_id\":1001,"
                        + "\"valid_start_timestamp\":1594401416000000000}");
    }
}
