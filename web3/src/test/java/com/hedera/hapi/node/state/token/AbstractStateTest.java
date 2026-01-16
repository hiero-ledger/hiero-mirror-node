// SPDX-License-Identifier: Apache-2.0

package com.hedera.hapi.node.state.token;

import static com.hedera.pbj.runtime.ProtoTestTools.BOOLEAN_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.LONG_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.generateListArguments;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Fraction;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.FixedFee;
import com.hedera.hapi.node.transaction.FractionalFee;
import com.hedera.hapi.node.transaction.RoyaltyFee;
import com.hedera.pbj.runtime.OneOf;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class AbstractStateTest {

    public static final List<Fraction> FRACTION_ARGUMENTS = createFractionArguments();
    public static final List<FractionalFee> FRACTIONAL_FEE_ARGUMENTS = createFractionalFeeArguments();
    public static final List<FixedFee> FIXED_FEES_ARGUMENTS = createFixedFeeArguments();
    public static final List<RoyaltyFee> ROYALTY_FEES_ARGUMENTS = createRoyaltyFeeArguments();
    public static final List<CustomFee> CUSTOM_FEE_ARGUMENTS = createCustomFeeArguments();
    public static final List<FileID> FILE_IDS_ARGUMENTS = createFileIDArguments();
    public static final List<KeyList> KEY_LISTS_ARGUMENTS = createKeyListArguments();

    private static List<Fraction> createFractionArguments() {
        final var numeratorList = LONG_TESTS_LIST;
        final var denominatorList = LONG_TESTS_LIST;
        int maxValues = Math.max(numeratorList.size(), denominatorList.size());

        return IntStream.range(0, maxValues)
                .mapToObj(i -> new Fraction(
                        numeratorList.get(Math.min(i, numeratorList.size() - 1)),
                        denominatorList.get(Math.min(i, denominatorList.size() - 1))))
                .toList();
    }

    private static List<FractionalFee> createFractionalFeeArguments() {
        final var fractionalAmountList = FRACTION_ARGUMENTS;
        final var minimumAmountList = LONG_TESTS_LIST;
        final var maximumAmountList = LONG_TESTS_LIST;
        final var netOfTransfersList = BOOLEAN_TESTS_LIST;
        int maxValues = Math.max(
                Math.max(fractionalAmountList.size(), minimumAmountList.size()),
                Math.max(maximumAmountList.size(), netOfTransfersList.size()));

        return IntStream.range(0, maxValues)
                .mapToObj(i -> new FractionalFee(
                        fractionalAmountList.get(Math.min(i, fractionalAmountList.size() - 1)),
                        minimumAmountList.get(Math.min(i, minimumAmountList.size() - 1)),
                        maximumAmountList.get(Math.min(i, maximumAmountList.size() - 1)),
                        netOfTransfersList.get(Math.min(i, netOfTransfersList.size() - 1))))
                .toList();
    }

    private static List<FixedFee> createFixedFeeArguments() {
        final var amountList = LONG_TESTS_LIST;
        final var denominatingTokenIdList = TokenIDTest.ARGUMENTS;
        int maxValues = Math.max(amountList.size(), denominatingTokenIdList.size());

        return IntStream.range(0, maxValues)
                .mapToObj(i -> new FixedFee(
                        amountList.get(Math.min(i, amountList.size() - 1)),
                        denominatingTokenIdList.get(Math.min(i, denominatingTokenIdList.size() - 1))))
                .toList();
    }

    private static List<RoyaltyFee> createRoyaltyFeeArguments() {
        final var exchangeValueFractionList = FRACTION_ARGUMENTS;
        final var fallbackFeeList = FIXED_FEES_ARGUMENTS;
        int maxValues = Math.max(exchangeValueFractionList.size(), fallbackFeeList.size());

        return IntStream.range(0, maxValues)
                .mapToObj(i -> new RoyaltyFee(
                        exchangeValueFractionList.get(Math.min(i, exchangeValueFractionList.size() - 1)),
                        fallbackFeeList.get(Math.min(i, fallbackFeeList.size() - 1))))
                .toList();
    }

    private static List<CustomFee> createCustomFeeArguments() {
        final var feeList = Stream.of(
                        List.of(new OneOf<>(CustomFee.FeeOneOfType.UNSET, null)),
                        FIXED_FEES_ARGUMENTS.stream()
                                .map(value -> new OneOf<>(CustomFee.FeeOneOfType.FIXED_FEE, value))
                                .toList(),
                        FRACTIONAL_FEE_ARGUMENTS.stream()
                                .map(value -> new OneOf<>(CustomFee.FeeOneOfType.FRACTIONAL_FEE, value))
                                .toList(),
                        ROYALTY_FEES_ARGUMENTS.stream()
                                .map(value -> new OneOf<>(CustomFee.FeeOneOfType.ROYALTY_FEE, value))
                                .toList())
                .flatMap(List::stream)
                .toList();

        final var feeCollectorAccountIdList = AccountIDTest.ARGUMENTS;
        final var allCollectorsAreExemptList = BOOLEAN_TESTS_LIST;
        int maxValues =
                Math.max(Math.max(feeList.size(), feeCollectorAccountIdList.size()), allCollectorsAreExemptList.size());

        return IntStream.range(0, maxValues)
                .mapToObj(i -> new CustomFee(
                        feeList.get(Math.min(i, feeList.size() - 1)),
                        feeCollectorAccountIdList.get(Math.min(i, feeCollectorAccountIdList.size() - 1)),
                        allCollectorsAreExemptList.get(Math.min(i, allCollectorsAreExemptList.size() - 1))))
                .toList();
    }

    private static List<FileID> createFileIDArguments() {
        final var shardNumList = LONG_TESTS_LIST;
        final var realmNumList = LONG_TESTS_LIST;
        final var fileNumList = LONG_TESTS_LIST;
        int maxValues = Math.max(Math.max(shardNumList.size(), realmNumList.size()), fileNumList.size());

        return IntStream.range(0, maxValues)
                .mapToObj(i -> new FileID(
                        shardNumList.get(Math.min(i, shardNumList.size() - 1)),
                        realmNumList.get(Math.min(i, realmNumList.size() - 1)),
                        fileNumList.get(Math.min(i, fileNumList.size() - 1))))
                .toList();
    }

    private static List<KeyList> createKeyListArguments() {
        final var keysList = generateListArguments(KeyTest.ARGUMENTS);
        int maxValues = keysList.size();

        return IntStream.range(0, maxValues)
                .mapToObj(i -> new KeyList(keysList.get(Math.min(i, keysList.size() - 1))))
                .toList();
    }
}
