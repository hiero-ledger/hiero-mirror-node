// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.leader.Leader;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;

@CustomLog
@Named
@Primary
public final class CompositeBlockSource implements BlockSource {

    private final SourceHealth blockFileSourceHealth;
    private final SourceHealth blockNodeSubscriberSourceHealth;
    private final BlockStreamVerifier blockStreamVerifier;
    private SourceHealth current;
    private final BlockProperties properties;

    public CompositeBlockSource(
            BlockFileSource blockFileSource,
            BlockNodeSubscriber blockNodeSubscriber,
            BlockStreamVerifier blockStreamVerifier,
            BlockProperties properties) {
        this.blockFileSourceHealth = new SourceHealth(blockFileSource, BlockSourceType.FILE);
        this.blockNodeSubscriberSourceHealth = new SourceHealth(blockNodeSubscriber, BlockSourceType.BLOCK_NODE);
        this.blockStreamVerifier = blockStreamVerifier;
        this.current = blockNodeSubscriberSourceHealth;
        this.properties = properties;
    }

    @Override
    @Leader
    @Scheduled(fixedDelayString = "#{@blockProperties.getFrequency().toMillis()}")
    public void get() {
        if (!properties.isEnabled()) {
            return;
        }

        var sourceHealth = getSourceHealth();
        try {
            sourceHealth.getSource().get();
            sourceHealth.reset();
        } catch (Throwable t) {
            log.error("Failed to get block from {} source", sourceHealth.getType(), t);
            sourceHealth.onError();
        }
    }

    private SourceHealth getSourceHealth() {
        return switch (properties.getSourceType()) {
            case AUTO -> {
                boolean switched = blockStreamVerifier
                        .getLastBlockFilename()
                        .map(BlockFile::isStreamedFilename)
                        .orElse(false);
                if (switched) {
                    yield blockNodeSubscriberSourceHealth;
                }

                if (properties.getNodes().isEmpty()) {
                    yield blockFileSourceHealth;
                }

                if (!current.isHealthy()) {
                    current = current == blockNodeSubscriberSourceHealth
                            ? blockFileSourceHealth
                            : blockNodeSubscriberSourceHealth;
                }

                yield current;
            }
            case BLOCK_NODE -> blockNodeSubscriberSourceHealth;
            case FILE -> blockFileSourceHealth;
        };
    }

    @Getter
    @RequiredArgsConstructor
    private static class SourceHealth {

        private int errors = 0;
        private final BlockSource source;
        private final BlockSourceType type;

        boolean isHealthy() {
            return errors < 3;
        }

        void onError() {
            errors++;
        }

        void reset() {
            errors = 0;
        }
    }
}
