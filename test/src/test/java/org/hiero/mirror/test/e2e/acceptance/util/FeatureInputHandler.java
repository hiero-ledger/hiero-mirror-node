// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.util;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractId;
import java.time.DateTimeException;
import java.time.Instant;

public class FeatureInputHandler {
    public static Instant messageQueryDateStringToInstant(String date) {
        return messageQueryDateStringToInstant(date, Instant.now());
    }

    public static Instant messageQueryDateStringToInstant(String date, Instant referenceInstant) {
        Instant refDate;
        try {
            refDate = Instant.parse(date);
        } catch (DateTimeException dtex) {
            refDate = referenceInstant.plusSeconds(Long.parseLong(date));
        }

        return refDate;
    }

    public static String evmAddress(AccountId accountId) {
        return FeatureInputHandler.evmAddress(accountId.shard, accountId.realm, accountId.num);
    }

    public static String evmAddress(ContractId contractId) {
        return FeatureInputHandler.evmAddress(contractId.shard, contractId.realm, contractId.num);
    }

    public static String evmAddress(long shard, long realm, long num) {
        return String.format("0x%08x%016x%016x", shard, realm, num);
    }
}
