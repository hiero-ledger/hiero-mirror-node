@network @fullsuite
Feature: Network Coverage Feature

  @networkstake @acceptance
  Scenario: Get network stake
    When I verify the network stake

  @networkfees @acceptance
  Scenario: Get network fees
    When I verify the network fees

  @networksupply @acceptance
  Scenario: Get network supply
    When I verify the network supply
