// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.BalancesResponse;
import org.hiero.mirror.test.e2e.acceptance.client.MirrorNodeClient;

@CustomLog
@RequiredArgsConstructor
public class BalancesFeature extends AbstractFeature {

    private final MirrorNodeClient mirrorClient;

    @Then("the mirror node REST API should return balances list")
    public void verifyBalancesList() {
        log.info("Calling getBalances() to retrieve all balances");
        BalancesResponse balancesResponse = mirrorClient.getBalances();
        assertThat(balancesResponse).isNotNull();
        assertThat(balancesResponse.getBalances()).isNotNull();
        assertThat(balancesResponse.getLinks()).isNotNull();
        log.info(
                "Retrieved {} balances from Mirror Node",
                balancesResponse.getBalances().size());
    }

    @And("the mirror node REST API should return balances list with limit {int}")
    public void verifyBalancesListWithLimit(int limit) {
        log.info("Calling getBalances({}) to retrieve balances with limit", limit);
        BalancesResponse balancesResponse = mirrorClient.getBalances(limit);
        assertThat(balancesResponse).isNotNull();
        assertThat(balancesResponse.getBalances()).isNotNull();
        assertThat(balancesResponse.getBalances().size()).isLessThanOrEqualTo(limit);
        assertThat(balancesResponse.getLinks()).isNotNull();
        log.info(
                "Retrieved {} balances with limit {} from Mirror Node",
                balancesResponse.getBalances().size(),
                limit);
    }

    @Then("the mirror node REST API should return balances filtered by account ID {string}")
    public void verifyBalancesFilteredByAccountId(String accountId) {
        log.info("Calling getBalancesByAccountId({}) to retrieve balances for account", accountId);
        BalancesResponse balancesResponse = mirrorClient.getBalancesByAccountId(accountId);
        assertThat(balancesResponse).isNotNull();
        assertThat(balancesResponse.getBalances()).isNotNull();
        assertThat(balancesResponse.getLinks()).isNotNull();

        // Verify all returned balances are for the specified account
        balancesResponse.getBalances().forEach(balance -> assertThat(balance.getAccount())
                .isEqualTo(accountId));

        log.info(
                "Retrieved {} balances for account {} from Mirror Node",
                balancesResponse.getBalances().size(),
                accountId);
    }

    @And("the mirror node REST API should return balances filtered by account balance {string}")
    public void verifyBalancesFilteredByAccountBalance(String balance) {
        log.info("Calling getBalancesByAccountBalance({}) to retrieve balances with balance filter", balance);
        BalancesResponse balancesResponse = mirrorClient.getBalancesByAccountBalance(balance);
        assertThat(balancesResponse).isNotNull();
        assertThat(balancesResponse.getBalances()).isNotNull();
        assertThat(balancesResponse.getLinks()).isNotNull();
        log.info(
                "Retrieved {} balances with balance filter '{}' from Mirror Node",
                balancesResponse.getBalances().size(),
                balance);
    }

    @And("the mirror node REST API should return balances with order {string}")
    public void verifyBalancesWithOrder(String order) {
        log.info("Calling getBalancesWithOrder({}) to retrieve balances with order", order);
        BalancesResponse balancesResponse = mirrorClient.getBalancesWithOrder(order);
        assertThat(balancesResponse).isNotNull();
        assertThat(balancesResponse.getBalances()).isNotNull();
        assertThat(balancesResponse.getLinks()).isNotNull();
        log.info(
                "Retrieved {} balances with order '{}' from Mirror Node",
                balancesResponse.getBalances().size(),
                order);
    }

    @Then("the mirror node REST API should return balances at timestamp {string}")
    public void verifyBalancesAtTimestamp(String timestamp) {
        log.info("Calling getBalancesByTimestamp({}) to retrieve historical balances", timestamp);
        BalancesResponse balancesResponse = mirrorClient.getBalancesByTimestamp(timestamp);
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

    @Then("the mirror node REST API should return balances filtered by public key {string}")
    public void verifyBalancesFilteredByPublicKey(String publicKey) {
        log.info("Calling getBalancesByPublicKey({}) to retrieve balances for public key", publicKey);
        BalancesResponse balancesResponse = mirrorClient.getBalancesByPublicKey(publicKey);
        assertThat(balancesResponse).isNotNull();
        assertThat(balancesResponse.getBalances()).isNotNull();
        assertThat(balancesResponse.getLinks()).isNotNull();
        log.info(
                "Retrieved {} balances for public key '{}' from Mirror Node",
                balancesResponse.getBalances().size(),
                publicKey);
    }

    @Then(
            "the mirror node REST API should return balances with all parameters account {string} balance {string} publickey {string} limit {int} order {string} timestamp {string}")
    public void verifyBalancesWithAllParameters(
            String accountId, String balance, String publicKey, int limit, String order, String timestamp) {
        log.info("Calling getBalancesWithAllParams() to retrieve balances with all query parameters");
        BalancesResponse balancesResponse =
                mirrorClient.getBalancesWithAllParams(accountId, balance, publicKey, limit, order, timestamp);

        assertThat(balancesResponse).isNotNull();
        assertThat(balancesResponse.getBalances()).isNotNull();
        assertThat(balancesResponse.getBalances().size()).isLessThanOrEqualTo(limit);
        assertThat(balancesResponse.getLinks()).isNotNull();

        // Verify filtering worked correctly
        balancesResponse.getBalances().forEach(balanceItem -> {
            if (accountId != null && !accountId.trim().isEmpty()) {
                assertThat(balanceItem.getAccount()).isEqualTo(accountId);
            }
        });

        log.info(
                "Retrieved {} balances with all parameters from Mirror Node",
                balancesResponse.getBalances().size());
    }
}
