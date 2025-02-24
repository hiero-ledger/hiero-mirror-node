// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.hedera.mirror.web3.ContextExtension;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import com.hedera.mirror.web3.utils.BareDatabaseAccessor;
import java.util.EmptyStackException;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ContextExtension.class)
class StackedStateFramesTest {

    @Test
    void constructionHappyPath() {

        final var accessors = List.<DatabaseAccessor<Object, ?>>of(
                new BareDatabaseAccessor<Object, Character>() {}, new BareDatabaseAccessor<Object, String>() {});

        final var sut = new StackedStateFrames(accessors);
        ContractCallContext.get().setStack(sut.getInitializedStackBase(Optional.empty()));

        final var softly = new SoftAssertions();
        softly.assertThat(sut.height()).as("visible height").isZero();
        softly.assertThat(sut.cachedFramesDepth()).as("true height").isEqualTo(2);
        softly.assertThat(sut.top()).as("RO on top").isInstanceOf(ROCachingStateFrame.class);
        softly.assertThat(sut.top().getUpstream())
                .as("DB at very bottom")
                .containsInstanceOf(DatabaseBackedStateFrame.class);
        softly.assertThat(sut.getValueClasses())
                .as("value classes correct")
                .containsExactlyInAnyOrder(Character.class, String.class);
        softly.assertAll();
        assertThatExceptionOfType(EmptyStackException.class)
                .as("cannot pop bare stack")
                .isThrownBy(sut::pop);
    }

    @Test
    void constructWithDuplicatedValueTypesFails() {
        final var accessors = List.<DatabaseAccessor<Object, ?>>of(
                new BareDatabaseAccessor<Object, Character>() {},
                new BareDatabaseAccessor<Object, String>() {},
                new BareDatabaseAccessor<Object, List<Integer>>() {},
                new BareDatabaseAccessor<Object, String>() {});

        assertThatIllegalArgumentException().isThrownBy(() -> new StackedStateFrames(accessors));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void constructWithDifferingKeyTypesFails() {
        final var accessors = List.<DatabaseAccessor<Object, ?>>of(
                new BareDatabaseAccessor<Object, Character>() {}, (BareDatabaseAccessor<Object, Character>)
                        (BareDatabaseAccessor) new BareDatabaseAccessor<Long, String>() {});

        assertThatIllegalArgumentException().isThrownBy(() -> new StackedStateFrames(accessors));
    }

    @Test
    void pushAndPopDoSo() {
        final var accessors = List.<DatabaseAccessor<Object, ?>>of(new BareDatabaseAccessor<Object, Character>() {});
        final var sut = new StackedStateFrames(accessors);
        ContractCallContext.get().setStack(sut.getInitializedStackBase(Optional.empty()));

        final var roOnTopOfBase = sut.top();

        assertThat(sut.height()).isZero();
        sut.push();
        assertThat(sut.height()).isEqualTo(1);
        assertThat(sut.cachedFramesDepth()).isEqualTo(3);
        assertThat(sut.top()).isInstanceOf(RWCachingStateFrame.class);
        assertThat(sut.top().getUpstream()).contains(roOnTopOfBase);
        sut.pop();
        assertThat(sut.height()).isZero();
        assertThat(sut.cachedFramesDepth()).isEqualTo(2);
        assertThat(sut.top()).isEqualTo(roOnTopOfBase);
    }

    @Test
    void forcePushOfSpecificFrameWithProperUpstream() {
        final var accessors = List.<DatabaseAccessor<Object, ?>>of(new BareDatabaseAccessor<Object, Character>() {});
        final var sut = new StackedStateFrames(accessors);
        ContractCallContext.get().setStack(sut.getInitializedStackBase(Optional.empty()));

        final var newTos = new RWCachingStateFrame<>(Optional.of(sut.top()), Character.class);
        final var actual = sut.push(newTos);
        assertThat(sut.height()).isEqualTo(1);
        assertThat(actual).isEqualTo(newTos);
    }

    @Test
    void forcePushOfSpecificFrameWithBadUpstream() {
        final var accessors = List.<DatabaseAccessor<Object, ?>>of(new BareDatabaseAccessor<Object, Character>() {});
        final var sut = new StackedStateFrames(accessors);
        ContractCallContext.get().setStack(sut.getInitializedStackBase(Optional.empty()));
        final var newTos = new RWCachingStateFrame<>(
                Optional.of(new RWCachingStateFrame<>(Optional.empty(), Character.class)), Character.class);
        assertThatIllegalArgumentException().isThrownBy(() -> sut.push(newTos));
    }
}
