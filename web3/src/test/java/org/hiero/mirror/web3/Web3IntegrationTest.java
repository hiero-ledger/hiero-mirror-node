// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import jakarta.annotation.Resource;
import org.hiero.mirror.common.config.CommonIntegrationTest;
import org.hiero.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import org.hiero.mirror.web3.evm.store.Store;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@ExtendWith(ContextExtension.class)
public abstract class Web3IntegrationTest extends CommonIntegrationTest {

    @MockitoSpyBean
    protected MirrorEvmTxProcessor processor;

    @Resource
    protected Store store;
}
