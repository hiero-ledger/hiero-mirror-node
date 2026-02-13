// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.ScheduleCreateTransaction;
import com.hedera.hashgraph.sdk.ScheduleDeleteTransaction;
import com.hedera.hashgraph.sdk.ScheduleId;
import com.hedera.hashgraph.sdk.ScheduleSignTransaction;
import com.hedera.hashgraph.sdk.Transaction;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hiero.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import org.hiero.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import org.springframework.core.retry.RetryTemplate;

@Named
public class ScheduleClient extends AbstractNetworkClient {

    private final Collection<ScheduleId> scheduleIds = new CopyOnWriteArrayList<>();

    public ScheduleClient(
            SDKClient sdkClient, RetryTemplate retryTemplate, AcceptanceTestProperties acceptanceTestProperties) {
        super(sdkClient, retryTemplate, acceptanceTestProperties);
    }

    @Override
    public void clean() {
        log.info("Deleting {} schedules", scheduleIds.size());
        deleteAll(scheduleIds, this::deleteSchedule);
    }

    public NetworkTransactionResponse createSchedule(
            ExpandedAccountId payerAccountId,
            Transaction<?> transaction,
            KeyList signatureKeyList,
            Instant expirationTime,
            Boolean waitForExpiry) {
        var memo = getMemo("Create schedule");

        ScheduleCreateTransaction scheduleCreateTransaction = new ScheduleCreateTransaction()
                .setAdminKey(payerAccountId.getPublicKey())
                .setPayerAccountId(payerAccountId.getAccountId())
                .setScheduleMemo(memo)
                .setScheduledTransaction(transaction)
                .setTransactionMemo(memo);
        if (expirationTime != null) {
            scheduleCreateTransaction.setExpirationTime(expirationTime).setWaitForExpiry(waitForExpiry);
        }

        if (signatureKeyList != null) {
            scheduleCreateTransaction
                    .setNodeAccountIds(List.of(sdkClient.getRandomNodeAccountId()))
                    .freezeWith(client);

            // add initial set of required signatures to ScheduleCreate transaction
            signatureKeyList.forEach(k -> {
                PrivateKey pk = (PrivateKey) k;
                byte[] signature = pk.signTransaction(scheduleCreateTransaction);
                scheduleCreateTransaction.addSignature(pk.getPublicKey(), signature);
            });
        }

        var response = executeTransactionAndRetrieveReceipt(scheduleCreateTransaction);
        var scheduleId = response.getReceipt().scheduleId;
        log.info("Created new schedule {} with memo '{}' via {}", scheduleId, memo, response.getTransactionId());
        scheduleIds.add(scheduleId);
        return response;
    }

    public NetworkTransactionResponse signSchedule(ExpandedAccountId expandedAccountId, ScheduleId scheduleId) {
        ScheduleSignTransaction scheduleSignTransaction =
                new ScheduleSignTransaction().setScheduleId(scheduleId).setTransactionMemo(getMemo("Sign schedule"));

        var keyList = KeyList.of(expandedAccountId.getPrivateKey());
        var response = executeTransactionAndRetrieveReceipt(scheduleSignTransaction, keyList);
        log.info("Signed schedule {} via {}", scheduleId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse deleteSchedule(ScheduleId scheduleId) {
        ScheduleDeleteTransaction scheduleDeleteTransaction = new ScheduleDeleteTransaction()
                .setScheduleId(scheduleId)
                .setTransactionMemo(getMemo("Delete schedule"));

        var response = executeTransactionAndRetrieveReceipt(scheduleDeleteTransaction);
        log.info("Deleted schedule {} via {}", scheduleId, response.getTransactionId());
        scheduleIds.remove(scheduleId);
        return response;
    }
}
