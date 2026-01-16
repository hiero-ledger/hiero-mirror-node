// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.retention;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component("retentionProperties")
@Data
@ConfigurationProperties("hiero.mirror.importer.retention")
@Validated
public class RetentionProperties {

    @NotNull
    private Duration batchPeriod = Duration.ofDays(1L);

    private boolean enabled = false;

    @NotNull
    private Set<String> exclude = Collections.emptySet();

    @NotNull
    private Duration frequency = Duration.ofDays(1L);

    @NotNull
    private Set<String> include = Collections.emptySet();

    @NotNull
    private Duration period = Duration.ofDays(90L);

    public boolean shouldPrune(String table) {
        return (include.isEmpty() || include.contains(table)) && (exclude.isEmpty() || !exclude.contains(table));
    }
}
