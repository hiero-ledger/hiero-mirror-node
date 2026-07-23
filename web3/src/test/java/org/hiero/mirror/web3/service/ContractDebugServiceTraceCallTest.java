// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import com.hedera.hapi.node.contract.ContractFunctionResult;
import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.rest.model.ActionResponse.TypeEnum;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.hiero.mirror.web3.service.model.TraceRequest;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.web3j.generated.EthCall;
import org.hiero.mirror.web3.web3j.generated.InternalCaller;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class ContractDebugServiceTraceCallTest extends AbstractContractCallServiceOpcodeTracerTest {

    @Test
    void processTraceCallReturnsAllActions() {
        final var nestedAction = action("0x01", TypeEnum.CALL);
        final var topLevelAction = action("0x02", TypeEnum.CALL);
        stubActions(nestedAction, topLevelAction);

        final var params = executionParameters();
        final var result = ContractCallContext.run(
                ctx -> contractDebugService.processTraceCall(params, new TraceRequest(params, false)));

        assertThat(result.getActions()).isNotNull();
        assertThat(result.getActions().getCalls()).containsExactly(nestedAction, topLevelAction);
    }

    @Test
    void processTraceCallReturnsOnlyTopCall() {
        final var nestedAction = action("0x01", TypeEnum.CALL);
        final var topLevelAction = action("0x02", TypeEnum.STATICCALL);
        stubActions(nestedAction, topLevelAction);

        final var params = executionParameters();
        final var result = ContractCallContext.run(
                ctx -> contractDebugService.processTraceCall(params, new TraceRequest(params, true)));

        assertThat(result.getActions()).isNotNull();
        assertThat(result.getActions().getCalls()).containsExactly(topLevelAction);
    }

    @Test
    void processTraceCallIntegrationReturnsActions() throws Exception {
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var functionCall = contract.call_multiplySimpleNumbers();
        final var params = getContractExecutionParameters(functionCall, contract);

        final var result = ContractCallContext.run(
                ctx -> contractDebugService.processTraceCall(params, new TraceRequest(params, false)));

        assertThat(result.getActions()).isNotNull();
        assertThat(result.getActions().getCalls()).isNotEmpty();
        assertThat(result.getActions().getCalls().getLast().getTo())
                .isEqualToIgnoringCase(contract.getContractAddress());
    }

    @Test
    void processTraceCallIntegrationOnlyTopCall() throws Exception {
        final var contract = testWeb3jService.deploy(InternalCaller::deploy);
        final var functionCall = contract.call_callNonExisting(contract.getContractAddress());
        final var params = getContractExecutionParameters(functionCall, contract);

        final var allActions = ContractCallContext.run(
                ctx -> contractDebugService.processTraceCall(params, new TraceRequest(params, false)));
        final var topCallOnly = ContractCallContext.run(
                ctx -> contractDebugService.processTraceCall(params, new TraceRequest(params, true)));

        assertThat(allActions.getActions().getCalls()).isNotEmpty();
        assertThat(topCallOnly.getActions().getCalls()).hasSize(1);
        assertThat(topCallOnly.getActions().getCalls().getFirst())
                .isEqualTo(allActions.getActions().getCalls().getLast());
    }

    private void stubActions(final ActionResponse... actions) {
        doAnswer(invocation -> {
                    final var actionContext = ContractCallContext.get().getActionContext();
                    assertThat(actionContext).isNotNull();
                    assertThat(actionContext.getTracerConfig()).isNotNull();
                    for (final var action : actions) {
                        actionContext.addAction(action);
                    }
                    return new EvmTransactionResult(
                            SUCCESS,
                            ContractFunctionResult.newBuilder()
                                    .gasUsed(TRANSACTION_GAS_LIMIT)
                                    .build());
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());
    }

    private ContractExecutionParameters executionParameters() {
        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(new byte[0])
                .callType(ETH_CALL)
                .gas(TRANSACTION_GAS_LIMIT)
                .receiver(Address.ZERO)
                .sender(Address.ZERO)
                .value(0L)
                .build();
    }

    private ActionResponse action(final String from, final TypeEnum type) {
        return new ActionResponse().from(from).to("0x03").type(type).gas("0x0").gasUsed("0x0");
    }
}
