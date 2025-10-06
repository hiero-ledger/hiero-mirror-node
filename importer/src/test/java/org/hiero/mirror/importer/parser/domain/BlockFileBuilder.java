// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.domain;

import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.util.DomainUtils;

@Named
@RequiredArgsConstructor
public class BlockFileBuilder {

    private final DomainBuilder domainBuilder;

    public BlockFile.BlockFileBuilder items(List<BlockTransaction> blockTransactions) {
        long blockNumber = domainBuilder.number();
        byte[] bytes = domainBuilder.bytes(256);
        String filename = StringUtils.leftPad(Long.toString(blockNumber), 36, "0") + ".blk.gz";
        var blockTimestamp = blockTransactions.isEmpty()
                ? domainBuilder.protoTimestamp()
                : blockTransactions.getLast().getTransactionResult().getConsensusTimestamp();
        var firstConsensusTimestamp = blockTransactions.isEmpty()
                ? domainBuilder.protoTimestamp()
                : blockTransactions.getFirst().getTransactionResult().getConsensusTimestamp();
        byte[] previousHash = domainBuilder.bytes(48);
        long consensusStart = DomainUtils.timestampInNanosMax(firstConsensusTimestamp);
        long consensusEnd = blockTransactions.isEmpty()
                ? consensusStart
                : DomainUtils.timestampInNanosMax(
                        blockTransactions.getLast().getTransactionResult().getConsensusTimestamp());

        return BlockFile.builder()
                .blockHeader(BlockHeader.newBuilder()
                        .setBlockTimestamp(blockTimestamp)
                        .setNumber(blockNumber)
                        .setHapiProtoVersion(SemanticVersion.newBuilder().setMinor(57))
                        .setSoftwareVersion(SemanticVersion.newBuilder().setMinor(57))
                        .build())
                .bytes(bytes)
                .consensusEnd(consensusEnd)
                .consensusStart(consensusStart)
                .count((long) blockTransactions.size())
                .digestAlgorithm(DigestAlgorithm.SHA_384)
                .hash(DomainUtils.bytesToHex(domainBuilder.bytes(48)))
                .index(blockNumber)
                .items(blockTransactions)
                .loadStart(System.currentTimeMillis())
                .name(filename)
                .nodeId(domainBuilder.number())
                .previousHash(DomainUtils.bytesToHex(previousHash))
                .roundEnd(blockNumber + 1)
                .roundStart(blockNumber + 1)
                .size(bytes.length)
                .version(7);
    }
}
