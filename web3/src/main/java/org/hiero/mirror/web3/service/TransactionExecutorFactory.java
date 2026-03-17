// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.service.entityid.EntityIdFactory;
import com.hedera.node.app.workflows.standalone.TransactionExecutor;
import com.hedera.node.app.workflows.standalone.TransactionExecutors;
import com.hedera.node.app.workflows.standalone.TransactionExecutors.Properties;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.state.MirrorNodeState;
import org.hyperledger.besu.evm.operation.BlockHashOperation;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@Named
@RequiredArgsConstructor
@CustomLog
public class TransactionExecutorFactory {

    private final BlockHashOperation mirrorBlockHashOperation;
    private final MirrorNodeState mirrorNodeState;
    private final EvmProperties evmProperties;
    private final Map<SemanticVersion, TransactionExecutor> transactionExecutors = new ConcurrentHashMap<>();
    private final EntityIdFactory entityIdFactory;

    //    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void init() throws InterruptedException {
        //        Thread.sleep(100000);
        //        NativeLibSodiumLoader.load();
        // Create transaction executor for each EVM version, on startup, before the k8s
        // readiness probe elapses, so we avoid slowing down the initial contract calls.
        log.info("Running warmup");
        ContractCallContext.run(ctx -> {
            evmProperties.getEvmVersions().values().forEach(this::create);
            log.info("Running warmaup lambda");
            return ctx;
        });
    }

    // Reuse TransactionExecutor across requests for the same EVM version
    public TransactionExecutor get() {
        var version = evmProperties.getSemanticEvmVersion();
        return transactionExecutors.computeIfAbsent(version, this::create);
    }

    private synchronized TransactionExecutor create(SemanticVersion evmVersion) {
        //        NativeLibSodiumLoader.load();

        var appProperties = new HashMap<>(evmProperties.getTransactionProperties());
        appProperties.put("contracts.evm.version", "v" + evmVersion.major() + "." + evmVersion.minor());

        var executorConfig = Properties.newBuilder()
                .appProperties(appProperties)
                .customOps(Set.of(mirrorBlockHashOperation))
                .state(mirrorNodeState)
                .build();

        return TransactionExecutors.TRANSACTION_EXECUTORS.newExecutor(executorConfig, entityIdFactory);
    }
}
