// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.listener;

@SuppressWarnings("java:S2187") // Ignore no tests in file warning
class PollingTopicListenerTest extends AbstractTopicListenerTest {

    @Override
    protected ListenerProperties.ListenerType getType() {
        return ListenerProperties.ListenerType.POLL;
    }
}
