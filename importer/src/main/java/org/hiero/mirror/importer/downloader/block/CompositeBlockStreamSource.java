// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.leader.Leader;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;

@CustomLog
@Named
@Primary
@RequiredArgsConstructor
public final class CompositeBlockStreamSource implements BlockStreamSource {

    private final BlockFileSource blockFileSource;
    private final BlockStreamProperties properties;

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
