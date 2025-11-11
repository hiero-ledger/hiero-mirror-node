// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.BlockSourceType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.util.Version;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component("blockProperties")
@ConfigurationProperties("hiero.mirror.importer.block")
@Data
@RequiredArgsConstructor
@Validated
public class BlockProperties {

    private boolean enabled = false;

    @NotNull
    private Duration frequency = Duration.ofMillis(100L);

    @NotNull
    private Version newRootHashAlgorithmVersion = Version.parse("0.68.1");

    @NotNull
    @Valid
    private Collection<BlockNodeProperties> nodes = Collections.emptyList();

    private boolean persistBytes = false;

    @NotNull
    private BlockSourceType sourceType = BlockSourceType.AUTO;

    @NotNull
    @Valid
    private StreamProperties stream = new StreamProperties();

    private boolean writeFiles = false;
}
