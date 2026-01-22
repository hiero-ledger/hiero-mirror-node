// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import io.grpc.ManagedChannelBuilder;

interface ManagedChannelBuilderProvider {

    ManagedChannelBuilder<?> get(BlockNodeProperties blockNodeProperties);

    ManagedChannelBuilder<?> getForStreaming(BlockNodeProperties blockNodeProperties);
}
