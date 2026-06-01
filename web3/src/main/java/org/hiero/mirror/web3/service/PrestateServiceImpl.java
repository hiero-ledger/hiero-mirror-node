// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.CustomLog;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.rest.model.AccountTrace;
import org.hiero.mirror.rest.model.PrestateResponse;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.PrestateContext;
import org.hiero.mirror.web3.repository.ContractResultRepository;
import org.hiero.mirror.web3.repository.ContractTransactionHashRepository;
import org.hiero.mirror.web3.repository.EthereumTransactionRepository;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.hiero.mirror.web3.service.model.PrestateRequest;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.hiero.mirror.web3.state.keyvalue.AccountReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.ContractBytecodeReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.ContractStorageReadableKVState;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

@Service
@CustomLog
public class PrestateServiceImpl extends TraceService implements PrestateService {

    public PrestateServiceImpl(
            final ContractDebugService contractDebugService,
            final ContractBytecodeReadableKVState contractBytecodeReadableKVState,
            final ContractStorageReadableKVState contractStorageReadableKVState,
            final CommonEntityAccessor commonEntityAccessor,
            final AccountReadableKVState accountReadableKVState,
            final CommonProperties commonProperties,
            final RecordFileService recordFileService,
            final EthereumTransactionRepository ethereumTransactionRepository,
            final ContractResultRepository contractResultRepository,
            final ContractTransactionHashRepository contractTransactionHashRepository,
            final TransactionRepository transactionRepository) {
        super(
                contractDebugService,
                contractBytecodeReadableKVState,
                contractStorageReadableKVState,
                commonEntityAccessor,
                accountReadableKVState,
                commonProperties,
                recordFileService,
                ethereumTransactionRepository,
                contractResultRepository,
                contractTransactionHashRepository,
                transactionRepository);
    }

    @Override
    public PrestateResponse processPrestateCall(@NonNull final PrestateRequest prestateRequest) {
        return ContractCallContext.run(ctx -> {
            final var params = buildCallServiceParameters(prestateRequest.getTransactionIdOrHashParameter());
            final var prestateContext = new PrestateContext(prestateRequest);

            ctx.setPrestateContext(prestateContext);
            contractDebugService.processPrestateCall(params, prestateContext);

            final var response = new PrestateResponse();

            final var preAccountTraceMap = loadAccountTraces(prestateContext, ctx.getReadCache());
            response.setPre(new ArrayList<>(preAccountTraceMap.values()));

            if (prestateContext.isDiff()) {
                response.setPost(loadPostAccountTraces(prestateContext, preAccountTraceMap, ctx.getWriteCache()));
            }

            return response;
        });
    }

    private List<AccountTrace> loadPostAccountTraces(
            final PrestateContext prestateContext,
            final Map<String, AccountTrace> preAccountTraceMap,
            final Map<Integer, Map<Object, Object>> states) {
        final var postAccountTraceMap = loadAccountTraces(prestateContext, states);

        final var result = new ArrayList<AccountTrace>();
        for (final var entry : postAccountTraceMap.entrySet()) {
            final var preTrace = preAccountTraceMap.get(entry.getKey());
            if (!entry.getValue().equals(preTrace)) {
                result.add(entry.getValue());
            }
        }
        return result;
    }

    private Map<String, AccountTrace> loadAccountTraces(
            final PrestateContext prestateContext, final Map<Integer, Map<Object, Object>> states) {
        final var accountTraceMap = new HashMap<String, AccountTrace>();
        final var accountCache = states.computeIfAbsent(AccountReadableKVState.STATE_ID, _ -> new HashMap<>());

        for (final var accountCacheEntry : accountCache.entrySet()) {
            if (accountCacheEntry.getValue() instanceof Account account) {
                final var accountTrace = new AccountTrace();
                accountTrace.setAddress(
                        !account.alias().equals(Bytes.EMPTY)
                                ? account.alias().toHex()
                                : EntityIdUtils.toEntityId(account.accountId()).toString());
                accountTrace.setBalance(HEX_PREFIX + Long.toHexString(account.tinybarBalance()));
                accountTrace.setNonce(account.ethereumNonce());

                if (account.smartContract()) {
                    populateContractFields(
                            prestateContext, (AccountID) accountCacheEntry.getKey(), accountTrace, states);
                }

                if (accountTrace.getAddress() != null) {
                    accountTraceMap.put(accountTrace.getAddress(), accountTrace);
                }
            }
        }

        return accountTraceMap;
    }

    private void populateContractFields(
            final PrestateContext prestateContext,
            final AccountID accountID,
            final AccountTrace accountTrace,
            final Map<Integer, Map<Object, Object>> states) {
        final var contractId = toProtoContractId(accountID);

        if (prestateContext.isCode()) {
            final var contractBytecodeCache = states.get(ContractBytecodeReadableKVState.STATE_ID);
            if (contractBytecodeCache != null && contractBytecodeCache.get(contractId) instanceof Bytecode bytecode) {
                accountTrace.setCode(bytecode.code().toHex());
            }
        }

        if (prestateContext.isStorage()) {
            final var touchedSlots = new HashMap<String, String>();
            final var contractStorageCache = states.get(ContractStorageReadableKVState.STATE_ID);
            if (contractStorageCache != null) {
                for (final var touchedStorageKey : contractStorageCache.entrySet()) {
                    final var slotKey = (SlotKey) touchedStorageKey.getKey();
                    if (!slotKey.hasContractID() || !slotKey.contractID().equals(toContractId(contractId))) {
                        continue;
                    }
                    if (contractStorageCache.get(slotKey) instanceof SlotValue slotValue) {
                        touchedSlots.put(
                                slotKey.key().toHex(), slotValue.value().toHex());
                    }
                }
            }
            accountTrace.setStorage(touchedSlots);
        }
    }

    private static com.hederahashgraph.api.proto.java.ContractID toProtoContractId(final AccountID accountID) {
        final var builder =
                ContractID.newBuilder().setShardNum(accountID.shardNum()).setRealmNum(accountID.realmNum());
        if (accountID.hasAlias()) {
            return builder.setEvmAddress(ByteString.copyFrom(accountID.alias().toByteArray()))
                    .build();
        }
        return builder.setContractNum(accountID.accountNum()).build();
    }

    private static com.hedera.hapi.node.base.ContractID toContractId(final ContractID contractID) {
        final var builder = com.hedera.hapi.node.base.ContractID.newBuilder()
                .shardNum(contractID.getShardNum())
                .realmNum(contractID.getRealmNum());
        if (contractID.hasEvmAddress()) {
            return builder.evmAddress(Bytes.wrap(contractID.getEvmAddress().toByteArray()))
                    .build();
        }
        return builder.contractNum(contractID.getContractNum()).build();
    }
}
