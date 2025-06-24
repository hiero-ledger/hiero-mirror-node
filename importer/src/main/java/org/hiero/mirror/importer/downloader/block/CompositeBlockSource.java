// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.inject.Named;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.BlockSourceType;
import org.hiero.mirror.importer.leader.Leader;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;

@CustomLog
@Named
@Primary
final class CompositeBlockSource implements BlockSource {

    private final SourceHealth blockFileSourceHealth;
    private final SourceHealth blockNodeSubscriberSourceHealth;
    private final BlockStreamVerifier blockStreamVerifier;
    private final AtomicReference<SourceHealth> current;
    private final BlockProperties properties;

    public CompositeBlockSource(
            BlockFileSource blockFileSource,
            BlockNodeSubscriber blockNodeSubscriber,
            BlockStreamVerifier blockStreamVerifier,
            BlockProperties properties) {
        this.blockFileSourceHealth = new SourceHealth(blockFileSource, BlockSourceType.FILE);
        this.blockNodeSubscriberSourceHealth = new SourceHealth(blockNodeSubscriber, BlockSourceType.BLOCK_NODE);
        this.blockStreamVerifier = blockStreamVerifier;
        this.current = new AtomicReference<>(blockNodeSubscriberSourceHealth);
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
                if (blockStreamVerifier
                        .getLastBlockFile()
                        .map(BlockFile::getSourceType)
                        .filter(type -> type == BlockSourceType.BLOCK_NODE)
                        .isPresent()) {
                    yield blockNodeSubscriberSourceHealth;
                }

                if (properties.getNodes().isEmpty()) {
                    yield blockFileSourceHealth;
                }

                if (!current.get().isHealthy()) {
                    var sourceHealth = current.get() == blockNodeSubscriberSourceHealth
                            ? blockFileSourceHealth
                            : blockNodeSubscriberSourceHealth;
                    current.set(sourceHealth);
                }

                yield current.get();
            }
            case BLOCK_NODE -> blockNodeSubscriberSourceHealth;
            case FILE -> blockFileSourceHealth;
        };
    }

    @Getter
    @RequiredArgsConstructor
    private static class SourceHealth {

        private final AtomicInteger errors = new AtomicInteger();
        private final BlockSource source;
        private final BlockSourceType type;

        boolean isHealthy() {
            return errors.get() < 3;
        }

        void onError() {
            errors.incrementAndGet();
        }

        void reset() {
            errors.set(0);
        }
    }
}
