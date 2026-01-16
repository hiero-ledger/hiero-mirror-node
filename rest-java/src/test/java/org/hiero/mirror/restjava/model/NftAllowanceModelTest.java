// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiero.mirror.rest.model.NftAllowancesResponse;
import org.junit.jupiter.api.Test;

class NftAllowanceModelTest {
    String allowanceResponse = """
            {
              "allowances": [
                {
                  "approved_for_all": true,
                  "owner": "0.0.1000",
                  "spender": "0.0.8488",
                  "timestamp": {
                    "from": "1633466229.96874612",
                    "to": null
                  },
                  "token_id": "0.0.1033"
                },
                {
                  "approved_for_all": true,
                  "owner": "0.0.1000",
                  "spender": "0.0.8488",
                  "timestamp": {
                    "from": "1633466229.96874618",
                    "to": null
                  },
                  "token_id": "0.0.1034"
                },
                {
                  "approved_for_all": false,
                  "owner": "0.0.1001",
                  "spender": "0.0.8488",
                  "timestamp": {
                    "from": "1633466229.96875612",
                    "to": null
                  },
                  "token_id": "0.0.1099"
                }
              ],
              "links": {
                "next": "/api/v1/accounts/0.0.8488/allowances/nfts?limit=3&order=asc&account.id=gte:1001&token.id=gt:0.0.1099"
              }
            }
            """;

    @Test
    void verifyModelGeneration() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        NftAllowancesResponse nftAllowance = mapper.readValue(allowanceResponse, NftAllowancesResponse.class);
        String allowance = mapper.writeValueAsString(nftAllowance);
        assertThat(allowance).isEqualToIgnoringWhitespace(allowanceResponse);
    }
}
