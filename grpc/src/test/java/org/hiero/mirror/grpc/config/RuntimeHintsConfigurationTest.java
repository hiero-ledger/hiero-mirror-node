// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.hiero.mirror.grpc.domain.TopicMessageFilter;
import org.hiero.mirror.grpc.service.NetworkServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

final class RuntimeHintsConfigurationTest {

    @Test
    void registersReflectionTypes() {
        final var hints = new RuntimeHints();
        new RuntimeHintsConfiguration.CustomRuntimeHints()
                .registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection()
                        .onType(NetworkServiceImpl.ServiceEndpointRow.class)
                        .withMemberCategories(
                                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(TopicMessageFilter.class))
                .accepts(hints);
    }
}
