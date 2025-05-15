@accounts @fullsuite
Feature: Account Coverage Feature

  @createcryptoaccount
  Scenario Outline: Create crypto account
    When I create a new account with balance <amount> tℏ
    Then the new balance should reflect cryptotransfer of <amount>
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
