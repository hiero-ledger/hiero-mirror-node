// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class StreamProperties {

    @Min(1000)
    private int maxBlockItems = 800_000;

    @Min(1)
    private int maxStreamResponseSizeMB = 8;

    @Min(1)
    private int maxSubscribeAttempts = 3;

    @DurationMin(seconds = 10)
    @NotNull
    private Duration readmitDelay = Duration.ofMinutes(1);

    @DurationMin(millis = 100)
    @NotNull
    private Duration statusTimeout = Duration.ofMillis(400);

    public int getMaxStreamResponseSize() {
        return maxStreamResponseSizeMB * 1024 * 1024;
    }
}
