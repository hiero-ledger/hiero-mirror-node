// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.importer.downloader.StreamFileNotifier;

public interface CutoverService extends StreamFileNotifier {

    boolean shouldGetStream(StreamType streamType);
}
