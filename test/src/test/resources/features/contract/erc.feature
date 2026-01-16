@contractbase @fullsuite @web3 @erc @acceptance
Feature: ERC Contract Base Coverage Feature

  Scenario Outline: Validate ERC Contract
    Given I successfully create an erc contract from contract bytes with balance 0
    Then I create a new token with freeze status 2 and kyc status 1
    Then I create a new nft with infinite supplyType
    Then I mint a serial number
    Then the mirror node REST API should return status 200 for the erc contract transaction
    And I call the erc contract via the mirror node REST API for token name
    And I call the erc contract via the mirror node REST API for token symbol
    And I call the erc contract via the mirror node REST API for token decimals
    And I call the erc contract via the mirror node REST API for token totalSupply
    And I call the erc contract via the mirror node REST API for token ownerOf
    And I call the erc contract via the mirror node REST API for token tokenUri
    And I call the erc contract via the mirror node REST API for token getApproved
    And I call the erc contract via the mirror node REST API for token allowance
    And I call the erc contract via the mirror node REST API for token isApprovedForAll
    And I call the erc contract via the mirror node REST API for token balance
    When I approve <spenderName> for nft
    Then the mirror node REST API should return status 200 for the erc contract transaction
    And I call the erc contract via the mirror node REST API for token getApproved with response BOB
    When I approve <approvedForAllSpenderName> for nft all serials
    Then the mirror node REST API should return status 200 for the erc contract transaction
    And I call the erc contract via the mirror node REST API for token isApprovedForAll with response true
    When I approve <tokenAllowanceSpender> with <allowances>
    Then the mirror node REST API should return status 200 for the erc contract transaction
    And I call the erc contract via the mirror node REST API for token allowance with allowances
    Then I associate and approve the tokens
    Then I call the erc contract via the mirror node REST API for token isApprovedForAll with response true with alias accounts
    Then I call the erc contract via the mirror node REST API for token allowance with alias accounts
    Then I call the erc contract via the mirror node REST API for token balance with alias account

    Examples:
      | spenderName | approvedForAllSpenderName | tokenAllowanceSpender | allowances |
      | "BOB"       | "ALICE"                   | "ALICE"               | 2          |
