// SPDX-License-Identifier: Apache-2.0

package com.hedera.hapi.node.state.token;

import static com.hedera.pbj.runtime.ProtoTestTools.BOOLEAN_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.BYTES_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.INTEGER_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.LONG_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.STRING_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.generateListArguments;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.AccountID.AccountOneOfType;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.FixedFee;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

public class TokenTest extends AbstractStateTest {

    /**
     * List of all valid arguments for testing, built as a static list, so we can reuse it.
     */
    public static final List<Token> ARGUMENTS;

    static {
        final var tokenIdList = TokenIDTest.ARGUMENTS;
        final var nameList = STRING_TESTS_LIST;
        final var symbolList = STRING_TESTS_LIST;
        final var decimalsList = INTEGER_TESTS_LIST;
        final var totalSupplyList = LONG_TESTS_LIST;
        final var treasuryAccountIdList = AccountIDTest.ARGUMENTS;
        final var adminKeyList = KeyTest.ARGUMENTS;
        final var kycKeyList = KeyTest.ARGUMENTS;
        final var freezeKeyList = KeyTest.ARGUMENTS;
        final var wipeKeyList = KeyTest.ARGUMENTS;
        final var supplyKeyList = KeyTest.ARGUMENTS;
        final var feeScheduleKeyList = KeyTest.ARGUMENTS;
        final var pauseKeyList = KeyTest.ARGUMENTS;
        final var lastUsedSerialNumberList = LONG_TESTS_LIST;
        final var deletedList = BOOLEAN_TESTS_LIST;
        final var tokenTypeList = Arrays.asList(TokenType.values());
        final var supplyTypeList = Arrays.asList(TokenSupplyType.values());
        final var autoRenewAccountIdList = AccountIDTest.ARGUMENTS;
        final var autoRenewSecondsList = LONG_TESTS_LIST;
        final var expirationSecondList = LONG_TESTS_LIST;
        final var memoList = STRING_TESTS_LIST;
        final var maxSupplyList = LONG_TESTS_LIST;
        final var pausedList = BOOLEAN_TESTS_LIST;
        final var accountsFrozenByDefaultList = BOOLEAN_TESTS_LIST;
        final var accountsKycGrantedByDefaultList = BOOLEAN_TESTS_LIST;
        final var customFeesList = generateListArguments(CUSTOM_FEE_ARGUMENTS);
        final var metadataList = BYTES_TESTS_LIST;
        final var metadataKeyList = KeyTest.ARGUMENTS;

        // work out the longest of all the lists of args as that is how many test cases we need
        final int maxValues = IntStream.of(
                        tokenIdList.size(),
                        nameList.size(),
                        symbolList.size(),
                        decimalsList.size(),
                        totalSupplyList.size(),
                        treasuryAccountIdList.size(),
                        adminKeyList.size(),
                        kycKeyList.size(),
                        freezeKeyList.size(),
                        wipeKeyList.size(),
                        supplyKeyList.size(),
                        feeScheduleKeyList.size(),
                        pauseKeyList.size(),
                        lastUsedSerialNumberList.size(),
                        deletedList.size(),
                        tokenTypeList.size(),
                        supplyTypeList.size(),
                        autoRenewAccountIdList.size(),
                        autoRenewSecondsList.size(),
                        expirationSecondList.size(),
                        memoList.size(),
                        maxSupplyList.size(),
                        pausedList.size(),
                        accountsFrozenByDefaultList.size(),
                        accountsKycGrantedByDefaultList.size(),
                        customFeesList.size(),
                        metadataList.size(),
                        metadataKeyList.size())
                .max()
                .getAsInt();
        // create new stream of model objects using lists above as constructor params
        ARGUMENTS = (maxValues > 0 ? IntStream.range(0, maxValues) : IntStream.of(0))
                .mapToObj(i -> new Token(
                        tokenIdList.get(Math.min(i, tokenIdList.size() - 1)),
                        nameList.get(Math.min(i, nameList.size() - 1)),
                        symbolList.get(Math.min(i, symbolList.size() - 1)),
                        decimalsList.get(Math.min(i, decimalsList.size() - 1)),
                        totalSupplyList.get(Math.min(i, totalSupplyList.size() - 1)),
                        treasuryAccountIdList.get(Math.min(i, treasuryAccountIdList.size() - 1)),
                        adminKeyList.get(Math.min(i, adminKeyList.size() - 1)),
                        kycKeyList.get(Math.min(i, kycKeyList.size() - 1)),
                        freezeKeyList.get(Math.min(i, freezeKeyList.size() - 1)),
                        wipeKeyList.get(Math.min(i, wipeKeyList.size() - 1)),
                        supplyKeyList.get(Math.min(i, supplyKeyList.size() - 1)),
                        feeScheduleKeyList.get(Math.min(i, feeScheduleKeyList.size() - 1)),
                        pauseKeyList.get(Math.min(i, pauseKeyList.size() - 1)),
                        lastUsedSerialNumberList.get(Math.min(i, lastUsedSerialNumberList.size() - 1)),
                        deletedList.get(Math.min(i, deletedList.size() - 1)),
                        tokenTypeList.get(Math.min(i, tokenTypeList.size() - 1)),
                        supplyTypeList.get(Math.min(i, supplyTypeList.size() - 1)),
                        autoRenewAccountIdList.get(Math.min(i, autoRenewAccountIdList.size() - 1)),
                        autoRenewSecondsList.get(Math.min(i, autoRenewSecondsList.size() - 1)),
                        expirationSecondList.get(Math.min(i, expirationSecondList.size() - 1)),
                        memoList.get(Math.min(i, memoList.size() - 1)),
                        maxSupplyList.get(Math.min(i, maxSupplyList.size() - 1)),
                        pausedList.get(Math.min(i, pausedList.size() - 1)),
                        accountsFrozenByDefaultList.get(Math.min(i, accountsFrozenByDefaultList.size() - 1)),
                        accountsKycGrantedByDefaultList.get(Math.min(i, accountsKycGrantedByDefaultList.size() - 1)),
                        customFeesList.get(Math.min(i, customFeesList.size() - 1)),
                        metadataList.get(Math.min(i, metadataList.size() - 1)),
                        metadataKeyList.get(Math.min(i, metadataKeyList.size() - 1))))
                .toList();
    }

