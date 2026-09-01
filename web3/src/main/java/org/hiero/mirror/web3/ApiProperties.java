// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class ApiProperties {

    @NotNull
    @Valid
    private RequestProperties request = new RequestProperties();

    @NotNull
    @Valid
    private ResponseProperties response = new ResponseProperties();

    @Data
    @Validated
    public static class RequestProperties {

        /**
         * Maximum requests per second for this API endpoint. A value of zero disables endpoint-specific throttling.
         */
        @Min(0)
        private long throttle;
    }

    @Data
    @Validated
    public static class ResponseProperties {

        /**
         * Response headers to add for this API endpoint. Header names are case-insensitive.
         */
        @NotNull
        private Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        @NotNull
        @DurationMin(seconds = 1L)
        private Duration timeout = Duration.ofSeconds(4L);
    }
}
