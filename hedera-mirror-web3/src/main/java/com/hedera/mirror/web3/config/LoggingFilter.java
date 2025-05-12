// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.config;

import static com.hedera.mirror.web3.utils.Constants.MODULARIZED_HEADER;
import static org.springframework.web.util.WebUtils.ERROR_EXCEPTION_ATTRIBUTE;

import com.hedera.mirror.web3.Web3Properties;
import jakarta.inject.Named;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

@CustomLog
@Named
@RequiredArgsConstructor
class LoggingFilter extends OncePerRequestFilter {

    @SuppressWarnings("java:S1075")
    private static final String ACTUATOR_PATH = "/actuator/";

    private static final String LOG_FORMAT = "{} {} {} in {} ms (mod={}): {} {} - {}";
    private static final String SUCCESS = "Success";

    private final Web3Properties web3Properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        long start = System.currentTimeMillis();
        Exception cause = null;

        if (!(request instanceof ContentCachingRequestWrapper)) {
            request = new ContentCachingRequestWrapper(request, web3Properties.getMaxPayloadLogSize());
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
        var wrapper = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);

        if (wrapper != null) {
            return StringUtils.deleteWhitespace(wrapper.getContentAsString());
        }

        return "";
    }

    private String getMessage(HttpServletRequest request, Exception e) {
        if (e != null) {
            return e.getMessage();
        }

        if (request.getAttribute(ERROR_EXCEPTION_ATTRIBUTE) instanceof Exception ex) {
            return ex.getMessage();
        }

        return SUCCESS;
    }
}
