// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

interface BlockSource {

    /**
     * Gets blocks from the source
     */
    void get();

    /**
     *  Returns false if this source has no block nodes to use
     */
    default boolean hasBlockNodes() {
        return true;
    }
}
