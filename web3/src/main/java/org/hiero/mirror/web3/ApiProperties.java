// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class ApiProperties {

    @NotNull
    @Valid
    private RequestProperties request = new RequestProperties();

    @Data
    @Validated
    public static class RequestProperties {

        @NotNull
        @DurationMin(seconds = 1L)
        private Duration timeout;
    }
}
