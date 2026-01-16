// SPDX-License-Identifier: Apache-2.0

package com.hedera.hapi.node.state.token;

import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.test.NoToStringWrapper;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PendingAirdropIdTest {

    /**
     * List of all valid arguments for testing, built as a static list, so we can reuse it.
     */
    public static final List<PendingAirdropId> ARGUMENTS;

    static {
        final var senderIdList = AccountIDTest.ARGUMENTS;
        final var receiverIdList = AccountIDTest.ARGUMENTS;
        final var tokenReferenceList = Stream.of(
                        List.of(new OneOf<>(PendingAirdropId.TokenReferenceOneOfType.UNSET, null)),
                        TokenIDTest.ARGUMENTS.stream()
                                .map(value -> new OneOf<>(
                                        PendingAirdropId.TokenReferenceOneOfType.FUNGIBLE_TOKEN_TYPE, value))
                                .toList(),
                        NftIDTest.ARGUMENTS.stream()
                                .map(value ->
                                        new OneOf<>(PendingAirdropId.TokenReferenceOneOfType.NON_FUNGIBLE_TOKEN, value))
                                .toList())
                .flatMap(List::stream)
                .toList();

        // work out the longest of all the lists of args as that is how many test cases we need
        final int maxValues = IntStream.of(senderIdList.size(), receiverIdList.size(), tokenReferenceList.size())
                .max()
                .getAsInt();
        // create new stream of model objects using lists above as constructor params
        ARGUMENTS = (maxValues > 0 ? IntStream.range(0, maxValues) : IntStream.of(0))
                .mapToObj(i -> new PendingAirdropId(
                        senderIdList.get(Math.min(i, senderIdList.size() - 1)),
                        receiverIdList.get(Math.min(i, receiverIdList.size() - 1)),
                        tokenReferenceList.get(Math.min(i, tokenReferenceList.size() - 1))))
                .toList();
    }

    /**
     * Create a stream of all test permutations of the PendingAirdropId class we are testing. This is reused by other tests
     * as well that have model objects with fields of this type.
     *
     * @return stream of model objects for all test cases
     */
    public static Stream<NoToStringWrapper<PendingAirdropId>> createModelTestArguments() {
        return ARGUMENTS.stream().map(NoToStringWrapper::new);
    }
}
