// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.common")
public class CommonProperties {

    private static final AtomicReference<CommonProperties> INSTANCE = new AtomicReference<>();

    @Min(0)
    private long realm = 0L;

    @Min(0)
    private long shard = 0L;

    @PostConstruct
    public void init() {
        INSTANCE.set(this);
    }

    /**
     * This method returns the singleton instance of CommonProperties.
     * It is unsafe to call this method before the Spring context is fully initialized.
     *
     * @return the singleton instance of CommonProperties
     * @throws IllegalStateException if CommonProperties has not been initialized
     * */
    public static CommonProperties getInstance() {
        var instance = INSTANCE.get();

        if (instance == null) {
            throw new IllegalStateException("CommonProperties has not been initialized");
        }

        return instance;
    }
}
