// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity.topic;

import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_TIME_PARTITION;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_TIME_PARTITION_OVERLAP;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.List;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.db.TimePartition;
import org.hiero.mirror.importer.db.TimePartitionService;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.repository.TopicMessageLookupRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;

public abstract class AbstractTopicMessageLookupIntegrationTest extends ImporterIntegrationTest {

    protected static final Duration RECORD_FILE_INTERVAL = StreamType.RECORD.getFileCloseInterval();

    private static final String CHECK_TABLE_EXISTENCE = "select 'topic_message_plain'::regclass";
    private static final String CREATE_DDL = """
                    alter table topic_message rename to topic_message_plain;
                    create table topic_message (like topic_message_plain including constraints, primary key (consensus_timestamp, topic_id)) partition by range (consensus_timestamp);
                    create table topic_message_default partition of topic_message default;
                    create table topic_message_00 partition of topic_message for values from ('1680000000000000000') to ('1682000000000000000');
                    create table topic_message_01 partition of topic_message for values from ('1682000000000000000') to ('1684000000000000000');
                    """;
    private static final String REVERT_DDL = """
                    drop table topic_message cascade;
                    alter table topic_message_plain rename to topic_message;
                    """;

    @Resource
    protected EntityProperties entityProperties;

    @Resource
    protected TimePartitionService timePartitionService;

    @Resource
    protected TopicMessageLookupRepository topicMessageLookupRepository;

    protected List<TimePartition> partitions;

    @Qualifier(CACHE_TIME_PARTITION)
    @Resource
    private CacheManager cacheManager1;

    @Qualifier(CACHE_TIME_PARTITION_OVERLAP)
    @Resource
    private CacheManager cacheManager2;

    @BeforeEach
    void setup() {
        entityProperties.getPersist().setTopics(true);
        entityProperties.getPersist().setTopicMessageLookups(true);
        createPartitions();
    }

    @AfterEach
    void teardown() {
        entityProperties.getPersist().setTopics(true);
        entityProperties.getPersist().setTopicMessageLookups(false);
    }

    private void createPartitions() {
        if (isV1()) {
            ownerJdbcTemplate.execute(CREATE_DDL);
        }

        partitions = timePartitionService.getTimePartitions("topic_message");
    }

    @AfterEach
    protected void revertPartitions() {
        if (!isV1()) {
            return;
        }

        try {
            ownerJdbcTemplate.execute(CHECK_TABLE_EXISTENCE);
            ownerJdbcTemplate.execute(REVERT_DDL);
            // clear cache so for v1 TimePartitionService won't return stale partition info
            cacheManager1.getCacheNames().forEach(n -> cacheManager1.getCache(n).clear());
            cacheManager2.getCacheNames().forEach(n -> cacheManager2.getCache(n).clear());
        } catch (Exception e) {
            // Ignore
        }
    }
}
