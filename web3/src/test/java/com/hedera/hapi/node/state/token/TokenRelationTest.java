// SPDX-License-Identifier: Apache-2.0

package com.hedera.hapi.node.state.token;

import static com.hedera.pbj.runtime.ProtoTestTools.BOOLEAN_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.LONG_TESTS_LIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.AccountID.AccountOneOfType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.pbj.runtime.OneOf;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

public class TokenRelationTest {

    @SuppressWarnings("EqualsWithItself")
    @Test
    void testTestEqualsAndHashCode() {
        if (ARGUMENTS.size() >= 3) {
            final var item1 = ARGUMENTS.get(0);
            final var item2 = ARGUMENTS.get(1);
            final var item3 = ARGUMENTS.get(2);
            assertThat(item1).isEqualTo(item1);
            assertThat(item2).isEqualTo(item2);
            assertThat(item3).isEqualTo(item3);
            assertThat(item1).isNotEqualTo(item2);
            assertThat(item2).isNotEqualTo(item3);
            final var item1HashCode = item1.hashCode();
            final var item2HashCode = item2.hashCode();
            final var item3HashCode = item3.hashCode();
            assertThat(item1HashCode).isNotEqualTo(item2HashCode);
            assertThat(item2HashCode).isNotEqualTo(item3HashCode);
        }
    }

    @Test
    void testHashCodeConsistency() {
        final var item1 = ARGUMENTS.get(0);
        final var item1Copy = item1.copyBuilder().build();

        assertThat(item1).hasSameHashCodeAs(item1Copy);
    }

    @Test
    void testHashCodeWithCustomSuppliers() {
        final var item1 = ARGUMENTS.get(1);
        final var itemCustomSuppliers = item1.copyBuilder().balance(1).build();

        assertThat(item1.hashCode()).isNotEqualTo(itemCustomSuppliers.hashCode());
    }

    @Test
    void testEqualsWithNull() {
        final var item1 = ARGUMENTS.get(0);
        assertThat(item1).isNotEqualTo(null);
    }

    @Test
    void testEqualsWithDifferentClass() {
        final var item1 = ARGUMENTS.get(0);
        assertThat(item1).isNotEqualTo(new Object());
    }

    @Test
    void testEqualsWithNullTokenId() {
        final var item1 = ARGUMENTS.get(0);
        final var item1WithNullTokenId =
                item1.copyBuilder().tokenId((TokenID) null).build();
        assertThat(item1).isNotEqualTo(item1WithNullTokenId);
        assertThat(item1WithNullTokenId).isNotEqualTo(item1);
    }

    @Test
    void testEqualsWithNullAccountId() {
        final var item1 = ARGUMENTS.get(0);
        final var item1WithNullTAccountId =
                item1.copyBuilder().accountId((AccountID) null).build();
        assertThat(item1).isNotEqualTo(item1WithNullTAccountId);
        assertThat(item1WithNullTAccountId).isNotEqualTo(item1);
    }

    @Test
    void testEqualsWithNullSuppliers() {
        final var item1 = ARGUMENTS.get(1);
        final var itemNullSuppliers1 = item1.copyBuilder().balanceSupplier(null).build();
        final var itemNullSuppliers2 = item1.copyBuilder().balanceSupplier(null).build();

        assertThat(itemNullSuppliers1).isEqualTo(itemNullSuppliers2);
    }

    @Test
    void testEqualsWithDifferentSuppliersAndValues() {
        final var item1 = ARGUMENTS.get(1);
        final var itemSupplier1 = item1.copyBuilder().balanceSupplier(() -> 1L).build();
        final var itemSupplier2 = item1.copyBuilder().balanceSupplier(() -> 2L).build();

        assertThat(itemSupplier1).isNotEqualTo(itemSupplier2);
    }

