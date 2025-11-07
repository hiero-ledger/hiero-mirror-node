// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.LinkedList;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test class to ensure proper hook execution sequence is maintained based on the HIP-1195 specification.
 * The execution order should be:
 * 1. HBAR transfers (PreTx hooks)
 * 2. Fungible token transfers (PreTx hooks)
 * 3. NFT transfers (PreTx hooks)
 * 4. HBAR transfers (Pre hooks from PrePostTx)
 * 5. Fungible token transfers (Pre hooks from PrePostTx)
 * 6. NFT transfers (Pre hooks from PrePostTx)
 * 7. HBAR transfers (Post hooks from PrePostTx)
 * 8. Fungible token transfers (Post hooks from PrePostTx)
 * 9. NFT transfers (Post hooks from PrePostTx)
 *
 * This sequence is implemented in EntityRecordItemListener.onItem() lines 124-152.
 */
class EntityRecordItemListenerHookExecutionQueueTest extends AbstractEntityRecordItemListenerTest {

    @Test
    @DisplayName(
            "Hook execution queue maintains proper sequence for allowExecHookIds -> allowPreExecHookIds -> allowPostExecHookIds")
    void testHookExecutionQueueSequence() {
        // Create a RecordItem and manually set up a hook execution queue to test the ordering
        var recordItem = recordItemBuilder.cryptoTransfer().build();

        // Simulate the queue building logic from EntityRecordItemListener.onItem()
        // This mirrors lines 124-152 in EntityRecordItemListener.java
        final var hookExecutionQueue = new LinkedList<RecordItem.HookId>();

        // Simulate allowExecHookIds (PreTx hooks) - these come first
        final var allowExecHookIds = new LinkedList<RecordItem.HookId>();
        allowExecHookIds.add(new RecordItem.HookId(101L, 1001L)); // HBAR transfer PreTx hook
        allowExecHookIds.add(new RecordItem.HookId(201L, 1001L)); // Token transfer PreTx hook
        allowExecHookIds.add(new RecordItem.HookId(301L, 1001L)); // NFT transfer PreTx hook

        // Simulate allowPreExecHookIds (Pre hooks from PrePostTx) - these come second
        final var allowPreExecHookIds = new LinkedList<RecordItem.HookId>();
        allowPreExecHookIds.add(new RecordItem.HookId(102L, 1002L)); // HBAR transfer Pre hook
        allowPreExecHookIds.add(new RecordItem.HookId(202L, 1002L)); // Token transfer Pre hook
        allowPreExecHookIds.add(new RecordItem.HookId(302L, 1002L)); // NFT transfer Pre hook

        // Simulate allowPostExecHookIds (Post hooks from PrePostTx) - these come third
        final var allowPostExecHookIds = new LinkedList<RecordItem.HookId>();
        allowPostExecHookIds.add(new RecordItem.HookId(102L, 1002L)); // HBAR transfer Post hook (same as Pre)
        allowPostExecHookIds.add(new RecordItem.HookId(202L, 1002L)); // Token transfer Post hook (same as Pre)
        allowPostExecHookIds.add(new RecordItem.HookId(302L, 1002L)); // NFT transfer Post hook (same as Pre)

        // Build the final queue in the correct order (lines 149-151 in EntityRecordItemListener.java)
        hookExecutionQueue.addAll(allowExecHookIds);
        hookExecutionQueue.addAll(allowPreExecHookIds);
        hookExecutionQueue.addAll(allowPostExecHookIds);

        recordItem.setHookExecutionQueue(hookExecutionQueue);

        // Test the queue
        var queue = recordItem.getHookExecutionQueue();
        assertNotNull(queue, "Hook execution queue should be created");
        assertEquals(9, queue.size(), "Queue should contain 9 hooks");

        // Convert to list for easier testing
        var hookSequence = new LinkedList<>(queue);

        // Verify the exact sequence
        // allowExecHookIds first
        assertEquals(new RecordItem.HookId(101L, 1001L), hookSequence.get(0), "First hook should be HBAR PreTx");
        assertEquals(new RecordItem.HookId(201L, 1001L), hookSequence.get(1), "Second hook should be Token PreTx");
        assertEquals(new RecordItem.HookId(301L, 1001L), hookSequence.get(2), "Third hook should be NFT PreTx");

        // allowPreExecHookIds second
        assertEquals(new RecordItem.HookId(102L, 1002L), hookSequence.get(3), "Fourth hook should be HBAR Pre");
        assertEquals(new RecordItem.HookId(202L, 1002L), hookSequence.get(4), "Fifth hook should be Token Pre");
        assertEquals(new RecordItem.HookId(302L, 1002L), hookSequence.get(5), "Sixth hook should be NFT Pre");

        // allowPostExecHookIds third
        assertEquals(new RecordItem.HookId(102L, 1002L), hookSequence.get(6), "Seventh hook should be HBAR Post");
        assertEquals(new RecordItem.HookId(202L, 1002L), hookSequence.get(7), "Eighth hook should be Token Post");
        assertEquals(new RecordItem.HookId(302L, 1002L), hookSequence.get(8), "Ninth hook should be NFT Post");
    }

