// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.config;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import java.util.Collection;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.SystemEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

@Import({CommonTestConfiguration.class, TableUsageReportTestConfiguration.class})
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:cleanup.sql")
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:cleanup.sql")
@SpringBootTest
public abstract class CommonIntegrationTest {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Resource
    protected CommonProperties commonProperties;

    @Resource
    protected DomainBuilder domainBuilder;

    @Autowired(required = false)
    protected MeterRegistry meterRegistry;

    @Resource
    protected SystemEntity systemEntity;

    @Autowired(required = false)
    private Collection<CacheManager> cacheManagers;

    private final CommonProperties defaultCommonProperties = new CommonProperties();

    @BeforeEach
    void logTest(TestInfo testInfo) {
        defaultCommonProperties.setShard(commonProperties.getShard());
        defaultCommonProperties.setRealm(commonProperties.getRealm());
        reset();
        log.info("Executing: {}", testInfo.getDisplayName());
    }

    @AfterEach
    void resetCommonProperties() {
        commonProperties.setRealm(defaultCommonProperties.getRealm());
        commonProperties.setShard(defaultCommonProperties.getShard());
    }

    protected void reset() {
        //        var defaultCommonProperties = new CommonProperties();
        //        commonProperties.setRealm(defaultCommonProperties.getRealm());
        //        commonProperties.setShard(defaultCommonProperties.getShard());

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
