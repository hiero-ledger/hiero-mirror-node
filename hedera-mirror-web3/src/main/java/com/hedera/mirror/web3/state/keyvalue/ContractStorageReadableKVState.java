/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.web3.state.keyvalue;

import static com.hedera.mirror.web3.state.Utils.padToBytes32;

import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.ContractStateRepository;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;

@Named
public class ContractStorageReadableKVState extends AbstractReadableKVState<SlotKey, SlotValue> {

    public static final String KEY = "STORAGE";
    private final ContractStateRepository contractStateRepository;

    protected ContractStorageReadableKVState(final ContractStateRepository contractStateRepository) {
        super(KEY);
        this.contractStateRepository = contractStateRepository;
    }

    @Override
    protected SlotValue readFromDataSource(@Nonnull SlotKey slotKey) {
        if (!slotKey.hasContractID()) {
            return null;
        }

        final var timestamp = ContractCallContext.get().getTimestamp();
        final var contractID = slotKey.contractID();
        final var entityId = EntityIdUtils.entityIdFromContractId(contractID).getId();
        final var keyBytes = slotKey.key().toByteArray();
        return timestamp
                .map(t -> contractStateRepository.findStorageByBlockTimestamp(entityId, keyBytes, t))
                .orElse(contractStateRepository.findStorage(entityId, keyBytes))
                .map(byteArr -> new SlotValue(Bytes.wrap(padToBytes32(byteArr)), Bytes.EMPTY, Bytes.EMPTY))
                .orElse(null);
    }
}
