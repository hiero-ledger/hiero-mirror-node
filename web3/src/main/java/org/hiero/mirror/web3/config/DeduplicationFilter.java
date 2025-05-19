// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import jakarta.inject.Named;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

@CustomLog
@Named
@RequiredArgsConstructor
public class DeduplicationFilter extends OncePerRequestFilter {

    private static final String REQUEST_DEDUPLICATION_MESSAGE =
            "Too many identical requests in a short period of time, please try again later.";
    private static final List<String> FILTERED_PATHS = List.of("/contracts/call");
    private final RequestDeduplicationCache deduplicationCache;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        if (FILTERED_PATHS.stream()
                .anyMatch(path -> wrappedRequest.getRequestURI().endsWith(path))) {
            String key = getRequestFingerprint(wrappedRequest);
            if (key != null && deduplicationCache.isDuplicate(key)) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.getWriter().write(REQUEST_DEDUPLICATION_MESSAGE);
                return;
            }
        }
        filterChain.doFilter(wrappedRequest, response);
    }

    public static String getRequestFingerprint(ContentCachingRequestWrapper request) {
        try {
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String payload = new String(
                    request.getContentAsByteArray(),
                    request.getCharacterEncoding());
            String keySource = method + "|" + uri + "|" + payload;
            return DigestUtils.sha256Hex(keySource);
        } catch (Exception e) {
            return null;
        }
    }
}
