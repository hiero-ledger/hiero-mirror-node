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
import java.time.Instant;
import java.util.Locale;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class GraphQlTimestamp implements Coercing<Instant, String> {

    public static final GraphQLScalarType INSTANCE = GraphQLScalarType.newScalar()
            .name("Timestamp")
            .description("An ISO 8601 compatible timestamp with nanoseconds granularity.")
            .coercing(new GraphQlTimestamp())
            .build();

    @Override
    public Instant parseLiteral(
            Value<?> input, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale)
            throws CoercingParseLiteralException {
        if (input instanceof StringValue str) {
            return Instant.parse(str.getValue());
        }
        throw new CoercingParseLiteralException("Expected a 'String' but was '" + typeName(input) + "'.");
    }

    @Override
    public Instant parseValue(Object input, GraphQLContext graphQLContext, Locale locale)
            throws CoercingParseValueException {
        if (input instanceof Instant instant) {
            return instant;
        } else if (input instanceof String string) {
            return Instant.parse(string);
        }
        throw new CoercingParseValueException("Expected a 'String' but was '" + typeName(input) + "'.");
    }

    @Override
    public String serialize(Object input, GraphQLContext graphQLContext, Locale locale)
            throws CoercingSerializeException {
        if (input instanceof Instant instant) {
            return instant.toString();
        }
        throw new CoercingSerializeException("Unable to serialize timestamp to string: " + input);
    }
}
