// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.commons.util.ReflectionUtils.getDeclaredConstructor;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.TopicMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.proto.ResponseCodeEnum;
import io.grpc.Status;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.SneakyThrows;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ObjectAssert;
import org.hiero.mirror.monitor.NodeProperties;
import org.hiero.mirror.monitor.publish.PublishMetrics.Tags;
import org.hiero.mirror.monitor.publish.transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PublishMetricsTest {

    private static final String SCENARIO_NAME = "test";

    private MeterRegistry meterRegistry;
    private PublishMetrics publishMetrics;
    private PublishProperties publishProperties;
    private PublishScenario publishScenario;
    private NodeProperties node;

    @BeforeEach
    void setup() {
        meterRegistry = new SimpleMeterRegistry();
        publishProperties = new PublishProperties();
        publishMetrics = new PublishMetrics(meterRegistry, publishProperties);

        PublishScenarioProperties publishScenarioProperties = new PublishScenarioProperties();
        publishScenarioProperties.setName(SCENARIO_NAME);
        publishScenarioProperties.setType(TransactionType.CONSENSUS_SUBMIT_MESSAGE);
        publishScenario = new PublishScenario(publishScenarioProperties);

        node = new NodeProperties();
        node.setAccountId("0.0.3");
        node.setHost("127.0.0.1");
        node.setNodeId(0L);
    }

    @Test
    void onSuccess() {
        publishMetrics.onSuccess(response());
        publishMetrics.onSuccess(response());

        assertMetric(meterRegistry.find(PublishMetrics.METRIC_DURATION).timeGauges())
                .extracting(Gauge::value)
                .asInstanceOf(InstanceOfAssertFactories.DOUBLE)
                .isPositive();

        assertMetric(meterRegistry.find(PublishMetrics.METRIC_HANDLE).timers())
                .returns(PublishMetrics.SUCCESS, t -> t.getId().getTag(PublishMetrics.Tags.TAG_STATUS))
                .extracting(t -> t.mean(TimeUnit.SECONDS))
                .asInstanceOf(InstanceOfAssertFactories.DOUBLE)
                .isGreaterThanOrEqualTo(5.0);

        assertMetric(meterRegistry.find(PublishMetrics.METRIC_SUBMIT).timers())
                .returns(PublishMetrics.SUCCESS, t -> t.getId().getTag(PublishMetrics.Tags.TAG_STATUS))
                .extracting(t -> t.mean(TimeUnit.SECONDS))
                .asInstanceOf(InstanceOfAssertFactories.DOUBLE)
                .isGreaterThanOrEqualTo(3.0);
    }

    @Test
    void onSuccessWithNullResponseTimestamp(CapturedOutput output) {
        // verifies that when unexpected exception happens, onSuccess catches it and no metric is recorded
        PublishResponse response = response().toBuilder().timestamp(null).build();

        publishMetrics.onSuccess(response);
        assertThat(meterRegistry.find(PublishMetrics.METRIC_DURATION).timeGauges())
                .isEmpty();
        assertThat(meterRegistry.find(PublishMetrics.METRIC_HANDLE).timeGauges())
                .isEmpty();
        assertThat(meterRegistry.find(PublishMetrics.METRIC_SUBMIT).timeGauges())
                .isEmpty();

        publishMetrics.status();
        assertThat(output).asString().contains("No publishers");
    }

    @Test
    void onErrorStatusRuntimeException(CapturedOutput logOutput) {
        Status status = Status.RESOURCE_EXHAUSTED;
        var publishException = new PublishException(request(), status.asRuntimeException());
        onError(logOutput, publishException, status.getCode().toString());
    }

    @Test
    void onErrorTimeoutException(CapturedOutput logOutput) {
        var publishException = new PublishException(request(), new TimeoutException());
        onError(logOutput, publishException, TimeoutException.class.getSimpleName());
    }

    @Test
    void onErrorPrecheckStatusException(CapturedOutput logOutput) throws Exception {
        TransactionId transactionId = TransactionId.withValidStart(AccountId.fromString("0.0.3"), Instant.now());
        com.hedera.hashgraph.sdk.Status status = com.hedera.hashgraph.sdk.Status.SUCCESS;
        Constructor<PrecheckStatusException> constructor = getDeclaredConstructor(PrecheckStatusException.class);
        constructor.setAccessible(true);
        PrecheckStatusException precheckStatusException = constructor.newInstance(status, transactionId);
        var publishException = new PublishException(request(), precheckStatusException);
        onError(logOutput, publishException, status.toString());
    }

    @Test
    void onErrorReceiptStatusException(CapturedOutput logOutput) throws Exception {
        TransactionId transactionId = TransactionId.withValidStart(AccountId.fromString("0.0.3"), Instant.now());
        TransactionReceipt transactionReceipt = receipt(ResponseCodeEnum.SUCCESS);
        Constructor<ReceiptStatusException> constructor = getDeclaredConstructor(ReceiptStatusException.class);
        constructor.setAccessible(true);
        ReceiptStatusException receiptStatusException = constructor.newInstance(transactionId, transactionReceipt);
        var publishException = new PublishException(request(), receiptStatusException);
        onError(logOutput, publishException, ResponseCodeEnum.SUCCESS.toString());
    }

    @Test
    void onErrorWithNullRequestTimestamp(CapturedOutput output) {
        // verifies that when unexpected exception happens, onError catches it and no metric is recorded
        PublishRequest request = request().toBuilder().timestamp(null).build();

        publishMetrics.onError(new PublishException(request, new IllegalArgumentException()));
        assertThat(meterRegistry.find(PublishMetrics.METRIC_DURATION).timeGauges())
                .isEmpty();
        assertThat(meterRegistry.find(PublishMetrics.METRIC_HANDLE).timeGauges())
                .isEmpty();
        assertThat(meterRegistry.find(PublishMetrics.METRIC_SUBMIT).timeGauges())
                .isEmpty();

        publishMetrics.status();
        assertThat(output).asString().contains("No publishers");
    }

    @Test
    void onErrorWithNullNode(CapturedOutput output) {
        var request = request().toBuilder().node(null).build();
        node = null;
        var exception = new PublishException(request, new IllegalArgumentException());
        onError(output, exception, IllegalArgumentException.class.getSimpleName());
    }

    void onError(CapturedOutput logOutput, PublishException publishException, String status) {
        publishScenario.onError(publishException);
        publishMetrics.onError(publishException);

        assertMetric(meterRegistry.find(PublishMetrics.METRIC_DURATION).timeGauges())
                .extracting(TimeGauge::value)
                .asInstanceOf(InstanceOfAssertFactories.DOUBLE)
                .isPositive();

        assertMetric(meterRegistry.find(PublishMetrics.METRIC_SUBMIT).timers())
                .returns(status, t -> t.getId().getTag(PublishMetrics.Tags.TAG_STATUS))
                .extracting(t -> t.mean(TimeUnit.SECONDS))
                .asInstanceOf(InstanceOfAssertFactories.DOUBLE)
                .isGreaterThanOrEqualTo(3.0);

        publishMetrics.status();
        assertThat(logOutput)
                .asString()
                .hasLineCount(1)
                .contains("INFO")
                .contains("Scenario " + SCENARIO_NAME + " published 0 transactions in")
                .contains("Errors: {" + status + "=1}");
    }

    @Test
    void statusSuccess(CapturedOutput logOutput) {
        PublishResponse response = response();
        publishScenario.onNext(response);
        publishMetrics.onSuccess(response);
        publishMetrics.status();
        assertThat(logOutput)
                .asString()
                .hasLineCount(1)
                .contains("INFO")
                .contains("Scenario " + SCENARIO_NAME + " published 1 transactions in")
                .contains("Errors: {}");
    }

    @Test
    void statusDisabled(CapturedOutput logOutput) {
        publishProperties.setEnabled(false);

        publishMetrics.onSuccess(response());
        publishMetrics.status();

        assertThat(logOutput).asString().isEmpty();
    }

    private <T extends Meter> ObjectAssert<T> assertMetric(Iterable<T> meters) {
        var iterableAssert = assertThat(meters)
                .hasSize(1)
                .first()
                .returns(SCENARIO_NAME, t -> t.getId().getTag(Tags.TAG_SCENARIO))
                .returns(TransactionType.CONSENSUS_SUBMIT_MESSAGE.toString(), t -> t.getId()
                        .getTag(Tags.TAG_TYPE));

        if (node != null) {
            iterableAssert
                    .returns(String.valueOf(node.getNodeId()), t -> t.getId().getTag(Tags.TAG_NODE))
                    .returns(node.getHost(), t -> t.getId().getTag(Tags.TAG_HOST))
                    .returns(String.valueOf(node.getPort()), t -> t.getId().getTag(Tags.TAG_PORT));
        }

        return iterableAssert;
    }

    private PublishRequest request() {
        return PublishRequest.builder()
                .node(node)
                .scenario(publishScenario)
                .timestamp(Instant.now().minusSeconds(5L))
                .transaction(new TopicMessageSubmitTransaction().setNodeAccountIds(node.getAccountIds()))
                .build();
    }

    @SneakyThrows
    private PublishResponse response() {
        return PublishResponse.builder()
                .receipt(receipt(ResponseCodeEnum.OK))
                .request(request())
                .timestamp(Instant.now().minusSeconds(2L))
                .build();
    }

    @SneakyThrows
    private com.hedera.hashgraph.sdk.TransactionReceipt receipt(ResponseCodeEnum status) {
        byte[] receiptBytes = com.hedera.hashgraph.sdk.proto.TransactionReceipt.newBuilder()
                .setStatus(status)
                .build()
                .toByteArray();
        return com.hedera.hashgraph.sdk.TransactionReceipt.fromBytes(receiptBytes);
    }
}
