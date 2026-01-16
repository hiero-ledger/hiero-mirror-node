// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reconciliation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hiero.mirror.importer.util.Utility;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.importer.reconciliation")
class ReconciliationProperties {

    @NotBlank
    private String cron = "0 0 0 * * *"; // Every day at midnight

    @DurationMin(millis = 0)
    private Duration delay = Duration.ofSeconds(1L);

    private boolean enabled = false;

    @NotNull
    private Instant endDate = Utility.MAX_INSTANT_LONG;

    private RemediationStrategy remediationStrategy = RemediationStrategy.FAIL;

    @NotNull
    private Instant startDate = Instant.EPOCH;

    // We can't rely upon the NFT count in the balance file and there's not an easy way to just reconcile fungible
    private boolean token = false;

    public void setStartDate(Instant startDate) {
        if (startDate == null || startDate.isAfter(endDate)) {
            String message = String.format("Start date %s must be valid and not after end date %s", startDate, endDate);
            throw new IllegalArgumentException(message);
        }

        this.startDate = startDate;
    }

    public enum RemediationStrategy {
        ACCUMULATE, // Continue processing after transfer failures without resetting balances for the next iteration
        FAIL, // Halt processing on any reconciliation failure
        RESET, // Continue processing after transfer failures with corrected balances
    }
}
