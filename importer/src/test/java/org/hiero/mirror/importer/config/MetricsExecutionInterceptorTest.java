// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.config.MetricsExecutionInterceptor.ACTION_LIST;
import static org.hiero.mirror.importer.config.MetricsExecutionInterceptor.METRIC_DOWNLOAD_REQUEST;
import static org.hiero.mirror.importer.config.MetricsExecutionInterceptor.QUERY_START_AFTER;
import static org.hiero.mirror.importer.config.MetricsExecutionInterceptor.TAG_ACTION;
import static org.hiero.mirror.importer.config.MetricsExecutionInterceptor.TAG_METHOD;
import static org.hiero.mirror.importer.config.MetricsExecutionInterceptor.TAG_NODE;
import static org.hiero.mirror.importer.config.MetricsExecutionInterceptor.TAG_SHARD;
import static org.hiero.mirror.importer.config.MetricsExecutionInterceptor.TAG_STATUS;
import static org.hiero.mirror.importer.config.MetricsExecutionInterceptor.TAG_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collection;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

/*
 * This test class was introduced with issue 6023, and is focused around the MetricsExecutionInterceptor
 * afterExecution() method, specifically the micrometer metrics tags and values that are produced depending on
 * the S3 activity being intercepted. No other verification of MetricsExecutionInterceptor is performed at this time.
 */
@ExtendWith(MockitoExtension.class)
class MetricsExecutionInterceptorTest {

    private static final int HTTP_STATUS_SUCCESS = 200;
    private static final String S3_REGION = "us-west-1";
    private static final String S3_BUCKET = "hedera-bucket";
    private static final String S3_HOST = "https://%s.s3.%s.amazonaws.com/".formatted(S3_BUCKET, S3_REGION);
    private static final String SEPARATOR = "/";
    private static final String SHARD = "0";
    private static final String REALM = "0";
    private static final String ACCOUNT_NUM = "4";
    private static final String NODE_ID = "1";
    private static final String BATCH_SIZE = "100";
    private ExecutionAttributes executionAttributes;

    @Mock
    private SdkHttpResponse sdkHttpResponse;

    @Mock
    private Context.BeforeTransmission beforeTransmissionContext;

    @Mock
    private Context.AfterExecution afterExecutionContext;

    private MeterRegistry meterRegistry;
    private MetricsExecutionInterceptor metricsExecutionInterceptor;

    @BeforeEach
    void setup() {
        meterRegistry = new SimpleMeterRegistry();
        metricsExecutionInterceptor = new MetricsExecutionInterceptor(new CommonProperties(), meterRegistry);
        executionAttributes = new ExecutionAttributes();
    }

    @Test
    void s3ListExecution() {
        var prefix = accountIdPrefix(StreamType.RECORD, SHARD, REALM, ACCOUNT_NUM);
        var sdkHttpRequest = createListObjectsRequest(prefix, StreamFilename.EPOCH.getFilename());

        when(afterExecutionContext.httpResponse()).thenReturn(sdkHttpResponse);
        when(sdkHttpResponse.statusCode()).thenReturn(HTTP_STATUS_SUCCESS);
        when(afterExecutionContext.httpRequest()).thenReturn(sdkHttpRequest);

        metricsExecutionInterceptor.beforeTransmission(beforeTransmissionContext, executionAttributes);
        assertNotNull(executionAttributes.getAttribute(MetricsExecutionInterceptor.START_TIME));

        metricsExecutionInterceptor.afterExecution(afterExecutionContext, executionAttributes);
        verifyTimerTags(ACTION_LIST, NODE_ID, StreamType.RECORD);
    }

    @ParameterizedTest
    @CsvSource({
        "RECORD, 2022-06-21T09_14_34.364804003Z.rcd, signed",
        "RECORD, 2020-02-09T18_30_00.000084Z.rcd_sig, signature",
        "RECORD, 2022-07-13T08_46_11.304284003Z_01.rcd.gz, sidecar",
        "BALANCE, 2021-03-10T22_12_56.075092Z_Balances.csv, signed",
        "BALANCE, 2021-03-10T22_12_56.075092Z_Balances.csv_sig, signature",
        "BALANCE, 2021-03-10T22_12_56.075092Z_Balances.pb.gz, signed",
        "BALANCE, 2021-03-10T22_12_56.075092Z_Balances.pb_sig.gz, signature"
    })
    void s3GetObjectExecution(StreamType streamType, String fileName, String expectedAction) {
        var prefix = accountIdPrefix(streamType, SHARD, REALM, ACCOUNT_NUM);
        var objectKey = prefix + fileName;
        var sdkHttpRequest = createGetObjectRequest(objectKey);

        when(afterExecutionContext.httpResponse()).thenReturn(sdkHttpResponse);
        when(sdkHttpResponse.statusCode()).thenReturn(HTTP_STATUS_SUCCESS);
        when(afterExecutionContext.httpRequest()).thenReturn(sdkHttpRequest);

        metricsExecutionInterceptor.beforeTransmission(beforeTransmissionContext, executionAttributes);
        assertNotNull(executionAttributes.getAttribute(MetricsExecutionInterceptor.START_TIME));
        metricsExecutionInterceptor.afterExecution(afterExecutionContext, executionAttributes);
        verifyTimerTags(expectedAction, NODE_ID, streamType);
    }

