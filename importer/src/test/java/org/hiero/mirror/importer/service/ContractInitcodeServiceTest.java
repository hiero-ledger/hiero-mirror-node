// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.services.stream.proto.ContractBytecode;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@RequiredArgsConstructor
final class ContractInitcodeServiceTest extends ImporterIntegrationTest {

    private final RecordItemBuilder recordItemBuilder;
    private final ContractInitcodeService service;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void childContractCreateMissingInitcode(boolean sidecarExists) {
        // given
        var contractId = recordItemBuilder.contractId();
        var contractBytecode = sidecarExists
                ? ContractBytecode.newBuilder()
                        .setContractId(contractId)
                        .setRuntimeBytecode(recordItemBuilder.bytes(100))
                        .build()
                : null;
        // child contract create has neither initcode nor fileId
        var contractCreate = recordItemBuilder
                .contractCreate(contractId)
                .transactionBody(b -> b.clearFileID().clearInitcode())
                .transactionBodyWrapper(b -> b.getTransactionIDBuilder().setNonce(1))
                .build();

        // when, then
        assertThat(service.get(contractBytecode, contractCreate)).isNull();
    }

    @Test
    void initcodeInSidecar() {
        // given
        var contractId = recordItemBuilder.contractId();
        var initcode = recordItemBuilder.bytes(256);
        var contractBytecode = ContractBytecode.newBuilder()
                .setContractId(contractId)
                .setInitcode(initcode)
                .setRuntimeBytecode(recordItemBuilder.bytes(200))
                .build();
        var contractCreate = recordItemBuilder.contractCreate(contractId).build();

        // when, then
        assertThat(service.get(contractBytecode, contractCreate)).isEqualTo(DomainUtils.toBytes(initcode));
    }

    @Test
    void initcodeInTransactionBody() {
        // given
        var contractBytecode = ContractBytecode.newBuilder()
                .setRuntimeBytecode(recordItemBuilder.bytes(100))
                .build();
        var initcode = recordItemBuilder.bytes(128);
        var recordItem = recordItemBuilder
                .contractCreate()
                .transactionBody(b -> b.setInitcode(initcode))
                .build();

        // when, then
        assertThat(service.get(contractBytecode, recordItem)).isEqualTo(DomainUtils.toBytes(initcode));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void missing(boolean sidecarExists) {
        // given: contractcreate recordItem from recordstream, contract created from file, however no bytecode sidecar
        // or no initcode in the sidecar record
        var contractId = recordItemBuilder.contractId();
        var contractBytecode = sidecarExists
                ? ContractBytecode.newBuilder()
                        .setContractId(contractId)
                        .setRuntimeBytecode(recordItemBuilder.bytes(100))
                        .build()
                : null;
        var contractCreate = recordItemBuilder.contractCreate(contractId).build();

        // when, then
        assertThat(service.get(contractBytecode, contractCreate)).isNull();
    }

    @Test
    void nonContractCreateTransaction() {
        var recordItem = recordItemBuilder.contractCall().build();
        assertThat(service.get(null, recordItem)).isNull();
    }

    @Test
    void readFromFile() {
        // given
        var contractId = recordItemBuilder.contractId();
        var contractBytecode = ContractBytecode.newBuilder()
                .setContractId(contractId)
                .setRuntimeBytecode(recordItemBuilder.bytes(100))
                .build();
        var fileId = recordItemBuilder.fileId();
        var recordItem = recordItemBuilder
                .contractCreate(contractId)
                .transactionBody(b -> b.setFileID(fileId))
                .recordItem(r -> r.blockstream(true))
                .build();
        var fileData = domainBuilder
                .fileData()
                .customize(b -> b.entityId(EntityId.of(fileId)))
                .persist();

        // when, then
        assertThat(service.get(contractBytecode, recordItem)).isEqualTo(fileData.getFileData());
    }

    @Test
    void readFromFileNotFound() {
        // given
        var contractId = recordItemBuilder.contractId();
        var contractBytecode = ContractBytecode.newBuilder()
                .setContractId(contractId)
                .setRuntimeBytecode(recordItemBuilder.bytes(100))
                .build();
        var recordItem = recordItemBuilder
                .contractCreate(contractId)
                .recordItem(r -> r.blockstream(true))
                .build();

        // when, then
        assertThat(service.get(contractBytecode, recordItem)).isNull();
    }
}