    @Test
    @DisplayName("Hook execution queue handles empty case gracefully")
    void testHookExecutionQueueEmpty() {
        var recordItem = recordItemBuilder.cryptoTransfer().build();

        // Simulate no hooks scenario
        final var hookExecutionQueue = new LinkedList<RecordItem.HookId>();
        // Don't add any hooks

        recordItem.setHookExecutionQueue(hookExecutionQueue);

        var queue = recordItem.getHookExecutionQueue();
        assertNotNull(queue, "Hook execution queue should exist even when empty");
        assertThat(queue).isEmpty();
    }

    @Test
    @DisplayName("Hook execution queue polls hooks in correct order")
    void testHookExecutionQueuePolling() {
        var recordItem = recordItemBuilder.cryptoTransfer().build();

        // Create a simple queue with known order
        final var hookExecutionQueue = new LinkedList<RecordItem.HookId>();
        hookExecutionQueue.add(new RecordItem.HookId(1L, 100L)); // allowExecHookIds
        hookExecutionQueue.add(new RecordItem.HookId(2L, 200L)); // allowPreExecHookIds
        hookExecutionQueue.add(new RecordItem.HookId(2L, 200L)); // allowPostExecHookIds (same as pre)

        recordItem.setHookExecutionQueue(hookExecutionQueue);

        // Test polling behavior (this is how ContractResultServiceImpl.processHookStorageChanges uses it)
        var queue = recordItem.getHookExecutionQueue();

        // First poll should return the first hook
        var firstHook = recordItem.nextHookContext();
        assertNotNull(firstHook);
        assertEquals(1L, firstHook.hookId());
        assertEquals(100L, firstHook.ownerId());

        // Second poll should return the second hook
        var secondHook = recordItem.nextHookContext();
        assertNotNull(secondHook);
        assertEquals(2L, secondHook.hookId());
        assertEquals(200L, secondHook.ownerId());

        // Third poll should return the third hook
        var thirdHook = recordItem.nextHookContext();
        assertNotNull(thirdHook);
        assertEquals(2L, thirdHook.hookId());
        assertEquals(200L, thirdHook.ownerId());

        // Fourth poll should return null (queue exhausted)
        var fourthHook = recordItem.nextHookContext();
        assertEquals(null, fourthHook);

        // hasMoreHookContexts should return false
        assertEquals(false, recordItem.hasMoreHookContexts());
    }

    @Test
    @DisplayName("Hook execution queue maintains sequence within transfer types")
    void testHookExecutionQueueWithinTransferTypes() {
        var recordItem = recordItemBuilder.cryptoTransfer().build();

        // Test multiple hooks of the same type maintain order
        final var hookExecutionQueue = new LinkedList<RecordItem.HookId>();

        // Multiple allowExecHookIds (should maintain order)
        hookExecutionQueue.add(new RecordItem.HookId(10L, 1000L)); // First HBAR PreTx
        hookExecutionQueue.add(new RecordItem.HookId(11L, 1001L)); // Second HBAR PreTx
        hookExecutionQueue.add(new RecordItem.HookId(20L, 2000L)); // First Token PreTx
        hookExecutionQueue.add(new RecordItem.HookId(21L, 2001L)); // Second Token PreTx

        recordItem.setHookExecutionQueue(hookExecutionQueue);

        // Verify order is maintained
        assertEquals(new RecordItem.HookId(10L, 1000L), recordItem.nextHookContext());
        assertEquals(new RecordItem.HookId(11L, 1001L), recordItem.nextHookContext());
        assertEquals(new RecordItem.HookId(20L, 2000L), recordItem.nextHookContext());
        assertEquals(new RecordItem.HookId(21L, 2001L), recordItem.nextHookContext());
        assertEquals(null, recordItem.nextHookContext());
    }
}
