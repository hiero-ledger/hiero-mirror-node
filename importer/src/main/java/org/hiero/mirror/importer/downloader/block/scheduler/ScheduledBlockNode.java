// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import org.hiero.mirror.importer.downloader.block.BlockNode;

public record ScheduledBlockNode(BlockNode blockNode, long nextBlockNumber) {}
