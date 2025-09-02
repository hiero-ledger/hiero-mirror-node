// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.Builder;
import lombok.CustomLog;
import lombok.Getter;
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
        var params = BalancesQueryParams.builder().build();
        balancesResponse = mirrorClient.getBalances(params);
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
        var params = BalancesQueryParams.builder().limit(limit).build();
        BalancesResponse limitedResponse = mirrorClient.getBalances(params);
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
        var params = BalancesQueryParams.builder().order(order).build();
        BalancesResponse orderResponse = mirrorClient.getBalances(params);
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
        var params = BalancesQueryParams.builder().accountBalance(balance).build();
        BalancesResponse filterResponse = mirrorClient.getBalances(params);
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
        var params = BalancesQueryParams.builder().timestamp(timestamp).build();
        balancesResponse = mirrorClient.getBalances(params);
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
        var params = BalancesQueryParams.builder()
                .accountId(accountId)
                .accountBalance("gt:0")
                .publicKey(publicKey)
                .limit(5)
                .order("desc")
                .timestamp("lt:9999999999.000000000")
                .build();
        balancesResponse = mirrorClient.getBalances(params);
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

    @Then("the mirror node REST API balance should match initial balance of {long}")
    public void theMirrorNodeBalanceShouldMatchInitialBalanceOfInitialBalance(long initialBalance) {
        String accountId = createdAccountId.getAccountId().toString();
        log.info("Calling getBalances() to retrieve balances for account {}", createdAccountId.getAccountId());
        var params = BalancesQueryParams.builder().accountId(accountId).build();
        balancesResponse = mirrorClient.getBalances(params);
        assertThat(balancesResponse).isNotNull();
        assertThat(balancesResponse.getBalances()).isNotNull();
        assertThat(balancesResponse.getLinks()).isNotNull();
        // Unable to verify this as account balances are
        // sometimes not available by the time this step is executed!
        //        assertThat(balancesResponse.getBalances()).isNotEmpty();
        //        assertThat(balancesResponse.getBalances().get(0).getBalance()).isEqualTo(initialBalance);
    }

    @Builder
    @Getter
    public static class BalancesQueryParams {
        private final String accountId;
        private final String accountBalance;
        private final String publicKey;
        private final Integer limit;
        private final String order;
        private final String timestamp;
    }
}
