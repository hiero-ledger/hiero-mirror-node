// SPDX-License-Identifier: Apache-2.0

package com.hedera.node.app.spi.workflows;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.DispatchOptions.StakingRewards;
import com.hedera.node.app.spi.workflows.DispatchOptions.UsePresetTxnId;
import com.hedera.node.app.spi.workflows.HandleContext.ConsensusThrottling;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DispatchOptionsTest {

    @Mock
    private AccountID accountID;

    @Mock
    private TransactionBody transactionBody;

    @Mock
    private Predicate<Key> predicate;

    @Mock
    private Set<Key> authorizingKeys;

    @Mock
    private StakingRewards stakingRewards;

    @Mock
    private UsePresetTxnId usePresetTxnId;

    @Mock
    private StreamBuilder.TransactionCustomizer transactionCustomizer;

    @Mock
    private HandleContext.DispatchMetadata metadata;

    @Test
    void testSubDispatchReturnsDisabledConsensusThrottle() {
        final var dispatchOptions = DispatchOptions.subDispatch(
                accountID,
                transactionBody,
                predicate,
                authorizingKeys,
                StreamBuilder.class,
                stakingRewards,
                usePresetTxnId);
        assertThat(dispatchOptions.throttling()).isEqualTo(ConsensusThrottling.OFF);
    }

    @Test
    void testStepDispatchWithMetadataReturnsDisabledConsensusThrottle() {
        final var dispatchOptions = DispatchOptions.stepDispatch(
                accountID, transactionBody, StreamBuilder.class, transactionCustomizer, metadata);
        assertThat(dispatchOptions.throttling()).isEqualTo(ConsensusThrottling.OFF);
    }

    @Test
    void testStepDispatchReturnsDisabledConsensusThrottle() {
        final var dispatchOptions =
                DispatchOptions.stepDispatch(accountID, transactionBody, StreamBuilder.class, transactionCustomizer);
        assertThat(dispatchOptions.throttling()).isEqualTo(ConsensusThrottling.OFF);
    }

    @Test
    void testSetupDispatchReturnsDisabledConsensusThrottle() {
        final var dispatchOptions = DispatchOptions.setupDispatch(accountID, transactionBody, StreamBuilder.class);
        assertThat(dispatchOptions.throttling()).isEqualTo(ConsensusThrottling.OFF);
    }

    @Test
    void testIndependentDispatchReturnsDisabledConsensusThrottle() {
        final var dispatchOptions =
                DispatchOptions.independentDispatch(accountID, transactionBody, StreamBuilder.class);
        assertThat(dispatchOptions.throttling()).isEqualTo(ConsensusThrottling.OFF);
    }

    @Test
    void testEffectiveKeyVerifierWithPreauthorizedKeys() {
        final var dispatchOptions = DispatchOptions.setupDispatch(accountID, transactionBody, StreamBuilder.class);
        assertThat(dispatchOptions.effectiveKeyVerifier()).isNull();
    }

    @Test
    void testEffectiveKeyVerifierWithCustomKeys() {
        final var dispatchOptions = DispatchOptions.subDispatch(
                accountID,
                transactionBody,
                predicate,
                authorizingKeys,
                StreamBuilder.class,
                stakingRewards,
                usePresetTxnId);
        assertThat(dispatchOptions.effectiveKeyVerifier()).isEqualTo(predicate);
    }

    @Test
    void testCommitImmediately() {
        final var dispatchOptions =
                DispatchOptions.independentDispatch(accountID, transactionBody, StreamBuilder.class);
        assertThat(dispatchOptions.commitImmediately()).isTrue();
    }

    @Test
    void testCommitImmediatelyWithParent() {
        final var dispatchOptions = DispatchOptions.setupDispatch(accountID, transactionBody, StreamBuilder.class);
        assertThat(dispatchOptions.commitImmediately()).isFalse();
    }
}
