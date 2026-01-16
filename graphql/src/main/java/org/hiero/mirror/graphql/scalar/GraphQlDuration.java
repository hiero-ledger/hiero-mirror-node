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
import java.time.Duration;
import java.util.Locale;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class GraphQlDuration implements Coercing<Duration, String> {

    public static final GraphQLScalarType INSTANCE = GraphQLScalarType.newScalar()
            .name("Duration")
            .description("An ISO 8601 compatible duration with support for nanoseconds granularity in the format "
                    + "P[n]Y[n]M[n]DT[n]H[n]M[n]S.")
            .coercing(new GraphQlDuration())
            .build();

    @Override
    public Duration parseLiteral(
            Value<?> input, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale)
            throws CoercingParseLiteralException {
        if (input instanceof StringValue str) {
            return Duration.parse(str.getValue());
        }
        throw new CoercingParseLiteralException("Expected a 'String' but was '" + typeName(input) + "'.");
    }

    @Override
    public Duration parseValue(Object input, GraphQLContext graphQLContext, Locale locale)
            throws CoercingParseValueException {
        if (input instanceof Duration duration) {
            return duration;
        } else if (input instanceof String string) {
            return Duration.parse(string);
        }
        throw new CoercingParseValueException("Expected a 'String' but was '" + typeName(input) + "'.");
    }

    @Override
    public String serialize(Object input, GraphQLContext graphQLContext, Locale locale)
            throws CoercingSerializeException {
        if (input instanceof Duration duration) {
            return duration.toString();
        }
        throw new CoercingSerializeException("Unable to serialize duration to string: " + input);
    }
}
