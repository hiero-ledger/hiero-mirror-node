// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.TOKENS_STATE_ID;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.TOKEN_RELS_STATE_ID;
import static com.hedera.services.utils.EntityIdUtils.toAccountId;
import static com.hedera.services.utils.EntityIdUtils.toTokenId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.token.TokenService;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.WritableKVState;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.TokenPauseStatusEnum;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.hiero.mirror.web3.service.model.TraceCall;
import org.hiero.mirror.web3.state.keyvalue.AccountReadableKVState;
import org.hiero.mirror.web3.web3j.generated.EthCall;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract.AccountAmount;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract.TokenTransferList;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract.TransferList;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.RemoteFunctionCall;

/**
 * Runs {@code debug_traceCallMany} with real token contract calls. After the transactions settle, the token and
 * token-relation entries are read back through the writable KV state, which holds what each one committed.
 */
class ContractDebugServiceTraceCommitTest extends AbstractContractCallServiceOpcodeTracerTest {

    /** Covers the token creation fee charged to the sender. */
    private static final long CREATE_TOKEN_VALUE = 10_000L * 100_000_000_000L;

    private static final long TREASURY_BALANCE = 1_000L;

    private Entity receiverEntity;
    private EntityIDPair receiverRelation;
    private long tokenEntityId;
    private TokenID tokenId;
    private EntityIDPair treasuryRelation;

    @Test
    void traceCallManyKeepsEachTransactionsTokenStatusAndBalances() throws Exception {
        final var contract = deployTokenContract();
        final var treasury = contract.getContractAddress();
        final var receiver = getAddressFromEntity(receiverEntity);

        final var traced = trace(
                contract,
                List.of(
                        contract.call_cryptoTransferExternal(hbarTransfers(), tokenTransfer(treasury, receiver, 100)),
                        contract.call_pauseTokenExternal(tokenAddress()),
                        contract.call_unpauseTokenExternal(tokenAddress()),
                        contract.call_cryptoTransferExternal(hbarTransfers(), tokenTransfer(treasury, receiver, 50)),
                        contract.call_cryptoTransferExternal(hbarTransfers(), tokenTransfer(treasury, receiver, 25)),
                        contract.call_pauseTokenExternal(tokenAddress())));

        assertThat(traced.statuses()).containsExactly(SUCCESS, SUCCESS, SUCCESS, SUCCESS, SUCCESS, SUCCESS);
        assertThat(traced.states())
                .containsExactly(
                        snapshot(false, 1_000L, 0L),
                        snapshot(false, 900L, 100L),
                        snapshot(true, 900L, 100L),
                        snapshot(false, 900L, 100L),
                        snapshot(false, 850L, 150L),
                        snapshot(false, 825L, 175L),
                        snapshot(true, 825L, 175L));
    }

    @Test
    void traceCallManyDropsStateFromTheRevertedTransaction() throws Exception {
        final var contract = deployTokenContract();
        final var treasury = contract.getContractAddress();
        final var receiver = getAddressFromEntity(receiverEntity);

        final var traced = trace(
                contract,
                List.of(
                        contract.call_cryptoTransferExternal(hbarTransfers(), tokenTransfer(treasury, receiver, 100)),
                        contract.call_cryptoTransferExternal(hbarTransfers(), tokenTransfer(treasury, receiver, 50)),
                        contract.send_cryptoTransferThenRevert(hbarTransfers(), tokenTransfer(treasury, receiver, 40)),
                        contract.call_pauseTokenExternal(tokenAddress()),
                        contract.call_unpauseTokenExternal(tokenAddress()),
                        contract.call_cryptoTransferExternal(hbarTransfers(), tokenTransfer(treasury, receiver, 10))));

        assertThat(traced.statuses()).hasSize(6);
        assertThat(traced.statuses().get(2)).isNotEqualTo(SUCCESS);
        assertThat(traced.statuses().get(0)).isEqualTo(SUCCESS);
        assertThat(traced.statuses().get(1)).isEqualTo(SUCCESS);
        assertThat(traced.statuses().get(3)).isEqualTo(SUCCESS);
        assertThat(traced.statuses().get(4)).isEqualTo(SUCCESS);
        assertThat(traced.statuses().get(5)).isEqualTo(SUCCESS);
        assertThat(traced.states())
                .containsExactly(
                        snapshot(false, 1_000L, 0L),
                        snapshot(false, 900L, 100L),
                        snapshot(false, 850L, 150L),
                        snapshot(false, 850L, 150L),
                        snapshot(true, 850L, 150L),
                        snapshot(false, 850L, 150L),
                        snapshot(false, 840L, 160L));
    }

