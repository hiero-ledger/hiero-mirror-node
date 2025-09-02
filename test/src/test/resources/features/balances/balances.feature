@balances @fullsuite @acceptance
Feature: Balances API Coverage Feature

  Scenario Outline: Basic balances API validation
    Given I have created a new account with initial balance of <initialBalance> tinybars
    When I query the mirror node REST API for balances
    Then the mirror node REST API should return balances list
    And the mirror node REST API should return balances list with limit <limit>
    And the mirror node REST API should return balances with order <order>
    And the mirror node REST API should return balances filtered by account balance <balanceFilter>
    When I query historical balances at timestamp <timestamp>
    Then the mirror node REST API should return balances at timestamp <timestamp>
    When I query balances with all parameters for account
    Then the mirror node REST API should return balances with all parameters
    Then the mirror node REST API balance should match initial balance of <initialBalance>
    Examples:
      | initialBalance | limit | balanceFilter | order | timestamp                 |
      | 1000000000     | 10    | "gt:0"        | "asc" | "lt:9999999999.000000000" |