// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiero.mirror.rest.model.TokenAirdropsResponse;
import org.junit.jupiter.api.Test;

class TokenAirdropModelTest {
    String airdropsResponse = """
            {
              "airdrops": [
                {
                  "amount": 333,
                  "receiver_id": "0.0.999",
                  "sender_id": "0.0.222",
                  "serial_number": null,
                  "timestamp": {
                    "from": "1111111111.111111111",
                    "to": null
                  },
                  "token_id": "0.0.111"
                },
                {
                  "amount": 555,
                  "receiver_id": "0.0.999",
                  "sender_id": "0.0.222",
                  "serial_number": null,
                  "timestamp": {
                    "from": "1111111111.111111112",
                    "to": null
                  },
                  "token_id": "0.0.444"
                },
                {
                  "amount": null,
                  "receiver_id": "0.0.999",
                  "sender_id": "0.0.222",
                  "serial_number": 888,
                  "timestamp": {
                    "from": "1111111111.111111113",
                    "to": null
                  },
                  "token_id": "0.0.666"
                }
              ],
              "links": {
                "next": "/api/v1/accounts/0.0.1000/airdrops/outstanding?limit=3&order=asc&token.id=gt:0.0.667"
              }
            }
            """;

    @Test
    void verifyModelGeneration() throws JsonProcessingException {
        var mapper = new ObjectMapper();
        var response = mapper.readValue(airdropsResponse, TokenAirdropsResponse.class);
        var tokenAirdrop = mapper.writeValueAsString(response);
        assertThat(tokenAirdrop).isEqualToIgnoringWhitespace(airdropsResponse);
    }
}
