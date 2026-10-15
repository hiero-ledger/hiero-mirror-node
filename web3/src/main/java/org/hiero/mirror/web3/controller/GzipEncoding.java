// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Requires {@code Accept-Encoding: gzip} for large opcode/prestate responses so clients receive compressed payloads.
 */
@UtilityClass
final class GzipEncoding {

    static final String MISSING_GZIP_HEADER_MESSAGE = "Accept-Encoding: gzip header is required";

    static void validate(final String acceptEncodingHeader) {
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
