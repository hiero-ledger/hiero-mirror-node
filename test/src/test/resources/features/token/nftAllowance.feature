@nftallowance @acceptance @allowance @fullsuite @critical @release
Feature: Account NFT Allowance Coverage Feature

  Scenario Outline: Validate approval NftTransfer affect on NftAllowance
    Given I ensure NFT <tokenName> has been created
    And I associate account <recipient> with token <tokenName>
    Then I mint a serial number from the token
    And the mirror node REST API should return the transaction for token serial number index 0 transaction flow
    Given I approve <spender> to all serials of the NFT
    Then the mirror node REST API should confirm the approved allowance of NFT <tokenName> and <spender> when owner is "true"
    And the mirror node REST API should confirm the approved allowance of NFT <tokenName> and <spender> when owner is "false"
    Given <spender> transfers NFT <tokenName> to <recipient> with approval=true
    Then the mirror node REST API should confirm the NFT transfer and confirm the new owner is <recipient>
    Then I delete the allowance on first NFT <tokenName> for recipient <recipient>
    When I delete the allowance on NFT <tokenName> for spender <spender>
    Then the mirror node REST API should confirm the approved allowance for NFT <tokenName> and <spender> is no longer available
    Examples:
      | tokenName              | spender | recipient |
      | ALLOWANCE_NON_FUNGIBLE | BOB     | ALICE     |