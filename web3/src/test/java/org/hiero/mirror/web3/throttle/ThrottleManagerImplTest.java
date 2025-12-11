// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.throttle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.web3.throttle.ThrottleManagerImpl.GAS_PER_SECOND_LIMIT_EXCEEDED;
import static org.hiero.mirror.web3.throttle.ThrottleManagerImpl.REQUEST_PER_SECOND_LIMIT_EXCEEDED;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.List;
import org.hiero.mirror.web3.exception.ThrottleException;
import org.hiero.mirror.web3.throttle.RequestFilter.FilterField;
import org.hiero.mirror.web3.throttle.RequestFilter.FilterType;
import org.hiero.mirror.web3.throttle.RequestProperties.ActionType;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
final class ThrottleManagerImplTest {

    private static final long GAS_PER_SECOND = 100_000L;

    private RequestFilter requestFilter;
    private RequestProperties requestProperties;
    private ThrottleProperties throttleProperties;
    private ThrottleManager throttleManager;

    @BeforeEach
    void setup() {
        requestFilter = new RequestFilter();
        requestFilter.setExpression("latest");
        requestFilter.setField(FilterField.BLOCK);
        requestFilter.setType(FilterType.EQUALS);

        requestProperties = new RequestProperties();
        requestProperties.setAction(ActionType.LOG);
        requestProperties.setFilters(List.of(requestFilter));

        throttleProperties = new ThrottleProperties();
        throttleProperties.setGasPerSecond(GAS_PER_SECOND);
        throttleProperties.setRequest(List.of(requestProperties));
        throttleProperties.setRequestsPerSecond(2);

        throttleManager = createThrottleManager();
    }

    @Test
    void notThrottled() {
        var request = request();
        throttleManager.throttle(request);
    }

    @Test
    void throttleRateLimit() {
        var request = request();
        request.setGas(21_000L);
        throttleManager.throttle(request);
        throttleManager.throttle(request);
        assertThatThrownBy(() -> throttleManager.throttle(request))
                .isInstanceOf(ThrottleException.class)
                .hasMessageContaining(REQUEST_PER_SECOND_LIMIT_EXCEEDED);
    }

    @Test
    void throttleGasLimit() {
        var request = request();
        throttleManager.throttle(request);
        assertThatThrownBy(() -> throttleManager.throttle(request))
                .isInstanceOf(ThrottleException.class)
                .hasMessageContaining(GAS_PER_SECOND_LIMIT_EXCEEDED);
    }

    @Test
    void restore() {
        var request = request();
        throttleManager.throttle(request);
        throttleManager.restore(request.getGas());
        throttleManager.throttle(request);
    }

    @Test
    void restoreZero() {
        throttleManager.restore(0);
    }

    @Test
    void restoreMax() {
        long gps = 10_000_000_000_000L;
        throttleProperties.setGasPerSecond(gps);
        var request = request();
        var customThrottleManager = createThrottleManager();

        customThrottleManager.throttle(request);
        customThrottleManager.restore(request.getGas());
        customThrottleManager.throttle(request);
    }

    @Test
    void requestLog(CapturedOutput output) {
        requestProperties.setAction(ActionType.LOG);
        var request = request();
        throttleManager.throttle(request);
        assertThat(output).contains("ContractCallRequest(");
    }

    @Test
    void requestModularized() {
        requestProperties.setAction(ActionType.MODULARIZED);
        var request = request();
        throttleManager.throttle(request);
    }

    @Test
    void requestRejected() {
        requestProperties.setAction(ActionType.REJECT);
        var request = request();
        assertThatThrownBy(() -> throttleManager.throttle(request))
                .isInstanceOf(ThrottleException.class)
                .hasMessageContaining("Invalid request");
    }

    @Test
    void requestThrottled() {
        requestProperties.setAction(ActionType.THROTTLE);
        requestProperties.setRate(1L);
        var request = request();
        request.setGas(21_000L);

        throttleManager.throttle(request);
        assertThatThrownBy(() -> throttleManager.throttle(request))
                .isInstanceOf(ThrottleException.class)
                .hasMessageContaining(REQUEST_PER_SECOND_LIMIT_EXCEEDED);
    }

    @Test
    void requestNotThrottled() {
        requestProperties.setAction(ActionType.THROTTLE);
        var request = request();
        throttleManager.throttle(request);
    }

