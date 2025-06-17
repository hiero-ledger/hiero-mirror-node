// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.config;

import java.net.URI;
import java.net.URISyntaxException;

public interface ApiProperties {
    String getBaseUrl();

    default String getEndpoint() throws URISyntaxException {
        var endpoint = getBaseUrl();

        if (endpoint != null) {
            URI uri = new URI(endpoint);
            if (uri.getPort() == -1) {
                var https = uri.getScheme().equals("https");
                var trimmedEndpoint = endpoint.replaceFirst("/api/v1/?$", "");
                endpoint = trimmedEndpoint + ":" + (https ? 443 : 80);
            }
            endpoint = (endpoint.replaceFirst("^https?://", "").replaceFirst("/api/v1/?$", ""));
        }

        return endpoint;
    }
}
