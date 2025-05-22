// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.converter;

import com.hedera.hashgraph.sdk.CustomFee;
import com.hedera.hashgraph.sdk.CustomFixedFee;
import com.hedera.hashgraph.sdk.CustomFractionalFee;
import com.hedera.hashgraph.sdk.TokenId;
import io.cucumber.java.DataTableType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.rest.model.AssessedCustomFee;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import org.hiero.mirror.test.e2e.acceptance.steps.TokenFeature;

@RequiredArgsConstructor
public class CustomFeesConverter {

    private final AccountClient accountClient;
    private final TokenFeature tokenFeature;

    @DataTableType
    public AssessedCustomFee mirrorAssessedCustomFee(Map<String, String> entry) {
        var collector = accountClient.getAccount(AccountNameEnum.valueOf(entry.get("collector")));
        var effectivePayer = entry.get("effectivePayer");
        var assessedCustomFee = new AssessedCustomFee();

        assessedCustomFee.setAmount(Long.parseLong(entry.get("amount")));
        assessedCustomFee.setCollectorAccountId(collector.getAccountId().toString());
        if (StringUtils.isNotEmpty(effectivePayer)) {
            var effectivePayerAccountId = accountClient.getAccount(AccountNameEnum.valueOf(effectivePayer));
            assessedCustomFee.setEffectivePayerAccountIds(
                    List.of(effectivePayerAccountId.getAccountId().toString()));
        }
        assessedCustomFee.setTokenId(getToken(entry.get("token")));
        return assessedCustomFee;
    }

    @DataTableType
    public CustomFee customFee(Map<String, String> entry) {
        String amount = entry.get("amount");
        var collector = accountClient.getAccount(AccountNameEnum.valueOf(entry.get("collector")));

        if (StringUtils.isNotEmpty(amount)) {
            var fixedFee = new CustomFixedFee();
            fixedFee.setAmount(Long.parseLong(amount));
            fixedFee.setFeeCollectorAccountId(collector.getAccountId());
            fixedFee.setDenominatingTokenId(getTokenId(entry.get("token")));
            return fixedFee;
        } else {
            var fractionalFee = new CustomFractionalFee();
            fractionalFee.setNumerator(Long.parseLong(entry.get("numerator")));
            fractionalFee.setDenominator(Long.parseLong(entry.get("denominator")));
            fractionalFee.setFeeCollectorAccountId(collector.getAccountId());
            fractionalFee.setMax(getValueOrDefault(entry.get("maximum")));
            fractionalFee.setMin(getValueOrDefault(entry.get("minimum")));
            return fractionalFee;
        }
    }

    private String getToken(String tokenIndex) {
        return Optional.ofNullable(getTokenId(tokenIndex))
                .map(TokenId::toString)
                .orElse(null);
    }

    private TokenId getTokenId(String tokenIndex) {
        return StringUtils.isNotEmpty(tokenIndex) ? tokenFeature.getTokenId() : null;
    }

    private long getValueOrDefault(String value) {
        return StringUtils.isNotEmpty(value) ? Long.parseLong(value) : 0;
    }
}
