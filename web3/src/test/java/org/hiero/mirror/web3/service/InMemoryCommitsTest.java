// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.node.app.service.entityid.impl.schemas.V0490EntityIdSchema.ENTITY_ID_STATE_ID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.entityid.EntityIdService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.services.utils.EntityIdUtils;
import com.swirlds.state.spi.WritableKVState;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.state.keyvalue.AccountReadableKVState;
import org.hiero.mirror.web3.web3j.generated.EthCall;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.web3j.protocol.core.RemoteFunctionCall;

/**
 * Calls of one request with in-memory commits enabled aggregate the state changes the real transaction executor
 * commits.
 */
class InMemoryCommitsTest extends AbstractContractCallServiceTest {

    /** PUSH1 0, PUSH1 0, REVERT */
    private static final String REVERTING_INIT_CODE = "60006000fd";

    private Address contractAddress;
    private EthCall contract;
    private Address payerAddress;
    private AccountID payerId;

    @BeforeEach
    void setUpPayerAndContract() {
        final var payer = accountEntityWithEvmAddressPersist();
        accountBalancePersist(payer, payer.getCreatedTimestamp());
        payerAddress = toAddress(payer.toEntityId());
        payerId = EntityIdUtils.toAccountId(payer.toEntityId());
        contract = testWeb3jService.deploy(EthCall::deploy);
        contractAddress = Address.fromHexString(contract.getContractAddress());
        warmUpExecutor();
    }

    @Test
    void revertedCallKeepsGasFeeForLaterCalls() {
        // Given
        final var before = payer();

        // When
        assertThatThrownBy(() -> call(params(contract.send_testRevert())))
                .isInstanceOf(MirrorEvmTransactionException.class);

        // Then Hedera charges the gas of a reverted call, but does not increment the nonce of a ContractCall sender.
        final var after = payer();
        assertThat(after.tinybarBalance()).isLessThan(before.tinybarBalance());
        assertThat(after.ethereumNonce()).isEqualTo(before.ethereumNonce());
    }

    @Test
    void successfulCallsAggregateBalanceChanges() {
        // Given
        final var before = payer();
        final var params = params(contract.send_multiplySimpleNumbers());

        // When
        call(params);
        final var afterFirst = payer();
        call(params);

        // Then
        final var afterSecond = payer();
        assertThat(afterFirst.tinybarBalance()).isLessThan(before.tinybarBalance());
        assertThat(afterSecond.tinybarBalance()).isLessThan(afterFirst.tinybarBalance());
    }

    @Test
    void disabledInMemoryCommitsDiscardBalanceChanges() {
        // Given
        final var before = payer();
        final var params = params(contract.send_multiplySimpleNumbers());

        // When
        call(params);
        ContractCallContext.get().reset();

        // Then
        assertThat(payer().tinybarBalance()).isEqualTo(before.tinybarBalance());
    }

    @Test
    void resetDiscardsCommittedChanges() {
        // Given
        final var context = ContractCallContext.get();
        final var before = payer();
        final var params = params(contract.send_multiplySimpleNumbers());
        call(params);
        call(params);
        assertThat(payer().tinybarBalance()).isLessThan(before.tinybarBalance());

        // When
        context.reset();

        // Then
        assertThat(payer().tinybarBalance()).isEqualTo(before.tinybarBalance());
        assertThat(payer().ethereumNonce()).isEqualTo(before.ethereumNonce());
    }

    @Test
    void revertedCreateDoesNotAdvanceEntityId() {
        // Given a request that already created a contract
        final long initial = entityNumber();
        call(create(EthCall.BINARY));
        final long before = entityNumber();
        assertThat(before).isGreaterThan(initial);

        // When
        assertThatThrownBy(() -> call(create(REVERTING_INIT_CODE))).isInstanceOf(MirrorEvmTransactionException.class);

        // Then the entity number the reverted create took is free again
        assertThat(entityNumber()).isEqualTo(before);
    }

    /**
     * Each test runs several calls in the test method's request context, which times out SQL after 4 seconds. The first
     * EVM execution in the JVM can take that long on its own, so run one in a separate request first. That execution
     * may time out, but it leaves the executor warm either way.
     */
    private void warmUpExecutor() {
        try {
            final var params = params(contract.send_multiplySimpleNumbers());
            ContractCallContext.run(ctx -> contractExecutionService.processCall(params, ctx));
        } catch (final MirrorEvmTransactionException | QueryTimeoutException e) {
            // Only a cold start is expected to fail here.
        }
    }

    private void call(final ContractExecutionParameters params) {
        contractExecutionService.callContract(params, ContractCallContext.get());
    }

    private ContractExecutionParameters params(final RemoteFunctionCall<?> functionCall) {
        return getContractExecutionParameters(
                functionCall.encodeFunctionCall(), contractAddress, payerAddress, 0L, ETH_CALL, TRANSACTION_GAS_LIMIT);
    }

    private ContractExecutionParameters create(final String initCode) {
        return testWeb3jService.serviceParametersForTopLevelContractCreate(initCode, ETH_CALL, payerAddress);
    }

    private long entityNumber() {
        return state.getWritableStates(EntityIdService.NAME)
                .<EntityNumber>getSingleton(ENTITY_ID_STATE_ID)
                .get()
                .number();
    }

    private Account payer() {
        final WritableKVState<AccountID, Account> accounts =
                state.getWritableStates(TokenService.NAME).get(AccountReadableKVState.STATE_ID);
        return accounts.get(payerId);
    }
}
