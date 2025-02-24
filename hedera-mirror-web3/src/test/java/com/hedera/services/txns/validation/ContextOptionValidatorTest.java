// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.validation;

import static com.hedera.services.utils.EntityIdUtils.toGrpcAccountId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.METADATA_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_NAME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.StoreImpl;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.Arrays;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContextOptionValidatorTest {

    private final Id id = new Id(0, 0, 13);
    private final AccountID accountId = toGrpcAccountId(id);
    private final Address address = id.asEvmAddress();

    @Mock
    MirrorNodeEvmProperties mirrorNodeEvmProperties;

    Account account;

    @Mock
    private StoreImpl store;

    private ContextOptionValidator subject;

    @BeforeEach
    void setup() {
        subject = new ContextOptionValidator(mirrorNodeEvmProperties);
    }

    @Test
    void rejectsInvalidMintBatchSize() {
        given(mirrorNodeEvmProperties.getMaxBatchSizeMint()).willReturn(10);
        assertEquals(BATCH_SIZE_LIMIT_EXCEEDED, subject.maxBatchSizeMintCheck(12));
    }

    @Test
    void rejectsInvalidMetadata() {
        given(mirrorNodeEvmProperties.getMaxNftMetadataBytes()).willReturn(2);
        assertEquals(METADATA_TOO_LONG, subject.nftMetadataCheck(new byte[] {1, 2, 3, 4}));
    }

    @Test
    void shortCircuitsLedgerExpiryCheckIfNoExpiryEnabled() {
        account = new Account(0L, id, 0);
        given(store.getAccount(address, OnMissing.THROW)).willReturn(account);
        given(mirrorNodeEvmProperties.shouldAutoRenewSomeEntityType()).willReturn(false);

        assertEquals(OK, subject.expiryStatusGiven(store, accountId));
    }

    @Test
    void shortCircuitsLedgerExpiryCheckIfBalanceIsNonZero() {
        account = new Account(0L, id, 1);
        given(store.getAccount(address, OnMissing.THROW)).willReturn(account);
        given(mirrorNodeEvmProperties.shouldAutoRenewSomeEntityType()).willReturn(true);

        assertEquals(OK, subject.expiryStatusGiven(store, accountId));
    }

    @Test
    void shortCircuitsIfBalanceIsZeroButNotDetached() {
        account = new Account(0L, id, 0).setExpiry(System.currentTimeMillis() / 1000 + 1000);
        given(store.getAccount(address, OnMissing.THROW)).willReturn(account);
        given(mirrorNodeEvmProperties.shouldAutoRenewSomeEntityType()).willReturn(true);

        assertEquals(OK, subject.expiryStatusGiven(store, accountId));
    }

    @Test
    void shortCircuitsIfContractExpiryNotEnabled() {
        account = new Account(0L, id, 0)
                .setExpiry(System.currentTimeMillis() / 1000 - 1000)
                .setIsSmartContract(true);
        given(store.getAccount(address, OnMissing.THROW)).willReturn(account);
        given(mirrorNodeEvmProperties.shouldAutoRenewSomeEntityType()).willReturn(true);
        assertEquals(OK, subject.expiryStatusGiven(store, accountId));
    }

    @Test
    void usesPreciseExpiryCheckIfBalanceIsZero() {
        account = new Account(0L, id, 0)
                .setExpiry(System.currentTimeMillis() / 1000 - 1000)
                .setIsSmartContract(true);
        given(store.getAccount(address, OnMissing.THROW)).willReturn(account);
        given(mirrorNodeEvmProperties.shouldAutoRenewSomeEntityType()).willReturn(true);
        given(mirrorNodeEvmProperties.shouldAutoRenewContracts()).willReturn(true);
        assertEquals(CONTRACT_EXPIRED_AND_PENDING_REMOVAL, subject.expiryStatusGiven(store, accountId));
    }

    @Test
    void alwaysOkExpiryStatusIfNonzeroBalance() {
        final var status = subject.expiryStatusGiven(1L, true, true);
        assertEquals(OK, status);
    }

    @Test
    void alwaysOkIfNotDetached() {
        final var status = subject.expiryStatusGiven(0L, false, true);
        assertEquals(OK, status);
    }

    @Test
    void contractIsExpiredIfZeroBalanceAndPastExpiry() {
        given(mirrorNodeEvmProperties.shouldAutoRenewContracts()).willReturn(true);
        final var status = subject.expiryStatusGiven(0, true, true);
        assertEquals(CONTRACT_EXPIRED_AND_PENDING_REMOVAL, status);
    }

    @Test
    void accountIsExpiredIfZeroBalanceAndDetached() {
        given(mirrorNodeEvmProperties.shouldAutoRenewAccounts()).willReturn(true);
        final var status = subject.expiryStatusGiven(0, true, false);
        assertEquals(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL, status);
    }

    @Test
    void ifAccountExpiryNotEnabledItsOk() {
        final var status = subject.expiryStatusGiven(0, true, false);
        assertEquals(OK, status);
    }

    @Test
    void ifContractExpiryNotEnabledItsOk() {
        final var status = subject.expiryStatusGiven(0, true, true);
        assertEquals(OK, status);
    }

    @Test
    void validateTimestampExpired() {
        final var expiry = Timestamp.getDefaultInstance();
        assertFalse(subject.isValidExpiry(expiry));
    }

    @Test
    void validateValidExpirationTimestamp() {
        final var expiry = Timestamp.newBuilder().setSeconds(9999999999L).build();
        assertTrue(subject.isValidExpiry(expiry));
    }

    @Test
    void rejectsBriefAutoRenewPeriod() {
        // setup:
        final Duration autoRenewPeriod = Duration.newBuilder().setSeconds(55L).build();

        given(mirrorNodeEvmProperties.getMinAutoRenewDuration()).willReturn(1_000L);

        // expect:
        assertFalse(subject.isValidAutoRenewPeriod(autoRenewPeriod));
        // and:
        verify(mirrorNodeEvmProperties).getMinAutoRenewDuration();
    }

    @Test
    void acceptsReasonablePeriod() {
        // setup:
        final Duration autoRenewPeriod =
                Duration.newBuilder().setSeconds(500_000L).build();

        given(mirrorNodeEvmProperties.getMinAutoRenewDuration()).willReturn(1_000L);
        given(mirrorNodeEvmProperties.getMaxAutoRenewDuration()).willReturn(1_000_000L);

        // expect:
        assertTrue(subject.isValidAutoRenewPeriod(autoRenewPeriod));
        // and:
        verify(mirrorNodeEvmProperties).getMinAutoRenewDuration();
        verify(mirrorNodeEvmProperties).getMaxAutoRenewDuration();
    }

    @Test
    void rejectsProlongedAutoRenewPeriod() {
        // setup:
        final Duration autoRenewPeriod =
                Duration.newBuilder().setSeconds(5_555_555L).build();

        given(mirrorNodeEvmProperties.getMinAutoRenewDuration()).willReturn(1_000L);
        given(mirrorNodeEvmProperties.getMaxAutoRenewDuration()).willReturn(1_000_000L);

        // expect:
        assertFalse(subject.isValidAutoRenewPeriod(autoRenewPeriod));
        // and:
        verify(mirrorNodeEvmProperties).getMinAutoRenewDuration();
        verify(mirrorNodeEvmProperties).getMaxAutoRenewDuration();
    }

    @Test
    void delegatesAutoRenewValidation() {
        final var len = 1234L;
        final var duration = Duration.newBuilder().setSeconds(len).build();

        final var mockOptionValidatorSubject = mock(OptionValidator.class);
        doCallRealMethod().when(mockOptionValidatorSubject).isValidAutoRenewPeriod(len);

        mockOptionValidatorSubject.isValidAutoRenewPeriod(len);

        verify(mockOptionValidatorSubject).isValidAutoRenewPeriod(duration);
    }

    @Test
    void acceptsReasonableTokenSymbol() {
        given(mirrorNodeEvmProperties.getMaxTokenSymbolUtf8Bytes()).willReturn(3);

        // expect:
        assertEquals(OK, subject.tokenSymbolCheck("AS"));
    }

    @Test
    void rejectsMalformedTokenSymbol() {
        given(mirrorNodeEvmProperties.getMaxTokenSymbolUtf8Bytes()).willReturn(100);

        // expect:
        assertEquals(MISSING_TOKEN_SYMBOL, subject.tokenSymbolCheck(""));
        assertEquals(INVALID_ZERO_BYTE_IN_STRING, subject.tokenSymbolCheck("\u0000"));
    }

    @Test
    void rejectsTooLongTokenSymbol() {
        given(mirrorNodeEvmProperties.getMaxTokenSymbolUtf8Bytes()).willReturn(3);

        // expect:
        assertEquals(TOKEN_SYMBOL_TOO_LONG, subject.tokenSymbolCheck("A€"));
    }

    @Test
    void acceptsReasonableTokenName() {
        given(mirrorNodeEvmProperties.getMaxTokenNameUtf8Bytes()).willReturn(100);

        // expect:
        assertEquals(OK, subject.tokenNameCheck("ASDF"));
    }

    @Test
    void rejectsMissingTokenName() {
        // expect:
        assertEquals(MISSING_TOKEN_NAME, subject.tokenNameCheck(""));
    }

    @Test
    void rejectsMalformedTokenName() {
        given(mirrorNodeEvmProperties.getMaxTokenNameUtf8Bytes()).willReturn(3);

        // expect:
        assertEquals(TOKEN_NAME_TOO_LONG, subject.tokenNameCheck("A€"));
        assertEquals(INVALID_ZERO_BYTE_IN_STRING, subject.tokenNameCheck("\u0000"));
    }

    @Test
    void memoCheckWorks() {
        final char[] aaa = new char[101];
        Arrays.fill(aaa, 'a');

        given(mirrorNodeEvmProperties.getMaxMemoUtf8Bytes()).willReturn(100);

        // expect:
        assertEquals(OK, subject.memoCheck("OK"));
        assertEquals(MEMO_TOO_LONG, subject.memoCheck(new String(aaa)));
        assertEquals(INVALID_ZERO_BYTE_IN_STRING, subject.memoCheck("Not s\u0000 ok!"));
    }
}
