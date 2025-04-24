// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record SpecSetup(
        Map<String, Object> config,
        List<Map<String, Object>> accounts,
        List<Map<String, Object>> balances,
        List<Map<String, Object>> contracts,
        List<Map<String, Object>> contractresults,
        List<Map<String, Object>> cryptoAllowances,
        @JsonProperty("cryptotransfers") List<Map<String, Object>> cryptoTransfers,
        List<Map<String, Object>> entities,
        List<Map<String, Object>> entityStakes,
        Map<String, String> features,
        @JsonProperty("filedata") List<Map<String, Object>> fileData,
        @JsonProperty("networkstakes") List<Map<String, Object>> networkStakes,
        List<Map<String, Object>> nfts,
        List<Map<String, Object>> recordFiles,
        List<Map<String, Object>> stakingRewardTransfers,
        @JsonProperty("tokenaccounts") List<Map<String, Object>> tokenAccounts,
        List<Map<String, Object>> tokenAllowances,
        List<Map<String, Object>> tokens,
        @JsonProperty("topicmessages") List<Map<String, Object>> topicMessages,
        List<Map<String, Object>> transactions) {}
