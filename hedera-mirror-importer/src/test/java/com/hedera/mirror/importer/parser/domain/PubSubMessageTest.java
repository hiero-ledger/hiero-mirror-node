// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.importer.util.Utility;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.CustomFeeLimit;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

class PubSubMessageTest {
    private static final Long DEFAULT_TIMESTAMP_LONG = 123456789L;
    private static final Timestamp TIMESTAMP =
            Utility.instantToTimestamp(Instant.ofEpochSecond(0L, DEFAULT_TIMESTAMP_LONG));
    private static final AccountID ACCOUNT_ID =
            AccountID.newBuilder().setAccountNum(10L).build();
    private static final TopicID TOPIC_ID =
            TopicID.newBuilder().setTopicNum(20L).build();
    private static final TokenID TOKEN_ID =
            TokenID.newBuilder().setTokenNum(30L).build();
    private static final ByteString BYTE_STRING = ByteString.copyFromUtf8("abcdef");
    private static final Long INT64_VALUE = 100_000_000L;

    private static TransactionBody getTransactionBody() {
        return TransactionBody.newBuilder()
                .addMaxCustomFees(CustomFeeLimit.newBuilder()
                        .addFees(FixedFee.newBuilder().setAmount(123L).setDenominatingTokenId(TOKEN_ID))
                        .setAccountId(ACCOUNT_ID))
                .setTransactionID(TransactionID.newBuilder()
                        .setAccountID(ACCOUNT_ID)
                        .setScheduled(false)
                        .setTransactionValidStart(TIMESTAMP)
                        .build())
                .setNodeAccountID(ACCOUNT_ID)
                .setTransactionFee(INT64_VALUE)
                .setTransactionValidDuration(
                        Duration.newBuilder().setSeconds(INT64_VALUE).build())
                .setMemoBytes(BYTE_STRING)
                .setConsensusSubmitMessage(ConsensusSubmitMessageTransactionBody.newBuilder()
                        .setTopicID(TOPIC_ID)
                        .setMessage(BYTE_STRING)
                        .build())
                .build();
    }

    private static SignatureMap getSignatureMap() {
        return SignatureMap.newBuilder()
                .addSigPair(SignaturePair.newBuilder()
                        .setEd25519(BYTE_STRING)
                        .setPubKeyPrefix(BYTE_STRING)
                        .build())
                .build();
    }

    private static String getExpectedTransactionJson() {
        return """
                "transaction" : {
                  "body": {
                    "transactionID": {
                      "transactionValidStart": {
                        "seconds": "0",
                        "nanos": 123456789
                      },
                      "accountID": {
                        "shardNum": "0",
                        "realmNum": "0",
                        "accountNum": "10"
                      },
                      "scheduled": false,
                      "nonce": 0
                    },
                    "maxCustomFees": [
                      {
                        "fees": [
                          {
                            "amount": "123",
                            "denominatingTokenId": {
                              "shardNum": "0",
                              "realmNum": "0",
                              "tokenNum": "30"
                            }
                          }
                        ],
                        "accountId": {
                          "shardNum": "0",
                          "realmNum": "0",
                          "accountNum": "10"
                        }
                      }
                    ],
                    "nodeAccountID": {
                      "shardNum": "0",
                      "realmNum": "0",
                      "accountNum": "10"
                    },
                    "transactionFee": "100000000",
                    "transactionValidDuration": {
                      "seconds": "100000000"
                    },
                    "generateRecord": false,
                    "memo": "abcdef",
                    "consensusSubmitMessage": {
                      "topicID": {
                        "shardNum": "0",
                        "realmNum": "0",
                        "topicNum": "20"
                      },
                      "message": "YWJjZGVm"
                    }
                  },
                  "sigMap": {
                    "sigPair": [{
                      "pubKeyPrefix": "YWJjZGVm",
                      "ed25519": "YWJjZGVm"
                    }]
                  }
                }
                """;
    }

    private static TransactionRecord getTransactionRecord() {
        return TransactionRecord.newBuilder()
                .setConsensusTimestamp(TIMESTAMP)
                .setEthereumHash(BYTE_STRING)
                .setReceipt(TransactionReceipt.newBuilder()
                        .setStatus(ResponseCodeEnum.SUCCESS)
                        .setTopicRunningHash(BYTE_STRING)
                        .setTopicSequenceNumber(INT64_VALUE)
                        .build())
                .setTransactionHash(BYTE_STRING)
                .setTransferList(TransferList.newBuilder()
                        .addAccountAmounts(AccountAmount.newBuilder()
                                .setAccountID(ACCOUNT_ID)
                                .setAmount(INT64_VALUE)
                                .build())
                        .addAccountAmounts(AccountAmount.newBuilder()
                                .setAccountID(ACCOUNT_ID)
                                .setAmount(INT64_VALUE)
                                .build())
                        .build())
                .build();
    }

