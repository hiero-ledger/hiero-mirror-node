// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils.accessors;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;

@SuppressWarnings("deprecation")
public final class RequestBuilderUtils {

    private static TransactionBody.Builder getTransactionBody(
            Long payerAccountNum,
            Long payerRealmNum,
            Long payerShardNum,
            Long nodeAccountNum,
            Long nodeRealmNum,
            Long nodeShardNum,
            long transactionFee,
            Timestamp timestamp,
            Duration transactionDuration,
            boolean generateRecord,
            String memo) {
        AccountID payerAccountID = getAccountIdBuild(payerAccountNum, payerRealmNum, payerShardNum);
        AccountID nodeAccountID = getAccountIdBuild(nodeAccountNum, nodeRealmNum, nodeShardNum);
        return getTxBodyBuilder(
                transactionFee, timestamp, transactionDuration, generateRecord, memo, payerAccountID, nodeAccountID);
    }

    public static TransactionBody.Builder getTxBodyBuilder(
            long transactionFee,
            Timestamp timestamp,
            Duration transactionDuration,
            boolean generateRecord,
            String memo,
            AccountID payerAccountID,
            AccountID nodeAccountID) {
        TransactionID transactionID = getTransactionID(timestamp, payerAccountID);
        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setNodeAccountID(nodeAccountID)
                .setTransactionFee(transactionFee)
                .setTransactionValidDuration(transactionDuration)
                .setGenerateRecord(generateRecord)
                .setMemo(memo);
    }

    public static AccountID getAccountIdBuild(Long accountNum, Long realmNum, Long shardNum) {
        return AccountID.newBuilder()
                .setAccountNum(accountNum)
                .setRealmNum(realmNum)
                .setShardNum(shardNum)
                .build();
    }

    public static AccountID getAccountIdBuild(ByteString alias, Long realmNum, Long shardNum) {
        return AccountID.newBuilder()
                .setAlias(alias)
                .setRealmNum(realmNum)
                .setShardNum(shardNum)
                .build();
    }

    public static TransactionID getTransactionID(Timestamp timestamp, AccountID payerAccountID) {
        return TransactionID.newBuilder()
                .setAccountID(payerAccountID)
                .setTransactionValidStart(timestamp)
                .build();
    }

    public static Transaction getCryptoTransferRequest(
            Long payerAccountNum,
            Long payerRealmNum,
            Long payerShardNum,
            Long nodeAccountNum,
            Long nodeRealmNum,
            Long nodeShardNum,
            long transactionFee,
            Timestamp timestamp,
            Duration transactionDuration,
            boolean generateRecord,
            String memo,
            Long senderActNum,
            Long amountSend,
            Long receiverAcctNum,
            Long amountReceived) {

        AccountAmount a1 = AccountAmount.newBuilder()
                .setAccountID(getAccountIdBuild(senderActNum, 0l, 0l))
                .setAmount(amountSend)
                .build();
        AccountAmount a2 = AccountAmount.newBuilder()
                .setAccountID(getAccountIdBuild(receiverAcctNum, 0l, 0l))
                .setAmount(amountReceived)
                .build();
        TransferList transferList = TransferList.newBuilder()
                .addAccountAmounts(a1)
                .addAccountAmounts(a2)
                .build();
        return getCryptoTransferRequest(
                payerAccountNum,
                payerRealmNum,
                payerShardNum,
                nodeAccountNum,
                nodeRealmNum,
                nodeShardNum,
                transactionFee,
                timestamp,
                transactionDuration,
                generateRecord,
                memo,
                transferList);
    }

    public static Transaction getHbarCryptoTransferRequestToAlias(
            Long payerAccountNum,
            Long payerRealmNum,
            Long payerShardNum,
            Long nodeAccountNum,
            Long nodeRealmNum,
            Long nodeShardNum,
            long transactionFee,
            Timestamp timestamp,
            Duration transactionDuration,
            boolean generateRecord,
            String memo,
            Long senderActNum,
            Long amountSend,
            ByteString receivingAlias,
            Long amountReceived) {

        AccountAmount a1 = AccountAmount.newBuilder()
                .setAccountID(getAccountIdBuild(senderActNum, 0l, 0l))
                .setAmount(amountSend)
                .build();
        AccountAmount a2 = AccountAmount.newBuilder()
                .setAccountID(getAccountIdBuild(receivingAlias, 0l, 0l))
                .setAmount(amountReceived)
                .build();
        TransferList transferList = TransferList.newBuilder()
                .addAccountAmounts(a1)
                .addAccountAmounts(a2)
                .build();
        return getCryptoTransferRequest(
                payerAccountNum,
                payerRealmNum,
                payerShardNum,
                nodeAccountNum,
                nodeRealmNum,
                nodeShardNum,
                transactionFee,
                timestamp,
                transactionDuration,
                generateRecord,
                memo,
                transferList);
    }

