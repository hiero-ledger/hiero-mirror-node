// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component("blockStreamProperties")
@ConfigurationProperties("hiero.mirror.importer.block")
@Data
@RequiredArgsConstructor
@Validated
public class BlockStreamProperties {

    private boolean enabled = false;

    @NotNull
    private Duration frequency = Duration.ofMillis(100L);

    private boolean persistBytes = false;

    private boolean writeFiles = false;
}
