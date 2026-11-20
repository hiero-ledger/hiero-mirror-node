// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.node.app.service.entityid.impl.schemas.V0490EntityIdSchema.ENTITY_ID_STATE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.math.BigInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.state.keyvalue.AccountReadableKVState;
import org.hiero.mirror.web3.web3j.generated.StorageContract;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.web3j.protocol.core.RemoteFunctionCall;

/**
 * Test validating state correctness for commit functionality of a single request. Request reads and commits in memory
 * is kept in its own {@link ContractCallContext}. Each request here is a separate {@link ContractCallContext#run}
 * scope, as it is for a call served by the controller, and no request may observe the contract storage, entity number
 * or payer balance that another request changed.
 */
class CrossRequestStateIsolationTest extends AbstractContractCallServiceTest {

    private static final BigInteger FIRST_VALUE = BigInteger.valueOf(42);

    /** PUSH1 0, PUSH1 0, REVERT */
    private static final String REVERTING_INIT_CODE = "60006000fd";

    private static final BigInteger SECOND_VALUE = BigInteger.valueOf(7);

    private Address contractAddress;
    private StorageContract contract;
    private Address payerAddress;
    private AccountID payerId;

    @BeforeEach
    void setUpPayerAndContract() {
        final var payer = accountEntityWithEvmAddressPersist();
        accountBalancePersist(payer, payer.getCreatedTimestamp());
        payerAddress = toAddress(payer.toEntityId());
        payerId = EntityIdUtils.toAccountId(payer.toEntityId());
        contract = testWeb3jService.deploy(StorageContract::deploy);
        contractAddress = Address.fromHexString(contract.getContractAddress());
        warmUpExecutor();
    }

    @Test
    void concurrentRequestsDoNotSeeEachOthersChanges() throws Exception {
        // Given
        final var initial = inRequest(this::snapshot);
        final var firstWritten = new CountDownLatch(1);
        final var secondDone = new CountDownLatch(1);

        try (final var executor = Executors.newFixedThreadPool(2)) {
            // When the first request changes state and stays open until the second request has run
            final var first = executor.submit(() -> inRequest(() -> {
                try {
                    changeState(FIRST_VALUE);
                } finally {
                    firstWritten.countDown();
                }
                await(secondDone);
                return snapshot();
            }));
            // and the second request runs in between, then changes the same state itself
            final var second = executor.submit(() -> {
                await(firstWritten);
                return inRequest(() -> {
                    try {
                        final var before = snapshot();
                        changeState(SECOND_VALUE);
                        return new BeforeAndAfter(before, snapshot());
                    } finally {
                        secondDone.countDown();
                    }
                });
            });

            // Then the second request starts from the database, not from what the open first request committed
            final var secondRequest = second.get(1, TimeUnit.MINUTES);
            assertThat(secondRequest.before()).isEqualTo(initial);
            assertChanged(secondRequest.after(), initial, SECOND_VALUE);

            // and the first request still sees only its own changes
            assertChanged(first.get(1, TimeUnit.MINUTES), initial, FIRST_VALUE);
        }

        // and nothing is left behind for later requests
        assertThat(inRequest(this::snapshot)).isEqualTo(initial);
    }

    @Test
    void failedRequestDoesNotLeakChangesToLaterRequest() {
        // Given
        final var initial = inRequest(this::snapshot);

        // When a request changes state, then fails with an exception that escapes its context
        assertThatThrownBy(() -> inRequest(() -> {
                    changeState(FIRST_VALUE);
                    return call(create(REVERTING_INIT_CODE));
                }))
                .isInstanceOf(MirrorEvmTransactionException.class);

        // Then
        assertThat(inRequest(this::snapshot)).isEqualTo(initial);
    }

    @Test
    void laterRequestDoesNotSeeChangesOfEarlierRequest() {
        // Given
        final var initial = inRequest(this::snapshot);

        // When
        final var inFirstRequest = inRequest(() -> {
            changeState(FIRST_VALUE);
            return snapshot();
        });

        // Then the first request saw its own changes, so they were committed in memory
        assertChanged(inFirstRequest, initial, FIRST_VALUE);
        // but a later request sees only the database
        assertThat(inRequest(this::snapshot)).isEqualTo(initial);
    }

    private void assertChanged(final Snapshot changed, final Snapshot initial, final BigInteger storedValue) {
        assertThat(changed.storedValue()).isEqualTo(storedValue);
        assertThat(changed.entityNumber()).isGreaterThan(initial.entityNumber());
        assertThat(changed.balance()).isLessThan(initial.balance());
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.MINUTES)) {
                throw new IllegalStateException("Timed out waiting for the concurrent request");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private String call(final ContractExecutionParameters params) {
        return contractExecutionService.processCall(params, ContractCallContext.get());
    }

    /** Writes contract storage, charges the payer and creates a contract, which advances the entity number. */
    private void changeState(final BigInteger storedValue) {
        call(params(contract.send_updateSingleValue(storedValue)));
        call(create(StorageContract.BINARY));
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

    /** Runs in a new request context, as every eth_call does. */
    private <T> T inRequest(final Supplier<T> request) {
        return ContractCallContext.run(_ -> request.get());
    }

    private ContractExecutionParameters params(final RemoteFunctionCall<?> functionCall) {
        return getContractExecutionParameters(
                functionCall.encodeFunctionCall(), contractAddress, payerAddress, 0L, ETH_CALL, TRANSACTION_GAS_LIMIT);
    }

    private Account payer() {
        final WritableKVState<AccountID, Account> accounts =
                state.getWritableStates(TokenService.NAME).get(AccountReadableKVState.STATE_ID);
        return accounts.get(payerId);
    }

    private Snapshot snapshot() {
        final var result = call(params(contract.call_storedValue()));
        return new Snapshot(new BigInteger(result.substring(2), 16), entityNumber(), payer().tinybarBalance());
    }

    /**
     * Requests time out SQL after a few seconds, and the first EVM execution in the JVM can take that long on its own,
     * so run one in a separate request first. That execution may time out, but it leaves the executor warm either way.
     */
    private void warmUpExecutor() {
        try {
            inRequest(() -> call(params(contract.call_storedValue())));
        } catch (final MirrorEvmTransactionException | QueryTimeoutException e) {
            // Only a cold start is expected to fail here.
        }
    }

    private record BeforeAndAfter(Snapshot before, Snapshot after) {}

    private record Snapshot(BigInteger storedValue, long entityNumber, long balance) {}
}
