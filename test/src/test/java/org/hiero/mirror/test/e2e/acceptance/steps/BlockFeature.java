// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.cucumber.java.en.When;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import org.hiero.mirror.test.e2e.acceptance.props.Order;
import org.springframework.context.annotation.Scope;
import org.springframework.util.CollectionUtils;

@CustomLog
@RequiredArgsConstructor
@Scope("cucumber-glue")
public class BlockFeature {

    private final MirrorNodeClient mirrorClient;

    @When("I verify block by hash and number")
    public void verifyBlockByHashAndNumber() {
        final var blocks = mirrorClient.getBlocks(Order.DESC, 1);

        if (CollectionUtils.isEmpty(blocks.getBlocks())) {
            log.warn("Skipping block verification since there are no blocks");
            return;
        }
        final var firstBlock = blocks.getBlocks().getFirst();

        final var blockByHash = mirrorClient.getBlockByHash(firstBlock.getHash());
        assertThat(blockByHash).isNotNull().isEqualTo(firstBlock);

        final var blockByNumber = mirrorClient.getBlockByNumber(firstBlock.getNumber());
        assertThat(blockByNumber).isNotNull().isEqualTo(firstBlock);
    }
}
