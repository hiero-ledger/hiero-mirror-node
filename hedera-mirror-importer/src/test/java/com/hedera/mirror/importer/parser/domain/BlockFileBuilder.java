// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.domain;

import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Named
@RequiredArgsConstructor
public class BlockFileBuilder {

    private final DomainBuilder domainBuilder;

    public BlockFile.BlockFileBuilder items(List<BlockItem> blockItems) {
        long blockNumber = domainBuilder.number();
        byte[] bytes = domainBuilder.bytes(256);
        String filename = StringUtils.leftPad(Long.toString(blockNumber), 36, "0") + ".blk.gz";
        var firstConsensusTimestamp = blockItems.isEmpty()
                ? domainBuilder.protoTimestamp()
                : blockItems.getFirst().transactionResult().getConsensusTimestamp();
        byte[] previousHash = domainBuilder.bytes(48);
        long consensusStart = DomainUtils.timestampInNanosMax(firstConsensusTimestamp);
        long consensusEnd = blockItems.isEmpty()
                ? consensusStart
                : DomainUtils.timestampInNanosMax(
                        blockItems.getLast().transactionResult().getConsensusTimestamp());

        return BlockFile.builder()
                .blockHeader(BlockHeader.newBuilder()
                        .setFirstTransactionConsensusTime(firstConsensusTimestamp)
                        .setNumber(blockNumber)
                        .setPreviousBlockHash(DomainUtils.fromBytes(previousHash))
                        .setHapiProtoVersion(SemanticVersion.newBuilder().setMinor(57))
                        .setSoftwareVersion(SemanticVersion.newBuilder().setMinor(57))
                        .build())
                .bytes(bytes)
                .consensusEnd(consensusEnd)
                .consensusStart(consensusStart)
                .count((long) blockItems.size())
                .digestAlgorithm(DigestAlgorithm.SHA_384)
                .hash(DomainUtils.bytesToHex(domainBuilder.bytes(48)))
                .index(blockNumber)
                .items(blockItems)
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
