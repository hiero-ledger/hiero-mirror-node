@tokenallowance @allowance @fullsuite
Feature: Account Token Allowance Coverage Feature

  @critical @release @acceptance
  Scenario Outline: Validate approval TokenTransfer affect on TokenAllowance amount
    Given I ensure token <tokenName> has been created
    And I associate account <recipient> with token <tokenName>
    Then the mirror node REST API should return the transaction
    Given I approve <spender> to transfer up to <approvedAmount> of token <tokenName>
    Then the mirror node REST API should confirm the approved allowance <approvedAmount> of <tokenName> for <spender>
    Given <spender> transfers <transferAmount> of token <tokenName> to <recipient>
    Then the mirror node REST API should confirm the approved transfer of <transferAmount> <tokenName>
    And the mirror node REST API should confirm the debit of <transferAmount> from <tokenName> allowance of <approvedAmount> for <spender>
    Given I approve <spender> to transfer up to <approvedAmount> of token <tokenName>
    Then the mirror node REST API should confirm the approved allowance <approvedAmount> of <tokenName> for <spender>
    When I delete the allowance on token <tokenName> for <spender>
    Then the mirror node REST API should confirm the approved allowance for <tokenName> and <spender> no longer exists
    Examples:
      | tokenName  | spender | recipient | approvedAmount | transferAmount |
      | ALLOWANCEFUNGIBLE | "BOB"   | "ALICE"   | 10000          | 100            |
