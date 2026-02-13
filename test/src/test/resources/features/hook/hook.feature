@hooks @fullsuite
Feature: Hooks Transaction Coverage Feature

  @hooks @acceptance
  Scenario Outline: Complete hooks transaction lifecycle
    # Setup: Attach hook to account
    When I attach a hook using existing contract to account <accountName>
    Then The mirror node REST API should return the account hook

    # Test 1: hook execution via crypto transfer
    When I trigger hook execution via crypto transfer of <transferAmount> tℏ
    And I create a HookStore transaction with both explicit and implicit storage slots
    Then The mirror node REST API should return hook storage entries
    When I create a HookStore transaction to remove all storage slots
    Then There should be no storage entry for hook

    # Cleanup: Delete hook
    When I delete hook
    Then The account should have no hooks attached

    Examples:
      | accountName | transferAmount |
      | "BOB"       | 1000           |
