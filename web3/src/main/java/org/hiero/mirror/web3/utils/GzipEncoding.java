// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@UtilityClass
public class GzipEncoding {

    public static final String MISSING_GZIP_HEADER_MESSAGE = "Accept-Encoding: gzip header is required";

    public static void require(final String acceptEncodingHeader) {
        if (acceptEncodingHeader == null || !acceptEncodingHeader.toLowerCase().contains("gzip")) {
            throw HttpClientErrorException.create(
                    MISSING_GZIP_HEADER_MESSAGE,
                    HttpStatus.NOT_ACCEPTABLE,
                    HttpStatus.NOT_ACCEPTABLE.getReasonPhrase(),
                    null,
                    null,
                    null);
        }
    }
}
