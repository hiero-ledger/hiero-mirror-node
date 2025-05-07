// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block;

import com.hedera.mirror.importer.addressbook.ConsensusNodeService;
import com.hedera.mirror.importer.downloader.BlockStreamSource;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.provider.StreamFileProvider;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.reader.block.BlockStreamReader;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import lombok.CustomLog;
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
