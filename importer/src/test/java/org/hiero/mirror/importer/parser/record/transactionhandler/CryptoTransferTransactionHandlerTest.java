// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.HookCall;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.hook.AbstractHook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CryptoTransferTransactionHandlerTest extends AbstractTransactionHandlerTest {
    @Override
    protected TransactionHandler getTransactionHandler() {
        return new CryptoTransferTransactionHandler(entityIdService);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransactionSuccessful() {
        // given
        var recordItem = recordItemBuilder.cryptoTransfer().build();
        long timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(null))
                .get();
        var expectedEntityTransactions = getExpectedEntityTransactions(recordItem, transaction);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @Test
    @DisplayName("Hook execution queue handles empty case gracefully")
    void testHookExecutionQueueEmpty() {
        var recordItem = recordItemBuilder.cryptoTransfer().build();
        var transaction = domainBuilder.transaction().get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        var queue = recordItem.getHookExecutionQueue();
        assertThat(queue).isEmpty();
    }

    @Test
    @DisplayName("Hook execution queue covers all use cases from HIP-1195 specification")
    void testComprehensiveHookExecutionQueueSequence() {
        // Mock EntityIdService to return proper EntityId objects for each account ID
        when(entityIdService.lookup(any(AccountID.class))).thenAnswer(invocation -> {
            AccountID accountId = invocation.getArgument(0);
            return Optional.of(EntityId.of(accountId));
        });
        var recordItem = recordItemBuilder
                .cryptoTransfer()
                .transactionBody(body -> body.setTransfers(TransferList.newBuilder()
                                // HBAR transfers - PreTx hooks (allowExec phase)
                                .addAccountAmounts(AccountAmount.newBuilder()
                                        .setAccountID(AccountID.newBuilder().setAccountNum(1001L))
                                        .setAmount(100L)
                                        .setPreTxAllowanceHook(
                                                HookCall.newBuilder().setHookId(101L)))
                                .addAccountAmounts(AccountAmount.newBuilder()
                                        .setAccountID(AccountID.newBuilder().setAccountNum(1002L))
                                        .setAmount(200L)
                                        .setPreTxAllowanceHook(
                                                HookCall.newBuilder().setHookId(102L)))
                                // HBAR transfers - PrePostTx hooks (allowPre and allowPost phases)
                                .addAccountAmounts(AccountAmount.newBuilder()
                                        .setAccountID(AccountID.newBuilder().setAccountNum(1003L))
                                        .setAmount(-150L)
                                        .setPrePostTxAllowanceHook(
                                                HookCall.newBuilder().setHookId(103L)))
                                .addAccountAmounts(AccountAmount.newBuilder()
                                        .setAccountID(AccountID.newBuilder().setAccountNum(1004L))
                                        .setAmount(-150L)
                                        .setPrePostTxAllowanceHook(
                                                HookCall.newBuilder().setHookId(104L))))
                        // Token transfers
                        .addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(TokenID.newBuilder().setTokenNum(2000L))
                                // Fungible token transfers - PreTx hooks (allowExec phase)
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(AccountID.newBuilder().setAccountNum(2001L))
                                        .setAmount(50L)
                                        .setPreTxAllowanceHook(
                                                HookCall.newBuilder().setHookId(201L)))
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(AccountID.newBuilder().setAccountNum(2002L))
                                        .setAmount(75L)
                                        .setPreTxAllowanceHook(
                                                HookCall.newBuilder().setHookId(202L)))
                                // Fungible token transfers - PrePostTx hooks (allowPre and allowPost phases)
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(AccountID.newBuilder().setAccountNum(2003L))
                                        .setAmount(-125L)
                                        .setPrePostTxAllowanceHook(
                                                HookCall.newBuilder().setHookId(203L)))
                                // NFT transfers with both sender and receiver hooks
                                .addNftTransfers(NftTransfer.newBuilder()
                                        .setSenderAccountID(
                                                AccountID.newBuilder().setAccountNum(3001L))
                                        .setReceiverAccountID(
                                                AccountID.newBuilder().setAccountNum(3101L))
                                        .setSerialNumber(1L)
                                        .setPreTxSenderAllowanceHook(
                                                HookCall.newBuilder().setHookId(301L))
                                        .setPreTxReceiverAllowanceHook(
                                                HookCall.newBuilder().setHookId(311L)))
                                .addNftTransfers(NftTransfer.newBuilder()
                                        .setSenderAccountID(
                                                AccountID.newBuilder().setAccountNum(3002L))
                                        .setReceiverAccountID(
                                                AccountID.newBuilder().setAccountNum(3102L))
                                        .setSerialNumber(2L)
                                        .setPrePostTxSenderAllowanceHook(
                                                HookCall.newBuilder().setHookId(302L))
                                        .setPrePostTxReceiverAllowanceHook(
                                                HookCall.newBuilder().setHookId(312L)))))
                .build();

        var transaction = domainBuilder.transaction().get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        var queue = recordItem.getHookExecutionQueue();
        assertNotNull(queue, "Hook execution queue should be created");

        // Expected: 2 HBAR PreTx + 2 Token PreTx + 2 NFT PreTx (sender first) + 2 NFT PreTx (receiver)
        //         + 2 HBAR PrePostTx Pre + 1 Token PrePostTx Pre + 2 NFT PrePostTx Pre (sender first) + 2 NFT PrePostTx
        // Pre (receiver)
        //         + 2 HBAR PrePostTx Post + 1 Token PrePostTx Post + 2 NFT PrePostTx Post (sender first) + 2 NFT
        // PrePostTx Post (receiver)
        // Expected: 2 HBAR PreTx + 2 Token PreTx + 2 NFT PreTx (1 sender + 1 receiver) = 6 allowExec
        //         + 2 HBAR PrePostTx Pre + 1 Token PrePostTx Pre + 2 NFT PrePostTx Pre (1 sender + 1 receiver) = 5
        // allowPre
        //         + 2 HBAR PrePostTx Post + 1 Token PrePostTx Post + 2 NFT PrePostTx Post (1 sender + 1 receiver) = 5
        // allowPost
        //         Total: 6 + 5 + 5 = 16 hooks
        assertEquals(16, queue.size(), "Queue should contain 16 hooks total");

        // Verify the exact execution sequence according to HIP-1195:

        // Phase 1: allowExec (PreTx hooks)
        // 1. All pre_tx_allowance_hook calls in HBAR transfers (in order)
        assertEquals(new AbstractHook.Id(101L, 1001L), recordItem.nextHookContext());
        assertEquals(new AbstractHook.Id(102L, 1002L), recordItem.nextHookContext());

        // 2. All pre_tx_allowance_hook calls in fungible token transfers (in order)
        assertEquals(new AbstractHook.Id(201L, 2001L), recordItem.nextHookContext());
        assertEquals(new AbstractHook.Id(202L, 2002L), recordItem.nextHookContext());

        // 3. All NFT pre_tx hooks (sender first, then receiver for each NFT)
        assertEquals(new AbstractHook.Id(301L, 3001L), recordItem.nextHookContext()); // NFT1 sender PreTx
        assertEquals(new AbstractHook.Id(311L, 3101L), recordItem.nextHookContext()); // NFT1 receiver PreTx

        // Phase 2: allowPre (Pre hooks from PrePostTx)
        // 4. All pre_post_tx_allowance_hook calls in HBAR transfers (in order) - Pre phase
        assertEquals(new AbstractHook.Id(103L, 1003L), recordItem.nextHookContext());
        assertEquals(new AbstractHook.Id(104L, 1004L), recordItem.nextHookContext());

        // 5. All pre_post_tx_allowance_hook calls in fungible token transfers (in order) - Pre phase
        assertEquals(new AbstractHook.Id(203L, 2003L), recordItem.nextHookContext());

        // 6. All NFT pre_post_tx hooks (sender first, then receiver for each NFT) - Pre phase
        assertEquals(new AbstractHook.Id(302L, 3002L), recordItem.nextHookContext()); // NFT2 sender PrePostTx Pre
        assertEquals(new AbstractHook.Id(312L, 3102L), recordItem.nextHookContext()); // NFT2 receiver PrePostTx Pre

        // Phase 3: allowPost (Post hooks from PrePostTx - same order as Pre)
        // 7. All pre_post_tx hooks from phases 4-6, same order - Post phase
        assertEquals(new AbstractHook.Id(103L, 1003L), recordItem.nextHookContext()); // HBAR PrePostTx Post
        assertEquals(new AbstractHook.Id(104L, 1004L), recordItem.nextHookContext()); // HBAR PrePostTx Post
        assertEquals(new AbstractHook.Id(203L, 2003L), recordItem.nextHookContext()); // Token PrePostTx Post
        assertEquals(new AbstractHook.Id(302L, 3002L), recordItem.nextHookContext()); // NFT2 sender PrePostTx Post
        assertEquals(new AbstractHook.Id(312L, 3102L), recordItem.nextHookContext()); // NFT2 receiver PrePostTx Post

        // Verify queue is exhausted
        assertNull(recordItem.nextHookContext());
    }

    @Test
    @DisplayName("Hook execution queue handles unresolvable alias/EVM address")
    void testHookExecutionQueueWithUnresolvableAlias() {
        // Mock EntityIdService to fail to resolve alias
        when(entityIdService.lookup(any(AccountID.class))).thenReturn(Optional.empty());

        var recordItem = recordItemBuilder
                .cryptoTransfer()
                .transactionBody(body -> body.setTransfers(TransferList.newBuilder()
                        .addAccountAmounts(AccountAmount.newBuilder()
                                .setAccountID(AccountID.newBuilder()
                                        .setAlias(com.google.protobuf.ByteString.copyFromUtf8("unresolvable_alias")))
                                .setAmount(100L)
                                .setPreTxAllowanceHook(HookCall.newBuilder().setHookId(502L)))))
                .build();

        var transaction = domainBuilder.transaction().get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        var queue = recordItem.getHookExecutionQueue();
        assertNotNull(queue, "Hook execution queue should be created");
        assertEquals(1, queue.size(), "Queue should contain 1 hook with EntityId.EMPTY");
        assertEquals(new AbstractHook.Id(502L, 0L), recordItem.nextHookContext()); // EntityId.EMPTY has ID 0
        assertNull(recordItem.nextHookContext());
    }

    @Test
    @DisplayName("Hook execution queue handles mixed resolvable and unresolvable aliases in NFT transfers")
    void testHookExecutionQueueWithMixedNftAliases() {
        // Mock EntityIdService to resolve some aliases and fail others
        when(entityIdService.lookup(any(AccountID.class))).thenAnswer(invocation -> {
            AccountID accountId = invocation.getArgument(0);
            if (accountId.hasAlias()) {
                var alias = accountId.getAlias().toStringUtf8();
                if ("resolvable_sender".equals(alias)) {
                    return Optional.of(EntityId.of(6001L));
                } else if ("resolvable_receiver".equals(alias)) {
                    return Optional.of(EntityId.of(6002L));
                }
                // Unresolvable aliases return empty
                return Optional.empty();
            }
            return Optional.of(EntityId.of(accountId));
        });

        var recordItem = recordItemBuilder
                .cryptoTransfer()
                .transactionBody(body -> body.addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(TokenID.newBuilder().setTokenNum(7000L))
                        .addNftTransfers(NftTransfer.newBuilder()
                                .setSenderAccountID(AccountID.newBuilder()
                                        .setAlias(com.google.protobuf.ByteString.copyFromUtf8("resolvable_sender")))
                                .setReceiverAccountID(AccountID.newBuilder()
                                        .setAlias(com.google.protobuf.ByteString.copyFromUtf8("unresolvable_receiver")))
                                .setSerialNumber(1L)
                                .setPreTxSenderAllowanceHook(
                                        HookCall.newBuilder().setHookId(701L))
                                .setPreTxReceiverAllowanceHook(
                                        HookCall.newBuilder().setHookId(702L)))
                        .addNftTransfers(NftTransfer.newBuilder()
                                .setSenderAccountID(AccountID.newBuilder()
                                        .setAlias(com.google.protobuf.ByteString.copyFromUtf8("unresolvable_sender")))
                                .setReceiverAccountID(AccountID.newBuilder()
                                        .setAlias(com.google.protobuf.ByteString.copyFromUtf8("resolvable_receiver")))
                                .setSerialNumber(2L)
                                .setPrePostTxSenderAllowanceHook(
                                        HookCall.newBuilder().setHookId(703L))
                                .setPrePostTxReceiverAllowanceHook(
                                        HookCall.newBuilder().setHookId(704L)))))
                .build();

        var transaction = domainBuilder.transaction().get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        var queue = recordItem.getHookExecutionQueue();
        assertNotNull(queue, "Hook execution queue should be created");
        assertEquals(
                6, queue.size(), "Queue should contain 6 hooks total"); // 2 PreTx + 2 PrePostTx Pre + 2 PrePostTx Post

        // Phase 1: allowExec (PreTx hooks)
        assertEquals(new AbstractHook.Id(701L, 6001L), recordItem.nextHookContext()); // Resolvable sender
        assertEquals(
                new AbstractHook.Id(702L, 0L), recordItem.nextHookContext()); // Unresolvable receiver -> EntityId.EMPTY

        // Phase 2: allowPre (Pre hooks from PrePostTx)
        assertEquals(
                new AbstractHook.Id(703L, 0L), recordItem.nextHookContext()); // Unresolvable sender -> EntityId.EMPTY
        assertEquals(new AbstractHook.Id(704L, 6002L), recordItem.nextHookContext()); // Resolvable receiver

        // Phase 3: allowPost (Post hooks from PrePostTx - same order as Pre)
        assertEquals(
                new AbstractHook.Id(703L, 0L), recordItem.nextHookContext()); // Unresolvable sender -> EntityId.EMPTY
        assertEquals(new AbstractHook.Id(704L, 6002L), recordItem.nextHookContext()); // Resolvable receiver

        // Verify queue is exhausted
        assertNull(recordItem.nextHookContext());
    }
}
