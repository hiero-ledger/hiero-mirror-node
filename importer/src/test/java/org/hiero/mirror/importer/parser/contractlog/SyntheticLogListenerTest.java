// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.trim;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_NAME;
import static org.hiero.mirror.importer.parser.contractlog.AbstractSyntheticContractLog.APPROVE_SIGNATURE;
import static org.hiero.mirror.importer.parser.contractlog.AbstractSyntheticContractLog.TRANSFER_SIGNATURE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.common.primitives.Longs;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Slf4j
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class SyntheticLogListenerTest {
    private static final DomainBuilder domainBuilder = new DomainBuilder();

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    private EntityProperties entityProperties;

    private SyntheticLogListener listener;

    private static final EntityId ENTITY_1 = domainBuilder.entityId();
    private static final EntityId ENTITY_2 = domainBuilder.entityId();

    private static final byte[] LONG_ZERO_1 = Longs.toByteArray(ENTITY_1.getNum());
    private static final byte[] LONG_ZERO_2 = Longs.toByteArray(ENTITY_2.getNum());

    private static final byte[] EVM_1 = domainBuilder.evmAddress();
    private static final byte[] EVM_2 = domainBuilder.evmAddress();

    @Captor
    ArgumentCaptor<MapSqlParameterSource> parameterSourceCaptor;

    @BeforeEach
    void setup() {
        var systemEntity = new SystemEntity(CommonProperties.getInstance());
        entityProperties = new EntityProperties(systemEntity);

        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        listener = new SyntheticLogListener(jdbcTemplate, entityProperties, cacheManager);
    }

    @Test
    void syntheticLogUpdateDisabled() {
        entityProperties.getPersist().setSyntheticContractLogEvmAddressLookup(false);

        var contractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(Longs.toByteArray(ENTITY_1.getNum()))
                        .topic2(Longs.toByteArray(ENTITY_2.getNum())))
                .get();

        listener.onContractLog(contractLog);

        verifyNoInteractions(jdbcTemplate, cache);

        listener.onEnd(domainBuilder.recordFile().get());

        verifyNoInteractions(jdbcTemplate, cache);

        assertArrayEquals(Longs.toByteArray(ENTITY_1.getNum()), contractLog.getTopic1());
        assertArrayEquals(Longs.toByteArray(ENTITY_2.getNum()), contractLog.getTopic2());
    }

    @Test
    void allInCache() {
        when(cache.get(ENTITY_1.getNum(), byte[].class)).thenReturn(EVM_1);
        when(cache.get(ENTITY_2.getNum(), byte[].class)).thenReturn(EVM_2);

        var contractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(Longs.toByteArray(ENTITY_1.getNum()))
                        .topic2(Longs.toByteArray(ENTITY_2.getNum())))
                .get();

        listener.onContractLog(contractLog);
        assertArrayEquals(Longs.toByteArray(ENTITY_1.getNum()), contractLog.getTopic1());
        assertArrayEquals(Longs.toByteArray(ENTITY_2.getNum()), contractLog.getTopic2());

        verifyNoInteractions(cache);

        listener.onEnd(domainBuilder.recordFile().get());

        verify(cache, times(1)).get(ENTITY_1.getNum(), byte[].class);
        verify(cache, times(1)).get(ENTITY_2.getNum(), byte[].class);

        verifyNoInteractions(jdbcTemplate);

        assertArrayEquals(EVM_1, contractLog.getTopic1());
        assertArrayEquals(EVM_2, contractLog.getTopic2());
    }

    @Test
    void someInCache() {
        when(cache.get(ENTITY_1.getNum(), byte[].class)).thenReturn(null);
        when(cache.get(ENTITY_2.getNum(), byte[].class)).thenReturn(EVM_2);

        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new SyntheticLogListener.EvmAddressMapping(EVM_1, ENTITY_1.getNum())));

        var transferContractLog = domainBuilder
                .contractLog()
                .customize(
                        cl -> cl.topic0(TRANSFER_SIGNATURE).topic1(LONG_ZERO_1).topic2(LONG_ZERO_2))
                .get();

        var approvalContractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(APPROVE_SIGNATURE)
                        .topic1(Longs.toByteArray(domainBuilder.entityId().getNum()))
                        .topic2(Longs.toByteArray(domainBuilder.entityId().getNum())))
                .get();

        listener.onContractLog(transferContractLog);
        listener.onContractLog(approvalContractLog);

        verifyNoInteractions(cache);

        assertNotEquals(EVM_1, transferContractLog.getTopic1());
        assertNotEquals(EVM_2, transferContractLog.getTopic2());

        listener.onEnd(domainBuilder.recordFile().get());

        verify(cache, times(1)).get(ENTITY_1.getNum(), byte[].class);
        verify(cache, times(1)).get(ENTITY_2.getNum(), byte[].class);
        verify(cache, times(1)).put(ENTITY_1.getNum(), EVM_1);
        verify(jdbcTemplate).query(anyString(), parameterSourceCaptor.capture(), any(RowMapper.class));
        verifyNoMoreInteractions(cache, jdbcTemplate);

        var capturedParams = parameterSourceCaptor.getValue();
        var value = capturedParams.getValue("ids");
        assertInstanceOf(List.class, value);
        var list = (List<Long>) value;
        assertThat(list).containsExactlyInAnyOrder(ENTITY_1.getNum());
        assertEquals(EVM_1, transferContractLog.getTopic1());
        assertEquals(EVM_2, transferContractLog.getTopic2());
    }

    @Test
    void noneInCache() {
        when(cache.get(ENTITY_1.getNum(), byte[].class)).thenReturn(null);
        when(cache.get(ENTITY_2.getNum(), byte[].class)).thenReturn(null);

        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(
                        new SyntheticLogListener.EvmAddressMapping(EVM_1, ENTITY_1.getNum()),
                        new SyntheticLogListener.EvmAddressMapping(EVM_2, ENTITY_2.getNum())));

        var contractLog = domainBuilder
                .contractLog()
                .customize(
                        cl -> cl.topic0(TRANSFER_SIGNATURE).topic1(LONG_ZERO_1).topic2(LONG_ZERO_2))
                .get();

        listener.onContractLog(contractLog);

        verifyNoInteractions(cache);

        assertNotEquals(EVM_1, contractLog.getTopic1());
        assertNotEquals(EVM_2, contractLog.getTopic2());

        listener.onEnd(domainBuilder.recordFile().get());

        verify(cache, times(1)).get(ENTITY_1.getNum(), byte[].class);
        verify(cache, times(1)).get(ENTITY_2.getNum(), byte[].class);
        verify(cache, times(1)).put(ENTITY_1.getNum(), EVM_1);
        verify(cache, times(1)).put(ENTITY_2.getNum(), EVM_2);
        verify(jdbcTemplate).query(anyString(), parameterSourceCaptor.capture(), any(RowMapper.class));
        verifyNoMoreInteractions(cache, jdbcTemplate);

        var capturedParams = parameterSourceCaptor.getValue();
        var value = capturedParams.getValue("ids");
        assertInstanceOf(List.class, value);
        var list = (List<Long>) value;
        assertThat(list).containsExactlyInAnyOrder(ENTITY_1.getNum(), ENTITY_2.getNum());
        assertEquals(EVM_1, contractLog.getTopic1());
        assertEquals(EVM_2, contractLog.getTopic2());
    }

    @Test
    void noneInCacheMissingSomeInDatabase() {
        when(cache.get(ENTITY_1.getNum(), byte[].class)).thenReturn(null);
        when(cache.get(ENTITY_2.getNum(), byte[].class)).thenReturn(null);

        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new SyntheticLogListener.EvmAddressMapping(EVM_2, ENTITY_2.getNum())));

        var contractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(Longs.toByteArray(ENTITY_1.getNum()))
                        .topic2(Longs.toByteArray(ENTITY_2.getNum())))
                .get();

        var contractLog2 = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE)
                        .topic1(Longs.toByteArray(ENTITY_1.getNum()))
                        .topic2(Longs.toByteArray(ENTITY_2.getNum())))
                .get();

        listener.onContractLog(contractLog);
        listener.onContractLog(contractLog2);

        verifyNoInteractions(cache);

        assertNotEquals(EVM_1, contractLog.getTopic1());
        assertNotEquals(EVM_2, contractLog.getTopic2());

        listener.onEnd(domainBuilder.recordFile().get());

        verify(cache, times(1)).get(ENTITY_1.getNum(), byte[].class);
        verify(cache, times(1)).get(ENTITY_2.getNum(), byte[].class);
        verify(cache, times(1)).put(ENTITY_1.getNum(), trim(Longs.toByteArray(ENTITY_1.getNum())));
        verify(cache, times(1)).put(ENTITY_2.getNum(), EVM_2);
        verify(jdbcTemplate, times(1)).query(anyString(), parameterSourceCaptor.capture(), any(RowMapper.class));
        verifyNoMoreInteractions(cache, jdbcTemplate);

        var capturedParams = parameterSourceCaptor.getValue();
        var value = capturedParams.getValue("ids");
        assertInstanceOf(List.class, value);
        var list = (List<Long>) value;
        assertThat(list).containsExactlyInAnyOrder(ENTITY_1.getNum(), ENTITY_2.getNum());
        assertArrayEquals(trim(Longs.toByteArray(ENTITY_1.getNum())), contractLog.getTopic1());
        assertEquals(EVM_2, contractLog.getTopic2());
    }

    @Test
    void alreadyHasEvmAddress() {
        var contractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.topic0(TRANSFER_SIGNATURE).topic1(EVM_1).topic2(EVM_2))
                .get();

        listener.onContractLog(contractLog);
        verifyNoInteractions(jdbcTemplate, cache);
    }

    @Test
    void noSenderOrReceiver() {
        var contractLog = domainBuilder
                .contractLog()
                .customize(
                        cl -> cl.topic0(TRANSFER_SIGNATURE).topic1(new byte[0]).topic2(new byte[0]))
                .get();

        listener.onContractLog(contractLog);

        verifyNoInteractions(jdbcTemplate, cache);

        listener.onEnd(domainBuilder.recordFile().get());

        verifyNoInteractions(jdbcTemplate, cache);
    }
}