    @SuppressWarnings("EqualsWithItself")
    @Test
    void testTestEqualsAndHashCode() {
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
    void testHashCodeConsistency() {
        final var item1 = ARGUMENTS.get(0);
        final var item1Copy = item1.copyBuilder().build();

        assertThat(item1).hasSameHashCodeAs(item1Copy);
    }

    @Test
    void testHashCodeWithCustomTreasuryAndAutoRenew() {
        final var item1 = ARGUMENTS.get(1);
        final var itemCustomTreasuryAndAutoRenew = item1.copyBuilder()
                .totalSupply(1)
                .treasuryAccountId(new AccountID(0L, 0L, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, 1L)))
                .autoRenewAccountId(new AccountID(0L, 0L, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, 2L)))
                .build();

        assertThat(item1.hashCode()).isNotEqualTo(itemCustomTreasuryAndAutoRenew.hashCode());
    }

    @Test
    void testEqualsWithNullTreasuryAndAutoRenew() {
        final var item1 = ARGUMENTS.get(1);
        final var itemNullTreasuryAndAutoRenew1 = item1.copyBuilder()
                .totalSupply(null)
                .treasuryAccountId(null)
                .autoRenewAccountId(null)
                .build();
        final var itemNullTreasuryAndAutoRenew2 = item1.copyBuilder()
                .totalSupply(null)
                .treasuryAccountId(null)
                .autoRenewAccountId(null)
                .build();

        assertEquals(itemNullTreasuryAndAutoRenew1, itemNullTreasuryAndAutoRenew2);
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
    void testEqualsWithNullTokenId() {
        final var item1 = ARGUMENTS.get(0);
        final var item1WithNullTokenId =
                item1.copyBuilder().tokenId((TokenID) null).build();
        assertNotEquals(item1, item1WithNullTokenId);
        assertNotEquals(item1WithNullTokenId, item1);
    }

    @Test
    void testEqualsWithNullName() {
        final var item1 = ARGUMENTS.get(0);
        final var item1WithNullName = item1.copyBuilder().name(null).build();
        assertNotEquals(item1, item1WithNullName);
        assertNotEquals(item1WithNullName, item1);
    }

    @Test
    void testEqualsWithDifferentName() {
        final var item1 = ARGUMENTS.get(0);
        final var item1WithNullName = item1.copyBuilder().name("different").build();
        assertNotEquals(item1, item1WithNullName);
    }

    @Test
    void testEqualsWithNullSymbol() {
        final var item1 = ARGUMENTS.get(0);
        final var item1WithNullSymbol = item1.copyBuilder().symbol(null).build();
        assertNotEquals(item1, item1WithNullSymbol);
        assertNotEquals(item1WithNullSymbol, item1);
    }

    @Test
    void testEqualsWithDifferentSymbol() {
        final var item1 = ARGUMENTS.get(0);
        final var item1WithNullName = item1.copyBuilder().symbol("different").build();
        assertNotEquals(item1, item1WithNullName);
    }

    @Test
    void testEqualsWithDifferentDecimals() {
        final var item1 = ARGUMENTS.get(0);
        final var item1WithDifferentDecimals =
                item1.copyBuilder().decimals(item1.decimals() + 1).build();
        assertNotEquals(item1, item1WithDifferentDecimals);
    }

    @Test
    void testEqualsWithDifferentTotalSupply() {
        final var item1 = ARGUMENTS.get(0);
        final var item1WithDifferentTotalSupply = item1.copyBuilder()
                .totalSupply(item1.totalSupplySupplier().get() + 1)
                .build();
        assertNotEquals(item1, item1WithDifferentTotalSupply);
    }

    @Test
    void testEqualsWithNullTotalSupply() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullTotalSupply = item1.copyBuilder().totalSupply(null).build();

        assertNotEquals(itemNullTotalSupply, item1);
        assertNotEquals(item1, itemNullTotalSupply);
    }

    @Test
    void testEqualsWithDifferentTreasuryAccountId() {
        final var item1 = ARGUMENTS.get(0);
        final var item1WithDifferentTreasuryAccountId = item1.copyBuilder()
                .treasuryAccountId(new AccountID(0L, 0L, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, 1L)))
                .build();
        assertNotEquals(item1, item1WithDifferentTreasuryAccountId);
    }

    @Test
    void testEqualsWithNullTreasuryAccountId() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullTreasuryAccountId =
                item1.copyBuilder().treasuryAccountId(null).build();
        assertNotEquals(itemNullTreasuryAccountId, item1);
        assertNotEquals(item1, itemNullTreasuryAccountId);
    }

    @Test
    void testEqualsWithNullTreasuryAccountIdBoth() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullTreasuryAccountId =
                item1.copyBuilder().treasuryAccountId(null).build();
        final var itemNullTreasuryAccountId2 =
                item1.copyBuilder().treasuryAccountId(null).build();
        assertEquals(itemNullTreasuryAccountId, itemNullTreasuryAccountId2);
    }

    @Test
    void testEqualsWithNullAdminKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullAdminKey = item1.copyBuilder().adminKey((Key) null).build();

        assertNotEquals(itemNullAdminKey, item1);
        assertNotEquals(item1, itemNullAdminKey);
    }

    @Test
    void testEqualsWithDifferentAdminKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentAdminKey = item1.copyBuilder()
                .adminKey(new Key(new OneOf<>(Key.KeyOneOfType.CONTRACT_ID, 1L)))
                .build();

        assertNotEquals(item1, itemDifferentAdminKey);
    }

    @Test
    void testEqualsWithNullKycKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullKycKey = item1.copyBuilder().kycKey((Key) null).build();

        assertNotEquals(itemNullKycKey, item1);
        assertNotEquals(item1, itemNullKycKey);
    }

    @Test
    void testEqualsWithDifferentKycKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentKycKey = item1.copyBuilder()
                .kycKey(new Key(new OneOf<>(Key.KeyOneOfType.CONTRACT_ID, 1L)))
                .build();

        assertNotEquals(item1, itemDifferentKycKey);
    }

    @Test
    void testEqualsWithNullFreezeKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullFreezeKey = item1.copyBuilder().freezeKey((Key) null).build();

        assertNotEquals(itemNullFreezeKey, item1);
        assertNotEquals(item1, itemNullFreezeKey);
    }

    @Test
    void testEqualsWithDifferentFreezeKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentFreezeKey = item1.copyBuilder()
                .freezeKey(new Key(new OneOf<>(Key.KeyOneOfType.CONTRACT_ID, 1L)))
                .build();

        assertNotEquals(item1, itemDifferentFreezeKey);
    }

    @Test
    void testEqualsWithNullWipeKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullWipeKey = item1.copyBuilder().wipeKey((Key) null).build();

        assertNotEquals(itemNullWipeKey, item1);
        assertNotEquals(item1, itemNullWipeKey);
    }

    @Test
    void testEqualsWithDifferentWipeKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentWipeKey = item1.copyBuilder()
                .wipeKey(new Key(new OneOf<>(Key.KeyOneOfType.CONTRACT_ID, 1L)))
                .build();

        assertNotEquals(item1, itemDifferentWipeKey);
    }

    @Test
    void testEqualsWithNullSupplyKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullSupplyKey = item1.copyBuilder().supplyKey((Key) null).build();

        assertNotEquals(itemNullSupplyKey, item1);
        assertNotEquals(item1, itemNullSupplyKey);
    }

    @Test
    void testEqualsWithDifferentSupplyKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentSupplyKey = item1.copyBuilder()
                .supplyKey(new Key(new OneOf<>(Key.KeyOneOfType.CONTRACT_ID, 1L)))
                .build();

        assertNotEquals(item1, itemDifferentSupplyKey);
    }

    @Test
    void testEqualsWithNullFeeScheduleKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullFeeScheduleKey =
                item1.copyBuilder().feeScheduleKey((Key) null).build();

        assertNotEquals(itemNullFeeScheduleKey, item1);
        assertNotEquals(item1, itemNullFeeScheduleKey);
    }

    @Test
    void testEqualsWithDifferentFeeScheduleKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentFeeScheduleKey = item1.copyBuilder()
                .feeScheduleKey(new Key(new OneOf<>(Key.KeyOneOfType.CONTRACT_ID, 1L)))
                .build();

        assertNotEquals(item1, itemDifferentFeeScheduleKey);
    }

    @Test
    void testEqualsWithNullPauseKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullPauseKey = item1.copyBuilder().pauseKey((Key) null).build();

        assertNotEquals(itemNullPauseKey, item1);
        assertNotEquals(item1, itemNullPauseKey);
    }

    @Test
    void testEqualsWithDifferentPauseKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentPauseKey = item1.copyBuilder()
                .pauseKey(new Key(new OneOf<>(Key.KeyOneOfType.CONTRACT_ID, 1L)))
                .build();

        assertNotEquals(item1, itemDifferentPauseKey);
    }

    @Test
    void testEqualsWithDifferentLastUsedSerialNumber() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentLastUsedSerialNumber = item1.copyBuilder()
                .lastUsedSerialNumber(item1.lastUsedSerialNumber() + 1)
                .build();

        assertNotEquals(itemDifferentLastUsedSerialNumber, item1);
    }

    @Test
    void testEqualsWithDifferentDeletedFlag() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentDeletedFlag =
                item1.copyBuilder().deleted(!item1.deleted()).build();

        assertNotEquals(itemDifferentDeletedFlag, item1);
    }

    @Test
    void testEqualsWithDifferentTokenType() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentTokenType =
                item1.copyBuilder().tokenType(TokenType.NON_FUNGIBLE_UNIQUE).build();

        assertNotEquals(itemDifferentTokenType, item1);
    }

    @Test
    void testEqualsWithNullTokenType() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullTokenType = item1.copyBuilder().tokenType(null).build();
        assertNotEquals(itemNullTokenType, item1);
    }

    @Test
    void testEqualsWithDifferentSupplyType() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentSupplyType =
                item1.copyBuilder().supplyType(TokenSupplyType.FINITE).build();

        assertNotEquals(itemDifferentSupplyType, item1);
    }

    @Test
    void testEqualsWithNullSupplyType() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullSupplyType = item1.copyBuilder().supplyType(null).build();
        assertNotEquals(itemNullSupplyType, item1);
    }

    @Test
    void testEqualsWithDifferentAutoRenewAccountId() {
        final var item1 = ARGUMENTS.get(0);
        final var item1WithDifferentAutoRenewAccountId = item1.copyBuilder()
                .autoRenewAccountId(new AccountID(0L, 0L, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, 1L)))
                .build();
        assertNotEquals(item1, item1WithDifferentAutoRenewAccountId);
    }

    @Test
    void testEqualsWithNullAutoRenewAccountId() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullAutoRenewAccountId =
                item1.copyBuilder().autoRenewAccountId(null).build();
        assertNotEquals(itemNullAutoRenewAccountId, item1);
        assertNotEquals(item1, itemNullAutoRenewAccountId);
    }

    @Test
    void testEqualsWithNullAutoRenewAccountIdBoth() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullAutoRenewAccountId =
                item1.copyBuilder().autoRenewAccountId(null).build();
        final var itemNullAutoRenewAccountId2 =
                item1.copyBuilder().autoRenewAccountId(null).build();
        assertEquals(itemNullAutoRenewAccountId, itemNullAutoRenewAccountId2);
    }

    @Test
    void testEqualsWithDifferentAutoRenewSeconds() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentAutoRenewSeconds = item1.copyBuilder()
                .autoRenewSeconds(item1.autoRenewSeconds() + 1)
                .build();
        assertNotEquals(itemDifferentAutoRenewSeconds, item1);
    }

    @Test
    void testEqualsWithDifferentExpirationSeconds() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentExpirationSeconds = item1.copyBuilder()
                .expirationSecond(item1.expirationSecond() + 1)
                .build();
        assertNotEquals(itemDifferentExpirationSeconds, item1);
    }

    @Test
    void testEqualsWithDifferentMemo() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentMemo =
                item1.copyBuilder().memo("1" + item1.memo()).build();
        assertNotEquals(itemDifferentMemo, item1);
    }

    @Test
    void testEqualsWithNullMemo() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullMemo = item1.copyBuilder().memo(null).build();
        assertNotEquals(itemNullMemo, item1);
        assertNotEquals(item1, itemNullMemo);
    }

    @Test
    void testEqualsWithDifferentMaxSupply() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentMaxSupply =
                item1.copyBuilder().maxSupply(item1.maxSupply() + 1).build();
        assertNotEquals(itemDifferentMaxSupply, item1);
    }

    @Test
    void testEqualsWithDifferentPausedFlag() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentPauseFlag =
                item1.copyBuilder().paused(!item1.paused()).build();
        assertNotEquals(itemDifferentPauseFlag, item1);
    }

    @Test
    void testEqualsWithDifferentAccountsFrozenByDefaultFlag() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentAccountsFrozenByDefaultFlag = item1.copyBuilder()
                .accountsFrozenByDefault(!item1.accountsFrozenByDefault())
                .build();
        assertNotEquals(itemDifferentAccountsFrozenByDefaultFlag, item1);
    }

    @Test
    void testEqualsWithDifferentAccountsKycGrantedByDefaultFlag() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentAccountsKycGrantedByDefaultFlag = item1.copyBuilder()
                .accountsKycGrantedByDefault(!item1.accountsKycGrantedByDefault())
                .build();
        assertNotEquals(itemDifferentAccountsKycGrantedByDefaultFlag, item1);
    }

    @Test
    void testEqualsWithDifferentCustomFees() {
        final var item1 = ARGUMENTS.get(1);
        final var itemDifferentCustomFees =
                item1.copyBuilder().customFees(Collections.EMPTY_LIST).build();
        assertNotEquals(itemDifferentCustomFees, item1);
    }

    @Test
    void testEqualsWithDifferentMetadata() {
        final var item1 = ARGUMENTS.get(1);
        final var itemDifferentMetadata =
                item1.copyBuilder().metadata(Bytes.EMPTY).build();
        assertNotEquals(itemDifferentMetadata, item1);
    }

    @Test
    void testEqualsWithNullMetadata() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentMetadata = item1.copyBuilder().metadata(null).build();
        assertNotEquals(itemDifferentMetadata, item1);
        assertNotEquals(item1, itemDifferentMetadata);
    }

    @Test
    void testEqualsWithDifferentMetadataKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentMetadataKey = item1.copyBuilder()
                .metadataKey(new Key(new OneOf<>(Key.KeyOneOfType.CONTRACT_ID, 1L)))
                .build();
        assertNotEquals(itemDifferentMetadataKey, item1);
    }

    @Test
    void testEqualsWithNullMetadataKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentMetadataKey =
                item1.copyBuilder().metadataKey((Key) null).build();
        assertNotEquals(itemDifferentMetadataKey, item1);
        assertNotEquals(item1, itemDifferentMetadataKey);
    }

    @Test
    void testHasTokenId() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullTokenId = item1.copyBuilder().tokenId((TokenID) null).build();
        assertThat(item1.hasTokenId()).isTrue();
        assertThat(itemNullTokenId.hasTokenId()).isFalse();
    }

    @Test
    void testTokenIdOrElse() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullTokenId = item1.copyBuilder().tokenId((TokenID) null).build();
        assertThat(item1.tokenIdOrElse(TokenID.DEFAULT)).isEqualTo(item1.tokenId());
        assertThat(itemNullTokenId.tokenIdOrElse(TokenID.DEFAULT)).isEqualTo(TokenID.DEFAULT);
    }

    @Test
    void testTokenIdOrThrow() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullTokenId = item1.copyBuilder().tokenId((TokenID) null).build();
        assertThat(item1.tokenIdOrThrow()).isEqualTo(item1.tokenId());
        assertThatThrownBy(itemNullTokenId::tokenIdOrThrow).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testIfTokenId() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullTokenId = item1.copyBuilder().tokenId((TokenID) null).build();
        List<TokenID> tokenIds = new ArrayList<>();
        Consumer<TokenID> tokenIDConsumer = tokenIds::add;

        item1.ifTokenId(tokenIDConsumer);
        itemNullTokenId.ifTokenId(tokenIDConsumer);
        assertThat(tokenIds).isNotEmpty().hasSize(1).contains(item1.tokenId());
    }

    @Test
    void testHasTreasuryAccountId() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullTreasuryId =
                item1.copyBuilder().treasuryAccountId(null).build();

        assertThat(item1.hasTreasuryAccountId()).isTrue();
        assertThat(itemNullTreasuryId.hasTreasuryAccountId()).isFalse();
    }

    @Test
    void testIfTreasuryAccountId() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullTreasuryId =
                item1.copyBuilder().treasuryAccountId(null).build();

        List<AccountID> accountIDS = new ArrayList<>();
        Consumer<AccountID> accountIDConsumerIDConsumer = accountIDS::add;

        item1.ifTreasuryAccountId(accountIDConsumerIDConsumer);
        itemNullTreasuryId.ifTreasuryAccountId(accountIDConsumerIDConsumer);
        assertThat(accountIDS).isNotEmpty().hasSize(1).contains(item1.treasuryAccountId());
    }

    @Test
    void testTreasuryAccountIdOrElse() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullTreasuryId =
                item1.copyBuilder().treasuryAccountId(null).build();

        assertThat(item1.treasuryAccountIdOrElse(AccountID.DEFAULT)).isEqualTo(item1.treasuryAccountId());
        assertThat(itemNullTreasuryId.treasuryAccountIdOrElse(AccountID.DEFAULT))
                .isEqualTo(AccountID.DEFAULT);
    }

    @Test
    void testTreasuryAccountIdOrThrow() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullTreasuryId =
                item1.copyBuilder().treasuryAccountId(null).build();

        assertThat(item1.treasuryAccountIdOrThrow()).isEqualTo(item1.treasuryAccountId());
        assertThatThrownBy(itemNullTreasuryId::treasuryAccountIdOrThrow).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testHasAdminKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullAdminKey = item1.copyBuilder().adminKey((Key) null).build();
        assertThat(item1.hasAdminKey()).isTrue();
        assertThat(itemNullAdminKey.hasAdminKey()).isFalse();
    }

    @Test
    void testAdminKeyOrElse() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullAdminKey = item1.copyBuilder().adminKey((Key) null).build();
        assertThat(item1.adminKeyOrElse(Key.DEFAULT)).isEqualTo(item1.adminKey());
        assertThat(itemNullAdminKey.adminKeyOrElse(Key.DEFAULT)).isEqualTo(Key.DEFAULT);
    }

    @Test
    void testAdminKeyOrThrow() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullAdminKey = item1.copyBuilder().adminKey((Key) null).build();
        assertThat(item1.adminKeyOrThrow()).isEqualTo(item1.adminKey());
        assertThatThrownBy(itemNullAdminKey::adminKeyOrThrow).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testIfAdminKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullAdminKey = item1.copyBuilder().adminKey((Key) null).build();
        List<Key> keys = new ArrayList<>();
        Consumer<Key> keyConsumer = keys::add;

        item1.ifAdminKey(keyConsumer);
        itemNullAdminKey.ifAdminKey(keyConsumer);
        assertThat(keys).isNotEmpty().hasSize(1).contains(item1.adminKey());
    }

    @Test
    void testHasKycKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullKycKey = item1.copyBuilder().kycKey((Key) null).build();
        assertThat(item1.hasKycKey()).isTrue();
        assertThat(itemNullKycKey.hasKycKey()).isFalse();
    }

    @Test
    void testKycKeyOrElse() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullKycKey = item1.copyBuilder().kycKey((Key) null).build();
        assertThat(item1.kycKeyOrElse(Key.DEFAULT)).isEqualTo(item1.kycKey());
        assertThat(itemNullKycKey.kycKeyOrElse(Key.DEFAULT)).isEqualTo(Key.DEFAULT);
    }

    @Test
    void testKycKeyOrThrow() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullKycKey = item1.copyBuilder().kycKey((Key) null).build();
        assertThat(item1.kycKeyOrThrow()).isEqualTo(item1.kycKey());
        assertThatThrownBy(itemNullKycKey::kycKeyOrThrow).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testIfKycKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullKycKey = item1.copyBuilder().kycKey((Key) null).build();
        List<Key> keys = new ArrayList<>();
        Consumer<Key> keyConsumer = keys::add;

        item1.ifKycKey(keyConsumer);
        itemNullKycKey.ifKycKey(keyConsumer);
        assertThat(keys).isNotEmpty().hasSize(1).contains(item1.kycKey());
    }

    @Test
    void testHasFreezeKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullFreezeKey = item1.copyBuilder().freezeKey((Key) null).build();
        assertThat(item1.hasFreezeKey()).isTrue();
        assertThat(itemNullFreezeKey.hasFreezeKey()).isFalse();
    }

    @Test
    void testFreezeKeyOrElse() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullFreezeKey = item1.copyBuilder().freezeKey((Key) null).build();
        assertThat(item1.freezeKeyOrElse(Key.DEFAULT)).isEqualTo(item1.freezeKey());
        assertThat(itemNullFreezeKey.freezeKeyOrElse(Key.DEFAULT)).isEqualTo(Key.DEFAULT);
    }

    @Test
    void testFreezeKeyOrThrow() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullFreezeKey = item1.copyBuilder().freezeKey((Key) null).build();
        assertThat(item1.freezeKeyOrThrow()).isEqualTo(item1.freezeKey());
        assertThatThrownBy(itemNullFreezeKey::freezeKeyOrThrow).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testIfFreezeKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullFreezeKey = item1.copyBuilder().freezeKey((Key) null).build();
        List<Key> keys = new ArrayList<>();
        Consumer<Key> keyConsumer = keys::add;

        item1.ifFreezeKey(keyConsumer);
        itemNullFreezeKey.ifFreezeKey(keyConsumer);
        assertThat(keys).isNotEmpty().hasSize(1).contains(item1.freezeKey());
    }

    @Test
    void testHasWipeKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullWipeKey = item1.copyBuilder().wipeKey((Key) null).build();
        assertThat(item1.hasWipeKey()).isTrue();
        assertThat(itemNullWipeKey.hasWipeKey()).isFalse();
    }

    @Test
    void testWipeKeyOrElse() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullWipeKey = item1.copyBuilder().wipeKey((Key) null).build();
        assertThat(item1.wipeKeyOrElse(Key.DEFAULT)).isEqualTo(item1.wipeKey());
        assertThat(itemNullWipeKey.wipeKeyOrElse(Key.DEFAULT)).isEqualTo(Key.DEFAULT);
    }

    @Test
    void testWipeKeyOrThrow() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullWipeKey = item1.copyBuilder().wipeKey((Key) null).build();
        assertThat(item1.wipeKeyOrThrow()).isEqualTo(item1.wipeKey());
        assertThatThrownBy(itemNullWipeKey::wipeKeyOrThrow).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testIfWipeKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullWipeKey = item1.copyBuilder().wipeKey((Key) null).build();
        List<Key> keys = new ArrayList<>();
        Consumer<Key> keyConsumer = keys::add;

        item1.ifWipeKey(keyConsumer);
        itemNullWipeKey.ifWipeKey(keyConsumer);
        assertThat(keys).isNotEmpty().hasSize(1).contains(item1.wipeKey());
    }

    @Test
    void testHasSupplyKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullSupplyKey = item1.copyBuilder().supplyKey((Key) null).build();
        assertThat(item1.hasSupplyKey()).isTrue();
        assertThat(itemNullSupplyKey.hasSupplyKey()).isFalse();
    }

    @Test
    void testSupplyKeyOrElse() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullSupplyKey = item1.copyBuilder().supplyKey((Key) null).build();
        assertThat(item1.supplyKeyOrElse(Key.DEFAULT)).isEqualTo(item1.supplyKey());
        assertThat(itemNullSupplyKey.supplyKeyOrElse(Key.DEFAULT)).isEqualTo(Key.DEFAULT);
    }

    @Test
    void testSupplyKeyOrThrow() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullSupplyKey = item1.copyBuilder().supplyKey((Key) null).build();
        assertThat(item1.supplyKeyOrThrow()).isEqualTo(item1.supplyKey());
        assertThatThrownBy(itemNullSupplyKey::supplyKeyOrThrow).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testIfSupplyKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullSupplyKey = item1.copyBuilder().supplyKey((Key) null).build();
        List<Key> keys = new ArrayList<>();
        Consumer<Key> keyConsumer = keys::add;

        item1.ifSupplyKey(keyConsumer);
        itemNullSupplyKey.ifSupplyKey(keyConsumer);
        assertThat(keys).isNotEmpty().hasSize(1).contains(item1.supplyKey());
    }

    @Test
    void testHasFeeScheduleKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullFeeScheduleKey =
                item1.copyBuilder().feeScheduleKey((Key) null).build();
        assertThat(item1.hasFeeScheduleKey()).isTrue();
        assertThat(itemNullFeeScheduleKey.hasFeeScheduleKey()).isFalse();
    }

    @Test
    void testFeeScheduleKeyOrElse() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullFeeScheduleKey =
                item1.copyBuilder().feeScheduleKey((Key) null).build();
        assertThat(item1.feeScheduleKeyOrElse(Key.DEFAULT)).isEqualTo(item1.feeScheduleKey());
        assertThat(itemNullFeeScheduleKey.feeScheduleKeyOrElse(Key.DEFAULT)).isEqualTo(Key.DEFAULT);
    }

    @Test
    void testFeeScheduleKeyOrThrow() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullFeeScheduleKey =
                item1.copyBuilder().feeScheduleKey((Key) null).build();
        assertThat(item1.feeScheduleKeyOrThrow()).isEqualTo(item1.feeScheduleKey());
        assertThatThrownBy(itemNullFeeScheduleKey::feeScheduleKeyOrThrow).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testIfFeeScheduleKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullFeeScheduleKey =
                item1.copyBuilder().feeScheduleKey((Key) null).build();
        List<Key> keys = new ArrayList<>();
        Consumer<Key> keyConsumer = keys::add;

        item1.ifFeeScheduleKey(keyConsumer);
        itemNullFeeScheduleKey.ifFeeScheduleKey(keyConsumer);
        assertThat(keys).isNotEmpty().hasSize(1).contains(item1.feeScheduleKey());
    }

    @Test
    void testHasPauseKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullPauseKey = item1.copyBuilder().pauseKey((Key) null).build();
        assertThat(item1.hasPauseKey()).isTrue();
        assertThat(itemNullPauseKey.hasPauseKey()).isFalse();
    }

    @Test
    void testPauseKeyOrElse() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullPauseKey = item1.copyBuilder().pauseKey((Key) null).build();
        assertThat(item1.pauseKeyOrElse(Key.DEFAULT)).isEqualTo(item1.pauseKey());
        assertThat(itemNullPauseKey.pauseKeyOrElse(Key.DEFAULT)).isEqualTo(Key.DEFAULT);
    }

    @Test
    void testPauseKeyOrThrow() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullPauseKey = item1.copyBuilder().pauseKey((Key) null).build();
        assertThat(item1.pauseKeyOrThrow()).isEqualTo(item1.pauseKey());
        assertThatThrownBy(itemNullPauseKey::pauseKeyOrThrow).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testIfPauseKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullPauseKey = item1.copyBuilder().pauseKey((Key) null).build();
        List<Key> keys = new ArrayList<>();
        Consumer<Key> keyConsumer = keys::add;

        item1.ifPauseKey(keyConsumer);
        itemNullPauseKey.ifPauseKey(keyConsumer);
        assertThat(keys).isNotEmpty().hasSize(1).contains(item1.pauseKey());
    }

    @Test
    void testHasAutoRenewAccountId() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullAutoRenewId =
                item1.copyBuilder().autoRenewAccountId(null).build();

        assertThat(item1.hasAutoRenewAccountId()).isTrue();
        assertThat(itemNullAutoRenewId.hasAutoRenewAccountId()).isFalse();
    }

    @Test
    void testIfAutoRenewAccountId() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullAutoRenewId =
                item1.copyBuilder().autoRenewAccountId(null).build();

        List<AccountID> accountIDS = new ArrayList<>();
        Consumer<AccountID> accountIDConsumer = accountIDS::add;

        item1.ifAutoRenewAccountId(accountIDConsumer);
        itemNullAutoRenewId.ifAutoRenewAccountId(accountIDConsumer);
        assertThat(accountIDS).isNotEmpty().hasSize(1).contains(item1.autoRenewAccountId());
    }

    @Test
    void testAutoRenewAccountIdOrElse() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullAutoRenewId =
                item1.copyBuilder().autoRenewAccountId(null).build();

        assertThat(itemNullAutoRenewId.autoRenewAccountIdOrElse(AccountID.DEFAULT))
                .isEqualTo(AccountID.DEFAULT);
    }

    @Test
    void testAutoRenewAccountIdOrThrow() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullAutoRenewId =
                item1.copyBuilder().autoRenewAccountId(null).build();

        assertThat(item1.autoRenewAccountIdOrThrow()).isEqualTo(item1.autoRenewAccountId());
        assertThatThrownBy(itemNullAutoRenewId::autoRenewAccountIdOrThrow).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testHasMetadataKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullMetadataKey =
                item1.copyBuilder().metadataKey((Key) null).build();

        assertThat(item1.hasMetadataKey()).isTrue();
        assertThat(itemNullMetadataKey.hasMetadataKey()).isFalse();
    }

    @Test
    void testMetadataKeyOrElse() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullMetadataKey =
                item1.copyBuilder().metadataKey((Key) null).build();

        assertThat(item1.metadataKeyOrElse(Key.DEFAULT)).isEqualTo(item1.metadataKey());
        assertThat(itemNullMetadataKey.metadataKeyOrElse(Key.DEFAULT)).isEqualTo(Key.DEFAULT);
    }

    @Test
    void testMetadataKeyOrThrow() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullMetadataKey =
                item1.copyBuilder().metadataKey((Key) null).build();

        assertThat(item1.metadataKeyOrThrow()).isEqualTo(item1.metadataKey());
        assertThatThrownBy(itemNullMetadataKey::metadataKeyOrThrow).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testIfMetadataKey() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullMetadataKey =
                item1.copyBuilder().metadataKey((Key) null).build();
        List<Key> keys = new ArrayList<>();
        Consumer<Key> keyConsumer = keys::add;

        item1.ifMetadataKey(keyConsumer);
        itemNullMetadataKey.ifMetadataKey(keyConsumer);
        assertThat(keys).isNotEmpty().hasSize(1).contains(item1.metadataKey());
    }

    @Test
    void testCustomFees() {
        final var item1 = ARGUMENTS.get(0);
        final var customFee = CustomFee.newBuilder()
                .fixedFee(FixedFee.newBuilder().amount(1L).build())
                .build();
        final var customFees = List.of(customFee);
        final var itemCustomFees = item1.copyBuilder().customFees(customFees).build();

        assertThat(itemCustomFees.customFees()).isEqualTo(customFees);
    }

    @Test
    void testTotalSupply() {
        final var item1 = ARGUMENTS.get(0);
        final var totalSupply = 123L;
        final var itemTotalSupply = item1.copyBuilder().totalSupply(totalSupply).build();

        assertThat(itemTotalSupply.totalSupply()).isEqualTo(totalSupply);
    }

    @Test
    void testAutoRenewAccountID() {
        final var item1 = ARGUMENTS.get(0);
        final var autoRenewAccountID = AccountID.newBuilder().accountNum(123L).build();
        final var itemAutoRenewAccountID =
                item1.copyBuilder().autoRenewAccountId(autoRenewAccountID).build();

        assertThat(itemAutoRenewAccountID.autoRenewAccountId()).isEqualTo(autoRenewAccountID);
    }

    @Test
    void testTreasuryAccountID() {
        final var item1 = ARGUMENTS.get(0);
        final var treasuryAccountID = AccountID.newBuilder().accountNum(123L).build();
        final var itemTreasuryAccountID =
                item1.copyBuilder().treasuryAccountId(treasuryAccountID).build();

        assertThat(itemTreasuryAccountID.treasuryAccountId()).isEqualTo(treasuryAccountID);
    }

    @Test
    void testBuilders() {
        Key.Builder keyBuilder = new Key.Builder();
        Token.Builder tokenBuilder = new Token.Builder();
        TokenID.Builder tokenIdBuilder = new TokenID.Builder();
        Token token = tokenBuilder
                .tokenId(tokenIdBuilder)
                .adminKey(keyBuilder)
                .kycKey(keyBuilder)
                .freezeKey(keyBuilder)
                .wipeKey(keyBuilder)
                .supplyKey(keyBuilder)
                .feeScheduleKey(keyBuilder)
                .pauseKey(keyBuilder)
                .metadataKey(keyBuilder)
                .build();

        assertThat(token.tokenId()).isNotNull();
        assertThat(token.adminKey()).isNotNull();
        assertThat(token.kycKey()).isNotNull();
        assertThat(token.freezeKey()).isNotNull();
        assertThat(token.wipeKey()).isNotNull();
        assertThat(token.supplyKey()).isNotNull();
        assertThat(token.feeScheduleKey()).isNotNull();
        assertThat(token.pauseKey()).isNotNull();
        assertThat(token.metadataKey()).isNotNull();
    }
}
