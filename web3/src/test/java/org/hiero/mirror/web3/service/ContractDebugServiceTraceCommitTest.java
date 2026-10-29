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
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.token.TokenService;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.WritableKVState;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.TokenPauseStatusEnum;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.hiero.mirror.web3.service.model.TraceBundle;
import org.hiero.mirror.web3.service.model.TraceCall;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract.AccountAmount;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract.TokenTransferList;
import org.hiero.mirror.web3.web3j.generated.ModificationPrecompileTestContract.TransferList;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.RemoteFunctionCall;

/**
 * Runs {@code debug_traceCallMany} as three bundles (1, then 3, then 2 transactions) of real token contract calls.
 * After each transaction settles, the token and token-relation entries are read back through both the writable and
 * readable KV state.
 */
class ContractDebugServiceTraceCommitTest extends AbstractContractCallServiceOpcodeTracerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
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

        contractDebugService.processTraceCall(TraceCall.builder()
                .block(org.hiero.mirror.web3.viewmodel.BlockType.of(
                        domainBuilder.recordFile().persist().getIndex().toString()))
                .bundles(List.of(
                        bundle(parameters.subList(0, 1)),
                        bundle(parameters.subList(1, 4)),
                        bundle(parameters.subList(4, 6))))
                .timeout(TIMEOUT)
                .build());
        return new TraceState(states, statuses);
    }

    private KvSnapshot readState() {
        final var writable = state.getWritableStates(TokenService.NAME);
        final var readable = state.getReadableStates(TokenService.NAME);
        final WritableKVState<TokenID, Token> tokenWritable = writable.get(TOKENS_STATE_ID);
        final ReadableKVState<TokenID, Token> tokenReadable = readable.get(TOKENS_STATE_ID);
        final WritableKVState<EntityIDPair, TokenRelation> relationWritable = writable.get(TOKEN_RELS_STATE_ID);
        final ReadableKVState<EntityIDPair, TokenRelation> relationReadable = readable.get(TOKEN_RELS_STATE_ID);

        final var writtenToken = tokenWritable.get(tokenId);
        final var readToken = tokenReadable.get(tokenId);
        assertThat(writtenToken).isNotNull();
        assertThat(readToken).isNotNull();
        assertThat(writtenToken.paused()).isEqualTo(readToken.paused());

        return new KvSnapshot(
                writtenToken.paused(),
                balance(relationWritable, relationReadable, treasuryRelation),
                balance(relationWritable, relationReadable, receiverRelation));
    }

    private long balance(
            final WritableKVState<EntityIDPair, TokenRelation> writable,
            final ReadableKVState<EntityIDPair, TokenRelation> readable,
            final EntityIDPair key) {
        final var written = writable.get(key);
        final var read = readable.get(key);
        assertThat(written).isNotNull();
        assertThat(read).isNotNull();
        assertThat(written.balance()).isEqualTo(read.balance());
        return written.balance();
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

    private static TraceBundle bundle(final List<ContractExecutionParameters> transactions) {
        return TraceBundle.builder().transactions(List.copyOf(transactions)).build();
    }

    private static KvSnapshot snapshot(final boolean paused, final long treasuryBalance, final long receiverBalance) {
        return new KvSnapshot(paused, treasuryBalance, receiverBalance);
    }

    private record KvSnapshot(boolean paused, long treasuryBalance, long receiverBalance) {}

    private record TraceState(List<KvSnapshot> states, List<ResponseCodeEnum> statuses) {}
}
