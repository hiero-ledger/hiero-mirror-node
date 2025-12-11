// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static com.google.common.net.HttpHeaders.X_FORWARDED_FOR;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.utils.Constants.MODULARIZED_HEADER;
import static org.springframework.web.util.WebUtils.ERROR_EXCEPTION_ATTRIBUTE;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.util.WebUtils;

@ExtendWith(OutputCaptureExtension.class)
class LoggingFilterTest {

    private final Web3Properties web3Properties = new Web3Properties();
    private final LoggingFilter loggingFilter = new LoggingFilter(web3Properties);
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @Test
    @SneakyThrows
    void filterOnSuccess(CapturedOutput output) {
        var request = new MockHttpServletRequest("GET", "/");
        response.addHeader(MODULARIZED_HEADER, "true");
        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, chain);

        assertLog(output, "INFO", "\\w+ GET / in \\d+ ms \\(mod=true\\): 200");
    }

    @Test
    @SneakyThrows
    void filterPath(CapturedOutput output) {
        var request = new MockHttpServletRequest("GET", "/actuator/");

        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, chain);

        assertThat(output).asString().isEmpty();
    }

    @Test
    @SneakyThrows
    void filterXForwardedFor(CapturedOutput output) {
        String clientIp = "10.0.0.100";
        var request = new MockHttpServletRequest("GET", "/");
        request.addHeader(X_FORWARDED_FOR, clientIp);
        response.addHeader(MODULARIZED_HEADER, "false");
        response.setStatus(HttpStatus.OK.value());

        new ForwardedHeaderFilter().doFilter(request, response, (req, res) -> loggingFilter.doFilter(req, res, chain));

        assertLog(output, "INFO", clientIp + " GET / in \\d+ ms \\(mod=false\\): 200");
    }

    @Test
    @SneakyThrows
    void filterOnError(CapturedOutput output) {
        var request = new MockHttpServletRequest("GET", "/");
        var exception = new IllegalArgumentException("error");

        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());

        loggingFilter.doFilter(request, response, (req, res) -> {
            throw exception;
        });

        assertLog(output, "WARN", "\\w+ GET / in \\d+ ms \\(mod=null\\): 500 " + exception.getMessage());
    }

    @Test
    @SneakyThrows
    void filterOnErrorAttribute(CapturedOutput output) {
        var request = new MockHttpServletRequest("GET", "/");
        var exception = new IllegalArgumentException("error");
        request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, exception);

        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());

        loggingFilter.doFilter(request, response, (req, res) -> {});

        assertLog(output, "WARN", "\\w+ GET / in \\d+ ms \\(mod=null\\): 500 " + exception.getMessage());
    }

    @Test
    @SneakyThrows
    void post(CapturedOutput output) {
        var content = "{\"to\":\"0x00\"}";
        var request = new MockHttpServletRequest("POST", "/");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, (req, res) -> IOUtils.toString(req.getReader()));

        assertLog(output, "INFO", "\\w+ POST / in \\d+ ms \\(mod=null\\): 200 Success - .+");
        assertThat(output.getOut()).contains(content);
    }

    @Test
    @SneakyThrows
    void postMultiLine(CapturedOutput output) {
        var content = " foo: bar\n";
        var request = new MockHttpServletRequest("POST", "/");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, (req, res) -> IOUtils.toString(req.getReader()));

        assertThat(output.getOut()).contains("foo:bar");
    }

    @Test
    @SneakyThrows
    void postLargeCompressedContent(CapturedOutput output) {
        int maxSize = web3Properties.getMaxPayloadLogSize();
        var content = StringUtils.repeat("abcdefghij", maxSize / 10 + 1);
        var request = new MockHttpServletRequest("POST", "/");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, (req, res) -> IOUtils.toString(req.getReader()));

        var compressed = " H4sIAAAAAAAA/0tMSk5JTUvPyMxKHGURzQIAy81t7zYBAAA=";
        assertThat(output.getOut()).contains(compressed).doesNotContain(content);
    }

    @Test
    @SneakyThrows
    void postLargeUncompressibleContent(CapturedOutput output) {
        int maxSize = web3Properties.getMaxPayloadLogSize();
        var content = RandomStringUtils.secure().next(maxSize + 100, "abcdef0123456789");
        var request = new MockHttpServletRequest("POST", "/");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, (req, res) -> IOUtils.toString(req.getReader()));

        assertThat(output.getOut()).contains(content.substring(0, maxSize)).doesNotContain(content);
    }

    @Test
    @SneakyThrows
    void getFullExceptionMessage(CapturedOutput output) {
        int maxSize = web3Properties.getMaxPayloadLogSize();
        var content = "abcdef0123456789";
        var request = new MockHttpServletRequest("POST", "/");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        request.setAttribute(
                ERROR_EXCEPTION_ATTRIBUTE,
                new MirrorEvmTransactionException(
                        CONTRACT_REVERT_EXECUTED, "detail", "0123456", null, List.of("childMessage")));
        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, (req, res) -> IOUtils.toString(req.getReader()));

        assertThat(output.getOut())
                .contains(
                        "Mirror EVM transaction error: CONTRACT_REVERT_EXECUTED, detail: detail, childTransactionErrors: [childMessage], data: 0123456 - abcdef0123456789");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @SneakyThrows
    void getFullExceptionMessageDetailEmpty(final String detail, CapturedOutput output) {
        int maxSize = web3Properties.getMaxPayloadLogSize();
        var content = "abcdef0123456789";
        var request = new MockHttpServletRequest("POST", "/");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        request.setAttribute(
                ERROR_EXCEPTION_ATTRIBUTE,
                new MirrorEvmTransactionException(
                        CONTRACT_REVERT_EXECUTED, detail, "0123456", null, List.of("childMessage")));
        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, (req, res) -> IOUtils.toString(req.getReader()));

        assertThat(output.getOut())
                .contains(
                        "Mirror EVM transaction error: CONTRACT_REVERT_EXECUTED, childTransactionErrors: [childMessage], data: 0123456 - abcdef0123456789");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @SneakyThrows
    void getFullExceptionMessageChildErrorsEmpty(final LinkedList<String> childErrors, CapturedOutput output) {
        int maxSize = web3Properties.getMaxPayloadLogSize();
        var content = "abcdef0123456789";
        var request = new MockHttpServletRequest("POST", "/");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        request.setAttribute(
                ERROR_EXCEPTION_ATTRIBUTE,
                new MirrorEvmTransactionException(CONTRACT_REVERT_EXECUTED, "detail", "0123456", null, childErrors));
        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, (req, res) -> IOUtils.toString(req.getReader()));

        assertThat(output.getOut())
                .contains(
                        "Mirror EVM transaction error: CONTRACT_REVERT_EXECUTED, detail: detail, data: 0123456 - abcdef0123456789");
    }

    @Test
    @SneakyThrows
    void getFullExceptionMessageWithCompressedContent(CapturedOutput output) {
        int maxSize = web3Properties.getMaxPayloadLogSize();
        var content = StringUtils.repeat("abcdefghij", maxSize / 10 + 1);
        var request = new MockHttpServletRequest("POST", "/");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        request.setAttribute(
                ERROR_EXCEPTION_ATTRIBUTE,
                new MirrorEvmTransactionException(
                        CONTRACT_REVERT_EXECUTED, "detail", "0123456", null, List.of("childMessage")));
        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, (req, res) -> IOUtils.toString(req.getReader()));

        var compressed = "H4sIAAAAAAAA/0tMSk5JTUvPyMxKHGURzQIAy81t7zYBAAA=";
        assertThat(output.getOut())
                .contains(
                        "Mirror EVM transaction error: CONTRACT_REVERT_EXECUTED, detail: detail, childTransactionErrors: [childMessage], data: 0123456 - "
                                + compressed);
    }

    private void assertLog(CapturedOutput logOutput, String level, String pattern) {
        assertThat(logOutput).asString().hasLineCount(1).contains(level).containsPattern(pattern);
    }
}
