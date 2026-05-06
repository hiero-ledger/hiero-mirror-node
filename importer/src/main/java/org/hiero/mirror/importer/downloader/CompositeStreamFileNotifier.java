// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader;

import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.StreamFile;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Primary;

@Named
@NullMarked
@Primary
@RequiredArgsConstructor
final class CompositeStreamFileNotifier implements StreamFileNotifier {

    private final List<StreamFileNotifier> notifiers;

    @Override
    public void verified(final StreamFile<?> streamFile) {
        for (final var notifier : notifiers) {
            notifier.verified(streamFile);
        }
    }
}
