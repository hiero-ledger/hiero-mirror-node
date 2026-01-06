// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.ACCOUNTS_STATE_ID;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_NETWORK_REWARDS_STATE_ID;
import static com.hedera.node.app.state.recordcache.schemas.V0490RecordCacheSchema.TRANSACTION_RECEIPTS_STATE_ID;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.hedera.node.app.blocks.BlockStreamService;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.entityid.EntityIdService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableQueueState;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableQueueState;
import com.swirlds.state.spi.WritableSingletonState;
import java.util.LinkedList;
import java.util.List;
import org.assertj.core.api.AssertionsForClassTypes;
import org.hiero.mirror.web3.state.keyvalue.AccountReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.AirdropsReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.AliasesReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.ContractBytecodeReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.ContractStorageReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.FileReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.NftReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.ScheduleReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.TokenReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.TokenRelationshipReadableKVState;
import org.hiero.mirror.web3.state.singleton.BlockInfoSingleton;
import org.hiero.mirror.web3.state.singleton.BlockStreamInfoSingleton;
import org.hiero.mirror.web3.state.singleton.CongestionLevelStartsSingleton;
import org.hiero.mirror.web3.state.singleton.EntityCountsSingleton;
import org.hiero.mirror.web3.state.singleton.EntityIdSingleton;
import org.hiero.mirror.web3.state.singleton.MidnightRatesSingleton;
import org.hiero.mirror.web3.state.singleton.RunningHashesSingleton;
import org.hiero.mirror.web3.state.singleton.ThrottleUsageSingleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("rawtypes")
final class MirrorNodeStateTest {

    @InjectMocks
    private MirrorNodeState mirrorNodeState;

    @Mock
    private AccountReadableKVState accountReadableKVState;

    @Mock
    private AirdropsReadableKVState airdropsReadableKVState;

    @Mock
    private AliasesReadableKVState aliasesReadableKVState;

    @Mock
    private ContractBytecodeReadableKVState contractBytecodeReadableKVState;

    @Mock
    private ContractStorageReadableKVState contractStorageReadableKVState;

    @Mock
    private FileReadableKVState fileReadableKVState;

    @Mock
    private NftReadableKVState nftReadableKVState;

    @Mock
    private ScheduleReadableKVState scheduleReadableKVState;

    @Mock
    private TokenReadableKVState tokenReadableKVState;

    @Mock
    private TokenRelationshipReadableKVState tokenRelationshipReadableKVState;

    @Mock
    private BlockInfoSingleton blockInfoSingleton;

    @Mock
    private BlockStreamInfoSingleton blockStreamInfoSingleton;

    @Mock
    private CongestionLevelStartsSingleton congestionLevelStartsSingleton;

    @Mock
    private EntityIdSingleton entityIdSingleton;

    @Mock
    private EntityCountsSingleton entityCountsSingleton;

    @Mock
    private MidnightRatesSingleton midnightRatesSingleton;

    @Mock
    private RunningHashesSingleton runningHashesSingleton;

    @Mock
    private ThrottleUsageSingleton throttleUsageSingleton;

    @Spy
    private List<ReadableKVState> readableKVStates = new LinkedList<>();

    @BeforeEach
    void setUp() {
        // Manually trigger @PostConstruct
        ReflectionTestUtils.invokeMethod(mirrorNodeState, "init");
        readableKVStates.add(accountReadableKVState);
        readableKVStates.add(airdropsReadableKVState);
        readableKVStates.add(aliasesReadableKVState);
        readableKVStates.add(contractBytecodeReadableKVState);
        readableKVStates.add(contractStorageReadableKVState);
        readableKVStates.add(fileReadableKVState);
        readableKVStates.add(nftReadableKVState);
        readableKVStates.add(scheduleReadableKVState);
        readableKVStates.add(tokenReadableKVState);
        readableKVStates.add(tokenRelationshipReadableKVState);
    }

    @Test
    void initPopulatesAllServices() {
        var states = mirrorNodeState.getStates();
        assertThat(states)
                .containsKeys(
                        BlockRecordService.NAME,
                        BlockStreamService.NAME,
                        CongestionThrottleService.NAME,
                        ContractService.NAME,
                        EntityIdService.NAME,
                        FeeService.NAME,
                        FileService.NAME,
                        RecordCacheService.NAME,
                        ScheduleService.NAME,
                        TokenService.NAME);
    }

    @Test
    void getReadableStatesWithSingleton() {
        final var tokenStates = mirrorNodeState.getReadableStates(TokenService.NAME);
        var state = tokenStates.getSingleton(STAKING_NETWORK_REWARDS_STATE_ID);
        assertThat(state).isInstanceOf(ReadableSingletonState.class);
    }

    @Test
    void testGetReadableStatesWithKV() {
        final var tokenStates = mirrorNodeState.getReadableStates(TokenService.NAME);
        var state = tokenStates.get(ACCOUNTS_STATE_ID);
        AssertionsForClassTypes.assertThat(state).isInstanceOf(ReadableKVState.class);
    }

    @Test
    void testGetReadableStatesWithQueue() {
        final var recordCacheService = mirrorNodeState.getReadableStates(RecordCacheService.NAME);
        var state = recordCacheService.getQueue(TRANSACTION_RECEIPTS_STATE_ID);
        AssertionsForClassTypes.assertThat(state).isInstanceOf(ReadableQueueState.class);
    }

    @Test
    void testGetReadableStateForUnsupportedService() {
        assertThat(mirrorNodeState.getReadableStates("").size()).isZero();
    }

    @Test
    void getWritableStatesWithSingleton() {
        final var tokenStates = mirrorNodeState.getWritableStates(TokenService.NAME);
        var state = tokenStates.getSingleton(STAKING_NETWORK_REWARDS_STATE_ID);
        assertThat(state).isInstanceOf(WritableSingletonState.class);
    }

    @Test
    void testGetWritableStatesWithKV() {
        final var tokenStates = mirrorNodeState.getWritableStates(TokenService.NAME);
        var state = tokenStates.get(ACCOUNTS_STATE_ID);
        AssertionsForClassTypes.assertThat(state).isInstanceOf(WritableKVState.class);
    }

    @Test
    void testGetWritableStatesWithQueue() {
        final var recordCacheService = mirrorNodeState.getWritableStates(RecordCacheService.NAME);
        var state = recordCacheService.getQueue(TRANSACTION_RECEIPTS_STATE_ID);
        AssertionsForClassTypes.assertThat(state).isInstanceOf(WritableQueueState.class);
    }

    @Test
    void testGetWritableStateForUnsupportedService() {
        assertThat(mirrorNodeState.getWritableStates("").size()).isZero();
    }

    @Test
    void testEqualsDifferentType() {
        assertThat(mirrorNodeState).isNotEqualTo("someString");
    }

    @Test
    void testEqualsWithNull() {
        assertThat(mirrorNodeState).isNotEqualTo(null);
    }

    @Test
    void testEqualsSameValues() {
        assertThat(mirrorNodeState).isEqualTo(mirrorNodeState);
    }

    @Test
    void testHashCode() {
        assertThat(mirrorNodeState).hasSameHashCodeAs(mirrorNodeState);
    }
}
