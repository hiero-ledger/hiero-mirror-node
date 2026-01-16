// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.subscribe;

import com.google.common.collect.Sets;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hiero.mirror.monitor.subscribe.grpc.GrpcSubscriberProperties;
import org.hiero.mirror.monitor.subscribe.rest.RestSubscriberProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.monitor.subscribe")
public class SubscribeProperties {

    @Min(1)
    @Max(1024)
    private int clients = 1;

    private boolean enabled = true;

    @NotNull
    @Valid
    private Map<String, GrpcSubscriberProperties> grpc = new LinkedHashMap<>();

    @NotNull
    @Valid
    private Map<String, RestSubscriberProperties> rest = new LinkedHashMap<>();

    @DurationMin(seconds = 1L)
    @NotNull
    private Duration statusFrequency = Duration.ofSeconds(10L);

    @PostConstruct
    void validate() {
        if (enabled && grpc.isEmpty() && rest.isEmpty()) {
            throw new IllegalArgumentException("There must be at least one subscribe scenario");
        }

        if (Sets.union(grpc.keySet(), rest.keySet()).stream().anyMatch(StringUtils::isBlank)) {
            throw new IllegalArgumentException("Subscribe scenario name cannot be empty");
        }

        Set<String> names = Sets.intersection(grpc.keySet(), rest.keySet());
        if (!names.isEmpty()) {
            throw new IllegalArgumentException("More than one subscribe scenario with the same name: " + names);
        }

        grpc.forEach((name, property) -> property.setName(name));
        rest.forEach((name, property) -> property.setName(name));
    }
}