    @Test
    void traceCallManyRunsACallAfterAnHbarTransferToAnotherAccount() throws Exception {
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var sender = accountEntityWithSufficientBalancePersist();
        accountBalanceRecordsPersist(sender);
        final var payer = Address.fromHexString(getAddressFromEntity(sender));
        final var receiver = accountEntityPersist();
        final var value = 1_000L;
        // The second transaction does not touch the receiver of the first one's hbar. hedera-app checks that its hbar
        // changes net to zero against the readable state, so the trace must commit the transfer before it runs.
        final var traceCall = TraceCall.builder()
                .block(org.hiero.mirror.web3.viewmodel.BlockType.of(
                        domainBuilder.recordFile().persist().getIndex().toString()))
                .transactions(List.of(
                        getContractExecutionParameters(
                                contract.send_transferHbarsToAddress(
                                        getAddressFromEntity(receiver), BigInteger.valueOf(value)),
                                contract,
                                payer,
                                value),
                        getContractExecutionParameters(contract.call_multiplySimpleNumbers(), contract, payer, 0L)))
                .build();

        ContractCallContext.run(ctx -> {
            final var actions = contractDebugService.processTraceCall(traceCall, ctx);

            assertThat(actions)
                    .hasSize(2)
                    .allSatisfy(action -> assertThat(action.getError()).isNull());
            assertThat(Bytes.fromHexString(actions.getLast().getOutput()).toUnsignedBigInteger())
                    .isEqualTo(BigInteger.valueOf(4L));
            final var receiverId = toAccountId(receiver.toEntityId());
            final ReadableKVState<AccountID, Account> readable =
                    state.getReadableStates(TokenService.NAME).get(AccountReadableKVState.STATE_ID);
            final WritableKVState<AccountID, Account> writable =
                    state.getWritableStates(TokenService.NAME).get(AccountReadableKVState.STATE_ID);
            assertThat(readable.get(receiverId)).isNotNull().isEqualTo(writable.get(receiverId));
            return actions;
        });
    }

    @Test
    void traceCallManyUsesTheTokenAnEarlierTransactionCreated() throws Exception {
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var sender = accountEntityWithSufficientBalancePersist();
        accountBalanceRecordsPersist(sender);
        final var payer = Address.fromHexString(getAddressFromEntity(sender));
        final var receiver = accountEntityPersist();
        final var receiverAddress = getAddressFromEntity(receiver);
        final var amount = BigInteger.valueOf(100L);

        // Each transaction after the first works with the token the first one created. Its address exists only in
        // that transaction's committed state, as the contract storage slot the later transactions read it from.
        final var transactions = List.of(
                getContractExecutionParameters(
                        contract.call_createAndStoreFungibleTokenExternal(BigInteger.valueOf(TREASURY_BALANCE)),
                        contract,
                        payer,
                        CREATE_TOKEN_VALUE),
                getContractExecutionParameters(
                        contract.call_associateLastCreatedTokenExternal(receiverAddress), contract, payer, 0L),
                getContractExecutionParameters(
                        contract.call_transferLastCreatedTokenExternal(receiverAddress, amount), contract, payer, 0L),
                getContractExecutionParameters(
                        contract.call_lastCreatedTokenBalanceOf(receiverAddress), contract, payer, 0L));
        final var traceCall = TraceCall.builder()
                .block(org.hiero.mirror.web3.viewmodel.BlockType.of(
                        domainBuilder.recordFile().persist().getIndex().toString()))
                .transactions(transactions)
                .build();

        ContractCallContext.run(ctx -> {
            final var actions = contractDebugService.processTraceCall(traceCall, ctx);

            assertThat(actions)
                    .hasSize(4)
                    .allSatisfy(action -> assertThat(action.getError()).isNull());
            final var createdToken = Address.wrap(
                    Bytes.fromHexString(actions.getFirst().getOutput()).slice(12, Address.SIZE));
            assertThat(createdToken).isNotEqualTo(Address.ZERO);
            // The last transaction reads the receiver's balance of that token through its ERC-20 redirect.
            assertThat(Bytes.fromHexString(actions.getLast().getOutput()).toUnsignedBigInteger())
                    .isEqualTo(amount);

            // The token and both relations exist only in the request's committed state.
            final var createdTokenId =
                    toTokenId(entityIdFromEvmAddress(createdToken).getId());
            final var writable = state.getWritableStates(TokenService.NAME);
            final WritableKVState<TokenID, Token> tokens = writable.get(TOKENS_STATE_ID);
            final WritableKVState<EntityIDPair, TokenRelation> relations = writable.get(TOKEN_RELS_STATE_ID);
            assertThat(tokens.get(createdTokenId)).isNotNull();
            final var treasury =
                    toAccountId(entityIdFromEvmAddress(Address.fromHexString(contract.getContractAddress())));
            assertThat(relationBalance(relations, createdTokenId, treasury))
                    .isEqualTo(TREASURY_BALANCE - amount.longValue());
            assertThat(relationBalance(relations, createdTokenId, toAccountId(receiver.toEntityId())))
                    .isEqualTo(amount.longValue());
            return actions;
        });
    }

