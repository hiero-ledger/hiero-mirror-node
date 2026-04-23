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

    private static final String ACTUATOR_PATH = "/actuator/";
    private static final String BRACE = "}";
    private static final String COMMA = ",";
    private static final String DATA_FIELD = "\"data\"";
    private static final String LOG_FORMAT = "{} {} {} in {} ms : {} {} - {}";
    private static final String SUCCESS = "Success";

    private final Web3Properties web3Properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        long start = System.currentTimeMillis();
        Exception cause = null;

        if (!(request instanceof ContentCachingRequestWrapper)) {
            request = new ContentCachingRequestWrapper(request, web3Properties.getMaxPayloadLogSize() * 10);
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
        } else {
            log.info(LOG_FORMAT, params);
        }
    }

    private String getContent(HttpServletRequest request, int status) {
        var content = StringUtils.EMPTY;
        final int maxPayloadLogSize = web3Properties.getMaxPayloadLogSize();
        final var wrapper = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);

        if (wrapper != null) {
            content = StringUtils.deleteWhitespace(wrapper.getContentAsString());
        }

        content = reorderFields(content);

        if (content.length() > maxPayloadLogSize) {
            final var bos = new ByteArrayOutputStream();
            try (final var out = new GZIPOutputStream(bos)) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
                out.finish();
                final var compressed = Base64.getEncoder().encodeToString(bos.toByteArray());

                if (compressed.length() <= maxPayloadLogSize) {
                    content = compressed;
                }
            } catch (Exception e) {
                // Ignore
            }
        }

        // Truncate log message size unless it's a 5xx error
        if (content.length() > maxPayloadLogSize && status < HttpStatus.INTERNAL_SERVER_ERROR.value()) {
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
        int start = json.indexOf(DATA_FIELD);
        if (start == -1) {
            return json;
        }

        int end = json.indexOf(COMMA, start);
        if (end == -1) {
            return json;
        }

        String dataField = json.substring(start, end);
        return json.replace(COMMA + dataField, StringUtils.EMPTY).replace(BRACE, COMMA + dataField + BRACE);
    }
}