    /*
     * Per HIP-1193, block bucket paths never encode a shard or node (e.g. network/block/0000/0000/.../nnn.blk.gz),
     * so node is tagged with the "cloud" sentinel and shard comes from CommonProperties rather than the URI.
     */
    @Test
    void s3GetObjectExecutionBlock() {
        var objectKey = "network/block/0000/0000/0000/0000/000000007858853.blk.gz";
        var sdkHttpRequest = createGetObjectRequest(objectKey);

        when(afterExecutionContext.httpResponse()).thenReturn(sdkHttpResponse);
        when(sdkHttpResponse.statusCode()).thenReturn(HTTP_STATUS_SUCCESS);
        when(afterExecutionContext.httpRequest()).thenReturn(sdkHttpRequest);

        metricsExecutionInterceptor.beforeTransmission(beforeTransmissionContext, executionAttributes);
        assertNotNull(executionAttributes.getAttribute(MetricsExecutionInterceptor.START_TIME));
        metricsExecutionInterceptor.afterExecution(afterExecutionContext, executionAttributes);
        verifyTimerTags("signed", "cloud", StreamType.BLOCK);
    }

    /*
     * Test failure scenario where the SDK uri does not have a parsable bucket path. The resultant inner
     * IllegalStateException is caught and logged and there is no visibility of that. However, a Timer
     * will not have been created, so verify that is the case.
     */
    @Test
    void invalids3GetObjectExecution() {
        var sdkHttpRequest = createGetObjectRequest("uripaththatdoesnotmatchregex");
        when(afterExecutionContext.httpRequest()).thenReturn(sdkHttpRequest);

        metricsExecutionInterceptor.beforeTransmission(beforeTransmissionContext, executionAttributes);
        assertNotNull(executionAttributes.getAttribute(MetricsExecutionInterceptor.START_TIME));
        metricsExecutionInterceptor.afterExecution(afterExecutionContext, executionAttributes);
        Collection<Timer> timers = meterRegistry.find(METRIC_DOWNLOAD_REQUEST).timers();
        assertEquals(0, timers.size());
    }

    private String accountIdPrefix(StreamType streamType, String shard, String realm, String accountNum) {
        return "%s/%s%s.%s.%s/".formatted(streamType.getPath(), streamType.getNodePrefix(), shard, realm, accountNum);
    }

    private void verifyTimerTags(String expectedAction, String expectedNodeId, StreamType expectedStreamType) {

        assertThat(meterRegistry.find(METRIC_DOWNLOAD_REQUEST).timers())
                .hasSize(1)
                .first()
                .returns(1L, Timer::count)
                .returns(expectedAction, t -> t.getId().getTag(TAG_ACTION))
                .returns("GET", t -> t.getId().getTag(TAG_METHOD))
                .returns(expectedNodeId, t -> t.getId().getTag(TAG_NODE))
                .returns("0", t -> t.getId().getTag(TAG_SHARD))
                .returns("200", t -> t.getId().getTag(TAG_STATUS))
                .returns(expectedStreamType.name(), t -> t.getId().getTag(TAG_TYPE));
    }

    /*
     * Create S3 list HTTP request per https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListObjectsV2.html
     */
    private SdkHttpRequest createListObjectsRequest(String prefix, String startAfter) {
        return SdkHttpRequest.builder()
                .protocol("https")
                .host(S3_HOST)
                .method(SdkHttpMethod.GET)
                .encodedPath("/")
                .appendRawQueryParameter("list-type", "2")
                .appendRawQueryParameter("delimiter", SEPARATOR)
                .appendRawQueryParameter("max-keys", BATCH_SIZE)
                .appendRawQueryParameter("prefix", prefix)
                .appendRawQueryParameter(QUERY_START_AFTER, prefix + startAfter)
                .build();
    }

    /*
     * Create S3 get object HTTP request per https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetObject.html
     */
    private SdkHttpRequest createGetObjectRequest(String objectKey) {
        return SdkHttpRequest.builder()
                .protocol("https")
                .host(S3_HOST)
                .method(SdkHttpMethod.GET)
                .encodedPath(objectKey)
                .build();
    }
}
