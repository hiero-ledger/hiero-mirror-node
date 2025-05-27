// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import com.hedera.hapi.block.stream.input.protoc.EventHeader;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.hapi.block.stream.protoc.BlockProof;
import com.hedera.hapi.block.stream.protoc.RecordFileItem;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.hiero.block.api.protoc.BlockItemSet;
import org.hiero.block.api.protoc.SubscribeStreamResponse;

class BlockNodeTestBase {

    protected static BlockItem blockHead(long blockNumber) {
        return BlockItem.newBuilder()
                .setBlockHeader(BlockHeader.newBuilder().setNumber(blockNumber).build())
                .build();
    }

    protected static BlockItemSet blockItemSet(long blockNumber) {
        return blockItemSet(blockHead(blockNumber), blockProof());
    }

    protected static BlockItemSet blockItemSet(BlockItem... items) {
        return BlockItemSet.newBuilder().addAllBlockItems(Arrays.asList(items)).build();
    }

    protected static BlockItem blockProof() {
        return BlockItem.newBuilder()
                .setBlockProof(BlockProof.getDefaultInstance())
                .build();
    }

    protected static BlockItem eventHeader() {
        return BlockItem.newBuilder()
                .setEventHeader(EventHeader.getDefaultInstance())
                .build();
    }

    protected static BlockItem recordFileItem() {
        return BlockItem.newBuilder()
                .setRecordFile(RecordFileItem.getDefaultInstance())
                .build();
    }

    protected static SubscribeStreamResponse subscribeStreamResponse(BlockItemSet blockItemSet) {
        return SubscribeStreamResponse.newBuilder().setBlockItems(blockItemSet).build();
    }

    protected static SubscribeStreamResponse subscribeStreamResponse(SubscribeStreamResponse.Code code) {
        return SubscribeStreamResponse.newBuilder().setStatus(code).build();
    }

    protected record ResponsesOrError(List<SubscribeStreamResponse> responses, Throwable error) {

        static ResponsesOrError fromResponse(SubscribeStreamResponse response) {
            return new ResponsesOrError(List.of(response), null);
        }

        static ResponsesOrError fromResponses(List<SubscribeStreamResponse> responses) {
            return new ResponsesOrError(responses, null);
        }

        static ResponsesOrError fromError(Throwable error) {
            return new ResponsesOrError(Collections.emptyList(), error);
        }
    }
}