    @Test
    void testEqualsWithDifferentSuppliersAndNull() {
        final var item1 = ARGUMENTS.get(1);
        final var itemSupplier1 = item1.copyBuilder().balanceSupplier(() -> 1L).build();
        final var itemSupplier2 = item1.copyBuilder().balanceSupplier(null).build();

        assertThat(itemSupplier1).isNotEqualTo(itemSupplier2);
        assertThat(itemSupplier2).isNotEqualTo(itemSupplier1);
    }

    @Test
    void testEqualsWithDifferentSuppliersReference() {
        final var item1 = ARGUMENTS.get(1);
        final var itemSupplier1 = item1.copyBuilder().balanceSupplier(() -> 1L).build();
        final var itemSupplier2 = item1.copyBuilder().balanceSupplier(() -> 1L).build();

        assertThat(itemSupplier1).isEqualTo(itemSupplier2);
    }

    @Test
    void testEqualsWithDifferentFrozenFlag() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentFrozenFlag =
                item1.copyBuilder().frozen(!item1.frozen()).build();

        assertThat(itemDifferentFrozenFlag).isNotEqualTo(item1);
    }

    @Test
    void testEqualsWithDifferentKycGrantedFlag() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentKycGrantedFlag =
                item1.copyBuilder().kycGranted(!item1.kycGranted()).build();

        assertThat(itemDifferentKycGrantedFlag).isNotEqualTo(item1);
    }

    @Test
    void testEqualsWithDifferentAutomaticAssociationFlag() {
        final var item1 = ARGUMENTS.get(0);
        final var itemDifferentAutomaticAssociationFlag = item1.copyBuilder()
                .automaticAssociation(!item1.automaticAssociation())
                .build();

        assertThat(itemDifferentAutomaticAssociationFlag).isNotEqualTo(item1);
    }

    @Test
    void testEqualsWithDifferentPreviousToken() {
        final var item1 = ARGUMENTS.get(0);
        final var item1WithNullTokenId =
                item1.copyBuilder().previousToken((TokenID) null).build();

        assertThat(item1).isNotEqualTo(item1WithNullTokenId);
        assertThat(item1WithNullTokenId).isNotEqualTo(item1);
    }

    @Test
    void testEqualsWithDifferentNextToken() {
        final var item1 = ARGUMENTS.get(0);
        final var item1WithNullTokenId =
                item1.copyBuilder().nextToken((TokenID) null).build();

        assertThat(item1).isNotEqualTo(item1WithNullTokenId);
        assertThat(item1WithNullTokenId).isNotEqualTo(item1);
    }

    @Test
    void testHasTokenId() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullTokenId = item1.copyBuilder().tokenId((TokenID) null).build();
        assertThat(item1.hasTokenId()).isTrue();
        assertThat(itemNullTokenId.hasTokenId()).isFalse();
    }

    @Test
    void testHasAccountId() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullAccountId =
                item1.copyBuilder().accountId((AccountID) null).build();
        assertThat(item1.hasAccountId()).isTrue();
        assertThat(itemNullAccountId.hasAccountId()).isFalse();
    }

    @Test
    void testHasPreviousToken() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullPreviousToken =
                item1.copyBuilder().previousToken((TokenID) null).build();
        assertThat(item1.hasPreviousToken()).isTrue();
        assertThat(itemNullPreviousToken.hasPreviousToken()).isFalse();
    }

    @Test
    void testHasNextToken() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullNextToken =
                item1.copyBuilder().nextToken((TokenID) null).build();
        assertThat(item1.hasNextToken()).isTrue();
        assertThat(itemNullNextToken.hasNextToken()).isFalse();
    }

    @Test
    void testTokenIdOrElse() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullTokenId = item1.copyBuilder().tokenId((TokenID) null).build();
        assertThat(item1.tokenIdOrElse(TokenID.DEFAULT)).isEqualTo(item1.tokenId());
        assertThat(itemNullTokenId.tokenIdOrElse(TokenID.DEFAULT)).isEqualTo(TokenID.DEFAULT);
    }

    @Test
    void testAccountIdOrElse() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullAccountId =
                item1.copyBuilder().accountId((AccountID) null).build();
        assertThat(item1.accountIdOrElse(AccountID.DEFAULT)).isEqualTo(item1.accountId());
        assertThat(itemNullAccountId.accountIdOrElse(AccountID.DEFAULT)).isEqualTo(AccountID.DEFAULT);
    }

    @Test
    void testPreviousTokenOrElse() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullPreviousToken =
                item1.copyBuilder().previousToken((TokenID) null).build();
        assertThat(item1.previousTokenOrElse(TokenID.DEFAULT)).isEqualTo(item1.tokenId());
        assertThat(itemNullPreviousToken.previousTokenOrElse(TokenID.DEFAULT)).isEqualTo(TokenID.DEFAULT);
    }

    @Test
    void testNextTokenOrElse() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullNextToken =
                item1.copyBuilder().nextToken((TokenID) null).build();
        assertThat(item1.nextTokenOrElse(TokenID.DEFAULT)).isEqualTo(item1.tokenId());
        assertThat(itemNullNextToken.nextTokenOrElse(TokenID.DEFAULT)).isEqualTo(TokenID.DEFAULT);
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
    void testIfAccountId() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullAccountId =
                item1.copyBuilder().accountId((AccountID) null).build();
        List<AccountID> accountIds = new ArrayList<>();
        Consumer<AccountID> accountIDConsumer = accountIds::add;

        item1.ifAccountId(accountIDConsumer);
        itemNullAccountId.ifAccountId(accountIDConsumer);
        assertThat(accountIds).isNotEmpty().hasSize(1).contains(item1.accountId());
    }

    @Test
    void testIfPreviousToken() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullPreviousToken =
                item1.copyBuilder().previousToken((TokenID) null).build();
        List<TokenID> tokenIds = new ArrayList<>();
        Consumer<TokenID> tokenIDConsumer = tokenIds::add;

        item1.ifPreviousToken(tokenIDConsumer);
        itemNullPreviousToken.ifPreviousToken(tokenIDConsumer);
        assertThat(tokenIds).isNotEmpty().hasSize(1).contains(item1.tokenId());
    }

    @Test
    void testIfNextToken() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullNextToken =
                item1.copyBuilder().nextToken((TokenID) null).build();
        List<TokenID> tokenIds = new ArrayList<>();
        Consumer<TokenID> tokenIDConsumer = tokenIds::add;

        item1.ifNextToken(tokenIDConsumer);
        itemNullNextToken.ifNextToken(tokenIDConsumer);
        assertThat(tokenIds).isNotEmpty().hasSize(1).contains(item1.tokenId());
    }

    @Test
    void testTokenIdOrThrow() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullTokenId = item1.copyBuilder().tokenId((TokenID) null).build();
        assertThat(item1.tokenIdOrThrow()).isEqualTo(item1.tokenId());
        assertThatThrownBy(itemNullTokenId::tokenIdOrThrow).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testAccountIdOrThrow() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullAccountId =
                item1.copyBuilder().accountId((AccountID) null).build();
        assertThat(item1.accountIdOrThrow()).isEqualTo(item1.accountId());
        assertThatThrownBy(itemNullAccountId::accountIdOrThrow).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testPreviousTokenOrThrow() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullTokenId =
                item1.copyBuilder().previousToken((TokenID) null).build();
        assertThat(item1.previousTokenOrThrow()).isEqualTo(item1.tokenId());
        assertThatThrownBy(itemNullTokenId::previousTokenOrThrow).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNextTokenOrThrow() {
        final var item1 = ARGUMENTS.get(0);
        final var itemNullTokenId =
                item1.copyBuilder().nextToken((TokenID) null).build();
        assertThat(item1.nextTokenOrThrow()).isEqualTo(item1.tokenId());
        assertThatThrownBy(itemNullTokenId::nextTokenOrThrow).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testBalance() {
        final var item1 = ARGUMENTS.get(0);
        final var balance = 123L;
        final var itemBalance = item1.copyBuilder().balance(balance).build();
        assertThat(itemBalance.balance()).isEqualTo(balance);
    }

    @Test
    void testTokenBuilder() {
        final var item1 = ARGUMENTS.get(0);
        final var tokenID = new TokenID(1, 2, 3);
        final var itemToken = item1.copyBuilder()
                .tokenId(new TokenID.Builder().shardNum(1).realmNum(2).tokenNum(3))
                .build();
        assertThat(itemToken.tokenId()).isEqualTo(tokenID);
    }

    @Test
    void testAccountBuilder() {
        final var item1 = ARGUMENTS.get(0);
        final var accountId = new AccountID(1, 2, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, 3L));
        final var itemAccount = item1.copyBuilder()
                .accountId(new AccountID.Builder().shardNum(1).realmNum(2).accountNum(3))
                .build();
        assertThat(itemAccount.accountId()).isEqualTo(accountId);
    }

    @Test
    void testPreviousTokenBuilder() {
        final var item1 = ARGUMENTS.get(0);
        final var tokenID = new TokenID(1, 2, 3);
        final var itemPreviousToken = item1.copyBuilder()
                .previousToken(new TokenID.Builder().shardNum(1).realmNum(2).tokenNum(3))
                .build();
        assertThat(itemPreviousToken.previousToken()).isEqualTo(tokenID);
    }

    @Test
    void testNextTokenBuilder() {
        final var item1 = ARGUMENTS.get(0);
        final var tokenID = new TokenID(1, 2, 3);
        final var itemNextToken = item1.copyBuilder()
                .nextToken(new TokenID.Builder().shardNum(1).realmNum(2).tokenNum(3))
                .build();
        assertThat(itemNextToken.nextToken()).isEqualTo(tokenID);
    }

    /**
     * List of all valid arguments for testing, built as a static list, so we can reuse it.
     */
    public static final List<TokenRelation> ARGUMENTS;

    static {
        final var tokenIdList = TokenIDTest.ARGUMENTS;
        final var accountIdList = AccountIDTest.ARGUMENTS;
        final var balanceList = LONG_TESTS_LIST;
        final var frozenList = BOOLEAN_TESTS_LIST;
        final var kycGrantedList = BOOLEAN_TESTS_LIST;
        final var automaticAssociationList = BOOLEAN_TESTS_LIST;
        final var previousTokenList = TokenIDTest.ARGUMENTS;
        final var nextTokenList = TokenIDTest.ARGUMENTS;

        // work out the longest of all the lists of args as that is how many test cases we need
        final int maxValues = IntStream.of(
                        tokenIdList.size(),
                        accountIdList.size(),
                        balanceList.size(),
                        frozenList.size(),
                        kycGrantedList.size(),
                        automaticAssociationList.size(),
                        previousTokenList.size(),
                        nextTokenList.size())
                .max()
                .getAsInt();
        // create new stream of model objects using lists above as constructor params
        ARGUMENTS = (maxValues > 0 ? IntStream.range(0, maxValues) : IntStream.of(0))
                .mapToObj(i -> new TokenRelation(
                        tokenIdList.get(Math.min(i, tokenIdList.size() - 1)),
                        accountIdList.get(Math.min(i, accountIdList.size() - 1)),
                        balanceList.get(Math.min(i, balanceList.size() - 1)),
                        frozenList.get(Math.min(i, frozenList.size() - 1)),
                        kycGrantedList.get(Math.min(i, kycGrantedList.size() - 1)),
                        automaticAssociationList.get(Math.min(i, automaticAssociationList.size() - 1)),
                        previousTokenList.get(Math.min(i, previousTokenList.size() - 1)),
                        nextTokenList.get(Math.min(i, nextTokenList.size() - 1))))
                .toList();
    }
}
