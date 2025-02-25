// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.provider;

import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

abstract class AbstractHip679S3StreamFileProviderTest extends S3StreamFileProviderTest {

    @SuppressWarnings("java:S2699")
    @Disabled("Doesn't apply to HIP-679")
    @Override
    @Test
    void getBlockFile() {}

    @SuppressWarnings("java:S2699")
    @Disabled("Doesn't apply to HIP-679")
    @Override
    @Test
    void getBlockFileNotFound() {}

    @SuppressWarnings("java:S2699")
    @Disabled("Doesn't apply to HIP-679")
    @Override
    @ParameterizedTest
    @EnumSource(PathType.class)
    void getBlockFileIncorrectPathType(PathType pathType) {}
}
