// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.common;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.exception.InvalidParametersException;

public record TransactionIdParameter(EntityId payerAccountId, Instant validStart)
        implements TransactionIdOrHashParameter {

    private static final Pattern TRANSACTION_ID_PATTERN =
            Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)-(\\d{1,19})-(\\d{1,9})$");

    public static TransactionIdParameter valueOf(String transactionId) throws InvalidParametersException {
        if (transactionId == null) {
            return null;
        }

        Matcher matcher = TRANSACTION_ID_PATTERN.matcher(transactionId);
        if (!matcher.matches()) {
            return null;
        }

        try {
            long shard = Long.parseLong(matcher.group(1));
            long realm = Long.parseLong(matcher.group(2));
            long num = Long.parseLong(matcher.group(3));
            long seconds = Long.parseLong(matcher.group(4));
            int nanos = Integer.parseInt(matcher.group(5));

            EntityId entityId = EntityId.of(shard, realm, num);
            Instant validStart = Instant.ofEpochSecond(seconds, nanos);

            return new TransactionIdParameter(entityId, validStart);
        } catch (Exception e) {
            throw new InvalidParametersException(e.getMessage());
        }
    }
}
