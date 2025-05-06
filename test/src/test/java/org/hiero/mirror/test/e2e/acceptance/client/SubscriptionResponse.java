// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

import com.google.common.base.Stopwatch;
import com.google.common.primitives.Longs;
import com.hedera.hashgraph.sdk.SubscriptionHandle;
import com.hedera.hashgraph.sdk.TopicMessage;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.CustomLog;
import lombok.Data;

@Data
@CustomLog
public class SubscriptionResponse {
    private SubscriptionHandle subscription;
    private List<MirrorHCSResponse> mirrorHCSResponses = new ArrayList<>();
    private Stopwatch elapsedTime;
    private Throwable responseError;

    public void handleConsensusTopicResponse(TopicMessage topicMessage) {
        mirrorHCSResponses.add(new SubscriptionResponse.MirrorHCSResponse(topicMessage, Instant.now()));
        if (log.isTraceEnabled()) {
            String messageAsString = new String(topicMessage.contents, StandardCharsets.UTF_8);
            log.trace(
                    "Received consensus timestamp: {} topic sequence number: , message: {}",
                    topicMessage.consensusTimestamp,
                    topicMessage.sequenceNumber,
                    messageAsString);
        }
    }

    public void handleThrowable(Throwable err, TopicMessage topicMessage) {
        if (err instanceof StatusRuntimeException sre && sre.getStatus().getCode() == Status.Code.CANCELLED) {
            return;
        }

        log.error("GRPC error on subscription : {}", err.getMessage());
        responseError = err;
    }

    public boolean errorEncountered() {
        return responseError != null;
    }

    public boolean hasResponse() {
        return errorEncountered() || !mirrorHCSResponses.isEmpty();
    }

    public void validateReceivedMessages() throws Exception {
        int invalidMessages = 0;
        TopicMessage lastTopicMessage = null;
        for (MirrorHCSResponse mirrorHCSResponseResponse : mirrorHCSResponses) {
            TopicMessage topicMessage = mirrorHCSResponseResponse.getTopicMessage();

            Instant publishInstant = Instant.ofEpochMilli(Longs.fromByteArray(topicMessage.contents));

            long publishSeconds = publishInstant.getEpochSecond();
            long consensusSeconds = topicMessage.consensusTimestamp.getEpochSecond();
            long receiptSeconds = mirrorHCSResponseResponse.getReceivedInstant().getEpochSecond();
            long e2eSeconds = receiptSeconds - publishSeconds;
            long consensusToDelivery = receiptSeconds - consensusSeconds;
            log.trace(
                    "Observed message {} with e2e {}s and consensusToDelivery {}s",
                    topicMessage.consensusTimestamp,
                    e2eSeconds,
                    consensusToDelivery);

            if (!validateResponse(lastTopicMessage, topicMessage)) {
                invalidMessages++;
            }

            lastTopicMessage = topicMessage;
        }

        if (invalidMessages > 0) {
            throw new Exception("Retrieved {} invalid messages in response");
        }

        log.info("{} messages were successfully validated", mirrorHCSResponses.size());
    }

    public boolean validateResponse(TopicMessage previousTopicMessage, TopicMessage currentTopicMessage) {
        boolean validResponse = true;

        if (previousTopicMessage != null && currentTopicMessage != null) {
            if (previousTopicMessage.consensusTimestamp.isAfter(currentTopicMessage.consensusTimestamp)) {
                log.error(
                        "Previous message {}, has a timestamp greater than current message {}",
                        previousTopicMessage.consensusTimestamp,
                        currentTopicMessage.consensusTimestamp);
                validResponse = false;
            }

            if (previousTopicMessage.sequenceNumber + 1 != currentTopicMessage.sequenceNumber) {
                log.error(
                        "Previous message {}, has a sequenceNumber greater than current message {}",
                        previousTopicMessage.sequenceNumber,
                        currentTopicMessage.sequenceNumber);
                validResponse = false;
            }
        }

        return validResponse;
    }

    @Data
    public static class MirrorHCSResponse {
        private final TopicMessage topicMessage;
        private final Instant receivedInstant;
    }
}
