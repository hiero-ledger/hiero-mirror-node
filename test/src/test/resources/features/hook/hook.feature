@hooks @fullsuite
Feature: Hooks Transaction Coverage Feature

  @hooks @acceptance
  Scenario Outline: Complete hooks transaction lifecycle
    # Setup: Attach hook to account
    When I attach a hook using existing contract to account <accountName>
    Then the mirror node REST API should return the account hook

    # Test 1: hook execution via crypto transfer
    And I trigger hook execution via crypto transfer of <transferAmount> tℏ
    When I create a HookStore transaction with both explicit and implicit storage slots
    Then the mirror node REST API should return hook storage entries
    When I create a HookStore transaction to remove all storage slots
    Then there should be no storage entry for hook

    # Cleanup: Delete hook
    When I delete hook
    Then the account should have no hooks attached

    Examples:
      | hookId | accountName | transferAmount | slot        | mapping          |
      | 124    | "BOB"       | 1000           | "0x01:0x02" | "0x03:0x04:0x05" |
