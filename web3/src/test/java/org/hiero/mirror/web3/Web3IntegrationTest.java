// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import jakarta.annotation.Resource;
import org.hiero.mirror.common.config.CommonIntegrationTest;
import org.hiero.mirror.web3.state.MirrorNodeState;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ContextExtension.class)
public abstract class Web3IntegrationTest extends CommonIntegrationTest {

    @Resource
    private MirrorNodeState mirrorNodeState;
}
