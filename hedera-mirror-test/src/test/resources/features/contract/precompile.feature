@contractbase @fullsuite @precompile @web3 @acceptance
Feature: Precompile Contract Base Coverage Feature

  Scenario Outline: Validate Precompile Contract
    Given I successfully create and verify a precompile contract from contract bytes
    Given I verify the precompile contract bytecode is deployed successfully
    Given I successfully create and verify a fungible token for custom fees
    Given I successfully create and verify a fungible token for precompile contract tests
    Given I create an ecdsa account and associate it to the tokens
    Then check if fungible token is token
    And verify fungible token isn't frozen
    And the contract call REST API to is token with invalid account id should return an error
    And the contract call REST API to is token with valid account id should return an error
    And check if fungible token is kyc granted
    Given I freeze fungible token for evm address
    Then the mirror node REST API should return status 200 for the latest transaction
    And check if fungible token is frozen for evm address
    Given I unfreeze fungible token for evm address
    Then the mirror node REST API should return status 200 for the latest transaction
    And check if fungible token is unfrozen for evm address
    And the contract call REST API should return the default freeze for a fungible token
    And the contract call REST API should return the default kyc for a fungible token
    And the contract call REST API should return the information for token for a fungible token
    And the contract call REST API should return the information for a fungible token
    And the contract call REST API should return the type for a fungible token
    And the contract call REST API should return the expiry token info for a fungible token
    And the contract call REST API should return the token key for a fungible token
    And the contract call REST API should return the name by direct call for a fungible token
    And the contract call REST API should return the symbol by direct call for a fungible token
    And the contract call REST API should return the decimals by direct call for a  fungible token
    And the contract call REST API should return the total supply by direct call for a  fungible token
    And the contract call REST API should return the balanceOf by direct call for a fungible token
    And the contract call REST API should return the allowance by direct call for a fungible token
    And the contract call REST API should return the custom fees for a fungible token
    Given I successfully create and verify a non fungible token for precompile contract tests
    Given I mint and verify a nft
    Then the mirror node REST API should return status 200 for the latest transaction
    And check if non fungible token is token
    And verify non fungible token isn't frozen
    Given I freeze a non fungible token
    Then the mirror node REST API should return status 200 for the latest transaction
    And check if non fungible token is frozen
    Given I unfreeze a non fungible token
    Then the mirror node REST API should return status 200 for the latest transaction
    And check if non fungible token is unfrozen
    And check if non fungible token is kyc granted
    And the contract call REST API should return the default freeze for a non fungible token
    And the contract call REST API should return the default kyc for a non fungible token
    And the contract call REST API should return the information for token for a non fungible token
    And the contract call REST API should return the information for a non fungible token
    And the contract call REST API should return the type for a non fungible token
    And the contract call REST API should return the expiry token info for a non fungible token
    And the contract call REST API should return the token key for a non fungible token
    And the contract call REST API should return the name by direct call for a non fungible token
    And the contract call REST API should return the symbol by direct call for a non fungible token
    And the contract call REST API should return the total supply by direct call for a non fungible token
    And the contract call REST API should return the ownerOf by direct call for a non fungible token
    And the contract call REST API should return the getApproved by direct call for a non fungible token
    And the contract call REST API should return the isApprovedForAll by direct call for a non fungible token
    And the contract call REST API should return the custom fees for a non fungible token
    And I call function with HederaTokenService getTokenCustomFees token - fractional fee and fixed fee - fungible token
    And I call function with HederaTokenService getTokenCustomFees token - royalty fee
    And I call function with HederaTokenService getTokenCustomFees token - royalty fee + fallback
