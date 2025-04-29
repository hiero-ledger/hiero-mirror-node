// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.monitor.publish.transaction.TransactionType.ACCOUNT_CREATE;
import static org.hiero.mirror.monitor.publish.transaction.TransactionType.CONSENSUS_CREATE_TOPIC;
import static org.hiero.mirror.monitor.publish.transaction.TransactionType.SCHEDULE_CREATE;
import static org.hiero.mirror.monitor.publish.transaction.TransactionType.TOKEN_CREATE;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.proto.AccountID;
import com.hedera.hashgraph.sdk.proto.ScheduleID;
import com.hedera.hashgraph.sdk.proto.TokenID;
import com.hedera.hashgraph.sdk.proto.TopicID;
import com.hedera.hashgraph.sdk.proto.TransactionReceipt;
import com.hedera.hashgraph.sdk.proto.TransactionRecord;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.hiero.mirror.monitor.MonitorProperties;
import org.hiero.mirror.monitor.exception.ExpressionConversionException;
import org.hiero.mirror.monitor.publish.PublishRequest;
import org.hiero.mirror.monitor.publish.PublishResponse;
import org.hiero.mirror.monitor.publish.TransactionPublisher;
import org.hiero.mirror.monitor.publish.transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ExpressionConverterImplTest {

    @Spy
    private final MonitorProperties monitorProperties = new MonitorProperties();

    @Mock
    private TransactionPublisher transactionPublisher;

    @InjectMocks
    private ExpressionConverterImpl expressionConverter;

    @Captor
    private ArgumentCaptor<PublishRequest> request;

    @BeforeEach
    void setup() {
        monitorProperties.getOperator().setAccountId("0.0.2");
        monitorProperties
                .getOperator()
                .setPrivateKey(PrivateKey.generateED25519().toString());
    }

    @Test
    void invalidExpression() {
        assertThatThrownBy(() -> expressionConverter.convert("${foo.bar}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not a valid property expression");
    }

    @Test
    void incompleteExpression() {
        assertThat(expressionConverter.convert("${topic.bar")).isEqualTo("${topic.bar");
    }

    @Test
    void withoutId() {
        assertThatThrownBy(() -> expressionConverter.convert("${topic}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not a valid property expression");
    }

    @Test
    void nullValue() {
        assertThat(expressionConverter.convert((String) null)).isNull();
    }

    @Test
    void empty() {
        assertThat(expressionConverter.convert("")).isEmpty();
    }

    @Test
    void regularString() {
        assertThat(expressionConverter.convert("0.0.100")).isEqualTo("0.0.100");
    }

    @Test
    void error() throws InvalidProtocolBufferException {
        TransactionType type = TransactionType.CONSENSUS_SUBMIT_MESSAGE;
        when(transactionPublisher.publish(any())).thenReturn(response(type, 100));
        assertThatThrownBy(() -> expressionConverter.convert("${topic.foo}"))
                .isInstanceOf(ExpressionConversionException.class);
    }

    @Test
    void errorPublishing() throws InvalidProtocolBufferException {
        TransactionType type = CONSENSUS_CREATE_TOPIC;
        when(transactionPublisher.publish(any()))
                .thenReturn(Mono.error(new TimeoutException("timeout")))
                .thenReturn(response(type, 100));
        assertThat(expressionConverter.convert("${topic.foo}")).isEqualTo("0.0.100");
    }

    @Test
    void account() throws InvalidProtocolBufferException {
        TransactionType type = ACCOUNT_CREATE;
        when(transactionPublisher.publish(any())).thenReturn(response(type, 100));
        assertThat(expressionConverter.convert("${account.foo}")).isEqualTo("0.0.100");

        verify(transactionPublisher).publish(request.capture());
        assertThat(request.getValue().getScenario().getProperties().getType()).isEqualTo(type);
    }

    @Test
    void token() throws InvalidProtocolBufferException {
        TransactionType type = TOKEN_CREATE;
        when(transactionPublisher.publish(any())).thenReturn(response(type, 101));
        assertThat(expressionConverter.convert("${token.foo}")).isEqualTo("0.0.101");

        verify(transactionPublisher).publish(request.capture());
        assertThat(request.getValue().getScenario().getProperties().getType()).isEqualTo(type);
    }

    @Test
    void nft() throws InvalidProtocolBufferException {
        TransactionType type = TOKEN_CREATE;
        when(transactionPublisher.publish(any())).thenReturn(response(type, 101));
        assertThat(expressionConverter.convert("${nft.foo}")).isEqualTo("0.0.101");

        verify(transactionPublisher).publish(request.capture());
        assertThat(request.getValue().getScenario().getProperties().getType()).isEqualTo(type);
    }

    @Test
    void topic() throws InvalidProtocolBufferException {
        TransactionType type = CONSENSUS_CREATE_TOPIC;
        when(transactionPublisher.publish(any())).thenReturn(response(type, 100));
        assertThat(expressionConverter.convert("${topic.foo}")).isEqualTo("0.0.100");

        verify(transactionPublisher).publish(request.capture());
        assertThat(request.getValue().getScenario().getProperties().getType()).isEqualTo(type);
    }

    @Test
    void schedule() throws InvalidProtocolBufferException {
        TransactionType type = SCHEDULE_CREATE;
        when(transactionPublisher.publish(any())).thenReturn(response(type, 100));

        assertThat(expressionConverter.convert("${schedule.foo}")).isEqualTo("0.0.100");

        verify(transactionPublisher).publish(request.capture());
        assertThat(request.getValue().getScenario().getProperties().getType()).isEqualTo(type);
    }

    @Test
    void cached() throws InvalidProtocolBufferException {
        TransactionType type = CONSENSUS_CREATE_TOPIC;
        when(transactionPublisher.publish(any())).thenReturn(response(type, 100));

        assertThat(expressionConverter.convert("${topic.foo}")).isEqualTo("0.0.100");
        assertThat(expressionConverter.convert("${topic.foo}")).isEqualTo("0.0.100");

        verify(transactionPublisher).publish(request.capture());
        assertThat(request.getValue().getScenario().getProperties().getType()).isEqualTo(type);
    }

    @Test
    void map() throws InvalidProtocolBufferException {
        Map<String, String> properties = Map.of("accountId", "0.0.100", "topicId", "${topic.fooBar_123}");
        TransactionType type = CONSENSUS_CREATE_TOPIC;
        when(transactionPublisher.publish(any())).thenReturn(response(type, 101));

        assertThat(expressionConverter.convert(properties))
                .hasSize(2)
                .containsEntry("accountId", "0.0.100")
                .containsEntry("topicId", "0.0.101");

        verify(transactionPublisher).publish(request.capture());
        assertThat(request.getValue().getScenario().getProperties().getType()).isEqualTo(type);
    }

    private Mono<PublishResponse> response(TransactionType type, long id) throws InvalidProtocolBufferException {
        TransactionReceipt.Builder receipt = TransactionReceipt.newBuilder();

        if (type == ACCOUNT_CREATE) {
            receipt.setAccountID(AccountID.newBuilder().setAccountNum(id).build());
        } else if (type == CONSENSUS_CREATE_TOPIC) {
            receipt.setTopicID(TopicID.newBuilder().setTopicNum(id).build());
        } else if (type == TOKEN_CREATE) {
            receipt.setTokenID(TokenID.newBuilder().setTokenNum(id).build());
        } else if (type == SCHEDULE_CREATE) {
            receipt.setScheduleID(ScheduleID.newBuilder().setScheduleNum(id).build());
        }

        com.hedera.hashgraph.sdk.TransactionRecord txnRecord = com.hedera.hashgraph.sdk.TransactionRecord.fromBytes(
                TransactionRecord.newBuilder().setReceipt(receipt).build().toByteArray());

        return Mono.just(PublishResponse.builder()
                .transactionRecord(txnRecord)
                .receipt(txnRecord.receipt)
                .build());
    }
}