    @Test
    void requestMultipleActions(CapturedOutput output) {
        var modularizedRequest = new RequestProperties();
        modularizedRequest.setAction(ActionType.MODULARIZED);
        throttleProperties.setRequest(List.of(requestProperties, modularizedRequest));
        var request = request();
        throttleManager.throttle(request);
        assertThat(output).contains("ContractCallRequest(");
    }

    @Test
    void requestNoFilters() {
        requestProperties.setAction(ActionType.MODULARIZED);
        requestProperties.setFilters(List.of());
        var request = request();
        throttleManager.throttle(request);
    }

    @Test
    void requestMultipleFilters() {
        var dataFilter = new RequestFilter();
        dataFilter.setExpression("beef");
        requestProperties.setAction(ActionType.MODULARIZED);
        requestProperties.setFilters(List.of(requestFilter, dataFilter));
        var request = request();
        throttleManager.throttle(request);
    }

    @Test
    void requestLimitReached() {
        requestProperties.setAction(ActionType.REJECT);
        requestProperties.setLimit(0L);
        var request = request();
        throttleManager.throttle(request);
    }

    @Test
    void requestDisabled() {
        requestProperties.setAction(ActionType.REJECT);
        requestProperties.setRate(0L);
        var request = request();
        throttleManager.throttle(request);
    }

    @Test
    void requestNoMatch() {
        requestProperties.setAction(ActionType.REJECT);
        var request = request();
        request.setBlock(BlockType.EARLIEST);
        throttleManager.throttle(request);
    }

    @Test
    void requestFilterData() {
        requestProperties.setAction(ActionType.MODULARIZED);
        requestFilter.setExpression("dead");
        requestFilter.setField(FilterField.DATA);
        requestFilter.setType(FilterType.CONTAINS);
        var request = request();
        throttleManager.throttle(request);
    }

    @Test
    void requestFilterEstimate() {
        requestProperties.setAction(ActionType.MODULARIZED);
        requestFilter.setExpression("false");
        requestFilter.setField(FilterField.ESTIMATE);
        requestFilter.setType(FilterType.EQUALS);
        var request = request();
        throttleManager.throttle(request);
    }

    @Test
    void requestFilterFrom() {
        requestProperties.setAction(ActionType.MODULARIZED);
        requestFilter.setExpression("04e2");
        requestFilter.setField(FilterField.FROM);
        requestFilter.setType(FilterType.CONTAINS);
        var request = request();
        throttleManager.throttle(request);
    }

    @Test
    void requestFilterGas() {
        requestProperties.setAction(ActionType.MODULARIZED);
        requestFilter.setExpression(String.valueOf(GAS_PER_SECOND));
        requestFilter.setField(FilterField.GAS);
        requestFilter.setType(FilterType.EQUALS);
        var request = request();
        throttleManager.throttle(request);
    }

    @Test
    void requestFilterTo() {
        requestProperties.setAction(ActionType.MODULARIZED);
        requestFilter.setExpression("0x00000000000000000000000000000000000004e4");
        requestFilter.setField(FilterField.TO);
        requestFilter.setType(FilterType.EQUALS);
        var request = request();
        throttleManager.throttle(request);
    }

    @Test
    void requestFilterValue() {
        requestProperties.setAction(ActionType.MODULARIZED);
        requestFilter.setExpression("1");
        requestFilter.setField(FilterField.VALUE);
        requestFilter.setType(FilterType.EQUALS);
        var request = request();
        throttleManager.throttle(request);
    }

    private Bucket createBucket(long capacity) {
        var limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, Duration.ofSeconds(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private ContractCallRequest request() {
        var request = new ContractCallRequest();
        request.setBlock(BlockType.LATEST);
        request.setData("0xdeadbeef");
        request.setEstimate(false);
        request.setFrom("0x00000000000000000000000000000000000004e2");
        request.setGas(GAS_PER_SECOND);
        request.setTo("0x00000000000000000000000000000000000004e4");
        request.setValue(1L);
        return request;
    }

    private ThrottleManager createThrottleManager() {
        var gasLimitBucket = createBucket(throttleProperties.getGasPerSecond());
        var rateLimitBucket = createBucket(throttleProperties.getRequestsPerSecond());
        return new ThrottleManagerImpl(gasLimitBucket, rateLimitBucket, throttleProperties);
    }
}
