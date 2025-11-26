@accounts @fullsuite
Feature: Account Coverage Feature

  @createcryptoaccount
  Scenario Outline: Create crypto account
    When I create a new account with balance <amount> tℏ
    Then the new balance should reflect cryptotransfer of <amount>
    And the mirror node REST API should return the list of accounts
    Then the mirror node REST API should return the balances
    Examples:
      | amount |
      | 10     |

  @critical @release @acceptance @cryptotransfer
  Scenario Outline: Validate simple CryptoTransfer
    Given I send <amount> tℏ to <accountName>
    Then the mirror node REST API should return status <httpStatusCode> for the crypto transfer transaction
    And the new balance should reflect cryptotransfer of <amount>
    Examples:
      | amount | accountName | httpStatusCode |
      | 1      | "ALICE"     | 200            |

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
    When I stake the account <accountName> to node <nodeId>
    Given I send <amount> tℏ to <accountName>
    Then the mirror node REST API should return the staking rewards for the account <accountName>
    Examples:
      | amount | accountName | nodeId |
      | 10     | "ALICE"     | 0      |

  @hooks @acceptance
  Scenario Outline: Validate account hook lifecycle
    When I attach a hook with ID <hookId> using existing contract to account <accountName>
    And I trigger hook execution via crypto transfer of <transferAmount> tℏ
    Then the mirror node REST API should return the account hooks
    And the mirror node REST API should return hook storage entries
    Examples:
      | accountName | hookId | transferAmount |
      | "ALICE"     | 1      | 10             |



