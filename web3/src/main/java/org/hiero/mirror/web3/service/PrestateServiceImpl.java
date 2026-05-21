// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.rest.model.AccountTrace;
import org.hiero.mirror.rest.model.PrestateResponse;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.PrestateContext;
import org.hiero.mirror.web3.service.model.PrestateRequest;
import org.hyperledger.besu.datatypes.Address;

public class PrestateServiceImpl extends TraceService implements PrestateService {

    @Override
    public PrestateResponse processPrestateCall(final PrestateRequest prestateRequest) {
        return ContractCallContext.run(ctx -> {
            final var params = buildCallServiceParameters(prestateRequest.getTransactionIdOrHashParameter());
            final var prestateContext = new PrestateContext(prestateRequest);

            ctx.setPrestateContext(prestateContext);
            contractDebugService.processPrestateCall(params, prestateContext);

            final var touchedAccounts = prestateContext.getTouchedAccounts();
            final var touchedStorages = prestateContext.getTouchedStorageKeys();

            final var response = new PrestateResponse();

            // Reset context to fetch pre-execution state
            ctx.reset();

            final var preAccountTraces = loadAccountTraces(prestateContext, touchedAccounts, touchedStorages);
            response.setPre(preAccountTraces);

            contractDebugService.processPrestateCall(params, prestateContext);

            if (prestateContext.isDiff()) {
                response.setPost(
                        loadPostAccountTraces(prestateContext, touchedAccounts, touchedStorages, preAccountTraces));
            }

            return response;
        });
    }

    private List<AccountTrace> loadPostAccountTraces(
            final PrestateContext prestateContext,
            final Set<Address> touchedAccounts,
            final Map<Address, Set<String>> touchedStorages,
            final List<AccountTrace> preAccountTraces) {
        final var postAccountTraces = loadAccountTraces(prestateContext, touchedAccounts, touchedStorages);

        for (final var postAccountTracesEntry : postAccountTraces) {
            for (final var preAccountTracesEntry : preAccountTraces) {
                if (postAccountTracesEntry.getAddress() != null
                        && preAccountTracesEntry.getAddress() != null
                        && postAccountTracesEntry.getAddress().equals(preAccountTracesEntry.getAddress())) {
                    if (postAccountTracesEntry.getBalance() != null
                            && preAccountTracesEntry.getBalance() != null
                            && postAccountTracesEntry.getBalance().equals(preAccountTracesEntry.getBalance())) {
                        postAccountTracesEntry.setBalance(null);
                    }

                    if (postAccountTracesEntry.getNonce() != null
                            && preAccountTracesEntry.getNonce() != null
                            && postAccountTracesEntry.getNonce().equals(preAccountTracesEntry.getNonce())) {
                        postAccountTracesEntry.setNonce(null);
                    }

                    if (postAccountTracesEntry.getCode() != null
                            && preAccountTracesEntry.getCode() != null
                            && postAccountTracesEntry.getCode().equals(preAccountTracesEntry.getCode())) {
                        postAccountTracesEntry.setCode(null);
                    }

                    final var postAccountStorage = postAccountTracesEntry.getStorage();
                    final var preAccountStorage = preAccountTracesEntry.getStorage();

                    if (postAccountStorage != null && preAccountStorage != null) {
                        for (final var preAccountStorageEntry : preAccountStorage.entrySet()) {
                            final var postAccountStorageEntryValue =
                                    postAccountStorage.get(preAccountStorageEntry.getKey());

                            if (preAccountStorageEntry.getValue().equals(postAccountStorageEntryValue)) {
                                postAccountStorage.remove(preAccountStorageEntry.getKey());
                            }
                        }
                    }
                }
            }
        }

        return postAccountTraces;
    }

    private List<AccountTrace> loadAccountTraces(
            final PrestateContext prestateContext,
            final Set<Address> touchedAccounts,
            final Map<Address, Set<String>> touchedStorageKeys) {
        final var preAccountTraces = new ArrayList<AccountTrace>();
        for (final var touchedAccount : touchedAccounts) {
            final var accountTrace = new AccountTrace();

            final var entity =
                    commonEntityAccessor.get(touchedAccount, Optional.empty()).orElse(null);
            if (entity != null) {
                final var type = entity.getType();

                if (EntityType.ACCOUNT.equals(type)) {
                    populateCommonFields(touchedAccount, accountTrace, entity);
                } else if (EntityType.CONTRACT.equals(type)) {
                    populateContractFields(
                            prestateContext, touchedAccount, accountTrace, touchedStorageKeys.get(touchedAccount));
                }
            }

            if (accountTrace.getAddress() != null) {
                preAccountTraces.add(accountTrace);
            }
        }

        return preAccountTraces;
    }

    private void populateCommonFields(
            final Address touchedAccount, final AccountTrace accountTrace, final Entity entity) {
        final var accountId = EntityIdUtils.toAccountID(touchedAccount);
        if (accountId != null) {
            final var account = accountReadableKVState.get(accountId);

            if (account != null) {
                accountTrace.setAddress(touchedAccount.toHexString());
                accountTrace.setBalance(HEX_PREFIX + Long.toHexString(entity.getBalance()));
                accountTrace.setNonce(account.ethereumNonce());
            }
        }
    }

    private void populateContractFields(
            final PrestateContext prestateContext,
            final Address touchedAccount,
            final AccountTrace accountTrace,
            final Set<String> touchedStorageKeys) {
        final var contractId = EntityIdUtils.toContractID(touchedAccount);

        if (contractId != null) {
            if (prestateContext.isCode()) {
                final var bytecode = contractBytecodeReadableKVState.get(contractId);

                if (bytecode != null) {
                    accountTrace.setCode(bytecode.toString());
                }
            }
            if (prestateContext.isStorage() && touchedStorageKeys != null && !touchedStorageKeys.isEmpty()) {
                final var touchedSlots = new HashMap<String, String>();
                for (final var touchedStorageKey : touchedStorageKeys) {
                    final var slotKey = SlotKey.newBuilder()
                            .contractID(contractId)
                            .key(Bytes.fromHex(touchedStorageKey))
                            .build();
                    final var slotValue = contractStorageReadableKVState.get(slotKey);

                    if (slotValue != null) {
                        touchedSlots.put(
                                slotKey.key().toHex(), slotValue.value().toHex());
                    }
                }

                accountTrace.setStorage(touchedSlots);
            }
        }
    }
}
