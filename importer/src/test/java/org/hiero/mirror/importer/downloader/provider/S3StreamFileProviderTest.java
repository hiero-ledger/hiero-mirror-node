// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.provider;

import static org.hiero.mirror.importer.TestUtils.S3_PROXY_PORT;
import static org.hiero.mirror.importer.downloader.provider.S3StreamFileProvider.SEPARATOR;
import static software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR;

import java.net.URI;
import java.util.concurrent.ForkJoinPool;
import lombok.SneakyThrows;
import org.gaul.s3proxy.S3Proxy;
import org.hiero.mirror.importer.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

class S3StreamFileProviderTest extends AbstractStreamFileProviderTest {

    private S3Proxy s3Proxy;

    @Override
    protected String providerPathSeparator() {
        return SEPARATOR;
    }

    @BeforeEach
    @Override
    void setup() {
        super.setup();
        var s3AsyncClient = S3AsyncClient.builder()
                .asyncConfiguration(b -> b.advancedOption(FUTURE_COMPLETION_EXECUTOR, ForkJoinPool.commonPool()))
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .endpointOverride(URI.create("http://localhost:" + S3_PROXY_PORT))
                .forcePathStyle(true)
                .region(Region.of(properties.getRegion()))
                .build();
        streamFileProvider =
                new S3StreamFileProvider(blockBucketProperties, commonProperties, properties, s3AsyncClient);
        s3Proxy = TestUtils.startS3Proxy(dataPath);
        blockStreamTargetRootPath = blockBucketProperties.getBucketName();
        targetRootPath = properties.getBucketName();
    }

    @AfterEach
    @SneakyThrows
    void after() {
        if (s3Proxy != null) {
            s3Proxy.stop();
        }
    }
}