    private static String getExpectedTransactionRecord() {
        return """
                "transactionRecord" : {
                  "receipt": {
                    "status": "SUCCESS",
                    "topicSequenceNumber": "100000000",
                    "topicRunningHash": "YWJjZGVm",
                    "topicRunningHashVersion": "0",
                    "newTotalSupply": "0",
                    "serialNumbers": [],
                    "nodeId": "0"
                  },
                  "transactionHash": "YWJjZGVm",
                  "consensusTimestamp": {
                    "seconds": "0",
                    "nanos": 123456789
                  },
                 "newPendingAirdrops": [],
                 "memo": "",
                 "transactionFee": "0",
                  "transferList": {
                    "accountAmounts": [{
                      "accountID": {
                        "shardNum": "0",
                        "realmNum": "0",
                        "accountNum": "10"
                      },
                      "amount": "100000000",
                      "isApproval": false
                    }, {
                      "accountID": {
                        "shardNum": "0",
                        "realmNum": "0",
                        "accountNum": "10"
                      },
                      "amount": "100000000",
                      "isApproval": false
                    }]
                  },
                  "tokenTransferLists":[],
                  "assessedCustomFees":[],
                  "automaticTokenAssociations":[],
                  "alias":"",
                  "ethereumHash":"YWJjZGVm",
                  "paidStakingRewards":[],
                  "evmAddress":""
                }
                """;
    }

    @Test
    void testSerializationAllFieldsSet() throws Exception {
        Iterable<AccountAmount> nonFeeTransfers = Lists.newArrayList(
                AccountAmount.newBuilder()
                        .setAccountID(ACCOUNT_ID)
                        .setAmount(INT64_VALUE)
                        .build(),
                AccountAmount.newBuilder()
                        .setAccountID(ACCOUNT_ID)
                        .setAmount(INT64_VALUE)
                        .build());
        PubSubMessage pubSubMessage = new PubSubMessage(
                DEFAULT_TIMESTAMP_LONG,
                EntityId.of(TOPIC_ID),
                10,
                new PubSubMessage.Transaction(getTransactionBody(), getSignatureMap()),
                getTransactionRecord(),
                nonFeeTransfers);
        ObjectMapper objectMapper = new ObjectMapper();
        String actual = objectMapper.writeValueAsString(pubSubMessage);
        String pre =
                """
                {
                  "consensusTimestamp" : 123456789,
                  "entity" : {
                    "shardNum" : 0,
                    "realmNum" : 0,
                    "entityNum" : 20,
                    "type" : 0
                  },
                  "transactionType" : 10,
                """;
        String post =
                """
                  "nonFeeTransfers" : [ {
                    "accountID": {
                      "shardNum": "0",
                      "realmNum": "0",
                      "accountNum": "10"
                      },
                    "amount": "100000000",
                    "isApproval": false
                  }, {
                    "accountID": {
                      "shardNum": "0",
                      "realmNum": "0",
                      "accountNum": "10"
                      },
                    "amount": "100000000",
                    "isApproval": false
                  } ]
                };
                """;
        String expected = pre + getExpectedTransactionJson() + "," + getExpectedTransactionRecord() + "," + post;
        JSONAssert.assertEquals(expected, actual, true);
    }

    @Test
    void testSerializationWithNullFields() throws Exception {
        PubSubMessage pubSubMessage = new PubSubMessage(
                DEFAULT_TIMESTAMP_LONG,
                null,
                10,
                new PubSubMessage.Transaction(getTransactionBody(), getSignatureMap()),
                getTransactionRecord(),
                null);
        ObjectMapper objectMapper = new ObjectMapper();
        String actual = objectMapper.writeValueAsString(pubSubMessage);
        String expected =
                """
                {
                  "consensusTimestamp" : 123456789,
                  "transactionType" : 10,
                """
                        + getExpectedTransactionJson()
                        + "," + getExpectedTransactionRecord()
                        + "}";
        JSONAssert.assertEquals(expected, actual, true);
    }
}
