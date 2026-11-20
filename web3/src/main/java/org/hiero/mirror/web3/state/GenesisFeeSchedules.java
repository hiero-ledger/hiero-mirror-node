// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.hapi.node.base.CurrentAndNextFeeSchedule;
import com.hedera.hapi.node.base.FeeComponents;
import com.hedera.hapi.node.base.FeeData;
import com.hedera.hapi.node.base.FeeSchedule;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.base.TransactionFeeSchedule;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/**
 * The legacy genesis fee schedule shipped with consensus node 0.78. Consensus node 0.79 removed
 * {@code genesisFeeSchedules}, so file 0.0.111 uses this copy when the database has no row.
 */
@NullMarked
final class GenesisFeeSchedules {

    private static final String RESOURCE = "/genesis/feeSchedules.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GenesisFeeSchedules() {}

    static Bytes protobufBytes() {
        try (var in = GenesisFeeSchedules.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalArgumentException("Legacy genesis fee schedule was not found at " + RESOURCE);
            }
            return CurrentAndNextFeeSchedule.PROTOBUF.toBytes(parse(MAPPER.readTree(in)));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to parse legacy genesis fee schedule", ex);
        }
    }

    private static CurrentAndNextFeeSchedule parse(JsonNode root) {
        final var builder = CurrentAndNextFeeSchedule.newBuilder();
        final var schedules = root.elements();
        while (schedules.hasNext()) {
            final var scheduleContainer = schedules.next();
            if (scheduleContainer.has("currentFeeSchedule")) {
                builder.currentFeeSchedule(parseFeeSchedule(scheduleContainer.get("currentFeeSchedule")));
            } else if (scheduleContainer.has("nextFeeSchedule")) {
                builder.nextFeeSchedule(parseFeeSchedule(scheduleContainer.get("nextFeeSchedule")));
            }
        }
        return builder.build();
    }

    private static FeeSchedule parseFeeSchedule(JsonNode scheduleNode) {
        final var builder = FeeSchedule.newBuilder();
        final var transactionFeeSchedules = new ArrayList<TransactionFeeSchedule>();
        final var children = scheduleNode.elements();
        while (children.hasNext()) {
            final var child = children.next();
            if (child.has("transactionFeeSchedule")) {
                final var transactionFeeSchedule = child.get("transactionFeeSchedule");
                final var fees = new ArrayList<FeeData>();
                final var feeNodes = transactionFeeSchedule.get("fees").elements();
                while (feeNodes.hasNext()) {
                    fees.add(parseFeeData(feeNodes.next()));
                }
                transactionFeeSchedules.add(TransactionFeeSchedule.newBuilder()
                        .hederaFunctionality(HederaFunctionality.fromString(transactionFeeSchedule
                                .get("hederaFunctionality")
                                .asText()))
                        .fees(fees)
                        .build());
            } else if (child.has("expiryTime")) {
                builder.expiryTime(new TimestampSeconds(child.get("expiryTime").asLong()));
            }
        }
        return builder.transactionFeeSchedule(transactionFeeSchedules).build();
    }

    private static FeeData parseFeeData(JsonNode feeNode) {
        final var subType = Optional.ofNullable(feeNode.get("subType"))
                .map(JsonNode::asText)
                .map(SubType::fromString)
                .orElse(SubType.DEFAULT);
        return FeeData.newBuilder()
                .subType(subType)
                .nodedata(parseFeeComponents(feeNode.get("nodedata")))
                .networkdata(parseFeeComponents(feeNode.get("networkdata")))
                .servicedata(parseFeeComponents(feeNode.get("servicedata")))
                .build();
    }

    private static FeeComponents parseFeeComponents(JsonNode componentNode) {
        final var builder = FeeComponents.newBuilder()
                .constant(componentNode.get("constant").asLong())
                .bpt(componentNode.get("bpt").asLong())
                .vpt(componentNode.get("vpt").asLong())
                .rbh(componentNode.get("rbh").asLong())
                .sbh(componentNode.get("sbh").asLong())
                .gas(componentNode.get("gas").asLong())
                .bpr(componentNode.get("bpr").asLong())
                .sbpr(componentNode.get("sbpr").asLong())
                .min(componentNode.get("min").asLong())
                .max(componentNode.get("max").asLong());
        final var transferVolume = componentNode.get("tv");
        if (transferVolume != null && !transferVolume.isNull()) {
            builder.tv(transferVolume.asLong());
        }
        return builder.build();
    }
}
