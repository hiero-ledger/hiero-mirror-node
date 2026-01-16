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
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
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
    private static final String NETWORK_NAME = "network";
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
        metricsExecutionInterceptor = new MetricsExecutionInterceptor(meterRegistry);
        executionAttributes = new ExecutionAttributes();
    }

    @ParameterizedTest(name = "S3 List using pathType {0}")
    @EnumSource(value = PathType.class, mode = Mode.EXCLUDE, names = "AUTO")
    void s3ListExecution(PathType pathType) {
        var prefix =
                switch (pathType) {
                    case ACCOUNT_ID, AUTO -> accountIdPrefix(StreamType.RECORD, SHARD, REALM, ACCOUNT_NUM);
                    case NODE_ID -> nodeIdPrefix(StreamType.RECORD, NETWORK_NAME, SHARD, NODE_ID);
                };

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
        "ACCOUNT_ID, RECORD, 2022-06-21T09_14_34.364804003Z.rcd, signed",
        "ACCOUNT_ID, RECORD, 2020-02-09T18_30_00.000084Z.rcd_sig, signature",
        "ACCOUNT_ID, RECORD, 2022-07-13T08_46_11.304284003Z_01.rcd.gz, sidecar",
        "NODE_ID, RECORD, 2022-06-21T09_14_34.364804003Z.rcd, signed",
        "NODE_ID, RECORD, 2020-02-09T18_30_00.000084Z.rcd_sig, signature",
        "NODE_ID, RECORD, 2022-07-13T08_46_11.304284003Z_01.rcd.gz, sidecar",
        "ACCOUNT_ID, BALANCE, 2021-03-10T22_12_56.075092Z_Balances.csv, signed",
        "ACCOUNT_ID, BALANCE, 2021-03-10T22_12_56.075092Z_Balances.csv_sig, signature",
        "ACCOUNT_ID, BALANCE, 2021-03-10T22_12_56.075092Z_Balances.pb.gz, signed",
        "ACCOUNT_ID, BALANCE, 2021-03-10T22_12_56.075092Z_Balances.pb_sig.gz, signature",
        "NODE_ID, BALANCE, 2021-03-10T22_12_56.075092Z_Balances.csv, signed",
        "NODE_ID, BALANCE, 2021-03-10T22_12_56.075092Z_Balances.csv_sig, signature",
        "NODE_ID, BALANCE, 2021-03-10T22_12_56.075092Z_Balances.pb.gz, signed",
        "NODE_ID, BALANCE, 2021-03-10T22_12_56.075092Z_Balances.pb_sig.gz, signature",
        "NODE_ID, BLOCK, 000000000000000000000000000007858853.blk.gz, signed"
    })
    void s3GetObjectExecution(PathType pathType, StreamType streamType, String fileName, String expectedAction) {

        var prefix =
                switch (pathType) {
                    case ACCOUNT_ID, AUTO -> accountIdPrefix(streamType, SHARD, REALM, ACCOUNT_NUM);
                    case NODE_ID -> nodeIdPrefix(streamType, NETWORK_NAME, SHARD, NODE_ID);
                };

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

    private String nodeIdPrefix(StreamType streamType, String network, String shard, String nodeId) {
        return streamType != StreamType.BLOCK
                ? "%s/%s/%s/%s/".formatted(network, shard, nodeId, streamType.getNodeIdBasedSuffix())
                : "%s/%s/".formatted(shard, nodeId);
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
