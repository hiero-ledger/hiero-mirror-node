// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.STATE_OVERRIDES_MAX_SIZE;
import static org.hiero.mirror.web3.viewmodel.StateOverride.CODE_MAX_LENGTH;
import static org.hiero.mirror.web3.viewmodel.StateOverride.STORAGE_MAX_SIZE;
import static org.hiero.mirror.web3.viewmodel.StorageEntry.SLOT_HEX_LENGTH;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hedera.node.config.data.JumboTransactionsConfig;
import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import java.util.Collections;
import java.util.stream.IntStream;
import org.hamcrest.core.StringContains;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@AutoConfigureMockMvc
final class JacksonConfigurationTest extends Web3IntegrationTest {

    @Resource
    private EvmProperties evmProperties;

    @Resource
    private JsonMapper jsonMapper;

    @Resource
    private MockMvc mockMvc;

    @Resource
    private Validator validator;

    @Test
    void appliesReadConstraintsToJsonMapper() {
        final int maxSize = jumboCallDataSize() * 2 + 1024;
        final var constraints = jsonMapper.tokenStreamFactory().streamReadConstraints();
        assertThat(constraints.getMaxDocumentLength()).isEqualTo(maxSize);
        assertThat(constraints.getMaxNameLength()).isEqualTo(100);
        assertThat(constraints.getMaxNestingDepth()).isEqualTo(10);
        assertThat(constraints.getMaxNumberLength()).isEqualTo(19);
        assertThat(constraints.getMaxStringLength()).isEqualTo(maxSize);
        assertThat(constraints.getMaxTokenCount()).isEqualTo(7_000);
    }

    @Test
    void parsesStateOverrideRequestWithMostTokens() {
        final var slot = "\"0x" + "1".repeat(SLOT_HEX_LENGTH) + "\"";
        final var storage = String.join(
                ",", Collections.nCopies(STORAGE_MAX_SIZE, "{\"key\":%s,\"value\":%s}".formatted(slot, slot)));
        final var overrides = IntStream.range(0, STATE_OVERRIDES_MAX_SIZE)
                .mapToObj(i -> """
                        {"address":"0x%040x","balance":1,"code":"0x00","nonce":1,"state":[%s],"state_diff":[]}\
                        """.formatted(1250 + i, storage))
                .toList();
        final var body = """
                {"block":"latest","block_override":{"base_fee_per_gas":1,"blob_base_fee":1,"block_number":1,
                 "fee_recipient":"0x00000000000000000000000000000000000004e2","gas_limit":1,"prev_randao":1,"time":1},
                 "data":"0x00","estimate":false,"from":"0x00000000000000000000000000000000000004e2","gas":21000,
                 "gas_price":1,"state_overrides":[%s],"to":"0x00000000000000000000000000000000000004e4","value":0}
                """.formatted(String.join(",", overrides));

        final var request = jsonMapper.readValue(body, ContractCallRequest.class);
        assertThat(request.getStateOverrides()).hasSize(STATE_OVERRIDES_MAX_SIZE);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsRequestExceedingDocumentLength() throws Exception {
        final var code = "0x" + "a".repeat(CODE_MAX_LENGTH);
        final var overrides = IntStream.range(0, STATE_OVERRIDES_MAX_SIZE)
                .mapToObj(i -> "{\"address\":\"0x%040x\",\"code\":\"%s\"}".formatted(1250 + i, code))
                .toList();
        final var body = """
                {"to":"0x00000000000000000000000000000000000004e4","state_overrides":[%s]}
                """.formatted(String.join(",", overrides));
        assertThat(body.length()).isGreaterThan(jumboCallDataSize() * 2 + 1024);

        mockMvc.perform(post("/api/v1/contracts/call")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains("Document length")));
    }

    @Test
    void rejectsRequestExceedingTokenCount() throws Exception {
        final var overrides = String.join(",", Collections.nCopies(1_800, "{\"address\":\"a\"}"));
        mockMvc.perform(post("/api/v1/contracts/call")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to":"0x00000000000000000000000000000000000004e4","state_overrides":[%s]}
                                """.formatted(overrides)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(new StringContains("Token count")));
    }

    private int jumboCallDataSize() {
        return evmProperties
                .getVersionedConfiguration()
                .getConfigData(JumboTransactionsConfig.class)
                .ethereumMaxCallDataSize();
    }
}