    private TraceState trace(final ModificationPrecompileTestContract contract, final List<RemoteFunctionCall<?>> calls)
            throws Exception {
        final var parameters = new ArrayList<ContractExecutionParameters>(calls.size());
        for (final var call : calls) {
            parameters.add(getContractExecutionParameters(
                    call, contract, Address.fromHexString(contract.getContractAddress()), 0L));
        }
        final var states = new ArrayList<KvSnapshot>();
        final var statuses = new ArrayList<ResponseCodeEnum>();
        doAnswer(invocation -> {
                    states.add(readState());
                    final var result = (EvmTransactionResult) invocation.callRealMethod();
                    statuses.add(result.responseCodeEnum());
                    if (statuses.size() == calls.size()) {
                        states.add(readState());
                    }
                    return result;
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());

        final var traceCall = TraceCall.builder()
                .block(org.hiero.mirror.web3.viewmodel.BlockType.of(
                        domainBuilder.recordFile().persist().getIndex().toString()))
                .transactions(parameters)
                .build();
        ContractCallContext.run(ctx -> contractDebugService.processTraceCall(traceCall, ctx));
        return new TraceState(states, statuses);
    }

    /**
     * Reads the request state that later transactions see. A transaction writes into the request write cache, and the
     * trace commits those writes into the read cache, which the readable and writable states both read through.
     */
    private KvSnapshot readState() {
        final var writable = state.getWritableStates(TokenService.NAME);
        final WritableKVState<TokenID, Token> tokens = writable.get(TOKENS_STATE_ID);
        final WritableKVState<EntityIDPair, TokenRelation> relations = writable.get(TOKEN_RELS_STATE_ID);

        final var token = tokens.get(tokenId);
        assertThat(token).isNotNull();

        return new KvSnapshot(
                token.paused(), balance(relations, treasuryRelation), balance(relations, receiverRelation));
    }

    private long relationBalance(
            final WritableKVState<EntityIDPair, TokenRelation> relations,
            final TokenID token,
            final AccountID account) {
        return balance(
                relations,
                EntityIDPair.newBuilder().tokenId(token).accountId(account).build());
    }

    private long balance(final WritableKVState<EntityIDPair, TokenRelation> relations, final EntityIDPair key) {
        final var relation = relations.get(key);
        assertThat(relation).isNotNull();
        return relation.balance();
    }

    private ModificationPrecompileTestContract deployTokenContract() {
        final var contract = testWeb3jService.deploy(ModificationPrecompileTestContract::deploy);
        final var contractId = entityIdFromEvmAddress(Address.fromHexString(contract.getContractAddress()));
        final var receiver = accountEntityPersist();
        final var persisted = fungibleTokenCustomizable(token -> token.treasuryAccountId(contractId)
                .pauseKey(getKeyWithContractId(contract).toByteArray())
                .pauseStatus(TokenPauseStatusEnum.UNPAUSED)
                .totalSupply(TREASURY_BALANCE));
        tokenAccount(account -> account.tokenId(persisted.getTokenId())
                .accountId(contractId.getId())
                .balance(TREASURY_BALANCE));
        tokenAccount(account -> account.tokenId(persisted.getTokenId())
                .accountId(receiver.getId())
                .balance(0L));
        // Pinned latest traces reconstruct balances from the transfer history, as historical calls do.
        domainBuilder
                .tokenTransfer()
                .customize(transfer -> transfer.id(new org.hiero.mirror.common.domain.token.TokenTransfer.Id(
                                domainBuilder.timestamp(), EntityId.of(persisted.getTokenId()), contractId))
                        .amount(TREASURY_BALANCE))
                .persist();

        tokenId = toTokenId(persisted.getTokenId());
        treasuryRelation = relation(toAccountId(contractId));
        receiverRelation = relation(toAccountId(receiver.toEntityId()));
        receiverEntity = receiver;
        tokenEntityId = persisted.getTokenId();
        return contract;
    }

    private EntityIDPair relation(final AccountID accountId) {
        return EntityIDPair.newBuilder().tokenId(tokenId).accountId(accountId).build();
    }

    private String tokenAddress() {
        return getAddressFromEntityId(EntityId.of(tokenEntityId));
    }

    private static TransferList hbarTransfers() {
        return new TransferList(new ArrayList<>());
    }

    private List<TokenTransferList> tokenTransfer(final String treasury, final String receiver, final long amount) {
        return List.of(new TokenTransferList(
                tokenAddress(),
                List.of(
                        new AccountAmount(treasury, BigInteger.valueOf(-amount), false),
                        new AccountAmount(receiver, BigInteger.valueOf(amount), false)),
                new ArrayList<>()));
    }

    private static KvSnapshot snapshot(final boolean paused, final long treasuryBalance, final long receiverBalance) {
        return new KvSnapshot(paused, treasuryBalance, receiverBalance);
    }

    private record KvSnapshot(boolean paused, long treasuryBalance, long receiverBalance) {}

    private record TraceState(List<KvSnapshot> states, List<ResponseCodeEnum> statuses) {}
}
