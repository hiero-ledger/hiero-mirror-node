// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

interface BlockSource {

    /*
     * Gets blocks from the source. An implementation can either download block files from cloud storage, or stream
     * blocks from a block node.
     */
    void get();
}
