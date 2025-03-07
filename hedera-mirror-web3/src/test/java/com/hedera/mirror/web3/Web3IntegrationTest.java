// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3;

import com.hedera.mirror.common.config.CommonIntegrationTest;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.store.Store;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@ExtendWith(ContextExtension.class)
public abstract class Web3IntegrationTest extends CommonIntegrationTest {

    @MockitoSpyBean
    protected MirrorEvmTxProcessor processor;

    @Resource
    protected Store store;
}
