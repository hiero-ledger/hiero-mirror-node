// SPDX-License-Identifier: Apache-2.0

package com.hedera.hapi.node.state.token;

import static com.hedera.pbj.runtime.ProtoTestTools.BYTES_TESTS_LIST;

import com.hedera.hapi.node.base.Key;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.test.NoToStringWrapper;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class KeyTest {

    /**
     * List of all valid arguments for testing, built as a static list, so we can reuse it.
     */
    public static final List<Key> ARGUMENTS;

    static {
        @SuppressWarnings("deprecation")
        final var keyList = Stream.of(
                        List.of(new OneOf<>(Key.KeyOneOfType.UNSET, null)),
                        ContractIDTest.ARGUMENTS.stream()
                                .map(value -> new OneOf<>(Key.KeyOneOfType.CONTRACT_ID, value))
                                .toList(),
                        BYTES_TESTS_LIST.stream()
                                .map(value -> new OneOf<>(Key.KeyOneOfType.ED25519, value))
                                .toList(),
                        BYTES_TESTS_LIST.stream()
                                .map(value -> new OneOf<>(Key.KeyOneOfType.RSA_3072, value))
                                .toList(),
                        BYTES_TESTS_LIST.stream()
                                .map(value -> new OneOf<>(Key.KeyOneOfType.ECDSA_384, value))
                                .toList(),
                        BYTES_TESTS_LIST.stream()
                                .map(value -> new OneOf<>(Key.KeyOneOfType.ECDSA_SECP256K1, value))
                                .toList(),
                        ContractIDTest.ARGUMENTS.stream()
                                .map(value -> new OneOf<>(Key.KeyOneOfType.DELEGATABLE_CONTRACT_ID, value))
                                .toList())
                .flatMap(List::stream)
                .toList();

        // work out the longest of all the lists of args as that is how many test cases we need
        final int maxValues = IntStream.of(keyList.size()).max().getAsInt();

        // create new stream of model objects using lists above as constructor params
        ARGUMENTS = (maxValues > 0 ? IntStream.range(0, maxValues) : IntStream.of(0))
                .mapToObj(i -> new Key(keyList.get(Math.min(i, keyList.size() - 1))))
                .toList();
    }

    /**
     * Create a stream of all test permutations of the Key class we are testing. This is reused by other tests
     * as well that have model objects with fields of this type.
     *
     * @return stream of model objects for all test cases
     */
    public static Stream<NoToStringWrapper<Key>> createModelTestArguments() {
        return ARGUMENTS.stream().map(NoToStringWrapper::new);
    }
}
