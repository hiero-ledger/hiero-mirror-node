// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.throttle;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomUtils;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
final class RequestProperties implements Predicate<ContractCallRequest> {

    private final AtomicLong counter = new AtomicLong(0L);

    @NotNull
    private ActionType action = ActionType.LOG;

    @NotNull
    private List<RequestFilter> filters = List.of();

    @PositiveOrZero
    private long limit = Long.MAX_VALUE;

    @Min(0)
    @Max(100)
    private long rate = 100;

    @Getter(lazy = true)
    private final Bucket bucket = createBucket();

    @Override
    public boolean test(ContractCallRequest contractCallRequest) {
        if (rate == 0 || counter.getAndIncrement() >= limit) {
            return false;
        }

        if (action != ActionType.THROTTLE && RandomUtils.secure().randomLong(0L, 100L) >= rate) {
            return false;
        }

        for (var filter : filters) {
            if (filter.test(contractCallRequest)) {
                return true;
            }
        }

        return filters.isEmpty();
    }

    private Bucket createBucket() {
        final var bandwidth = Bandwidth.builder()
                .capacity(rate)
                .refillGreedy(rate, Duration.ofSeconds(1))
                .build();
        return Bucket.builder().addLimit(bandwidth).build();
    }

    @RequiredArgsConstructor
    enum ActionType {
        LOG,
        REJECT,
        THROTTLE
    }
}
