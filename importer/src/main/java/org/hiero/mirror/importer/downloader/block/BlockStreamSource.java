// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

interface BlockStreamSource {

    /*
     * Gets block streams from the source. An implementation can either download block streams from cloud storage, or
     * stream block streams from a block node.
     */
    void get();
}
