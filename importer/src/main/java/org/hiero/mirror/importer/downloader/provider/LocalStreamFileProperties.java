// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.provider;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.importer.downloader.local")
public class LocalStreamFileProperties {

    private boolean deleteAfterProcessing = true;
}
