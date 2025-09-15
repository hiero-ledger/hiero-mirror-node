@accounts @fullsuite
Feature: Account Coverage Feature

  @createcryptoaccount
  Scenario Outline: Create crypto account
    When I create a new account with balance <amount> tℏ
    Then the new balance should reflect cryptotransfer of <amount>
    And the mirror node REST API should return the accounts list
    Then the mirror node REST API should return correct balance of <amount>
    Examples:
      | amount |
      | 10     |

  @critical @release @acceptance @cryptotransfer
  Scenario Outline: Validate simple CryptoTransfer
    Given I send <amount> tℏ to <accountName>
    Then the mirror node REST API should return status <httpStatusCode> for the crypto transfer transaction
    And the new balance should reflect cryptotransfer of <amount>
    And the mirror node REST API should return the accounts list with limit <limit>
    Examples:
      | amount | accountName | httpStatusCode | limit |
      | 1      | "ALICE"     | 200            | 25    |

  @release @acceptance @cryptotransfer @createcryptoaccount
  Scenario Outline: Create crypto account when transferring to alias
    Given I send <amount> tℏ to <keyType> alias not present in the network
    Then the transfer auto creates a new account with balance of transferred amount <amount> tℏ
    Examples:
      | amount | keyType   |
      | 1      | "ED25519" |
      | 1      | "ECDSA"   |

  @rewards @acceptance
  Scenario Outline: Validate account staking rewards API
    When I create a new account with balance <amount> tℏ
    Then I stake the account to node <nodeId>
    Given I send <amount> tℏ to <accountName>
    Then the mirror node REST API should return the staking rewards for the account
    Examples:
      | amount | accountName | nodeId |
      | 1      | "ALICE"     | 2      |



