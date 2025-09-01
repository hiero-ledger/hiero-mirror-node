// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.cucumber.java.en.When;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import org.hiero.mirror.test.e2e.acceptance.props.Order;
import org.springframework.util.CollectionUtils;

@CustomLog
@RequiredArgsConstructor
public class BlockFeature {

    private final MirrorNodeClient mirrorClient;

    @When("I verify block by hash")
    public void verifyBlockByHash() {
        final var blocks = mirrorClient.getBlocks(Order.ASC, 1);
        if (CollectionUtils.isEmpty(blocks.getBlocks())) {
            log.warn("Skipping block by hash verification since there are no blocks");
            return;
        }
        final var firstBlock = blocks.getBlocks().getFirst();
        final var blockHash = firstBlock.getHash();
        final var blockResponse = mirrorClient.getBlockByHash(blockHash);

        assertThat(blockResponse).isNotNull();
        assertThat(blockResponse).isEqualTo(firstBlock);
    }
}
