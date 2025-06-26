// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.hiero.mirror.web3.utils.Constants.MODULARIZED_HEADER;
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
    private static final String LOG_FORMAT = "{} {} {} in {} ms (mod={}): {} {} - {}";
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
        boolean actuator = StringUtils.startsWith(uri, ACTUATOR_PATH);

        if (!log.isDebugEnabled() && actuator) {
            return;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        var content = getContent(request);
        var message = getMessage(request, e);
        var modularized = response.getHeader(MODULARIZED_HEADER);
        int status = response.getStatus();
        var params = new Object[] {
            request.getRemoteAddr(), request.getMethod(), uri, elapsed, modularized, status, message, content
        };

        if (actuator) {
            log.debug(LOG_FORMAT, params);
        } else if (status >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            log.warn(LOG_FORMAT, params);
        } else {
            log.info(LOG_FORMAT, params);
        }
    }

    private String getContent(HttpServletRequest request) {
        var content = StringUtils.EMPTY;
        int maxPayloadLogSize = web3Properties.getMaxPayloadLogSize();
        var wrapper = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);

        if (wrapper != null) {
            content = StringUtils.deleteWhitespace(wrapper.getContentAsString());
        }

        if (content.length() > maxPayloadLogSize) {
            var bos = new ByteArrayOutputStream();
            try (var out = new GZIPOutputStream(bos)) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
                out.finish();
                var compressed = Base64.getEncoder().encodeToString(bos.toByteArray());

                if (compressed.length() <= maxPayloadLogSize) {
                    content = compressed;
                }
            } catch (Exception e) {
                // Ignore
            }
        }

        if (content.length() > maxPayloadLogSize) {
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
}
