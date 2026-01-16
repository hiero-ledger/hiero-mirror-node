// SPDX-License-Identifier: Apache-2.0

package com.hedera.hapi.node.state.token;

import static com.hedera.pbj.runtime.ProtoTestTools.LONG_TESTS_LIST;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.pbj.runtime.test.NoToStringWrapper;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TokenIDTest {

    /**
     * List of all valid arguments for testing, built as a static list, so we can reuse it.
     */
    public static final List<TokenID> ARGUMENTS;

    static {
        final var shardNumList = LONG_TESTS_LIST;
        final var realmNumList = LONG_TESTS_LIST;
        final var tokenNumList = LONG_TESTS_LIST;

        // work out the longest of all the lists of args as that is how many test cases we need
        final int maxValues = IntStream.of(shardNumList.size(), realmNumList.size(), tokenNumList.size())
                .max()
                .getAsInt();
        // create new stream of model objects using lists above as constructor params
        ARGUMENTS = (maxValues > 0 ? IntStream.range(0, maxValues) : IntStream.of(0))
                .mapToObj(i -> new TokenID(
                        shardNumList.get(Math.min(i, shardNumList.size() - 1)),
                        realmNumList.get(Math.min(i, realmNumList.size() - 1)),
                        tokenNumList.get(Math.min(i, tokenNumList.size() - 1))))
                .toList();
    }

    /**
     * Create a stream of all test permutations of the TokenID class we are testing. This is reused by other tests
     * as well that have model objects with fields of this type.
     *
     * @return stream of model objects for all test cases
     */
    public static Stream<NoToStringWrapper<TokenID>> createModelTestArguments() {
        return ARGUMENTS.stream().map(NoToStringWrapper::new);
    }
}
