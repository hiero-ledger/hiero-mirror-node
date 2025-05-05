// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.scalar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import graphql.language.BooleanValue;
import graphql.language.StringValue;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GraphQlTimestampTest {
    @Test
    void parseLiteral() {
        var graphQlTimestamp = new GraphQlTimestamp();
        var instant = Instant.EPOCH;
        var value = StringValue.newStringValue(instant.toString()).build();
        assertThat(graphQlTimestamp.parseLiteral(value, null, null, null)).isEqualTo(instant);
        var invalidValue = new BooleanValue(true);
        assertThatThrownBy(() -> graphQlTimestamp.parseLiteral(invalidValue, null, null, null))
                .isInstanceOf(CoercingParseLiteralException.class);
    }

    @Test
    void parseValue() {
        var graphQlTimestamp = new GraphQlTimestamp();
        var instant = Instant.EPOCH;
        assertThat(graphQlTimestamp.parseValue(instant, null, null)).isEqualTo(instant);
        assertThat(graphQlTimestamp.parseValue("1970-01-01T00:00:00Z", null, null))
                .isEqualTo(instant);
        assertThatThrownBy(() -> graphQlTimestamp.parseValue(5L, null, null))
                .isInstanceOf(CoercingParseValueException.class);
    }

    @Test
    void serialize() {
        var graphQlTimestamp = new GraphQlTimestamp();
        var instant = Instant.now();
        assertThat(graphQlTimestamp.serialize(instant, null, null)).isEqualTo(instant.toString());
        assertThatThrownBy(() -> graphQlTimestamp.serialize("5s", null, null))
                .isInstanceOf(CoercingSerializeException.class);
    }
}
