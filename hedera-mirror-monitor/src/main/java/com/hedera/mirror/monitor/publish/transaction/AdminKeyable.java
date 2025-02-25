// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.publish.transaction;

public interface AdminKeyable {

    String getAdminKey();

    void setAdminKey(String adminKey);
}
