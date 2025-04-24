// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.scalar;

import static graphql.scalars.util.Kit.typeName;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import jakarta.annotation.Nonnull;
import java.time.Instant;
import java.util.Locale;

public class GraphQlTimestamp implements Coercing<Instant, String> {

    public static final GraphQLScalarType INSTANCE = GraphQLScalarType.newScalar()
            .name("Timestamp")
            .description("An ISO 8601 compatible timestamp with nanoseconds granularity.")
            .coercing(new GraphQlTimestamp())
            .build();

    @Override
    public @Nonnull Instant parseLiteral(
            @Nonnull Value<?> input,
            @Nonnull CoercedVariables variables,
            @Nonnull GraphQLContext graphQLContext,
            @Nonnull Locale locale)
            throws CoercingParseLiteralException {
        if (input instanceof StringValue str) {
            return Instant.parse(str.getValue());
        }
        throw new CoercingParseLiteralException("Expected a 'String' but was '" + typeName(input) + "'.");
    }

    @Override
    public @Nonnull Instant parseValue(
            @Nonnull Object input, @Nonnull GraphQLContext graphQLContext, @Nonnull Locale locale)
            throws CoercingParseValueException {
        if (input instanceof Instant instant) {
            return instant;
        } else if (input instanceof String string) {
            return Instant.parse(string);
        }
        throw new CoercingParseValueException("Expected a 'String' but was '" + typeName(input) + "'.");
    }

    @Override
    public String serialize(@Nonnull Object input, @Nonnull GraphQLContext graphQLContext, @Nonnull Locale locale)
            throws CoercingSerializeException {
        if (input instanceof Instant instant) {
            return instant.toString();
        }
        throw new CoercingSerializeException("Unable to serialize timestamp to string: " + input);
    }
}
