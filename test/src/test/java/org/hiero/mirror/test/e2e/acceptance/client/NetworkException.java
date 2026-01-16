// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.client;

public class NetworkException extends RuntimeException {

    private static final long serialVersionUID = 4080400972797042474L;

    public NetworkException(String message) {
        super(message);
    }
}
