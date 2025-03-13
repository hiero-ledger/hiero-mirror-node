// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.provider;

import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import org.junit.jupiter.api.Disabled;

abstract class AbstractHip679S3StreamFileProviderTest extends S3StreamFileProviderTest {

    @Disabled("Doesn't apply to HIP-679")
    @Override
    void getBlockFile() {}

    @Disabled("Doesn't apply to HIP-679")
    @Override
    void getBlockFileWithPathPrefix() {}

    @Disabled("Doesn't apply to HIP-679")
    @Override
    void getBlockFileNotFound() {}

    @Disabled("Doesn't apply to HIP-679")
    @Override
    void getBlockFileIncorrectPathType(PathType pathType) {}
}
