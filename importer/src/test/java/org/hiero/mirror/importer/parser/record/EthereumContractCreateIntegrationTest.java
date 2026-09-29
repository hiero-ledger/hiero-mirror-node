// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.converter.WeiBarTinyBarConverter.WEIBARS_TO_TINYBARS;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.ContractID;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.RecordItemBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.ContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A legacy Ethereum contract create whose raw transaction carries the Parent contract creation bytecode.
 * The bytecode sidecar leaves initcode empty because it is already in the calldata.
 */
@RequiredArgsConstructor
class EthereumContractCreateIntegrationTest extends ImporterIntegrationTest {

    private static final long GAS_LIMIT = 5_750_000L;
    private static final long GAS_PRICE_TINYBARS = 50L;
    private static final long INITIAL_BALANCE_TINYBARS = 10_000_000L;
    private static final long MAX_GAS_ALLOWANCE_TINYBARS = 10_000_000_000L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ContractRepository contractRepository;
    private final DomainBuilder domainBuilder;
    private final RecordFileParser recordFileParser;
    private final RecordItemBuilder recordItemBuilder;

    @BeforeEach
    void setup() {
        recordFileParser.clear();
    }

    @Test
    void ethereumContractCreatePersistsRuntimeAndInitBytecode() throws Exception {
        var artifact = parentArtifact();
        var initcode = hexBytecode(artifact.get("bytecode").asText());
        var runtimeBytecode = hexBytecode(artifact.get("deployedBytecode").asText());
        var contractId = recordItemBuilder.contractId();

        var parent = recordItemBuilder
                .ethereumTransaction(true)
                .transactionBody(body -> body.clearCallData()
                        .setEthereumData(ByteString.copyFrom(legacyContractCreate(initcode)))
                        .setMaxGasAllowance(MAX_GAS_ALLOWANCE_TINYBARS))
                .record(record -> record.getReceiptBuilder().setContractID(contractId))
                .sidecarRecords(sidecars -> sidecars.add(TransactionSidecarRecord.newBuilder()
                        .setBytecode(ContractBytecode.newBuilder()
                                .setContractId(contractId)
                                .setInitcode(ByteString.EMPTY))))
                .build();

        var child = childContractCreate(contractId, parent, runtimeBytecode);
        var consensusStart = Math.min(parent.getConsensusTimestamp(), child.getConsensusTimestamp());
        var consensusEnd = Math.max(parent.getConsensusTimestamp(), child.getConsensusTimestamp());
        var recordFile = domainBuilder
                .recordFile()
                .customize(file -> file.consensusStart(consensusStart)
                        .consensusEnd(consensusEnd)
                        .count(2L)
                        .items(List.of(parent, child)))
                .get();

        recordFileParser.parse(recordFile);

        var contract =
                contractRepository.findById(EntityId.of(contractId).getId()).orElseThrow();
        assertThat(contract.getInitcode()).isEqualTo(initcode);
        assertThat(contract.getRuntimeBytecode()).isEqualTo(runtimeBytecode);
    }

    private RecordItem childContractCreate(ContractID contractId, RecordItem parent, byte[] runtimeBytecode) {
        return recordItemBuilder
                .contractCreate(contractId)
                .transactionBody(body -> body.clearFileID().clearInitcode())
                .record(record -> record.setParentConsensusTimestamp(
                        parent.getTransactionRecord().getConsensusTimestamp()))
                .recordItem(item -> item.parent(parent).previous(parent))
                .sidecarRecords(sidecars -> {
                    for (var sidecar : sidecars) {
                        if (sidecar.hasBytecode()) {
                            sidecar.getBytecodeBuilder()
                                    .setContractId(contractId)
                                    .setInitcode(ByteString.EMPTY)
                                    .setRuntimeBytecode(ByteString.copyFrom(runtimeBytecode));
                        }
                    }
                })
                .build();
    }

    /**
     * Same shape as {@code EthereumClient.createContract}: legacy transaction, empty {@code to}, value from the Parent
     * contract initial balance, and creation bytecode as call data.
     */
    private static byte[] legacyContractCreate(byte[] callData) {
        var gasPrice = GAS_PRICE_TINYBARS * WEIBARS_TO_TINYBARS;
        var value = INITIAL_BALANCE_TINYBARS * WEIBARS_TO_TINYBARS;
        return RLPEncoder.list(
                Integers.toBytes(0),
                Integers.toBytes(gasPrice),
                Integers.toBytes(GAS_LIMIT),
                new byte[0],
                Integers.toBytes(value),
                callData,
                HexFormat.of().parseHex("0277"),
                HexFormat.of().parseHex("f9fbff985d374be4a55f296915002eec11ac96f1ce2df183adf992baa9390b2f"),
                HexFormat.of().parseHex("0c1e867cc960d9c74ec2e6a662b7908ec4c8cc9f3091e886bcefbeb2290fb792"));
    }

    private static byte[] hexBytecode(String hex) {
        var payload = hex.startsWith("0x") ? hex.substring(2) : hex;
        return HexFormat.of().parseHex(payload);
    }

    private static JsonNode parentArtifact() throws Exception {
        var path = Path.of("").toAbsolutePath();
        var artifact = path.resolve("../test/src/test/resources/solidity/artifacts/contracts/Parent.sol/Parent.json");
        if (!artifact.toFile().isFile()) {
            artifact = path.resolve("test/src/test/resources/solidity/artifacts/contracts/Parent.sol/Parent.json");
        }
        return OBJECT_MAPPER.readTree(artifact.toFile());
    }
}
