// SPDX-License-Identifier: Apache-2.0

package com.hedera.hapi.node.state.token;

import static com.hedera.pbj.runtime.ProtoTestTools.BYTES_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.LONG_TESTS_LIST;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.test.NoToStringWrapper;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ContractIDTest {

    /**
     * List of all valid arguments for testing, built as a static list, so we can reuse it.
     */
    public static final List<ContractID> ARGUMENTS;

    static {
        final var shardNumList = LONG_TESTS_LIST;
        final var realmNumList = LONG_TESTS_LIST;
        final var contractList = Stream.of(
                        List.of(new OneOf<>(ContractID.ContractOneOfType.UNSET, null)),
                        LONG_TESTS_LIST.stream()
                                .map(value -> new OneOf<>(ContractID.ContractOneOfType.CONTRACT_NUM, value))
                                .toList(),
                        BYTES_TESTS_LIST.stream()
                                .map(value -> new OneOf<>(ContractID.ContractOneOfType.EVM_ADDRESS, value))
                                .toList())
                .flatMap(List::stream)
                .toList();

        // work out the longest of all the lists of args as that is how many test cases we need
        final int maxValues = IntStream.of(shardNumList.size(), realmNumList.size(), contractList.size())
                .max()
                .getAsInt();
        // create new stream of model objects using lists above as constructor params
        ARGUMENTS = (maxValues > 0 ? IntStream.range(0, maxValues) : IntStream.of(0))
                .mapToObj(i -> new ContractID(
                        shardNumList.get(Math.min(i, shardNumList.size() - 1)),
                        realmNumList.get(Math.min(i, realmNumList.size() - 1)),
                        contractList.get(Math.min(i, contractList.size() - 1))))
                .toList();
    }

    /**
     * Create a stream of all test permutations of the ContractID class we are testing. This is reused by other tests
     * as well that have model objects with fields of this type.
     *
     * @return stream of model objects for all test cases
     */
    public static Stream<NoToStringWrapper<ContractID>> createModelTestArguments() {
        return ARGUMENTS.stream().map(NoToStringWrapper::new);
    }
}
