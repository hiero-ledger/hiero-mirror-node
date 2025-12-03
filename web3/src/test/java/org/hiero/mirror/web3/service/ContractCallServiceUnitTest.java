// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.service.model.CallServiceParameters;
import org.hiero.mirror.web3.service.model.CallServiceParameters.CallType;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.throttle.ThrottleProperties;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@ExtendWith(ContextExtension.class)
class ContractCallServiceUnitTest {

    @Mock
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Mock
    private CallServiceParameters params;

    @Mock
    private ContractCallContext ctx;

    @Mock
    private RecordFileService recordFileService;

    @Mock
    private ThrottleManager throttleManager;

    @Mock
    private TransactionExecutionService transactionExecutionService;

    private ContractCallService contractCallService;

    private boolean isModularized;

    @BeforeEach
    void setup() {
        isModularized = mirrorNodeEvmProperties.isModularizedServices();
        contractCallService = new ContractCallService(
                throttleManager,
                new ThrottleProperties(),
                new SimpleMeterRegistry(),
                recordFileService,
                mirrorNodeEvmProperties,
                transactionExecutionService) {};
    }

    @AfterEach
    void after() {
        mirrorNodeEvmProperties.setModularizedServices(isModularized);
    }

    @Test
    void callContractShouldInitializeStackFramesPropertyFalse() throws MirrorEvmTransactionException {
        mirrorNodeEvmProperties.setModularizedServices(false);
        when(params.getCallType()).thenReturn(CallType.ETH_CALL);
        when(recordFileService.findByBlockType(any())).thenReturn(Optional.of(new RecordFile()));
        final var successResult = HederaEvmTransactionProcessingResult.successful(null, 1000, 0, 0, null, Address.ZERO);

        contractCallService.callContract(params, ctx);
    }

    @Test
    void callContractShouldInitializeStackFramesPropertyTrueTrafficFalse() throws MirrorEvmTransactionException {
        mirrorNodeEvmProperties.setModularizedServices(true);
        when(params.isModularized()).thenReturn(false);
        when(params.getCallType()).thenReturn(CallType.ETH_CALL);
        when(recordFileService.findByBlockType(any())).thenReturn(Optional.of(new RecordFile()));
        final var successResult = HederaEvmTransactionProcessingResult.successful(null, 1000, 0, 0, null, Address.ZERO);

        contractCallService.callContract(params, ctx);
    }

    @Test
    void callContractShouldInitializeStackFramesPropertyTrueTrafficTrue() throws MirrorEvmTransactionException {
        mirrorNodeEvmProperties.setModularizedServices(true);
        when(params.isModularized()).thenReturn(true);
        when(params.getCallType()).thenReturn(CallType.ETH_CALL);
        when(recordFileService.findByBlockType(any())).thenReturn(Optional.of(new RecordFile()));
        final var successResult = HederaEvmTransactionProcessingResult.successful(null, 1000, 0, 0, null, Address.ZERO);
        when(transactionExecutionService.execute(any(), anyLong())).thenReturn(successResult);

        contractCallService.callContract(params, ctx);
    }
}
