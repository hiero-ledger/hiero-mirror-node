// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state;

import static com.hedera.hapi.node.base.ResponseCodeEnum.OPERATION_REPEATED_IN_BUCKET_GROUPS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNPARSEABLE_THROTTLE_DEFINITIONS;
import static java.util.Collections.disjoint;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.node.app.hapi.utils.sysfiles.validation.ExpectedCustomThrottles;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Named
public class CustomThrottleParser {
    public static final Set<HederaFunctionality> EXPECTED_OPS = ExpectedCustomThrottles.ACTIVE_OPS.stream()
            .map(protoOp -> HederaFunctionality.fromProtobufOrdinal(protoOp.getNumber()))
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(HederaFunctionality.class)));

    public ValidatedThrottles parse(final Bytes bytes) throws ParseException {
        try {
            final var throttleDefinitions = ThrottleDefinitions.PROTOBUF.parse(bytes.toReadableSequentialData());
            validate(throttleDefinitions);
            final var successStatus =
                    allExpectedOperations(throttleDefinitions) ? SUCCESS : SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
            return new ValidatedThrottles(throttleDefinitions, successStatus);
        } catch (ParseException e) {
            throw new ParseException(UNPARSEABLE_THROTTLE_DEFINITIONS.name());
        }
    }

    private void validate(ThrottleDefinitions throttleDefinitions) {
        checkForZeroOpsPerSec(throttleDefinitions);
        checkForRepeatedOperations(throttleDefinitions);
    }

    private boolean allExpectedOperations(ThrottleDefinitions throttleDefinitions) {
        final Set<HederaFunctionality> customizedOps = EnumSet.noneOf(HederaFunctionality.class);
        for (final var bucket : throttleDefinitions.throttleBuckets()) {
            for (final var group : bucket.throttleGroups()) {
                customizedOps.addAll(group.operations());
            }
        }
        return customizedOps.containsAll(EXPECTED_OPS);
    }

    private void checkForZeroOpsPerSec(ThrottleDefinitions throttleDefinitions) {
        for (var bucket : throttleDefinitions.throttleBuckets()) {
            for (var group : bucket.throttleGroups()) {
                if (group.milliOpsPerSec() == 0) {
                    throw new HandleException(THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC);
                }
            }
        }
    }

    private void checkForRepeatedOperations(ThrottleDefinitions throttleDefinitions) {
        for (var bucket : throttleDefinitions.throttleBuckets()) {
            final Set<HederaFunctionality> seenSoFar = new HashSet<>();
            for (var group : bucket.throttleGroups()) {
                final var functions = group.operations();
                if (!disjoint(seenSoFar, functions)) {
                    throw new HandleException(OPERATION_REPEATED_IN_BUCKET_GROUPS);
                }
                seenSoFar.addAll(functions);
            }
        }
    }

    public record ValidatedThrottles(
            @Nonnull ThrottleDefinitions throttleDefinitions, @Nonnull ResponseCodeEnum successStatus) {
        public ValidatedThrottles {
            requireNonNull(successStatus);
            requireNonNull(throttleDefinitions);
        }
    }
}
