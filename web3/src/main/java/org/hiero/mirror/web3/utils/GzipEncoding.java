// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@UtilityClass
public class GzipEncoding {

    public static final String MISSING_GZIP_HEADER_MESSAGE = "Accept-Encoding: gzip header is required";

    public static void require(final @Nullable String acceptEncodingHeader) {
        if (acceptEncodingHeader == null
                || !acceptEncodingHeader.toLowerCase(Locale.ROOT).contains("gzip")) {
            throw HttpClientErrorException.create(
                    MISSING_GZIP_HEADER_MESSAGE,
                    HttpStatus.NOT_ACCEPTABLE,
                    HttpStatus.NOT_ACCEPTABLE.getReasonPhrase(),
                    HttpHeaders.EMPTY,
                    new byte[0],
                    StandardCharsets.UTF_8);
        }
    }
}
