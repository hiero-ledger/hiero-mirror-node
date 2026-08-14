// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.parameter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.restjava.common.Constants.MAX_REPEATED_QUERY_PARAMETERS;

import java.util.stream.IntStream;
import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.support.DefaultDataBinderFactory;
import org.springframework.web.context.request.ServletWebRequest;

final class RequestParameterArgumentResolverTest {

    private static final String PARAM = "ids";

    private final RequestParameterArgumentResolver resolver = new RequestParameterArgumentResolver();

    @Test
    void rejectsTooManyRepeatedValues() {
        final var webRequest = requestWithValues(MAX_REPEATED_QUERY_PARAMETERS + 1);

        assertThatThrownBy(() -> resolver.resolveArgument(methodParameter(), null, webRequest, binderFactory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Too many values for " + PARAM);
    }

    @Test
    void allowsValuesUpToLimit() throws Exception {
        final var webRequest = requestWithValues(MAX_REPEATED_QUERY_PARAMETERS);

        final var result = resolver.resolveArgument(methodParameter(), null, webRequest, binderFactory());

        assertThat(result).isInstanceOf(TestRequest.class);
        assertThat(((TestRequest) result).getIds()).hasSize(MAX_REPEATED_QUERY_PARAMETERS);
    }

    @Test
    void allowsSingleValue() {
        final var webRequest = requestWithValues(1);

        assertThatCode(() -> resolver.resolveArgument(methodParameter(), null, webRequest, binderFactory()))
                .doesNotThrowAnyException();
    }

    private ServletWebRequest requestWithValues(int count) {
        final var request = new MockHttpServletRequest();
        final var values = IntStream.range(0, count).mapToObj(Integer::toString).toArray(String[]::new);
        request.addParameter(PARAM, values);
        return new ServletWebRequest(request);
    }

    private DefaultDataBinderFactory binderFactory() {
        return new DefaultDataBinderFactory(null);
    }

    private MethodParameter methodParameter() {
        try {
            return new MethodParameter(getClass().getDeclaredMethod("handler", TestRequest.class), 0);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unused")
    private void handler(TestRequest request) {}

    @Data
    public static class TestRequest {

        @RestJavaQueryParam(name = PARAM, required = false)
        private String @Nullable [] ids;
    }
}
