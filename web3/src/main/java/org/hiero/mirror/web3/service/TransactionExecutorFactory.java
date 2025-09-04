// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.workflows.standalone.TransactionExecutor;
import com.hedera.node.app.workflows.standalone.TransactionExecutors;
import com.hedera.node.app.workflows.standalone.TransactionExecutors.Properties;
import com.swirlds.state.lifecycle.EntityIdFactory;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.evm.config.ModularizedOperation;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.state.MirrorNodeState;

@Named
@RequiredArgsConstructor
public class TransactionExecutorFactory {

    private final Set<ModularizedOperation> customOperations;
    private final MirrorNodeState mirrorNodeState;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;
    private final Map<SemanticVersion, TransactionExecutor> transactionExecutors = new ConcurrentHashMap<>();
    private final EntityIdFactory entityIdFactory;

    // Reuse TransactionExecutor across requests for the same EVM version
    public TransactionExecutor get() {
        var version = mirrorNodeEvmProperties.getSemanticEvmVersion();
        return transactionExecutors.computeIfAbsent(version, this::create);
    }

    private synchronized TransactionExecutor create(SemanticVersion evmVersion) {
        var appProperties = new HashMap<>(mirrorNodeEvmProperties.getTransactionProperties());
        appProperties.put("contracts.evm.version", "v" + evmVersion.major() + "." + evmVersion.minor());

        var executorConfig = Properties.newBuilder()
                .appProperties(appProperties)
                .customOps(customOperations)
                .state(mirrorNodeState)
                .build();

        return TransactionExecutors.TRANSACTION_EXECUTORS.newExecutor(executorConfig, entityIdFactory);
    }
}
