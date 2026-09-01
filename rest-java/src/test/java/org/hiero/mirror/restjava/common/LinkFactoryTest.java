// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.restjava.common.Constants.ACCOUNT_ID;
import static org.hiero.mirror.restjava.common.Constants.NODE_ID;
import static org.hiero.mirror.restjava.common.Constants.TOKEN_ID;
import static org.hiero.mirror.restjava.common.LinkFactory.LINK_HEADER;
import static org.mockito.Mockito.when;
import static org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.hiero.mirror.rest.model.Links;
import org.hiero.mirror.rest.model.NftAllowance;
import org.hiero.mirror.restjava.controller.AllowancesController;
import org.hiero.mirror.restjava.dto.NetworkNodeRequest;
import org.hiero.mirror.restjava.parameter.EntityIdParameter;
import org.hiero.mirror.restjava.parameter.EntityIdRangeParameter;
import org.hiero.mirror.restjava.parameter.RequestParameter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
final class LinkFactoryTest {

    @Mock
    private HttpServletRequest request;

    private HttpServletResponse response;

    @Mock
    private ServletRequestAttributes attributes;

    private MockedStatic<RequestContextHolder> context;

    @Mock
    private Function<NftAllowance, Map<String, String>> extractor;

    private final LinkFactory linkFactory = new LinkFactoryImpl();

    private final NftAllowance nftAllowance =
            new NftAllowance().owner("0.0.1000").spender("0.0.2000").tokenId("0.0.6458");

    private static final HandlerMethod ALLOWANCES_HANDLER = handlerMethod(
            AllowancesController.class,
            "getNftAllowances",
            EntityIdParameter.class,
            EntityIdRangeParameter[].class,
            int.class,
            Direction.class,
            boolean.class,
            EntityIdRangeParameter[].class);

    private static final HandlerMethod NODES_HANDLER =
            handlerMethod(TestNodesController.class, "getNodes", NetworkNodeRequest.class);

    private static final String URI = "/api";

