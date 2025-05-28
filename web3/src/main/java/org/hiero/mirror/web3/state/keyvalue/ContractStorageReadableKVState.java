// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static org.hiero.mirror.common.util.DomainUtils.leftPadBytes;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.service.model.ContractSlotValue;
import org.hiero.mirror.web3.state.ContractStateKey;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;

@Named
public class ContractStorageReadableKVState extends AbstractReadableKVState<SlotKey, SlotValue> {

    public static final String KEY = "STORAGE";
    private final ContractStateRepository contractStateRepository;
    private final CacheManager contractStateCacheManager;

    protected ContractStorageReadableKVState(
            final ContractStateRepository contractStateRepository,
            @Qualifier(CACHE_MANAGER_CONTRACT_STATE) CacheManager contractStateCacheManager) {
        super(KEY);
        this.contractStateRepository = contractStateRepository;
        this.contractStateCacheManager = contractStateCacheManager;
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
                .map(t -> contractStateRepository.findStorageByBlockTimestamp(
                        entityId, Bytes32.wrap(keyBytes).trimLeadingZeros().toArrayUnsafe(), t))
                .orElse(findSlotValue(entityId, keyBytes))
                .map(byteArr ->
                        new SlotValue(Bytes.wrap(leftPadBytes(byteArr, Bytes32.SIZE)), Bytes.EMPTY, Bytes.EMPTY))
                .orElse(null);
    }

    private Optional<byte[]> findSlotValue(final Long entityId, final byte[] slotKeyByteArray) {
        final var cache = contractStateCacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            return contractStateRepository.findStorage(entityId, slotKeyByteArray);
        }

        final var cacheKey = new ContractStateKey(entityId, slotKeyByteArray);

        final byte[] cachedValue = cache.get(cacheKey, byte[].class);
        if (cachedValue != null) {
            return Optional.of(cachedValue);
        }

        final var slotValues = contractStateRepository.findSlotsValuesByContractId(entityId);

        byte[] matchedValue = null;
        for (ContractSlotValue slotValue : slotValues) {
            final byte[] slot = slotValue.slot();
            final byte[] value = slotValue.value();
            final var generatedKey = new ContractStateKey(entityId, slot);
            cache.put(generatedKey, value);

            if (generatedKey.equals(cacheKey)) {
                matchedValue = value;
            }
        }

        if (matchedValue != null) {
            return Optional.of(matchedValue);
        }
        return contractStateRepository.findStorage(entityId, slotKeyByteArray);
    }
}
