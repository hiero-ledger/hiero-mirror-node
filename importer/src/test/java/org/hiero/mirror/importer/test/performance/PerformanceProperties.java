// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.performance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("hiero.mirror.importer.test.performance")
@Data
public class PerformanceProperties {

    @Valid
    private DownloaderPerformanceProperties downloader = new DownloaderPerformanceProperties();

    @Valid
    private ParserPerformanceProperties parser = new ParserPerformanceProperties();

    @NotNull
    @Valid
    private Map<String, List<PerformanceScenarioProperties>> scenarios = Map.of();

    public enum SubType {
        STANDARD,
        TOKEN_TRANSFER,
        CONTRACT_CALL;
    }

    @Data
    @Validated
    public static class DownloaderPerformanceProperties {

        private boolean enabled = true;

        @DurationMin(millis = 1)
        @NotNull
        private Duration latency = Duration.ofMillis(250L);

        @NotBlank
        private String scenario;
    }

    @Data
    @Validated
    public static class ParserPerformanceProperties {

        private boolean enabled = true;

        @DurationMin(millis = 1)
        @NotNull
        private Duration latency = Duration.ofSeconds(2L);

        @NotBlank
        private String scenario;
    }

    @Data
    @Validated
    public static class PerformanceScenarioProperties {

        private String description;

        @DurationMin(seconds = 1L)
        @NotNull
        private Duration duration = Duration.ofSeconds(10L);

        private boolean enabled = true;

        @NotNull
        @Valid
        private List<PerformanceTransactionProperties> transactions = List.of();

        public String getDescription() {
            if (description != null) {
                return description;
            }
            return transactions.stream()
                    .map(PerformanceTransactionProperties::getDescription)
                    .collect(Collectors.joining(", "));
        }
    }

    @Data
    @Validated
    public static class PerformanceTransactionProperties {

        @Min(1)
        private int entities = 1;

        @NotNull
        private SubType subType = SubType.STANDARD;

        @Min(1)
        private int tps = 10_000;

        @NotNull
        private TransactionType type = TransactionType.CONSENSUSSUBMITMESSAGE;

        public String getDescription() {
            var name = subType != SubType.STANDARD ? subType : type;
            return tps + " " + name;
        }
    }
}
