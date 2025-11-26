// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import com.hedera.hapi.block.stream.input.protoc.EventHeader;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.hapi.block.stream.protoc.BlockProof;
import com.hedera.hapi.block.stream.protoc.RecordFileItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import org.hiero.block.api.protoc.BlockEnd;
import org.hiero.block.api.protoc.BlockItemSet;
import org.hiero.block.api.protoc.SubscribeStreamResponse;

class BlockNodeTestBase {

    protected static BlockItem blockHead(final long blockNumber) {
        return BlockItem.newBuilder()
                .setBlockHeader(BlockHeader.newBuilder().setNumber(blockNumber).build())
                .build();
    }

    protected static BlockItemSet blockItemSet(final long blockNumber) {
        return blockItemSet(blockHead(blockNumber), blockProof());
    }

    protected static BlockItemSet blockItemSet(final BlockItem... items) {
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

    protected List<SubscribeStreamResponse> fullBlockResponses(final long blockNumber) {
        return List.of(subscribeStreamResponse(blockItemSet(blockNumber)), subscribeStreamResponse(blockNumber));
    }

    protected static BlockItem recordFileItem() {
        return BlockItem.newBuilder()
                .setRecordFile(RecordFileItem.getDefaultInstance())
                .build();
    }

    protected static SubscribeStreamResponse subscribeStreamResponse(final long endOfBlockNumber) {
        return SubscribeStreamResponse.newBuilder()
                .setEndOfBlock(BlockEnd.newBuilder().setBlockNumber(endOfBlockNumber))
                .build();
    }

    protected static SubscribeStreamResponse subscribeStreamResponse(final BlockItemSet blockItemSet) {
        return SubscribeStreamResponse.newBuilder().setBlockItems(blockItemSet).build();
    }

    protected static SubscribeStreamResponse subscribeStreamResponse(final SubscribeStreamResponse.Code code) {
        return SubscribeStreamResponse.newBuilder().setStatus(code).build();
    }

    @Getter
    protected static class ResponsesOrError {

        private final List<SubscribeStreamResponse> responses = new ArrayList<>();
        private Throwable error;

        private ResponsesOrError() {}

        protected ResponsesOrError addResponse(final SubscribeStreamResponse response) {
            responses.add(response);
            return this;
        }

        protected ResponsesOrError addResponses(final List<SubscribeStreamResponse> responses) {
            this.responses.addAll(responses);
            return this;
        }

        protected static ResponsesOrError fromError(final Throwable error) {
            final var responseOrError = new ResponsesOrError();
            responseOrError.error = error;
            return responseOrError;
        }

        protected static ResponsesOrError fromResponse(final SubscribeStreamResponse response) {
            final var responseOrError = new ResponsesOrError();
            responseOrError.responses.add(response);
            return responseOrError;
        }

        protected static ResponsesOrError fromResponses(final List<SubscribeStreamResponse> responses) {
            final var responseOrError = new ResponsesOrError();
            responseOrError.responses.addAll(responses);
            return responseOrError;
        }
    }
}
