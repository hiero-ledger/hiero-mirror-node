// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.HttpClientErrorException;

@Component
@ConfigurationProperties(prefix = "hiero.mirror.test.acceptance.rest")
@Data
@RequiredArgsConstructor
@Validated
public class RestProperties implements ApiProperties {

    public static final String URL_PREFIX = "/api/v1";

    @NotBlank
    private String baseUrl;

    @Min(1)
    private int maxAttempts = 20;

    @NotNull
    @DurationMin(millis = 500L)
    private Duration maxBackoff = Duration.ofSeconds(4L);

    @NotNull
    @DurationMin(millis = 100L)
    private Duration minBackoff = Duration.ofMillis(500L);

    private boolean retryOnBadRequestException = true;

    // Don't retry negative test cases
    public boolean shouldRetry(Throwable t) {
        return !(t instanceof HttpClientErrorException e)
                || (retryOnBadRequestException
                        || e.getStatusCode() != HttpStatus.BAD_REQUEST
                                && e.getStatusCode() != HttpStatus.NOT_IMPLEMENTED);
    }

    public String getBaseUrl() {
        if (baseUrl != null && !baseUrl.endsWith(URL_PREFIX)) {
            return baseUrl + URL_PREFIX;
        }
        return baseUrl;
    }
}
