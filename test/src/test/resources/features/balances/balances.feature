@balances @fullsuite
Feature: Balances API Coverage Feature

  @basic @acceptance
  Scenario Outline: Basic balances API validation
    Given I have created a new account with initial balance of <initialBalance> tinybars
    When I query the mirror node REST API for balances
    Then the mirror node REST API should return balances list
    And the mirror node REST API should return balances list with limit <limit>
    Examples:
      | initialBalance | limit |
      | 1000000000     | 10    |

  @parameters @acceptance
  Scenario Outline: Balances API with query parameters
    Given I have created a new account with initial balance of 2000000000 tinybars
    When I query balances for account
    And the mirror node REST API should return balances filtered by account balance <balanceFilter>
    And the mirror node REST API should return balances with order <order>
    Examples:
      | balanceFilter | order |
      | "gt:0"        | "asc" |

  @historical @acceptance
  Scenario Outline: Historical balances API validation
    When I query historical balances at timestamp <timestamp>
    Then the mirror node REST API should return balances at timestamp <timestamp>
    Examples:
      | timestamp                  |
      | "lt:9999999999.000000000"  |
      | "gte:1640995200.000000000" |

  @comprehensive @acceptance
  Scenario: Comprehensive balances API validation
    Given I have created a new account with initial balance of 3000000000 tinybars
    When I query balances with all parameters for account
    Then the mirror node REST API should return balances with all parameters