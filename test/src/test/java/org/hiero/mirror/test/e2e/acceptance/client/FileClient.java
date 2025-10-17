// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

import com.hedera.hashgraph.sdk.FileAppendTransaction;
import com.hedera.hashgraph.sdk.FileCreateTransaction;
import com.hedera.hashgraph.sdk.FileDeleteTransaction;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.FileUpdateTransaction;
import com.hedera.hashgraph.sdk.KeyList;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hiero.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import org.hiero.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import org.springframework.retry.support.RetryTemplate;

@Named
public class FileClient extends AbstractNetworkClient {

    private final Collection<FileId> fileIds = new CopyOnWriteArrayList<>();

    public FileClient(
            SDKClient sdkClient, RetryTemplate retryTemplate, AcceptanceTestProperties acceptanceTestProperties) {
        super(sdkClient, retryTemplate, acceptanceTestProperties);
    }

    @Override
    public void clean() {
        log.info("Deleting {} files", fileIds.size());
        deleteAll(fileIds, this::deleteFile);
    }

    public NetworkTransactionResponse createFile(byte[] content) {
        var memo = getMemo("Create file");
        FileCreateTransaction fileCreateTransaction = new FileCreateTransaction()
                .setKeys(sdkClient.getExpandedOperatorAccountId().getPublicKey())
                .setContents(content)
                .setFileMemo(memo)
                .setTransactionMemo(memo);

        var keyList = KeyList.of(sdkClient.getExpandedOperatorAccountId().getPrivateKey());
        var response = executeTransactionAndRetrieveReceipt(fileCreateTransaction, keyList);

        var fileId = response.getReceipt().fileId;
        log.info("Created new file {} with {} B via {}", fileId, content.length, memo, response.getTransactionId());
        fileIds.add(fileId);
        return response;
    }

    public NetworkTransactionResponse updateFile(FileId fileId, byte[] contents) {
        var memo = getMemo("Update file");
        FileUpdateTransaction fileUpdateTransaction =
                new FileUpdateTransaction().setFileId(fileId).setFileMemo(memo).setTransactionMemo(memo);

        int count = 0;
        if (contents != null) {
            fileUpdateTransaction.setContents(contents);
            count = contents.length;
        }

        var response = executeTransactionAndRetrieveReceipt(fileUpdateTransaction);
        log.info("Updated file {} with {} B via {}", fileId, count, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse appendFile(FileId fileId, byte[] contents) {
        var memo = getMemo("Append file");
        FileAppendTransaction fileAppendTransaction = new FileAppendTransaction()
                .setFileId(fileId)
                .setContents(contents)
                .setTransactionMemo(memo)
                .setChunkSize(4096);

        var response = executeTransactionAndRetrieveReceipt(fileAppendTransaction);
        log.info("Appended {} B to file {} via {}", contents.length, fileId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse deleteFile(FileId fileId) {
        var memo = getMemo("Delete file");
        FileDeleteTransaction fileUpdateTransaction =
                new FileDeleteTransaction().setFileId(fileId).setTransactionMemo(memo);

        var response = executeTransactionAndRetrieveReceipt(fileUpdateTransaction);
        log.info("Deleted file {} via {}", fileId, response.getTransactionId());
        fileIds.remove(fileId);
        return response;
    }
}
