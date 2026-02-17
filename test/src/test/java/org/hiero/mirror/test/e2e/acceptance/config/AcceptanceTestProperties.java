// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.config;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.LedgerId;
import jakarta.inject.Named;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.test.e2e.acceptance.client.ContractClient.NodeNameEnum;
import org.hiero.mirror.test.e2e.acceptance.props.NodeProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Named
@ConfigurationProperties(prefix = "hiero.mirror.test.acceptance")
@Data
@RequiredArgsConstructor
@Validated
@EnableConfigurationProperties(CommonProperties.class)
public final class AcceptanceTestProperties {
    private static final String DEFAULT_OPERATOR_ID = "0.0.2";

    private final FeatureProperties featureProperties;
    private final RestProperties restProperties;
    private final WebClientProperties webClientProperties;
    private final CommonProperties commonProperties;

    @NotNull
    private Duration backOffPeriod = Duration.ofMillis(5000);

    @NotNull
    @DecimalMax("1000000")
    @DecimalMin("0.1")
    private BigDecimal childAccountBalance = BigDecimal.valueOf(0.1); // Amount in USD

    // A new account is usually necessary since shared accounts like 0.0.2 might reach maxTokensPerAccount, etc
    private boolean createOperatorAccount = true;

    private boolean emitBackgroundMessages = false;

    @Min(1)
    private int maxNodes = 10;

    @Max(5)
    private int maxRetries = 2;

    @Min(1L)
    private long maxTinyBarTransactionFee = Hbar.from(50).toTinybars();

    @DurationMin(seconds = 1)
    @NotNull
    private Duration messageTimeout = Duration.ofSeconds(20);

    @NotBlank
    private String mirrorNodeAddress;

    @NotNull
    private HederaNetwork network = HederaNetwork.TESTNET;

    @NotNull
    @Valid
    private Set<NodeProperties> nodes = new LinkedHashSet<>();

    @NotNull
    @DecimalMax("1000000")
    @DecimalMin("1.0")
    private BigDecimal operatorBalance = BigDecimal.valueOf(72); // Amount in USD

    @NotBlank
    private String operatorId;

    @NotBlank
    private String operatorKey =
            "302e020100300506032b65700422042091132178e72057a1d7528025956fe39b0b847f200ab59b2fdd367017f3087137";

    private boolean retrieveAddressBook = true;

    @DurationMin(seconds = 0L)
    @NotNull
    private Duration startupTimeout = Duration.ofMinutes(60);

    @NotNull
    private NodeNameEnum nodeType = NodeNameEnum.MIRROR;

    private boolean skipEntitiesCleanup;

    public void setOperatorId(String operatorId) {
        var configuredOperator = AccountId.fromString(operatorId);
        var configuredShard = commonProperties.getShard();
        var configuredRealm = commonProperties.getRealm();

        var shardRealmMismatch =
                configuredOperator.realm != configuredRealm || configuredOperator.shard != configuredShard;
        var configInvalid = shardRealmMismatch && !DEFAULT_OPERATOR_ID.equals(operatorId);

        if (configInvalid) {
            throw new IllegalArgumentException(String.format(
                    "Operator account %s must be in shard %d and realm %d",
                    operatorId, configuredShard, configuredRealm));
        }

        this.operatorId = String.format("%d.%d.%d", configuredShard, configuredRealm, configuredOperator.num);
    }

    @Getter
    @RequiredArgsConstructor
    public enum HederaNetwork {
        MAINNET(295L, LedgerId.MAINNET),
        TESTNET(296L, LedgerId.TESTNET),
        PREVIEWNET(297L, LedgerId.PREVIEWNET),
        OTHER(298L, LedgerId.fromBytes(new byte[] {(byte) 3}));
        private final long chainId;
        private final LedgerId ledgerId;
    }
}
