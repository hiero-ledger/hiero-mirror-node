// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_CONTRACT_STORAGE_VALUE;

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.output.protoc.MapChangeKey;
import com.hedera.hapi.block.stream.output.protoc.MapChangeValue;
import com.hedera.hapi.block.stream.output.protoc.MapDeleteChange;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChange;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.SlotKey;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;
import lombok.experimental.UtilityClass;
import org.hiero.mirror.common.util.CommonUtils;

@UtilityClass
public final class StateChangeTestUtils {

    private static final AtomicLong id = new AtomicLong(new SecureRandom().nextLong(1_000, 1_000_000));

    public static ByteString bytes(int size) {
        return ByteString.copyFrom(CommonUtils.nextBytes(size));
    }

    public static StateChange contractStorageMapDeleteChange(ContractID contractId, ByteString slot) {
        return StateChange.newBuilder()
                .setStateId(STATE_ID_CONTRACT_STORAGE_VALUE)
                .setMapDelete(MapDeleteChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder()
                                .setSlotKeyKey(SlotKey.newBuilder()
                                        .setContractID(contractId)
                                        .setKey(slot))))
                .build();
    }

    public static StateChange contractStorageMapUpdateChange(ContractID contractId, ByteString slot, ByteString value) {
        return StateChange.newBuilder()
                .setStateId(STATE_ID_CONTRACT_STORAGE_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder()
                                .setSlotKeyKey(SlotKey.newBuilder()
                                        .setContractID(contractId)
                                        .setKey(slot)))
                        .setValue(MapChangeValue.newBuilder()
                                .setSlotValueValue(com.hederahashgraph.api.proto.java.SlotValue.newBuilder()
                                        .setValue(value))))
                .build();
    }

    public static AccountID getAccountId() {
        return AccountID.newBuilder().setAccountNum(nextId()).build();
    }

    public static ContractID getContractId() {
        return ContractID.newBuilder().setContractNum(nextId()).build();
    }

    public static FileID getFileId() {
        return FileID.newBuilder().setFileNum(nextId()).build();
    }

    public static TokenID getTokenId() {
        return TokenID.newBuilder().setTokenNum(nextId()).build();
    }

    public static TopicID getTopicId() {
        return TopicID.newBuilder().setTopicNum(nextId()).build();
    }

    public static StateChange makeIdentical(StateChange stateChange) {
        assert stateChange.hasMapUpdate();
        var builder = stateChange.toBuilder();
        builder.getMapUpdateBuilder().setIdentical(true);
        return builder.build();
    }

    private static long nextId() {
        return id.getAndIncrement();
    }
}
