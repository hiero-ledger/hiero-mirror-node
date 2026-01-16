// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.scalar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import graphql.language.BooleanValue;
import graphql.language.StringValue;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class GraphQlDurationTest {
    @Test
    void parseLiteral() {
        var graphQlDuration = new GraphQlDuration();
        var duration = Duration.ofSeconds(1L);
        assertThat(graphQlDuration.parseLiteral(
                        StringValue.newStringValue("PT1s").build(), null, null, null))
                .isEqualTo(duration);
        var invalidValue = new BooleanValue(true);
        assertThatThrownBy(() -> graphQlDuration.parseLiteral(invalidValue, null, null, null))
                .isInstanceOf(CoercingParseLiteralException.class);
    }

    @Test
    void parseValue() {
        var graphQlDuration = new GraphQlDuration();
        var duration = Duration.ofSeconds(1L);
        assertThat(graphQlDuration.parseValue(duration, null, null)).isEqualTo(duration);
        assertThat(graphQlDuration.parseValue("PT1s", null, null)).isEqualTo(duration);
        assertThatThrownBy(() -> graphQlDuration.parseValue(5L, null, null))
                .isInstanceOf(CoercingParseValueException.class);
    }

    @Test
    void serialize() {
        var graphQlDuration = new GraphQlDuration();
        var duration = Duration.ofSeconds(1L);
        assertThat(graphQlDuration.serialize(duration, null, null)).isEqualTo(duration.toString());
        assertThatThrownBy(() -> graphQlDuration.serialize("5s", null, null))
                .isInstanceOf(CoercingSerializeException.class);
    }
}
