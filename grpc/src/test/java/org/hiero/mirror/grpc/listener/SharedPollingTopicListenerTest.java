// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.listener;

@SuppressWarnings("java:S2187") // Ignore no tests in file warning
class SharedPollingTopicListenerTest extends AbstractSharedTopicListenerTest {

    @Override
    protected ListenerProperties.ListenerType getType() {
        return ListenerProperties.ListenerType.SHARED_POLL;
    }
}
