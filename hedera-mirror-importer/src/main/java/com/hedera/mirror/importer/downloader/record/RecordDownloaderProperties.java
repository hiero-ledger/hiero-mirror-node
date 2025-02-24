// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.record;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.DownloaderProperties;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component("recordDownloaderProperties")
@ConfigurationProperties("hedera.mirror.importer.downloader.record")
@Data
@RequiredArgsConstructor
@Validated
public class RecordDownloaderProperties implements DownloaderProperties {

    private final CommonDownloaderProperties common;

    private boolean enabled = true;

    @NotNull
    private Duration frequency = Duration.ofMillis(500L);

    private boolean persistBytes = false;

    private boolean writeFiles = false;

    private boolean writeSignatures = false;

    @Override
    public StreamType getStreamType() {
        return StreamType.RECORD;
    }
}
