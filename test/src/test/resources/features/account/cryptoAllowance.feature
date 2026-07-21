@cryptoallowance @allowance @fullsuite
Feature: Account Crypto Allowance Coverage Feature

  @critical @release @acceptance
  Scenario Outline: Validate approval CryptoTransfer affect on CryptoAllowance amount
    Given I approve <spender> to transfer up to <approvedAmount> tℏ
    Then the mirror node REST API should confirm the approved <approvedAmount> tℏ crypto allowance
    # This transfer by owner does not debit allowance amount
    Given I send <transferAmount> tℏ to <recipient>
    Then the mirror node REST API should return status 200 for the crypto transfer transaction
    And the new balance should reflect cryptotransfer of <transferAmount>
    But the mirror node REST API should confirm the approved <approvedAmount> tℏ crypto allowance
    When <spender> transfers <transferAmount> tℏ from the approved allowance to <recipient>
    Then the mirror node REST API should confirm the approved transfer of <transferAmount> tℏ
    And the mirror node REST API should confirm the approved allowance of <approvedAmount> tℏ was debited by <transferAmount> tℏ
    When I approve <spender> to transfer up to <approvedAmount> tℏ
    Then the mirror node REST API should confirm the approved <approvedAmount> tℏ crypto allowance
    When I delete the crypto allowance for <spender>
    Then the mirror node REST API should confirm the crypto allowance no longer exists
    Examples:
      | spender | approvedAmount | recipient | transferAmount |
      | "BOB"   | 100            | "ALICE"   | 1              |

  @critical @release @acceptance
  Scenario Outline: Validate contract spender debits CryptoAllowance via the HTS precompile
    Given I successfully create a spend on behalf contract
    And I approve the spend on behalf contract to transfer up to <approvedAmount> tℏ
    Then the mirror node REST API should confirm the approved <approvedAmount> tℏ crypto allowance for the contract
    When the spend on behalf contract spends <transferAmount> tℏ of the approved allowance to <recipient>
    Then the mirror node REST API should confirm the contract approved allowance of <approvedAmount> tℏ was debited by <transferAmount> tℏ
    And the mirror node contract call should return the approved allowance of <approvedAmount> tℏ debited by <transferAmount> tℏ
    Examples:
      | approvedAmount | recipient | transferAmount |
      | 10000000       | "ALICE"   | 1000000        |