    @BeforeEach
    void setUp() {
        context = Mockito.mockStatic(RequestContextHolder.class);
        context.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);
        response = new MockHttpServletResponse();
        when(attributes.getRequest()).thenReturn(request);
        when(attributes.getResponse()).thenReturn(response);
        when(request.getRequestURI()).thenReturn(URI);
        when(request.getAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE)).thenReturn(ALLOWANCES_HANDLER);
        when(extractor.apply(nftAllowance))
                .thenReturn(Map.of(ACCOUNT_ID, nftAllowance.getOwner(), TOKEN_ID, nftAllowance.getTokenId()));
    }

    @AfterEach
    void closeMocks() {
        context.close();
    }

    @DisplayName("Get pagination links for all query parameters")
    @ParameterizedTest
    @CsvSource({
        "1, ASC,  eq:0.0.1000, eq:0.0.1000, false, /api?limit=1&order=ASC&account.id=eq:0.0.1000&token.id=eq:0.0.1000&owner=false",
        "1, asc,  lt:0.0.2000, 0.0.1000,    false, /api?limit=1&order=asc&account.id=lt:0.0.2000&account.id=gt:0.0.1000&token.id=0.0.1000&owner=false",
        "1, DESC, 0.0.1000,    0.0.1000,    false, /api?limit=1&order=DESC&account.id=0.0.1000&token.id=0.0.1000&owner=false",
        "1, desc, gt:0.0.900,  gt:0.0.900,  false, /api?limit=1&order=desc&account.id=gt:0.0.900&account.id=lte:0.0.1000&token.id=gt:0.0.900&token.id=lt:0.0.6458&owner=false",
        "1, desc, gt:0.0.900, gte:0.0.900,  false,  /api?limit=1&order=desc&account.id=gt:0.0.900&account.id=lte:0.0.1000&token.id=gte:0.0.900&token.id=lt:0.0.6458&owner=false",
        "1, desc, lt:0.0.9000, gte:0.0.900, false, /api?limit=1&order=desc&token.id=gte:0.0.900&token.id=lt:0.0.6458&owner=false&account.id=lte:0.0.1000",
        "1, desc, lt:0.0.9000, gte:0.0.900, true,  /api?limit=1&order=desc&token.id=gte:0.0.900&token.id=lt:0.0.6458&owner=true&account.id=lte:0.0.2000",
        "2, ASC,  0.0.1000,    0.0.1000, false,",
        "2, desc, 0.0.1000,    0.0.1000, false,",
        "1, asc,  gte:0.0.2000, 0.0.1000,   false, /api?limit=1&order=asc&token.id=0.0.1000&owner=false&account.id=gt:0.0.1000",
        "1, desc, gte:0.0.2000, 0.0.1000,   false, /api?limit=1&order=desc&account.id=gte:0.0.2000&account.id=lt:0.0.1000&token.id=0.0.1000&owner=false",
        "1, desc, 0.0.2000, gte:0.0.1000,   false, /api?limit=1&order=desc&account.id=0.0.2000&token.id=gte:0.0.1000&token.id=lt:0.0.6458&owner=false",
    })
    void testAllQueryParameters(
            String limit,
            String order,
            String accountParameter,
            String tokenParameter,
            boolean owner,
            String expectedLink) {
        var params = new LinkedHashMap<String, String[]>();
        params.put("limit", new String[] {limit});
        params.put("order", new String[] {order});
        params.put("account.id", new String[] {accountParameter});
        params.put("token.id", new String[] {tokenParameter});
        params.put("owner", new String[] {Boolean.toString(owner)});
        when(request.getParameterMap()).thenReturn(params);
        when(extractor.apply(nftAllowance))
                .thenReturn(Map.of(
                        ACCOUNT_ID,
                        owner ? nftAllowance.getSpender() : nftAllowance.getOwner(),
                        TOKEN_ID,
                        nftAllowance.getTokenId()));
        var sort = Sort.by(Direction.valueOf(order.toUpperCase()), ACCOUNT_ID, TOKEN_ID);
        var pageable = PageRequest.of(0, Integer.parseInt(limit), sort);

        assertThat(linkFactory.create(List.of(nftAllowance), pageable, extractor))
                .returns(expectedLink, Links::getNext);

        if (expectedLink != null) {
            assertThat(response.getHeader(HttpHeaders.LINK)).isEqualTo(LINK_HEADER.formatted(expectedLink));
        } else {
            assertThat(response.getHeader(HttpHeaders.LINK)).isNull();
        }
    }

    @DisplayName("Get pagination links with no primary sort")
    @ParameterizedTest
    @CsvSource({
        "0.0.1000,    0.0.1000,    /api?limit=1&account.id=0.0.1000&token.id=0.0.1000",
        "lt:0.0.9000, gte:0.0.900, /api?limit=1&account.id=lt:0.0.9000&account.id=gte:0.0.1000&token.id=gt:0.0.6458",
    })
    void testNoPrimarySort(String accountParameter, String tokenParameter, String expectedLink) {
        var params = new LinkedHashMap<String, String[]>();
        params.put("limit", new String[] {"1"});
        params.put("account.id", new String[] {accountParameter});
        params.put("token.id", new String[] {tokenParameter});
        when(request.getParameterMap()).thenReturn(params);
        var sort = Sort.by(Direction.ASC, ACCOUNT_ID, TOKEN_ID);
        var pageable = PageRequest.of(0, 1, sort);

        assertThat(linkFactory.create(List.of(nftAllowance), pageable, extractor))
                .returns(expectedLink, Links::getNext);
        assertThat(response.getHeader(HttpHeaders.LINK)).isEqualTo(LINK_HEADER.formatted(expectedLink));
    }

    @DisplayName("Omits unknown query parameters from pagination links")
    @Test
    void testUnknownParameter() {
        var params = new LinkedHashMap<String, String[]>();
        params.put("limit", new String[] {"1"});
        params.put("account.id", new String[] {"0.0.1000"});
        params.put("unknown", new String[] {"value", "value2"});
        when(request.getParameterMap()).thenReturn(params);
        var sort = Sort.by(Direction.ASC, ACCOUNT_ID, TOKEN_ID);
        var pageable = PageRequest.of(0, 1, sort);

        final var expectedLink = "/api?limit=1&account.id=0.0.1000&token.id=gt:0.0.6458";
        assertThat(linkFactory.create(List.of(nftAllowance), pageable, extractor))
                .returns(expectedLink, Links::getNext);
        assertThat(response.getHeader(HttpHeaders.LINK)).isEqualTo(LINK_HEADER.formatted(expectedLink));
    }

    @Test
    @DisplayName("Rejects control characters in query parameter names and values")
    void testRejectsControlCharacters() {
        var params = new LinkedHashMap<String, String[]>();
        params.put("limit", new String[] {"1"});
        params.put("order", new String[] {"asc\r\nX-Injected: true"});
        params.put("evil\r\nInjected", new String[] {"value"});
        params.put("account.id", new String[] {"0.0.1000"});
        when(request.getParameterMap()).thenReturn(params);
        var sort = Sort.by(Direction.ASC, ACCOUNT_ID, TOKEN_ID);
        var pageable = PageRequest.of(0, 1, sort);

        final var expectedLink = "/api?limit=1&account.id=0.0.1000&token.id=gt:0.0.6458";
        var links = linkFactory.create(List.of(nftAllowance), pageable, extractor);
        var linkHeader = response.getHeader(HttpHeaders.LINK);

        assertThat(links).returns(expectedLink, Links::getNext);
        assertThat(linkHeader).isEqualTo(LINK_HEADER.formatted(expectedLink));
        assertThat(links.getNext()).doesNotContain("\r", "\n", "Injected");
        assertThat(linkHeader).doesNotContain("\r", "\n", "Injected");
    }

    @Test
    @DisplayName("Percent-encodes reserved characters in reflected query values")
    void testEncodesReservedCharacters() {
        var params = new LinkedHashMap<String, String[]>();
        params.put("limit", new String[] {"1"});
        params.put("order", new String[] {"asc \">evil"});
        params.put("account.id", new String[] {"0.0.1000"});
        when(request.getParameterMap()).thenReturn(params);
        var sort = Sort.by(Direction.ASC, ACCOUNT_ID, TOKEN_ID);
        var pageable = PageRequest.of(0, 1, sort);

        final var expectedLink = "/api?limit=1&order=asc%20%22%3Eevil&account.id=0.0.1000&token.id=gt:0.0.6458";
        var links = linkFactory.create(List.of(nftAllowance), pageable, extractor);
        var linkHeader = response.getHeader(HttpHeaders.LINK);

        assertThat(links).returns(expectedLink, Links::getNext);
        assertThat(linkHeader).isEqualTo(LINK_HEADER.formatted(expectedLink));
        assertThat(links.getNext()).doesNotContain("\">");
        assertThat(linkHeader).doesNotContain("\">");
    }

    @DisplayName("Get pagination links with multiple parameter values")
    @ParameterizedTest
    @CsvSource({
        "asc,   lt:0.0.2000, lte:0.0.2000, lte:0.0.7000, gt:0.0.6000,  /api?order=asc&account.id=lt:0.0.2000&account.id=lte:0.0.2000&account.id=gte:0.0.1000&token.id=lte:0.0.7000&token.id=gt:0.0.6458",
        "desc,  lt:0.0.2000, lte:0.0.2000, lte:0.0.7000, gt:0.0.6000,  /api?order=desc&token.id=gt:0.0.6000&token.id=lt:0.0.6458&account.id=lte:0.0.1000",
        "asc,    gt:0.0.100,  gte:0.0.100, lte:0.0.7000, gt:0.0.6000,  /api?order=asc&token.id=lte:0.0.7000&token.id=gt:0.0.6458&account.id=gte:0.0.1000",
        "desc,   gt:0.0.100,  gte:0.0.100, lte:0.0.7000, gt:0.0.6000,  /api?order=desc&account.id=gt:0.0.100&account.id=gte:0.0.100&account.id=lte:0.0.1000&token.id=gt:0.0.6000&token.id=lt:0.0.6458",
        "asc,   lt:0.0.2000,   gt:0.0.100,  gt:0.0.6000, lt:0.0.7000,  /api?order=asc&account.id=lt:0.0.2000&account.id=gte:0.0.1000&token.id=lt:0.0.7000&token.id=gt:0.0.6458",
        "desc,  lt:0.0.2000,   gt:0.0.100,  gt:0.0.6000, lt:0.0.7000,  /api?order=desc&account.id=gt:0.0.100&account.id=lte:0.0.1000&token.id=gt:0.0.6000&token.id=lt:0.0.6458",
        "asc,      0.0.1000,  gt:0.0.1000, lte:0.0.7000, gt:0.0.6000,  /api?order=asc&account.id=0.0.1000&token.id=lte:0.0.7000&token.id=gt:0.0.6458",
        "desc,     0.0.1000,  gt:0.0.1000, lte:0.0.7000, gt:0.0.6000,  /api?order=desc&account.id=0.0.1000&account.id=gt:0.0.1000&token.id=gt:0.0.6000&token.id=lt:0.0.6458",
    })
    void testMultipleParameters(
            String order,
            String accountParameter,
            String accountParameter2,
            String tokenParameter,
            String tokenParameter2,
            String expectedLink) {
        var params = new LinkedHashMap<String, String[]>();
        params.put("order", new String[] {order});
        params.put("account.id", new String[] {accountParameter, accountParameter2});
        params.put("token.id", new String[] {tokenParameter, tokenParameter2});
        when(request.getParameterMap()).thenReturn(params);
        var sort = Sort.by(Direction.valueOf(order.toUpperCase()), ACCOUNT_ID, TOKEN_ID);
        var pageable = PageRequest.of(0, 1, sort);

        assertThat(linkFactory.create(List.of(nftAllowance), pageable, extractor))
                .returns(expectedLink, Links::getNext);
        assertThat(response.getHeader(HttpHeaders.LINK)).isEqualTo(LINK_HEADER.formatted(expectedLink));
    }

    @Test
    void testNullRequestAttributes() {
        // given
        var params = new LinkedHashMap<String, String[]>();
        params.put("account.id", new String[] {"0.0.1002"});
        params.put("token.id", new String[] {"0.0.3000"});
        when(request.getParameterMap()).thenReturn(params);
        context.when(RequestContextHolder::getRequestAttributes).thenReturn(null);

        // when then
        assertThat(linkFactory.create(List.of(nftAllowance), PageRequest.ofSize(1), extractor))
                .returns(null, Links::getNext);
    }

    @Test
    @DisplayName("Empty next link")
    void testEmptyPaginationLinks() {
        assertThat(linkFactory.create(null, PageRequest.ofSize(1), extractor)).returns(null, Links::getNext);
        assertThat(linkFactory.create(List.of(), PageRequest.ofSize(1), extractor))
                .returns(null, Links::getNext);
        assertThat(attributes.getResponse().getHeader(HttpHeaders.LINK)).isNull();
    }

    @DisplayName("Omits query parameters not declared on the matched handler")
    @Test
    void testParamsFromOtherApis() {
        var params = new LinkedHashMap<String, String[]>();
        params.put("limit", new String[] {"1"});
        params.put("account.id", new String[] {"0.0.1000"});
        params.put("hook.id", new String[] {"1"});
        params.put("node.id", new String[] {"3"});
        params.put("token.id", new String[] {"0.0.1000"});
        when(request.getParameterMap()).thenReturn(params);
        var sort = Sort.by(Direction.ASC, ACCOUNT_ID, TOKEN_ID);
        var pageable = PageRequest.of(0, 1, sort);

        final var expectedLink = "/api?limit=1&account.id=0.0.1000&token.id=0.0.1000";
        assertThat(linkFactory.create(List.of(nftAllowance), pageable, extractor))
                .returns(expectedLink, Links::getNext);
        assertThat(response.getHeader(HttpHeaders.LINK)).isEqualTo(LINK_HEADER.formatted(expectedLink));
    }

    @DisplayName("Allows query parameters declared on a @RequestParameter DTO")
    @Test
    void testRequestParameterDtoParams() {
        when(request.getAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE)).thenReturn(NODES_HANDLER);
        var params = new LinkedHashMap<String, String[]>();
        params.put("limit", new String[] {"1"});
        params.put("order", new String[] {"asc"});
        params.put("file.id", new String[] {"0.0.101"});
        params.put("node.id", new String[] {"1"});
        params.put("token.id", new String[] {"0.0.1000"});
        when(request.getParameterMap()).thenReturn(params);
        when(extractor.apply(nftAllowance)).thenReturn(Map.of(NODE_ID, "3"));
        var sort = Sort.by(Direction.ASC, NODE_ID);
        var pageable = PageRequest.of(0, 1, sort);

        final var expectedLink = "/api?limit=1&order=asc&file.id=0.0.101&node.id=1";
        assertThat(linkFactory.create(List.of(nftAllowance), pageable, extractor))
                .returns(expectedLink, Links::getNext);
        assertThat(response.getHeader(HttpHeaders.LINK)).isEqualTo(LINK_HEADER.formatted(expectedLink));
    }

    @DisplayName("Without a matched handler, only extractor pagination keys are copied")
    @Test
    void testMissingHandlerAllowsOnlyExtractorParams() {
        when(request.getAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE)).thenReturn(null);
        var params = new LinkedHashMap<String, String[]>();
        params.put("limit", new String[] {"1"});
        params.put("order", new String[] {"asc"});
        params.put("account.id", new String[] {"0.0.1000"});
        params.put("owner", new String[] {"false"});
        when(request.getParameterMap()).thenReturn(params);
        var sort = Sort.by(Direction.ASC, ACCOUNT_ID, TOKEN_ID);
        var pageable = PageRequest.of(0, 1, sort);

        final var expectedLink = "/api?account.id=0.0.1000&token.id=gt:0.0.6458";
        assertThat(linkFactory.create(List.of(nftAllowance), pageable, extractor))
                .returns(expectedLink, Links::getNext);
        assertThat(response.getHeader(HttpHeaders.LINK)).isEqualTo(LINK_HEADER.formatted(expectedLink));
    }

    private static HandlerMethod handlerMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        try {
            return new HandlerMethod(new Object(), type.getDeclaredMethod(methodName, parameterTypes));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class TestNodesController {
        @SuppressWarnings("unused")
        void getNodes(@RequestParameter NetworkNodeRequest request) {}
    }
}
