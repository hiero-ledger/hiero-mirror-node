// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import static org.springframework.web.util.WebUtils.ERROR_EXCEPTION_ATTRIBUTE;

import jakarta.inject.Named;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.CustomLog;
import org.apache.commons.lang3.Strings;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

@CustomLog
@Named
class LoggingFilter extends OncePerRequestFilter {

    @SuppressWarnings("java:S1075")
    private static final String ACTUATOR_PATH = "/actuator/";

    private static final String LOG_FORMAT = "{} {} {} in {} ms: {} {}";
    private static final String SUCCESS = "Success";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        long start = System.currentTimeMillis();
        Exception cause = null;

        try {
            filterChain.doFilter(request, response);
        } catch (Exception t) {
            cause = t;
        } finally {
            logRequest(request, response, start, cause);
        }
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, long startTime, Exception cause) {
        String uri = request.getRequestURI();
        boolean actuator = Strings.CS.startsWith(uri, ACTUATOR_PATH);

        if (!log.isDebugEnabled() && actuator) {
            return;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        var message = getMessage(request, cause);
        int status = response.getStatus();
        var params = new Object[] {request.getRemoteAddr(), request.getMethod(), uri, elapsed, status, message};

        if (actuator) {
            log.debug(LOG_FORMAT, params);
        } else if (status >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            log.warn(LOG_FORMAT, params);
        } else {
            log.info(LOG_FORMAT, params);
        }
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
