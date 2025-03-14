// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.config;

import com.hedera.mirror.common.domain.DomainBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

@Import(CommonTestConfiguration.class)
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:cleanup.sql")
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:cleanup.sql")
@SpringBootTest
public abstract class CommonIntegrationTest {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Resource
    protected DomainBuilder domainBuilder;

    @Autowired(required = false)
    protected MeterRegistry meterRegistry;

    @Autowired(required = false)
    private Collection<CacheManager> cacheManagers;

    @BeforeEach
    void logTest(TestInfo testInfo) {
        reset();
        log.info("Executing: {}", testInfo.getDisplayName());
    }

    protected void reset() {
        if (cacheManagers != null) {
            cacheManagers.forEach(this::resetCacheManager);
        }
        if (meterRegistry != null) {
            meterRegistry.clear();
        }
    }

    protected final void resetCacheManager(CacheManager cacheManager) {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }
}
