// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.google.common.collect.Range;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.web3j.generated.StorageContract;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StorageContractTest extends AbstractContractCallServiceHistoricalTest {

    @Test
    void testRemoveSlot0() {
        final var contract = testWeb3jService.deployWithoutPersist(StorageContract::deploy);
        final var entity = persistContract(
                testWeb3jService.getContractRuntime(), Address.fromHexString(contract.getContractAddress()));

        var zeroSlot = DomainUtils.leftPadBytes(BigInteger.ZERO.toByteArray(), 32);
        var oneValue = DomainUtils.leftPadBytes(BigInteger.ONE.toByteArray(), 32);

        domainBuilder
                .contractState()
                .customize(c -> c.contractId(entity.toEntityId().getId())
                        .slot(UInt256.fromBytes(Bytes.wrap(zeroSlot)).toArray())
                        .value(UInt256.fromBytes(Bytes.wrap(oneValue)).toArray()))
                .persist();
        final var functionCall = contract.send_setSlot0(BigInteger.ZERO);
        assertDoesNotThrow(functionCall::send);
    }

    @Test
    void testUpdateSlot0() {
        final var contract = testWeb3jService.deployWithoutPersist(StorageContract::deploy);
        persistContract(testWeb3jService.getContractRuntime(), Address.fromHexString(contract.getContractAddress()));

        final var functionCall = contract.send_updateStorage(BigInteger.ONE, BigInteger.valueOf(10));
        assertDoesNotThrow(functionCall::send);
    }

    @ParameterizedTest
    @ValueSource(longs = {100, 150, 200})
    void testUpdateSlot1Historical(long blockNumber) {
        final var historicalRange = setUpHistoricalContext(blockNumber);
        final var contract = testWeb3jService.deployWithoutPersist(StorageContract::deploy);
        final var entity = persistHistoricalContract(
                testWeb3jService.getContractRuntime(),
                Address.fromHexString(contract.getContractAddress()),
                historicalRange);
        domainBuilder
                .contractStateChange()
                .customize(c -> c.contractId(entity.toEntityId().getId())
                        .slot(BigInteger.ONE.toByteArray())
                        .valueRead(BigInteger.ONE.toByteArray())
                        .valueWritten(BigInteger.ONE.toByteArray())
                        .consensusTimestamp(historicalRange.lowerEndpoint()))
                .persist();
        final var functionCall = contract.send_setSlot1(BigInteger.ZERO);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    private Entity persistContract(final byte[] runtimeBytecode, final Address contractAddress) {
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var evmAddress = toEvmAddress(contractEntityId);
        final var entity = domainBuilder
                .entity(contractEntityId)
                .customize(e -> e.evmAddress(evmAddress).type(CONTRACT).balance(1500L))
                .persist();
        domainBuilder
                .contract()
                .customize(c -> c.id(contractEntityId.getId()).runtimeBytecode(runtimeBytecode))
                .persist();
        domainBuilder.recordFile().customize(f -> f.bytes(runtimeBytecode)).persist();
        return entity;
    }

    private Entity persistHistoricalContract(
            final byte[] runtimeBytecode, final Address contractAddress, final Range<Long> historicalRange) {
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var evmAddress = toEvmAddress(contractEntityId);
        final var entity = domainBuilder
                .entity(contractEntityId)
                .customize(e -> e.evmAddress(evmAddress)
                        .type(CONTRACT)
                        .createdTimestamp(historicalRange.lowerEndpoint())
                        .timestampRange(historicalRange)
                        .balance(1500L))
                .persist();
        domainBuilder
                .contract()
                .customize(c -> c.id(contractEntityId.getId()).runtimeBytecode(runtimeBytecode))
                .persist();
        domainBuilder
                .recordFile()
                .customize(f -> f.bytes(runtimeBytecode).consensusEnd(historicalRange.lowerEndpoint()))
                .persist();
        return entity;
    }
}