    public static Transaction getTokenTransferRequestToAlias(
            Long payerAccountNum,
            Long payerRealmNum,
            Long payerShardNum,
            Long nodeAccountNum,
            Long nodeRealmNum,
            Long nodeShardNum,
            long transactionFee,
            Timestamp timestamp,
            Duration transactionDuration,
            boolean generateRecord,
            String memo,
            Long senderActNum,
            Long tokenNum,
            Long amountSend,
            ByteString receivingAlias,
            Long amountReceived) {

        AccountAmount a1 = AccountAmount.newBuilder()
                .setAccountID(getAccountIdBuild(senderActNum, 0l, 0l))
                .setAmount(amountSend)
                .build();
        AccountAmount a2 = AccountAmount.newBuilder()
                .setAccountID(getAccountIdBuild(receivingAlias, 0l, 0l))
                .setAmount(amountReceived)
                .build();
        NftTransfer a3 = NftTransfer.newBuilder()
                .setReceiverAccountID(
                        AccountID.newBuilder().setAlias(receivingAlias).build())
                .setSenderAccountID(getAccountIdBuild(senderActNum, 0l, 0l))
                .setSerialNumber(1)
                .build();
        TokenTransferList tokenTransferList = TokenTransferList.newBuilder()
                .setToken(TokenID.newBuilder().setTokenNum(tokenNum).build())
                .addTransfers(a1)
                .addTransfers(a2)
                .addNftTransfers(a3)
                .build();
        return getTokenTransferRequest(
                payerAccountNum,
                payerRealmNum,
                payerShardNum,
                nodeAccountNum,
                nodeRealmNum,
                nodeShardNum,
                transactionFee,
                timestamp,
                transactionDuration,
                generateRecord,
                memo,
                tokenTransferList);
    }

    public static Transaction getCryptoTransferRequest(
            Long payerAccountNum,
            Long payerRealmNum,
            Long payerShardNum,
            Long nodeAccountNum,
            Long nodeRealmNum,
            Long nodeShardNum,
            long transactionFee,
            Timestamp timestamp,
            Duration transactionDuration,
            boolean generateRecord,
            String memo,
            TransferList transferList) {
        CryptoTransferTransactionBody cryptoTransferTransaction = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(transferList)
                .build();

        TransactionBody.Builder body = getTransactionBody(
                payerAccountNum,
                payerRealmNum,
                payerShardNum,
                nodeAccountNum,
                nodeRealmNum,
                nodeShardNum,
                transactionFee,
                timestamp,
                transactionDuration,
                generateRecord,
                memo);
        body.setCryptoTransfer(cryptoTransferTransaction);
        byte[] bodyBytesArr = body.build().toByteArray();
        ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
        return getAsTransaction(bodyBytes);
    }

    public static Transaction getTokenTransferRequest(
            Long payerAccountNum,
            Long payerRealmNum,
            Long payerShardNum,
            Long nodeAccountNum,
            Long nodeRealmNum,
            Long nodeShardNum,
            long transactionFee,
            Timestamp timestamp,
            Duration transactionDuration,
            boolean generateRecord,
            String memo,
            TokenTransferList tokenTransferList) {
        CryptoTransferTransactionBody cryptoTransferTransaction = CryptoTransferTransactionBody.newBuilder()
                .addTokenTransfers(tokenTransferList)
                .build();

        TransactionBody.Builder body = getTransactionBody(
                payerAccountNum,
                payerRealmNum,
                payerShardNum,
                nodeAccountNum,
                nodeRealmNum,
                nodeShardNum,
                transactionFee,
                timestamp,
                transactionDuration,
                generateRecord,
                memo);
        body.setCryptoTransfer(cryptoTransferTransaction);
        byte[] bodyBytesArr = body.build().toByteArray();
        ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
        return getAsTransaction(bodyBytes);
    }

    private static Transaction getAsTransaction(ByteString bodyBytes) {
        return Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(bodyBytes)
                        .build()
                        .toByteString())
                .build();
    }
}
