// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.inject.Named;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("hiero.mirror.importer.downloader.block")
@Data
@Named
@Validated
public class BlockDownloaderProperties {
    private final boolean enabled = false;
}
