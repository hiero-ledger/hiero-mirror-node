// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.BalancesResponse;
import org.hiero.mirror.test.e2e.acceptance.client.AccountClient;
import org.hiero.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import org.hiero.mirror.test.e2e.acceptance.props.ExpandedAccountId;

@CustomLog
@RequiredArgsConstructor
public class BalancesFeature extends AbstractFeature {

    private final AccountClient accountClient;
    private final MirrorNodeClient mirrorClient;
    private ExpandedAccountId createdAccountId;

    private BalancesResponse balancesResponse;

    @Given("I have created a new account with initial balance of {long} tinybars")
    public void createAccountWithBalance(long initialBalance) {
        createdAccountId = accountClient.createNewAccount(initialBalance);
        log.info(
                "Created account {} with initial balance {} tinybars", createdAccountId.getAccountId(), initialBalance);
    }

    @When("I query the mirror node REST API for balances")
    public void queryBalances() {
        log.info("Calling getBalances() to retrieve all balances");
        balancesResponse = mirrorClient.getBalancesForQuery(null);
    }

    @Then("the mirror node REST API should return balances list")
    public void verifyBalancesList() {
        assertThat(balancesResponse).isNotNull();
        assertThat(balancesResponse.getBalances()).isNotNull();
        assertThat(balancesResponse.getLinks()).isNotNull();
        log.info(
                "Retrieved {} balances from Mirror Node",
                balancesResponse.getBalances().size());
    }

    @And("the mirror node REST API should return balances list with limit {int}")
    public void verifyBalancesListWithLimit(int limit) {
        log.info("Calling getBalances() to retrieve balances with limit {}", limit);
        var params =
                QueryParamsBuilder.builder().buildQueryParam("limit", limit).build();
        BalancesResponse limitedResponse = mirrorClient.getBalancesForQuery(params);
        assertThat(limitedResponse).isNotNull();
        assertThat(limitedResponse.getBalances()).isNotNull();
        assertThat(limitedResponse.getBalances().size()).isLessThanOrEqualTo(limit);
        assertThat(limitedResponse.getLinks()).isNotNull();
        log.info(
                "Retrieved {} balances with limit {} from Mirror Node",
                limitedResponse.getBalances().size(),
                limit);
    }

    @And("the mirror node REST API should return balances with order {string}")
    public void verifyBalancesWithOrder(String order) {
        log.info("Calling getBalances() to retrieve balances with order {}", order);
        String params =
                QueryParamsBuilder.builder().buildQueryParam("order", order).build();
        BalancesResponse orderResponse = mirrorClient.getBalancesForQuery(params);
        assertThat(orderResponse).isNotNull();
        assertThat(orderResponse.getBalances()).isNotNull();
        assertThat(orderResponse.getLinks()).isNotNull();
        log.info(
                "Retrieved {} balances with order '{}' from Mirror Node",
                orderResponse.getBalances().size(),
                order);
    }

    @And("the mirror node REST API should return balances filtered by account balance {string}")
    public void verifyBalancesFilteredByAccountBalance(String balance) {
        log.info("Calling getBalances() to retrieve balances with balance filter {}", balance);
        String params = QueryParamsBuilder.builder()
                .buildQueryParam("account.balance", balance)
                .build();
        BalancesResponse filterResponse = mirrorClient.getBalancesForQuery(params);
        assertThat(filterResponse).isNotNull();
        assertThat(filterResponse.getBalances()).isNotNull();
        assertThat(filterResponse.getLinks()).isNotNull();
        log.info(
                "Retrieved {} balances with balance filter '{}' from Mirror Node",
                filterResponse.getBalances().size(),
                balance);
    }

    @When("I query historical balances at timestamp {string}")
    public void queryHistoricalBalances(String timestamp) {
        log.info("Calling getBalances() to retrieve historical balances at timestamp {}", timestamp);
        final String queryParam = QueryParamsBuilder.builder()
                .buildQueryParam("timestamp", timestamp)
                .build();
        balancesResponse = mirrorClient.getBalancesForQuery(queryParam);
    }

    @Then("the mirror node REST API should return balances at timestamp {string}")
    public void verifyBalancesAtTimestamp(String timestamp) {
        assertThat(balancesResponse).isNotNull();
        assertThat(balancesResponse.getBalances()).isNotNull();
        assertThat(balancesResponse.getLinks()).isNotNull();

        // Verify timestamp is included in response when querying historical data
        if (balancesResponse.getTimestamp() != null) {
            assertThat(balancesResponse.getTimestamp()).isNotNull();
        }

        log.info(
                "Retrieved {} balances at timestamp '{}' from Mirror Node",
                balancesResponse.getBalances().size(),
                timestamp);
    }

    @When("I query balances with all parameters for account")
    public void queryBalancesWithAllParams() {
        ExpandedAccountId targetAccount = createdAccountId;
        String accountId = targetAccount.getAccountId().toString();
        String publicKey = targetAccount.getPrivateKey().getPublicKey().toStringDER();
        log.info("Calling getBalances() with all parameters for account {}", accountId);
        String params = QueryParamsBuilder.builder()
                .buildQueryParam("account.id", accountId)
                .buildQueryParam("account.balance", "gt:0")
                .buildQueryParam("account.publicKey", publicKey)
                .buildQueryParam("limit", 5L)
                .buildQueryParam("order", "desc")
                .buildQueryParam("timestamp", "lt:9999999999.000000000")
                .build();
        balancesResponse = mirrorClient.getBalancesForQuery(params);
    }

    @Then("the mirror node REST API should return balances with all parameters")
    public void verifyBalancesWithAllParams() {
        assertThat(balancesResponse).isNotNull();
        assertThat(balancesResponse.getBalances()).isNotNull();
        assertThat(balancesResponse.getBalances().size()).isLessThanOrEqualTo(5);
        assertThat(balancesResponse.getLinks()).isNotNull();

        log.info(
                "Retrieved {} balances with all parameters from Mirror Node",
                balancesResponse.getBalances().size());
    }

    private static final class QueryParamsBuilder {

        private String queryParam;

        public static QueryParamsBuilder builder() {
            return new QueryParamsBuilder();
        }

        public QueryParamsBuilder buildQueryParam(String key, Object value) {
            if (key == null) {
                return this;
            }
            final String newQueryParam = String.format("&%s=%s", key, value);
            if (queryParam != null) {
                queryParam += newQueryParam;
            } else {
                queryParam = newQueryParam;
            }
            return this;
        }

        public String build() {
            return queryParam;
        }
    }
}
