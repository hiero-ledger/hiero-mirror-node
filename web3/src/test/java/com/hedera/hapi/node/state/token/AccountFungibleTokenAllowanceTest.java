// SPDX-License-Identifier: Apache-2.0

package com.hedera.hapi.node.state.token;

import static com.hedera.pbj.runtime.ProtoTestTools.LONG_TESTS_LIST;

import com.hedera.pbj.runtime.test.NoToStringWrapper;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class AccountFungibleTokenAllowanceTest {

    /**
     * List of all valid arguments for testing, built as a static list, so we can reuse it.
     */
    public static final List<AccountFungibleTokenAllowance> ARGUMENTS;

    static {
        final var tokenIdList = TokenIDTest.ARGUMENTS;
        final var spenderIdList = AccountIDTest.ARGUMENTS;
        final var amountList = LONG_TESTS_LIST;

        // work out the longest of all the lists of args as that is how many test cases we need
        final int maxValues = IntStream.of(tokenIdList.size(), spenderIdList.size(), amountList.size())
                .max()
                .getAsInt();
        // create new stream of model objects using lists above as constructor params
        ARGUMENTS = (maxValues > 0 ? IntStream.range(0, maxValues) : IntStream.of(0))
                .mapToObj(i -> new AccountFungibleTokenAllowance(
                        tokenIdList.get(Math.min(i, tokenIdList.size() - 1)),
                        spenderIdList.get(Math.min(i, spenderIdList.size() - 1)),
                        amountList.get(Math.min(i, amountList.size() - 1))))
                .toList();
    }

    /**
     * Create a stream of all test permutations of the AccountFungibleTokenAllowance class we are testing. This is reused by other tests
     * as well that have model objects with fields of this type.
     *
     * @return stream of model objects for all test cases
     */
    public static Stream<NoToStringWrapper<AccountFungibleTokenAllowance>> createModelTestArguments() {
        return ARGUMENTS.stream().map(NoToStringWrapper::new);
    }
}
