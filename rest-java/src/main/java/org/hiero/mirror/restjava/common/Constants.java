// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Constants {

    // Parameter names
    public static final String ACCOUNT_ID = "account.id";
    public static final String HOOK_ID = "hook.id";
    public static final String RECEIVER_ID = "receiver.id";
    public static final String SENDER_ID = "sender.id";
    public static final String SERIAL_NUMBER = "serialnumber";
    public static final String TIMESTAMP = "timestamp";
    public static final String TOKEN_ID = "token.id";

    // Defaults and constraints
    public static final String DEFAULT_LIMIT = "25";
    public static final int MAX_LIMIT = 100;
    public static final int MAX_REPEATED_QUERY_PARAMETERS = 100;
}
