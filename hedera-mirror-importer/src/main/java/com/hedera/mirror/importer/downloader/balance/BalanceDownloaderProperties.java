// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.balance;

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

@Component("balanceDownloaderProperties")
@ConfigurationProperties("hedera.mirror.importer.downloader.balance")
@Data
@RequiredArgsConstructor
@Validated
public class BalanceDownloaderProperties implements DownloaderProperties {

    private final CommonDownloaderProperties common;

    private boolean enabled = false;

    @NotNull
    private Duration frequency = Duration.ofSeconds(30);

    private boolean persistBytes = false;

    private boolean writeFiles = false;

    private boolean writeSignatures = false;

    @Override
    public StreamType getStreamType() {
        return StreamType.BALANCE;
    }
}
