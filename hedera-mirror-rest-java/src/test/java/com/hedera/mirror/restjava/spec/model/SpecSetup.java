// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record SpecSetup(
        Map<String, Object> config,
        @JsonProperty("assessedcustomfees") List<Map<String, Object>> assessedCustomFees,
        List<Map<String, Object>> accounts,
        List<Map<String, Object>> balances,
        List<Map<String, Object>> contractactions,
        List<Map<String, Object>> contracts,
        // TODO refactor these names and use json property
        List<Map<String, Object>> contractlogs,
        List<Map<String, Object>> contractresults,
        List<Map<String, Object>> contractStateChanges,
        List<Map<String, Object>> contractStates,
        List<Map<String, Object>> cryptoAllowances,
        @JsonProperty("cryptotransfers") List<Map<String, Object>> cryptoTransfers,
        List<Map<String, Object>> entities,
        List<Map<String, Object>> entityStakes,
        List<Map<String, Object>> ethereumtransactions,
        Map<String, String> features,
        @JsonProperty("filedata") List<Map<String, Object>> fileData,
        @JsonProperty("networkstakes") List<Map<String, Object>> networkStakes,
        List<Map<String, Object>> nfts,
        List<Map<String, Object>> recordFiles,
        List<Map<String, Object>> stakingRewardTransfers,
        Map<String, Object> sql,
        @JsonProperty("tokenaccounts") List<Map<String, Object>> tokenAccounts,
        List<Map<String, Object>> tokenAllowances,
        List<Map<String, Object>> tokens,
        @JsonProperty("topicmessages") List<Map<String, Object>> topicMessages,
        List<Map<String, Object>> transactions,
        List<Map<String, Object>> transactionhashes) {}
