@crypto
Feature: Crypto related transactions

  Scenario: Validate CryptoCreate transaction
    When I create a crypto account
    Then the DATA API should show the CryptoCreate transaction

  @alias
  Scenario: Validate auto account creation with CryptoTransfer transaction and then transfer funds from the alias account
    When I transfer some hbar to a new alias
    Then the DATA API should show the CryptoTransfer transaction and new account id
    When I transfer some hbar from the alias
    Then the DATA API should show the CryptoTransfer transaction from the alias

  Scenario: Validate CryptoTransfer transaction
    When I transfer some hbar to the treasury account
    Then the DATA API should show the CryptoTransfer transaction
