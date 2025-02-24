// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor;

public enum ScenarioStatus {
    COMPLETED, // The scenario has completed normally due to reaching the configured duration or limit
    IDLE, // The scenario has not completed but is not currently receiving any responses
    RUNNING, // The scenario is still actively receiving responses
}
