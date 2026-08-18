// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.springframework.web.util.WebUtils.ERROR_EXCEPTION_ATTRIBUTE;

import jakarta.inject.Named;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

@CustomLog
@Named
@RequiredArgsConstructor
class LoggingFilter extends OncePerRequestFilter {

    static final int PAYLOAD_CACHE_MULTIPLIER = 10;

    private static final String ACTUATOR_PATH = "/actuator/";
    private static final String LOG_FORMAT = "{} {} {} in {} ms : {} {} - {}";
    private static final String SUCCESS = "Success";
    private final Web3Properties web3Properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        long start = System.currentTimeMillis();
        Exception cause = null;

        if (!(request instanceof ContentCachingRequestWrapper)) {
            request = new ContentCachingRequestWrapper(
                    request, web3Properties.getMaxPayloadLogSize() * PAYLOAD_CACHE_MULTIPLIER);
        }

        try {
            filterChain.doFilter(request, response);
        } catch (Exception t) {
            cause = t;
        } finally {
            logRequest(request, response, start, cause);
        }
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, long startTime, Exception e) {
        var uri = request.getRequestURI();
        boolean actuator = Strings.CS.startsWith(uri, ACTUATOR_PATH);

        if (!log.isDebugEnabled() && actuator) {
            return;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        int status = response.getStatus();
        var content = getContent(request, status);
        var message = getMessage(request, e);
        var params =
                new Object[] {request.getRemoteAddr(), request.getMethod(), uri, elapsed, status, message, content};

        if (actuator) {
            log.debug(LOG_FORMAT, params);
        } else if (status >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            log.warn(LOG_FORMAT, params);
        } else if (log.isInfoEnabled()) {
            log.info(LOG_FORMAT, params);
        } else {
            log.debug(LOG_FORMAT, params); // params are more verbose if debug enabled
        }
    }

    private String getContent(HttpServletRequest request, int status) {
        var content = StringUtils.EMPTY;
        final int maxPayloadLogSize = web3Properties.getMaxPayloadLogSize();
        final var wrapper = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);

        if (wrapper != null) {
            content = StringUtils.deleteWhitespace(wrapper.getContentAsString());
        }
        if (content.length() > maxPayloadLogSize) {
            final var bos = new ByteArrayOutputStream(content.length() / 4);
            try (final var out = new GZIPOutputStream(bos)) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
                out.finish();
                final var compressed = Base64.getEncoder().encodeToString(bos.toByteArray());

                if (compressed.length() <= maxPayloadLogSize) {
                    content = compressed;
                }
            } catch (Exception _) {
                // Ignore
            }
        }

        // Truncate log message size unless it's a 5xx error
        if (log.isInfoEnabled()
                && content.length() > maxPayloadLogSize
                && status < HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            // Finally truncate to max payload size
            content = reorderFields(content);
            content = StringUtils.substring(content, 0, maxPayloadLogSize);
        }

        return content;
    }

    private String getMessage(HttpServletRequest request, Exception e) {
        if (e != null) {
            return e.getMessage();
        }

        if (request.getAttribute(ERROR_EXCEPTION_ATTRIBUTE) instanceof Exception ex) {
            if (ex instanceof MirrorEvmTransactionException mirrorEvmTransactionException) {
                return mirrorEvmTransactionException.getFullMessage();
            }
            return ex.getMessage();
        }

        return SUCCESS;
    }

    // Move data field to the end of the JSON so shorter fields are not truncated.
    private String reorderFields(String json) {
        // Find the last occurrence of "data":
        int dataStart = json.lastIndexOf("\"data\":");
        if (dataStart == -1) {
            return json; // No "data" field found
        }

        // Find the end of the data value (looking for comma or closing brace that's NOT inside the value)
        int dataValueStart = dataStart + "\"data\":".length();
        int dataValueEnd = findDataValueEnd(json, dataValueStart);
        if (dataValueEnd == -1) {
            return json; // Can't find value end
        }

        // Check if there's a comma right after the data field
        int commaAfterData = dataValueEnd;
        if (commaAfterData >= json.length() || json.charAt(commaAfterData) != ',') {
            return json; // No comma after data field, leave unchanged (like original regex)
        }

        // Find the last closing brace
        int lastBrace = json.lastIndexOf('}');
        if (lastBrace == -1 || lastBrace <= commaAfterData) {
            return json; // Malformed or data is already at the end
        }

        // Extract parts: before data, data with value, after data comma, tail
        String beforeData = json.substring(0, dataStart);
        String dataField = json.substring(dataStart, commaAfterData);
        String afterData = json.substring(commaAfterData + 1, lastBrace);
        String tail = json.substring(lastBrace);

        // Reconstruct: beforeData + afterData + ',' + dataField + tail
        return beforeData + afterData + "," + dataField + tail;
    }

    // Find where the data field value ends (simple approach for strings and primitives)
    private int findDataValueEnd(String json, int start) {
        if (start >= json.length()) {
            return -1;
        }

        // Skip whitespace
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }

        if (start >= json.length()) {
            return -1;
        }

        char first = json.charAt(start);

        // String value: scan until closing quote (handling escapes)
        if (first == '"') {
            int pos = start + 1;
            while (pos < json.length()) {
                char c = json.charAt(pos);
                if (c == '\\' && pos + 1 < json.length()) {
                    // Skip escaped character
                    pos += 2;
                } else if (c == '"') {
                    // Position after closing quote
                    return pos + 1;
                } else {
                    pos++;
                }
            }
            // Unterminated string
            return -1;
        }

        // Object or array: use StringUtils.indexOfAny or simple scan
        if (first == '{' || first == '[') {
            return findMatchingBrace(json, start);
        }

        // Primitive value (number, boolean, null): find next delimiter
        int nextComma = json.indexOf(',', start);
        int nextBrace = json.indexOf('}', start);

        if (nextComma == -1 && nextBrace == -1) {
            return json.length();
        } else if (nextComma == -1) {
            return nextBrace;
        } else if (nextBrace == -1) {
            return nextComma;
        } else {
            return Math.min(nextComma, nextBrace);
        }
    }

    // Find matching closing brace/bracket for nested structures
    private int findMatchingBrace(String json, int start) {
        char openChar = json.charAt(start);
        char closeChar = (openChar == '{') ? '}' : ']';
        int depth = 0;
        boolean inString = false;

        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);

            if (inString) {
                if (c == '\\' && i + 1 < json.length()) {
                    // Skip next character
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else {
                if (c == '"') {
                    inString = true;
                } else if (c == openChar) {
                    depth++;
                } else if (c == closeChar) {
                    depth--;
                    if (depth == 0) {
                        // Position after closing brace
                        return i + 1;
                    }
                }
            }
        }
        // No matching brace
        return -1;
    }
}
