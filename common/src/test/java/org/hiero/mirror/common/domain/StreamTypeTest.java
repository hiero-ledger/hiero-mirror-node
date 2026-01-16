// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StreamTypeTest {

    private static final Map<StreamType, List<String>> DATA_EXTENSIONS =
            ImmutableMap.<StreamType, List<String>>builder()
                    .put(StreamType.BALANCE, List.of("csv", "pb"))
                    .put(StreamType.RECORD, List.of("rcd"))
                    .put(StreamType.BLOCK, List.of("blk"))
                    .build();
    private static final Map<StreamType, List<String>> SIGNATURE_EXTENSIONS =
            ImmutableMap.<StreamType, List<String>>builder()
                    .put(StreamType.BALANCE, List.of("csv_sig", "pb_sig"))
                    .put(StreamType.RECORD, List.of("rcd_sig"))
                    .build();

    private static Stream<Arguments> provideTypeAndExtensions(boolean isDataExtension) {
        Map<StreamType, List<String>> extensionsMap = isDataExtension ? DATA_EXTENSIONS : SIGNATURE_EXTENSIONS;
        List<Arguments> argumentsList = new ArrayList<>();
        for (StreamType streamType : StreamType.values()) {
            if (streamType == StreamType.BLOCK && !isDataExtension) {
                // Block streams have no signature files
                continue;
            }

            List<String> extensions = extensionsMap.get(streamType);
            if (extensions == null) {
                throw new IllegalArgumentException("Unknown StreamType " + streamType);
            }

            argumentsList.add(Arguments.of(streamType, extensions));
        }

        return argumentsList.stream();
    }

    private static Stream<Arguments> provideTypeAndDataExtensions() {
        return provideTypeAndExtensions(true);
    }

    private static Stream<Arguments> provideTypeAndSignatureExtensions() {
        return provideTypeAndExtensions(false);
    }

    @ParameterizedTest
    @MethodSource("provideTypeAndDataExtensions")
    void getDataExtensions(StreamType streamType, List<String> dataExtensions) {
        assertPriorities(streamType.getDataExtensions(), dataExtensions);
    }

    @ParameterizedTest
    @MethodSource("provideTypeAndSignatureExtensions")
    void getSignatureExtensions(StreamType streamType, List<String> signatureExtensions) {
        assertPriorities(streamType.getSignatureExtensions(), signatureExtensions);
    }

    void assertPriorities(SortedSet<StreamType.Extension> actual, List<String> expected) {
        // ensures extensions are ordered by priority
        assertThat(actual.stream().map(StreamType.Extension::getName)).containsExactlyElementsOf(expected);

        Stream<Integer> priorities = actual.stream().map(StreamType.Extension::getPriority);
        assertThat(priorities)
                .doesNotHaveDuplicates()
                .isSortedAccordingTo(Comparator.naturalOrder())
                .contains(0);
    }
}
