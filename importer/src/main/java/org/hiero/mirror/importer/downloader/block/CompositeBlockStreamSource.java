// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import lombok.CustomLog;
import org.hiero.mirror.importer.addressbook.ConsensusNodeService;
import org.hiero.mirror.importer.downloader.BlockStreamSource;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.provider.StreamFileProvider;
import org.hiero.mirror.importer.leader.Leader;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.springframework.scheduling.annotation.Scheduled;

@CustomLog
@Named
public class CompositeBlockStreamSource implements BlockStreamSource {

    private final BlockFileSource blockFileSource;
    private final BlockStreamProperties properties;

    CompositeBlockStreamSource(
            BlockStreamReader blockStreamReader,
            BlockStreamVerifier blockStreamVerifier,
            CommonDownloaderProperties commonDownloaderProperties,
            ConsensusNodeService consensusNodeService,
            MeterRegistry meterRegistry,
            BlockStreamProperties blockStreamProperties,
            StreamFileProvider streamFileProvider) {
        blockFileSource = new BlockFileSource(
                blockStreamReader,
                blockStreamVerifier,
                commonDownloaderProperties,
                consensusNodeService,
                meterRegistry,
                blockStreamProperties,
                streamFileProvider);
        this.properties = blockStreamProperties;
    }

    @Override
    @Leader
    @Scheduled(fixedDelayString = "#{@blockStreamProperties.getFrequency().toMillis()}")
    public void get() {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            blockFileSource.get();
        } catch (Exception e) {
            log.error("Failed to get block from source", e);
        }
    }
}
