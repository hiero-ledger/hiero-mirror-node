// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import io.grpc.ManagedChannelBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;

class InProcessManagedChannelBuilderProvider implements ManagedChannelBuilderProvider {

    public static final InProcessManagedChannelBuilderProvider INSTANCE = new InProcessManagedChannelBuilderProvider();

    @Override
    public ManagedChannelBuilder<?> get(BlockNodeProperties blockNodeProperties) {
        return InProcessChannelBuilder.forName(blockNodeProperties.getHost()).usePlaintext();
    }
}
