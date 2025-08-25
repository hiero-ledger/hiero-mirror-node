@network @fullsuite
Feature: Network Stake Coverage Feature

  @networkstake @acceptance
  Scenario Outline: Get network stake
    When I verify the network stake
    When I verify the network fees
    When I verify the network supply
