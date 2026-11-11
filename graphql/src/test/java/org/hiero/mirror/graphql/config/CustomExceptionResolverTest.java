// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.config;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.ExecutionInput;
import graphql.GraphQLError;
import graphql.execution.ExecutionId;
import graphql.language.SourceLocation;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.hiero.mirror.common.exception.InvalidEntityException;
import org.hiero.mirror.common.exception.NonParsableKeyException;
import org.hiero.mirror.common.exception.ProtobufException;
import org.hiero.mirror.graphql.exception.InvalidInputException;
import org.hiero.mirror.graphql.exception.NotImplementedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.execution.GraphQlSource;

@ExtendWith(OutputCaptureExtension.class)
class CustomExceptionResolverTest {

    private static final String EXECUTION_ID = "exception-resolver-test";
    private static final String INTERNAL_MESSAGE = "select private_column from entity; path=/srv/private";

    @Test
    void invalidInput() {
        final var error = execute(new InvalidInputException("Invalid entity ID"));

        assertThat(error.getMessage()).isEqualTo("Invalid entity ID");
        assertThat(error.getErrorType()).isEqualTo(graphql.ErrorType.ValidationError);
    }

    @Test
    void invalidInputWithCause() {
        final var cause = new IllegalArgumentException(INTERNAL_MESSAGE);
        final var error = execute(new InvalidInputException("Invalid Node.id", cause));

        assertThat(error.getMessage()).isEqualTo("Invalid Node.id");
        assertThat(error.toSpecification().toString()).doesNotContain(INTERNAL_MESSAGE);
    }

    @Test
    void notImplemented() {
        final var error = execute(new NotImplementedException("Not implemented"));

        assertThat(error.getMessage()).isEqualTo("Not implemented");
        assertThat(error.getErrorType()).isEqualTo(graphql.ErrorType.ValidationError);
    }

    @MethodSource("unexpectedExceptions")
    @ParameterizedTest
    void unexpectedException(RuntimeException exception, CapturedOutput output) {
        final var error = execute(exception);

        assertThat(error.getMessage()).isEqualTo("INTERNAL_ERROR for " + EXECUTION_ID);
        assertThat(error.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
        assertThat(error.toSpecification().toString()).doesNotContain(INTERNAL_MESSAGE);
        assertThat(output).asString().contains(EXECUTION_ID, INTERNAL_MESSAGE);
    }

    private static Stream<RuntimeException> unexpectedExceptions() {
        return Stream.of(
                new IllegalArgumentException(INTERNAL_MESSAGE),
                new IllegalStateException(INTERNAL_MESSAGE),
                new NumberFormatException(INTERNAL_MESSAGE),
                new InvalidEntityException(INTERNAL_MESSAGE),
                new NonParsableKeyException(new IllegalStateException(INTERNAL_MESSAGE)),
                new ProtobufException(INTERNAL_MESSAGE),
                new DataIntegrityViolationException(INTERNAL_MESSAGE),
                new CompletionException(new IllegalArgumentException(INTERNAL_MESSAGE)),
                new RuntimeException(INTERNAL_MESSAGE));
    }

    private GraphQLError execute(RuntimeException exception) {
        final var source = GraphQlSource.schemaResourceBuilder()
                .schemaResources(
                        new ByteArrayResource("type Query { account: String }".getBytes(StandardCharsets.UTF_8)))
                .configureRuntimeWiring(builder -> builder.type(
                        "Query",
                        type -> type.dataFetcher("account", env -> {
                            throw exception;
                        })))
                .exceptionResolvers(List.of(new CustomExceptionResolver()))
                .build();
        final var input = ExecutionInput.newExecutionInput()
                .query("{ account }")
                .executionId(ExecutionId.from(EXECUTION_ID))
                .build();
        final var result = source.graphQl().execute(input);

        assertThat(result.getErrors()).hasSize(1);
        final var error = result.getErrors().getFirst();
        assertThat(error.getPath()).containsExactly("account");
        assertThat(error.getLocations()).containsExactly(new SourceLocation(1, 3));
        return error;
    }
}
