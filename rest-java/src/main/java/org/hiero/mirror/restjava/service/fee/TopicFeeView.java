// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.transaction.CustomFee;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * Topic plus consensus custom fees in Hedera PBJ form for fee estimation (STATE mode).
 */
@NullMarked
public record TopicFeeView(TopicID topicId, List<CustomFee> customFees) {}
