// SPDX-License-Identifier: Apache-2.0

package com.hedera.hapi.node.state.token;

import static com.hedera.pbj.runtime.ProtoTestTools.BOOLEAN_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.BYTES_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.INTEGER_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.LONG_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.STRING_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.UNSIGNED_INTEGER_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.UNSIGNED_LONG_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.generateListArguments;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account.StakedIdOneOfType;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.test.NoToStringWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AccountTest {

    /** A reference to the protoc generated object class. */
    public static final Class<com.hederahashgraph.api.proto.java.Account> PROTOC_MODEL_CLASS =
            com.hederahashgraph.api.proto.java.Account.class;
    /**
     * List of all valid arguments for testing, built as a static list, so we can reuse it.
     */
    public static final List<Account> ARGUMENTS;

    static {
        final var accountIdList = AccountIDTest.ARGUMENTS;
        final var aliasList = BYTES_TESTS_LIST;
        final var keyList = KeyTest.ARGUMENTS;
        final var expirationSecondList = LONG_TESTS_LIST;
        final var tinybarBalanceList = LONG_TESTS_LIST;
        final var memoList = STRING_TESTS_LIST;
        final var deletedList = BOOLEAN_TESTS_LIST;
        final var stakedToMeList = LONG_TESTS_LIST;
        final var stakePeriodStartList = LONG_TESTS_LIST;
        final var stakedIdList = Stream.of(
                        List.of(new OneOf<>(Account.StakedIdOneOfType.UNSET, null)),
                        AccountIDTest.ARGUMENTS.stream()
                                .map(value -> new OneOf<>(Account.StakedIdOneOfType.STAKED_ACCOUNT_ID, value))
                                .toList(),
                        LONG_TESTS_LIST.stream()
                                .map(value -> new OneOf<>(Account.StakedIdOneOfType.STAKED_NODE_ID, value))
                                .toList())
                .flatMap(List::stream)
                .toList();
        final var declineRewardList = BOOLEAN_TESTS_LIST;
        final var receiverSigRequiredList = BOOLEAN_TESTS_LIST;
        final var headTokenIdList = TokenIDTest.ARGUMENTS;
        final var headNftIdList = NftIDTest.ARGUMENTS;
        final var headNftSerialNumberList = LONG_TESTS_LIST;
        final var numberOwnedNftsList = LONG_TESTS_LIST;
        final var maxAutoAssociationsList = INTEGER_TESTS_LIST;
        final var usedAutoAssociationsList = INTEGER_TESTS_LIST;
        final var numberAssociationsList = INTEGER_TESTS_LIST;
        final var smartContractList = BOOLEAN_TESTS_LIST;
        final var numberPositiveBalancesList = INTEGER_TESTS_LIST;
        final var ethereumNonceList = LONG_TESTS_LIST;
        final var stakeAtStartOfLastRewardedPeriodList = LONG_TESTS_LIST;
        final var autoRenewAccountIdList = AccountIDTest.ARGUMENTS;
        final var autoRenewSecondsList = LONG_TESTS_LIST;
        final var contractKvPairsNumberList = INTEGER_TESTS_LIST;
        final var cryptoAllowancesList = generateListArguments(AccountCryptoAllowanceTest.ARGUMENTS);
        final var approveForAllNftAllowancesList = generateListArguments(AccountApprovalForAllAllowanceTest.ARGUMENTS);
        final var tokenAllowancesList = generateListArguments(AccountFungibleTokenAllowanceTest.ARGUMENTS);
        final var numberTreasuryTitlesList = UNSIGNED_INTEGER_TESTS_LIST;
        final var expiredAndPendingRemovalList = BOOLEAN_TESTS_LIST;
        final var firstContractStorageKeyList = BYTES_TESTS_LIST;
        final var headPendingAirdropIdList = PendingAirdropIdTest.ARGUMENTS;
        final var numberPendingAirdropsList = UNSIGNED_LONG_TESTS_LIST;
        final var numberHooksInUseList = UNSIGNED_LONG_TESTS_LIST;
        final var firstHookIdList = UNSIGNED_LONG_TESTS_LIST;
        final var numberLambdaStorageSlotsList = UNSIGNED_LONG_TESTS_LIST;

        // work out the longest of all the lists of args as that is how many test cases we need
        final int maxValues = IntStream.of(
                        accountIdList.size(),
                        aliasList.size(),
                        keyList.size(),
                        expirationSecondList.size(),
                        tinybarBalanceList.size(),
                        memoList.size(),
                        deletedList.size(),
                        stakedToMeList.size(),
                        stakePeriodStartList.size(),
                        stakedIdList.size(),
                        declineRewardList.size(),
                        receiverSigRequiredList.size(),
                        headTokenIdList.size(),
                        headNftIdList.size(),
                        headNftSerialNumberList.size(),
                        numberOwnedNftsList.size(),
                        maxAutoAssociationsList.size(),
                        usedAutoAssociationsList.size(),
                        numberAssociationsList.size(),
                        smartContractList.size(),
                        numberPositiveBalancesList.size(),
                        ethereumNonceList.size(),
                        stakeAtStartOfLastRewardedPeriodList.size(),
                        autoRenewAccountIdList.size(),
                        autoRenewSecondsList.size(),
                        contractKvPairsNumberList.size(),
                        cryptoAllowancesList.size(),
                        approveForAllNftAllowancesList.size(),
                        tokenAllowancesList.size(),
                        numberTreasuryTitlesList.size(),
                        expiredAndPendingRemovalList.size(),
                        firstContractStorageKeyList.size(),
                        headPendingAirdropIdList.size(),
                        numberPendingAirdropsList.size(),
                        numberHooksInUseList.size(),
                        firstHookIdList.size(),
                        numberLambdaStorageSlotsList.size())
                .max()
                .getAsInt();
        // create new stream of model objects using lists above as constructor params
        ARGUMENTS = (maxValues > 0 ? IntStream.range(0, maxValues) : IntStream.of(0))
                .mapToObj(i -> new Account(
                        accountIdList.get(Math.min(i, accountIdList.size() - 1)),
                        aliasList.get(Math.min(i, aliasList.size() - 1)),
                        keyList.get(Math.min(i, keyList.size() - 1)),
                        expirationSecondList.get(Math.min(i, expirationSecondList.size() - 1)),
                        tinybarBalanceList.get(Math.min(i, tinybarBalanceList.size() - 1)),
                        memoList.get(Math.min(i, memoList.size() - 1)),
                        deletedList.get(Math.min(i, deletedList.size() - 1)),
                        stakedToMeList.get(Math.min(i, stakedToMeList.size() - 1)),
                        stakePeriodStartList.get(Math.min(i, stakePeriodStartList.size() - 1)),
                        stakedIdList.get(Math.min(i, stakedIdList.size() - 1)),
                        declineRewardList.get(Math.min(i, declineRewardList.size() - 1)),
                        receiverSigRequiredList.get(Math.min(i, receiverSigRequiredList.size() - 1)),
                        headTokenIdList.get(Math.min(i, headTokenIdList.size() - 1)),
                        headNftIdList.get(Math.min(i, headNftIdList.size() - 1)),
                        headNftSerialNumberList.get(Math.min(i, headNftSerialNumberList.size() - 1)),
                        numberOwnedNftsList.get(Math.min(i, numberOwnedNftsList.size() - 1)),
                        maxAutoAssociationsList.get(Math.min(i, maxAutoAssociationsList.size() - 1)),
                        usedAutoAssociationsList.get(Math.min(i, usedAutoAssociationsList.size() - 1)),
                        numberAssociationsList.get(Math.min(i, numberAssociationsList.size() - 1)),
                        smartContractList.get(Math.min(i, smartContractList.size() - 1)),
                        numberPositiveBalancesList.get(Math.min(i, numberPositiveBalancesList.size() - 1)),
                        ethereumNonceList.get(Math.min(i, ethereumNonceList.size() - 1)),
                        stakeAtStartOfLastRewardedPeriodList.get(
                                Math.min(i, stakeAtStartOfLastRewardedPeriodList.size() - 1)),
                        autoRenewAccountIdList.get(Math.min(i, autoRenewAccountIdList.size() - 1)),
                        autoRenewSecondsList.get(Math.min(i, autoRenewSecondsList.size() - 1)),
                        contractKvPairsNumberList.get(Math.min(i, contractKvPairsNumberList.size() - 1)),
                        cryptoAllowancesList.get(Math.min(i, cryptoAllowancesList.size() - 1)),
                        approveForAllNftAllowancesList.get(Math.min(i, approveForAllNftAllowancesList.size() - 1)),
                        tokenAllowancesList.get(Math.min(i, tokenAllowancesList.size() - 1)),
                        numberTreasuryTitlesList.get(Math.min(i, numberTreasuryTitlesList.size() - 1)),
                        expiredAndPendingRemovalList.get(Math.min(i, expiredAndPendingRemovalList.size() - 1)),
                        firstContractStorageKeyList.get(Math.min(i, firstContractStorageKeyList.size() - 1)),
                        headPendingAirdropIdList.get(Math.min(i, headPendingAirdropIdList.size() - 1)),
                        numberPendingAirdropsList.get(Math.min(i, numberPendingAirdropsList.size() - 1)),
                        numberHooksInUseList.get(Math.min(i, numberHooksInUseList.size() - 1)),
                        firstHookIdList.get(Math.min(i, firstHookIdList.size() - 1)),
                        numberLambdaStorageSlotsList.get(Math.min(i, numberLambdaStorageSlotsList.size() - 1))))
                .toList();
    }

    /**
     * Create a stream of all test permutations of the Account class we are testing. This is reused by other tests
     * as well that have model objects with fields of this type.
     *
     * @return stream of model objects for all test cases
     */
    public static Stream<NoToStringWrapper<Account>> createModelTestArguments() {
        return ARGUMENTS.stream().map(NoToStringWrapper::new);
    }

    @SuppressWarnings("EqualsWithItself")
    @Test
    void testEqualsAndHashCode() {
        if (ARGUMENTS.size() >= 3) {
            final var item1 = ARGUMENTS.get(0);
            final var item2 = ARGUMENTS.get(1);
            final var item3 = ARGUMENTS.get(2);
            assertEquals(item1, item1);
            assertEquals(item2, item2);
            assertEquals(item3, item3);
            assertNotEquals(item1, item2);
            assertNotEquals(item2, item3);
            final var item1HashCode = item1.hashCode();
            final var item2HashCode = item2.hashCode();
            final var item3HashCode = item3.hashCode();
            assertNotEquals(item1HashCode, item2HashCode);
            assertNotEquals(item2HashCode, item3HashCode);
        }
    }

    @Test
    void testEqualsWithNull() {
        final var item1 = ARGUMENTS.get(0);
        assertNotEquals(null, item1);
    }

    @Test
    void testEqualsWithDifferentClass() {
        final var item1 = ARGUMENTS.get(0);
        assertNotEquals(item1, new Object());
    }

    @Test
    void testEqualsWithNullAccountId() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithNullAccountId =
                item1.copyBuilder().accountId((AccountID) null).build();
        assertNotEquals(item1, item1WithNullAccountId);
    }

    @Test
    void testEqualsWithNullAlias() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithNullAlias = item1.copyBuilder().alias(null).build();
        assertNotEquals(item1, item1WithNullAlias);
    }

    @Test
    void testEqualsWithNullKey() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithPopulatedKey = item1.copyBuilder()
                .key(Key.newBuilder().contractID(ContractID.newBuilder()))
                .build();
        final var item1WithNullKey = item1.copyBuilder().key((Key) null).build();
        assertNotEquals(item1WithPopulatedKey, item1WithNullKey);
    }

    @Test
    void testEqualsWithDifferentKey() {
        final var item2 = ARGUMENTS.get(1);

        final var item1WithDifferentKey = item2.copyBuilder()
                .key(Key.newBuilder().delegatableContractId(ContractID.newBuilder()))
                .build();
        assertNotEquals(item2, item1WithDifferentKey);
    }

    @Test
    void testEqualsWithDifferentExpirationSecond() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentExpiration =
                item1.copyBuilder().expirationSecond(1).build();
        assertNotEquals(item1, item1WithDifferentExpiration);
    }

    @Test
    void testEqualsWithDifferentTinybarBalance() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentTinybarBalance =
                item1.copyBuilder().tinybarBalance(10).build();
        assertNotEquals(item1, item1WithDifferentTinybarBalance);
    }

    @Test
    void testEqualsWithDifferentMemo() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentMemo = item1.copyBuilder().memo("New Memo").build();
        assertNotEquals(item1, item1WithDifferentMemo);
    }

    @Test
    void testEqualsWithDifferentDeletedStatus() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentDeletedStatus =
                item1.copyBuilder().deleted(false).build();
        assertNotEquals(item1, item1WithDifferentDeletedStatus);
    }

    @Test
    void testEqualsWithDifferentStakedToMe() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentStakedToMe =
                item1.copyBuilder().stakedToMe(1001).build();
        assertNotEquals(item1, item1WithDifferentStakedToMe);
    }

    @Test
    void testEqualsWithDifferentStakePeriodStart() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentStakePeriodStart =
                item1.copyBuilder().stakePeriodStart(113345).build();
        assertNotEquals(item1, item1WithDifferentStakePeriodStart);
    }

    @Test
    void testEqualsWithDifferentStakedId() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentStakedId =
                item1.copyBuilder().stakedNodeId(3).build();
        assertNotEquals(item1, item1WithDifferentStakedId);
    }

    @Test
    void testEqualsWithNullStakedId() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithPopulatedStakedId =
                item1.copyBuilder().stakedNodeId(3).build();
        final var item1WithStakedIdNull =
                item1.copyBuilder().stakedAccountId((AccountID) null).build();
        assertNotEquals(item1WithPopulatedStakedId, item1WithStakedIdNull);
    }

    @Test
    void testEqualsWithDifferentDeclineReward() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentDeclineReward =
                item1.copyBuilder().declineReward(false).build();
        assertNotEquals(item1, item1WithDifferentDeclineReward);
    }

    @Test
    void testEqualsWithDifferentReceiverSigRequired() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentReceiverSigRequired =
                item1.copyBuilder().receiverSigRequired(false).build();
        assertNotEquals(item1, item1WithDifferentReceiverSigRequired);
    }

    @Test
    void testEqualsWithNullHeadTokenId() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithPopulatedHeadTokenId = item1.copyBuilder()
                .headTokenId(TokenID.newBuilder().shardNum(0).realmNum(0).tokenNum(10))
                .build();
        final var item1WithNullHeadTokenId =
                item1.copyBuilder().headTokenId((TokenID) null).build();
        assertNotEquals(item1WithPopulatedHeadTokenId, item1WithNullHeadTokenId);
    }

    @Test
    void testEqualsWithDifferentHeadTokenId() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentHeadTokenId =
                item1.copyBuilder().headTokenId(TokenID.DEFAULT).build();
        assertNotEquals(item1, item1WithDifferentHeadTokenId);
    }

    @Test
    void testEqualsWithNullHeadNftId() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithPopulatedHeadNftId = item1.copyBuilder()
                .headNftId(NftID.newBuilder()
                        .tokenId(TokenID.newBuilder().shardNum(0).realmNum(0).tokenNum(10))
                        .serialNumber(1))
                .build();
        final var item1WithNullHeadNftId =
                item1.copyBuilder().headNftId((NftID) null).build();
        assertNotEquals(item1WithPopulatedHeadNftId, item1WithNullHeadNftId);
    }

    @Test
    void testEqualsWithDifferentHeadNftId() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentHeadNftId =
                item1.copyBuilder().headNftId(NftID.DEFAULT).build();
        assertNotEquals(item1, item1WithDifferentHeadNftId);
    }

    @Test
    void testEqualsWithDifferentHeadNftSerialNumber() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentHeadNftSerialNumber =
                item1.copyBuilder().headNftSerialNumber(24).build();
        assertNotEquals(item1, item1WithDifferentHeadNftSerialNumber);
    }

    @Test
    void testEqualsWithDifferentNumberOwnedNfts() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentNumberOwnedNfts =
                item1.copyBuilder().numberOwnedNfts(2).build();
        assertNotEquals(item1, item1WithDifferentNumberOwnedNfts);
    }

    @Test
    void testEqualsWithDifferentMaxAutoAssociations() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentMaxAutoAssociations =
                item1.copyBuilder().maxAutoAssociations(20005).build();
        assertNotEquals(item1, item1WithDifferentMaxAutoAssociations);
    }

    @Test
    void testEqualsWithDifferentUsedAssociations() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentUsedAssociations =
                item1.copyBuilder().usedAutoAssociations(2007).build();
        assertNotEquals(item1, item1WithDifferentUsedAssociations);
    }

    @Test
    void testEqualsWithDifferentNumberAssociations() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentNumberAssociations =
                item1.copyBuilder().numberAssociations(65353).build();
        assertNotEquals(item1, item1WithDifferentNumberAssociations);
    }

    @Test
    void testEqualsWithDifferentSmartContractFlag() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentSmartContractFlag =
                item1.copyBuilder().smartContract(false).build();
        assertNotEquals(item1, item1WithDifferentSmartContractFlag);
    }

    @Test
    void testEqualsWithDifferentPositiveBalance() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentPositiveBalance =
                item1.copyBuilder().numberPositiveBalances(1010).build();
        assertNotEquals(item1, item1WithDifferentPositiveBalance);
    }

    @Test
    void testEqualsWithDifferentEthereumNonce() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentEthereumNonce =
                item1.copyBuilder().ethereumNonce(102).build();
        assertNotEquals(item1, item1WithDifferentEthereumNonce);
    }

    @Test
    void testEqualsWithDifferentStakeAtStartOfLastRewardedPeriod() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentStakeAtStartOfLastRewardedPeriod =
                item1.copyBuilder().stakeAtStartOfLastRewardedPeriod(204).build();
        assertNotEquals(item1, item1WithDifferentStakeAtStartOfLastRewardedPeriod);
    }

    @Test
    void testEqualsWithDifferentAutoRenewAccountId() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentAutoRenewAccountId =
                item1.copyBuilder().autoRenewAccountId(AccountID.DEFAULT).build();
        assertNotEquals(item1, item1WithDifferentAutoRenewAccountId);
    }

    @Test
    void testEqualsWithNullAutoRenewAccountId() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithPopulatedAutoRenewAccountId = item1.copyBuilder()
                .autoRenewAccountId(
                        AccountID.newBuilder().shardNum(0).realmNum(0).accountNum(10))
                .build();
        final var item1WithNullAutoRenewAccountId =
                item1.copyBuilder().autoRenewAccountId((AccountID) null).build();
        assertNotEquals(item1WithPopulatedAutoRenewAccountId, item1WithNullAutoRenewAccountId);
    }

    @Test
    void testEqualsWithDifferentAutoRenewSeconds() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentAutoRenewSeconds =
                item1.copyBuilder().autoRenewSeconds(905535).build();
        assertNotEquals(item1, item1WithDifferentAutoRenewSeconds);
    }

    @Test
    void testEqualsWithDifferentContractKvPairsNumber() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentContractKvPairsNumber =
                item1.copyBuilder().contractKvPairsNumber(35).build();
        assertNotEquals(item1, item1WithDifferentContractKvPairsNumber);
    }

    @Test
    void testEqualsWithDifferentCryptoAllowances() {
        final var item1 = ARGUMENTS.get(0);

        final var spender =
                AccountID.newBuilder().shardNum(0).realmNum(0).accountNum(10).build();
        final var cryptoAllowance = AccountCryptoAllowance.newBuilder()
                .spenderId(spender)
                .amount(100)
                .build();
        final var item1WithDifferentCryptoAllowances = item1.copyBuilder()
                .cryptoAllowances(() -> List.of(cryptoAllowance))
                .build();
        assertNotEquals(item1, item1WithDifferentCryptoAllowances);
    }

    @Test
    void testEqualsWithDifferentTokenAllowances() {
        final var item1 = ARGUMENTS.get(0);

        final var spender =
                AccountID.newBuilder().shardNum(0).realmNum(0).accountNum(10).build();
        final var token =
                TokenID.newBuilder().shardNum(0).realmNum(0).tokenNum(19).build();
        final var tokenAllowance = AccountFungibleTokenAllowance.newBuilder()
                .tokenId(token)
                .spenderId(spender)
                .amount(100)
                .build();
        final var item1WithDifferentTokenAllowances = item1.copyBuilder()
                .tokenAllowances(() -> List.of(tokenAllowance))
                .build();
        assertNotEquals(item1, item1WithDifferentTokenAllowances);
    }

    @Test
    void testEqualsWithDifferentApproveForAllNftAllowances() {
        final var item1 = ARGUMENTS.get(0);

        final var spender =
                AccountID.newBuilder().shardNum(0).realmNum(0).accountNum(10).build();
        final var token =
                TokenID.newBuilder().shardNum(0).realmNum(0).tokenNum(15).build();
        final var approveForAllNftAllowance = AccountApprovalForAllAllowance.newBuilder()
                .spenderId(spender)
                .tokenId(token)
                .build();
        final var item1WithDifferentApproveForAllNftAllowances = item1.copyBuilder()
                .approveForAllNftAllowances(() -> List.of(approveForAllNftAllowance))
                .build();
        assertNotEquals(item1, item1WithDifferentApproveForAllNftAllowances);
    }

    @Test
    void testEqualsWithDifferentNumberTreasuryTitles() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentNumberTreasuryTitles =
                item1.copyBuilder().numberTreasuryTitles(5353).build();
        assertNotEquals(item1, item1WithDifferentNumberTreasuryTitles);
    }

    @Test
    void testEqualsWithDifferentExpiredAndPendingRemovalStatus() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentExpiredAndPendingRemovalStatus =
                item1.copyBuilder().expiredAndPendingRemoval(false).build();
        assertNotEquals(item1, item1WithDifferentExpiredAndPendingRemovalStatus);
    }

    @Test
    void testEqualsWithDifferentFirstContractStorageKey() {
        final var item2 = ARGUMENTS.get(1);

        final var item1WithDifferentFirstContractStorageKey =
                item2.copyBuilder().firstContractStorageKey(Bytes.EMPTY).build();
        assertNotEquals(item2, item1WithDifferentFirstContractStorageKey);
    }

    @Test
    void testEqualsWithDifferentHeadPendingAirdropId() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithDifferentHeadPendingAirdropId = item1.copyBuilder()
                .headPendingAirdropId(PendingAirdropId.DEFAULT)
                .build();
        assertNotEquals(item1, item1WithDifferentHeadPendingAirdropId);
    }

    @Test
    void testEqualsWithNullHeadPendingAirdropId() {
        final var item1 = ARGUMENTS.get(0);

        final var item1WithPopulatedHeadPendingAirdropId = item1.copyBuilder()
                .headPendingAirdropId(PendingAirdropId.newBuilder()
                        .receiverId(
                                AccountID.newBuilder().shardNum(0).realmNum(0).accountNum(10))
                        .senderId(AccountID.newBuilder().shardNum(0).realmNum(0).accountNum(11))
                        .build())
                .build();
        final var item1WithNullHeadPendingAirdropId = item1.copyBuilder()
                .headPendingAirdropId((PendingAirdropId) null)
                .build();
        assertNotEquals(item1WithPopulatedHeadPendingAirdropId, item1WithNullHeadPendingAirdropId);
    }

    @Test
    void testEqualsWithDifferentNumberPendingAirdrops() {
        final var item2 = ARGUMENTS.get(1);

        final var item1WithDifferentNumberPendingAirdrops =
                item2.copyBuilder().numberPendingAirdrops(0).build();
        assertNotEquals(item2, item1WithDifferentNumberPendingAirdrops);
    }

    @Test
    void testEqualsWithDifferentNumberHooksInUse() {
        final var item2 = ARGUMENTS.get(1);

        final var item1WithDifferentNumberHooksInUse =
                item2.copyBuilder().numberHooksInUse(0).build();
        assertNotEquals(item2, item1WithDifferentNumberHooksInUse);
    }

    @Test
    void testEqualsWithDifferentFirstHookId() {
        final var item2 = ARGUMENTS.get(1);

        final var item1WithDifferentFirstHookId =
                item2.copyBuilder().firstHookId(0).build();
        assertNotEquals(item2, item1WithDifferentFirstHookId);
    }

    @Test
    void testEqualsWithDifferentNumberLambdaStorageSlots() {
        final var item2 = ARGUMENTS.get(1);

        final var item1WithDifferentNumberLambdaStorageSlots =
                item2.copyBuilder().numberLambdaStorageSlots(0).build();
        assertNotEquals(item2, item1WithDifferentNumberLambdaStorageSlots);
    }

    @Test
    void testAccountIdOrThrow() {
        final var item1 = ARGUMENTS.get(0);

        final var accountId = item1.accountIdOrThrow();
        assertThat(accountId).isNotNull();
    }

    @Test
    void testIfAccountId() {
        final var item1 = ARGUMENTS.get(0);

        List<AccountID> listToAcceptAccounts = new ArrayList<>();
        item1.ifAccountId(listToAcceptAccounts::add);
        assertThat(listToAcceptAccounts).isNotEmpty().hasSize(1);
    }

    @Test
    void testKeyOrThrow() {
        final var item1 = ARGUMENTS.get(0);

        final var key = item1.keyOrThrow();
        assertThat(key).isNotNull();
    }

    @Test
    void testIfKey() {
        final var item1 = ARGUMENTS.get(0);

        List<Key> listToAcceptKeys = new ArrayList<>();
        item1.ifKey(listToAcceptKeys::add);
        assertThat(listToAcceptKeys).isNotEmpty().hasSize(1);
    }

    @Test
    void testHeadTokenIdOrThrow() {
        final var item1 = ARGUMENTS.get(0);

        final var headTokenId = item1.headTokenIdOrThrow();
        assertThat(headTokenId).isNotNull();
    }

    @Test
    void testIfHeadTokenId() {
        final var item1 = ARGUMENTS.get(0);

        List<TokenID> listToAcceptTokens = new ArrayList<>();
        item1.ifHeadTokenId(listToAcceptTokens::add);
        assertThat(listToAcceptTokens).isNotEmpty().hasSize(1);
    }

    @Test
    void testHeadNftIdOrThrow() {
        final var item1 = ARGUMENTS.get(0);

        final var headNftId = item1.headNftIdOrThrow();
        assertThat(headNftId).isNotNull();
    }

    @Test
    void testIfHeadNftId() {
        final var item1 = ARGUMENTS.get(0);

        List<NftID> listToAcceptNfts = new ArrayList<>();
        item1.ifHeadNftId(listToAcceptNfts::add);
        assertThat(listToAcceptNfts).isNotEmpty().hasSize(1);
    }

    @Test
    void testAutoRenewAccountIdOrThrow() {
        final var item1 = ARGUMENTS.get(0);

        final var autoRenewAccountId = item1.autoRenewAccountIdOrThrow();
        assertThat(autoRenewAccountId).isNotNull();
    }

    @Test
    void testIfAutoRenewAccountId() {
        final var item1 = ARGUMENTS.get(0);

        List<AccountID> listToAcceptAccounts = new ArrayList<>();
        item1.ifAutoRenewAccountId(listToAcceptAccounts::add);
        assertThat(listToAcceptAccounts).isNotEmpty().hasSize(1);
    }

    @Test
    void testHeadPendingAirdropIdOrThrow() {
        final var item1 = ARGUMENTS.get(0);

        final var headPendingAirdropId = item1.headPendingAirdropIdOrThrow();
        assertThat(headPendingAirdropId).isNotNull();
    }

    @Test
    void testIfHeadPendingAirdropId() {
        final var item1 = ARGUMENTS.get(0);

        List<PendingAirdropId> listToAcceptPendingAirdrops = new ArrayList<>();
        item1.ifHeadPendingAirdropId(listToAcceptPendingAirdrops::add);
        assertThat(listToAcceptPendingAirdrops).isNotEmpty().hasSize(1);
    }

    @Test
    void testStakedAccountIdOrThrow() {
        final var item2 = ARGUMENTS.get(1);

        final var stakedAccountId = item2.stakedAccountIdOrThrow();
        assertThat(stakedAccountId).isNotNull();
    }

    @Test
    void testStakedNodeIdOrThrow() {
        final var item21 = ARGUMENTS.get(20);

        final var stakedNodeId = item21.stakedNodeIdOrThrow();
        assertThat(stakedNodeId).isNotNull();
    }

    @Test
    void testCopyBuilder() {
        final var item1 = ARGUMENTS.get(0);

        final var copy = item1.copyBuilder().build();
        assertThat(item1).isEqualTo(copy);
    }

    @ParameterizedTest
    @CsvSource({"10, STAKED_ACCOUNT_ID", "11, STAKED_NODE_ID"})
    void testStakedIdFromProtobufOrdinal(final int ordinal, final StakedIdOneOfType expected) {
        final var stakedId = Account.StakedIdOneOfType.fromProtobufOrdinal(ordinal);
        assertThat(stakedId).isEqualTo(expected);
    }

    @Test
    void testStakedIdFromProtobufThrowsException() {
        assertThatThrownBy(() -> Account.StakedIdOneOfType.fromProtobufOrdinal(100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"STAKED_ACCOUNT_ID, STAKED_ACCOUNT_ID", "STAKED_NODE_ID, STAKED_NODE_ID"})
    void testStakedIdFromString(final String name, final StakedIdOneOfType expected) {
        final var stakedId = Account.StakedIdOneOfType.fromString(name);
        assertThat(stakedId).isEqualTo(expected);
    }

    @Test
    void testStakedIdFromStringThrowsException() {
        assertThatThrownBy(() -> Account.StakedIdOneOfType.fromString("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testKeyBuilder() {
        final var item1 = ARGUMENTS.get(0);

        final var contractId = ContractID.DEFAULT;
        final var copy = item1.copyBuilder()
                .key(Key.newBuilder().contractID(contractId).build())
                .build();
        assertThat(copy.key()).isEqualTo(Key.newBuilder().contractID(contractId).build());
    }

    @Test
    void testStakedAccountIdBuilder() {
        final var item1 = ARGUMENTS.get(0);

        final var accountId =
                AccountID.newBuilder().shardNum(0).realmNum(0).accountNum(10).build();
        final var copy = item1.copyBuilder()
                .stakedAccountId(AccountID.newBuilder().shardNum(0).realmNum(0).accountNum(10))
                .build();
        assertThat(copy.stakedAccountId()).isEqualTo(accountId);
    }

    @Test
    void testHeadTokenIdBuilder() {
        final var item1 = ARGUMENTS.get(0);

        final var tokenId =
                TokenID.newBuilder().shardNum(0).realmNum(0).tokenNum(10).build();
        final var copy = item1.copyBuilder()
                .headTokenId(TokenID.newBuilder().shardNum(0).realmNum(0).tokenNum(10))
                .build();
        assertThat(copy.headTokenId()).isEqualTo(tokenId);
    }

    @Test
    void testHeadNftIdBuilder() {
        final var item1 = ARGUMENTS.get(0);

        final var token =
                TokenID.newBuilder().shardNum(0).realmNum(0).tokenNum(10).build();
        final var nft = NftID.newBuilder().tokenId(token).serialNumber(1).build();
        final var copy = item1.copyBuilder()
                .headNftId(NftID.newBuilder().tokenId(token).serialNumber(1))
                .build();
        assertThat(copy.headNftId()).isEqualTo(nft);
    }

    @Test
    void testAutoRenewAccountIdBuilder() {
        final var item1 = ARGUMENTS.get(0);

        final var autoRenewAccount =
                AccountID.newBuilder().shardNum(0).realmNum(0).accountNum(10).build();
        final var copy = item1.copyBuilder()
                .autoRenewAccountId(
                        AccountID.newBuilder().shardNum(0).realmNum(0).accountNum(10))
                .build();
        assertThat(copy.autoRenewAccountId()).isEqualTo(autoRenewAccount);
    }

    @Test
    void testCryptoAllowancesBuilder() {
        final var item1 = ARGUMENTS.get(0);

        final var spender =
                AccountID.newBuilder().shardNum(0).realmNum(0).accountNum(10).build();
        final var cryptoAllowance = AccountCryptoAllowance.newBuilder()
                .spenderId(spender)
                .amount(100L)
                .build();
        final var copy = item1.copyBuilder()
                .cryptoAllowances(List.of(AccountCryptoAllowance.newBuilder()
                        .spenderId(spender)
                        .amount(100L)
                        .build()))
                .build();
        assertThat(copy.cryptoAllowances()).isEqualTo(List.of(cryptoAllowance));
    }

    @Test
    void testTokenAllowancesBuilder() {
        final var item1 = ARGUMENTS.get(0);

        final var token =
                TokenID.newBuilder().shardNum(0).realmNum(0).tokenNum(10).build();
        final var tokenAllowance = AccountFungibleTokenAllowance.newBuilder()
                .tokenId(token)
                .amount(100L)
                .build();
        final var copy = item1.copyBuilder()
                .tokenAllowances(List.of(AccountFungibleTokenAllowance.newBuilder()
                        .tokenId(token)
                        .amount(100L)
                        .build()))
                .build();
        assertThat(copy.tokenAllowances()).isEqualTo(List.of(tokenAllowance));
    }

    @Test
    void testApproveForAllNftAllowancesBuilder() {
        final var item1 = ARGUMENTS.get(0);

        final var spender =
                AccountID.newBuilder().shardNum(0).realmNum(0).accountNum(10).build();
        final var token =
                TokenID.newBuilder().shardNum(0).realmNum(0).tokenNum(10).build();
        final var approveForAllNftAllowance = AccountApprovalForAllAllowance.newBuilder()
                .tokenId(token)
                .spenderId(spender)
                .build();
        final var copy = item1.copyBuilder()
                .approveForAllNftAllowances(List.of(AccountApprovalForAllAllowance.newBuilder()
                        .tokenId(token)
                        .spenderId(spender)
                        .build()))
                .build();
        assertThat(copy.approveForAllNftAllowances()).isEqualTo(List.of(approveForAllNftAllowance));
    }

    @Test
    void testHeadPendingAirdropIdBuilder() {
        final var item1 = ARGUMENTS.get(0);

        final var token =
                TokenID.newBuilder().shardNum(0).realmNum(0).tokenNum(10).build();
        final var receiver =
                AccountID.newBuilder().shardNum(0).realmNum(0).accountNum(10).build();
        final var sender =
                AccountID.newBuilder().shardNum(0).realmNum(0).accountNum(9).build();
        final var pendingAirdropId = PendingAirdropId.newBuilder()
                .senderId(sender)
                .receiverId(receiver)
                .fungibleTokenType(token)
                .build();
        final var copy = item1.copyBuilder()
                .headPendingAirdropId(PendingAirdropId.newBuilder()
                        .senderId(sender)
                        .receiverId(receiver)
                        .fungibleTokenType(token))
                .build();
        assertThat(copy.headPendingAirdropId()).isEqualTo(pendingAirdropId);
    }

    @Test
    void testCryptoAllowancesWithNull() {
        final var item1 = ARGUMENTS.get(0);

        final var copy = item1.copyBuilder()
                .cryptoAllowances((List<AccountCryptoAllowance>) null)
                .build();
        assertNotEquals(item1, copy);
    }

    @Test
    void testFungibleTokenAllowancesWithNull() {
        final var item1 = ARGUMENTS.get(0);

        final var copy = item1.copyBuilder()
                .tokenAllowances((List<AccountFungibleTokenAllowance>) null)
                .build();
        assertNotEquals(item1, copy);
    }

    @Test
    void testApproveForAllNftAllowancesWithNull() {
        final var item1 = ARGUMENTS.get(0);

        final var copy = item1.copyBuilder()
                .approveForAllNftAllowances((List<AccountApprovalForAllAllowance>) null)
                .build();
        assertNotEquals(item1, copy);
    }
}
