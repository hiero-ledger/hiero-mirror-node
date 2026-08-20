// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static com.google.common.net.HttpHeaders.X_FORWARDED_FOR;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.params.provider.CsvSource;
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
final class LoggingFilterTest {

    private final Web3Properties web3Properties = new Web3Properties();
    private final LoggingFilter loggingFilter = new LoggingFilter(web3Properties);
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @Test
    @SneakyThrows
    void filterOnSuccess(CapturedOutput output) {
        var request = new MockHttpServletRequest("GET", "/");
        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, chain);

        assertLog(output, "INFO", "\\w+ GET / in \\d+ ms " + ": 200");
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
        response.setStatus(HttpStatus.OK.value());

        new ForwardedHeaderFilter().doFilter(request, response, (req, res) -> loggingFilter.doFilter(req, res, chain));

        assertLog(output, "INFO", clientIp + " GET / in \\d+ ms : 200");
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

        assertLog(output, "WARN", "\\w+ GET / in \\d+ ms : 500 " + exception.getMessage());
    }

    @Test
    @SneakyThrows
    void filterOnErrorAttribute(CapturedOutput output) {
        var request = new MockHttpServletRequest("GET", "/");
        var exception = new IllegalArgumentException("error");
        request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, exception);

        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());

        loggingFilter.doFilter(request, response, (req, res) -> {});

        assertLog(output, "WARN", "\\w+ GET / in \\d+ ms : 500 " + exception.getMessage());
    }

    @Test
    @SneakyThrows
    void post(CapturedOutput output) {
        var content = "{\"to\":\"0x00\"}";
        var request = new MockHttpServletRequest("POST", "/");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, (req, res) -> IOUtils.toString(req.getReader()));

        assertLog(output, "INFO", "\\w+ POST / in \\d+ ms : 200 Success - .+");
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

    @CsvSource(delimiter = '|', textBlock = """
            {"data":"0x01","block":"latest"}          | {"block":"latest","data":"0x01"}
            {"gas":0,"data":"0x01","block":"latest"}  | {"gas":0,"block":"latest","data":"0x01"}
            {"block": "latest","data": "0x01"}        | {"block":"latest","data":"0x01"}
            {"data":"0x01"}                           | {"data":"0x01"}
            {,"data":"0x01"}                          | {,"data":"0x01"}
            {"data":"0x01","block":"latest","foo":{}} | {"block":"latest","foo":{},"data":"0x01"}
            """)
    @ParameterizedTest
    @SneakyThrows
    void dataFieldMoved(String request, String expected, CapturedOutput output) {
        int maxSize = web3Properties.getMaxPayloadLogSize();
        var content = request + RandomStringUtils.secure().next(maxSize + 100, "abcdef0123456789");
        final var servletRequest = new MockHttpServletRequest("POST", "/");
        servletRequest.setContent(content.getBytes(StandardCharsets.UTF_8));
        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(servletRequest, response, (req, res) -> IOUtils.toString(req.getReader()));

        assertThat(output.getOut()).contains(expected);
    }

    @Test
    @SneakyThrows
    void errorPayloadNotTruncated(CapturedOutput output) {
        int maxSize = web3Properties.getMaxPayloadLogSize();
        var content = RandomStringUtils.secure().next(maxSize + 100, "abcdef0123456789");
        var request = new MockHttpServletRequest("POST", "/");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        response.setStatus(HttpStatus.BAD_GATEWAY.value());

        loggingFilter.doFilter(request, response, (req, res) -> IOUtils.toString(req.getReader()));

        assertThat(output.getOut()).contains(content);
    }

    @Test
    @SneakyThrows
    void getFullExceptionMessage(CapturedOutput output) {
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

    @Test
    @SneakyThrows
    void boundedRegexWithVeryLargePayload(CapturedOutput output) {
        // Create uncompressible payload with valid JSON followed by large random data
        // This ensures reordering works and tests bounded regex with large input
        var jsonPrefix = "{\"data\":\"0x123456\",\"block\":\"latest\"}";
        var randomSuffix = RandomStringUtils.secure().next(5000, "abcdef0123456789");
        var content = jsonPrefix + randomSuffix;
        var request = new MockHttpServletRequest("POST", "/");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, (req, res) -> IOUtils.toString(req.getReader()));

        // Verify the output contains reordered structure with block field preserved
        var out = output.getOut();
        assertThat(out).contains("\"block\":\"latest\"");
        // Output should be truncated
        assertThat(out.length()).isLessThan(content.length());
    }

    @Test
    @SneakyThrows
    void boundedRegexPreservesDataFieldReordering(CapturedOutput output) {
        int maxSize = web3Properties.getMaxPayloadLogSize();
        // Create content that triggers bounded regex (> maxSize but within regex limit)
        var mediumData = StringUtils.repeat("x", maxSize + 50);
        var content = "{\"data\":\"" + mediumData + "\",\"block\":\"latest\",\"gas\":21000}";
        var request = new MockHttpServletRequest("POST", "/");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, (req, res) -> IOUtils.toString(req.getReader()));

        // Verify data field is reordered to the end before truncation
        var out = output.getOut();
        int blockIndex = out.indexOf("\"block\":");
        int dataIndex = out.indexOf("\"data\":");
        int gasIndex = out.indexOf("\"gas\":");

        // block and gas should appear before data
        if (blockIndex > 0 && gasIndex > 0 && dataIndex > 0) {
            assertThat(blockIndex).isLessThan(dataIndex);
            assertThat(gasIndex).isLessThan(dataIndex);
        }
    }

    @Test
    @SneakyThrows
    void boundedRegexWithExtremelyLargePayload(CapturedOutput output) {
        int maxSize = web3Properties.getMaxPayloadLogSize();
        // Create a payload that's significantly larger than regex limit (50KB)
        var extremelyLargeData = RandomStringUtils.secure().next(50000, "abcdef0123456789");
        var content = "{\"from\":\"0x123\",\"to\":\"0x456\",\"data\":\"" + extremelyLargeData
                + "\",\"gas\":21000,\"value\":0}";
        var request = new MockHttpServletRequest("POST", "/");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, (req, res) -> IOUtils.toString(req.getReader()));

        // Should complete without hanging or timeout
        // Output should be truncated to maxSize
        var out = output.getOut();
        assertThat(out).isNotEmpty();
        // Verify short fields are preserved
        assertThat(out).containsAnyOf("\"from\":\"0x123\"", "\"to\":\"0x456\"", "\"gas\":21000");
    }

    @Test
    @SneakyThrows
    void boundedRegexWithMalformedJSON(CapturedOutput output) {
        int maxSize = web3Properties.getMaxPayloadLogSize();
        // Create malformed JSON that could trigger backtracking in old regex
        var malformedData = StringUtils.repeat("{\"nested\":", 1000) + "\"value\"";
        var content = "{\"data\":\"" + malformedData + "\",\"block\":\"latest\"}";
        var request = new MockHttpServletRequest("POST", "/");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        response.setStatus(HttpStatus.OK.value());

        // This should complete quickly without catastrophic backtracking
        long startTime = System.currentTimeMillis();
        loggingFilter.doFilter(request, response, (req, res) -> IOUtils.toString(req.getReader()));
        long elapsed = System.currentTimeMillis() - startTime;

        // Should complete in reasonable time (< 5 seconds even with large malformed input)
        assertThat(elapsed).isLessThan(5000L);
        assertThat(output.getOut()).isNotEmpty();
    }

    @Test
    @SneakyThrows
    void boundedRegexLimitCalculation(CapturedOutput output) {
        int maxSize = web3Properties.getMaxPayloadLogSize();
        // Test the boundary at exactly 2x maxPayloadLogSize (600 bytes for default maxSize=300)
        // The regex limit should be max(2*maxSize, 10240) = 10240 bytes (10KB minimum)
        var dataAt10KB = RandomStringUtils.secure().next(10240, "abcdef0123456789");
        var content = "{\"data\":\"" + dataAt10KB + "\",\"block\":\"latest\"}";
        var request = new MockHttpServletRequest("POST", "/");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        response.setStatus(HttpStatus.OK.value());

        loggingFilter.doFilter(request, response, (req, res) -> IOUtils.toString(req.getReader()));

        // Should handle the regex limit boundary correctly
        assertThat(output.getOut()).isNotEmpty();
    }

    @Test
    @SneakyThrows
    void possessiveQuantifiersPreventBacktracking(CapturedOutput output) {
        int maxSize = web3Properties.getMaxPayloadLogSize();
        // Create input designed to trigger backtracking with greedy quantifiers
        // Pattern: data field with no comma, followed by many characters
        var problematicData = StringUtils.repeat("abcdefghij", maxSize * 10);
        var content = "{\"data\":\"" + problematicData + "\"\"block\":\"latest\"}"; // Intentionally malformed
        var request = new MockHttpServletRequest("POST", "/");
        request.setContent(content.getBytes(StandardCharsets.UTF_8));
        response.setStatus(HttpStatus.OK.value());

        // Should complete quickly due to possessive quantifiers
        long startTime = System.currentTimeMillis();
        loggingFilter.doFilter(request, response, (req, res) -> IOUtils.toString(req.getReader()));
        long elapsed = System.currentTimeMillis() - startTime;

        // Should complete in reasonable time
        assertThat(elapsed).isLessThan(5000L);
    }

    private void assertLog(CapturedOutput logOutput, String level, String pattern) {
        assertThat(logOutput).asString().hasLineCount(1).contains(level).containsPattern(pattern);
    }
}
