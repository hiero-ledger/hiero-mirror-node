@hooks @fullsuite
Feature: Hooks Transaction Coverage Feature

  @hooks @acceptance
  Scenario Outline: Complete hooks transaction lifecycle
    # Setup: Attach hook to account
    When I attach a hook with ID <hookId> using existing contract to account <accountName>
    Then the mirror node REST API should return the account hooks for <accountName>

    # Test 1: hook execution via crypto transfer
    And I trigger hook execution via crypto transfer from <accountName> of <transferAmount> tℏ with hook <hookId>
    Then the mirror node REST API should return hook storage entries for account <accountName> and <hookId>
    When I clean up the hook execution storage for account <accountName> and hook ID <hookId>
    
    # Test 2: Create and remove HookStore transactions
    When I create a HookStore transaction with key "0x01" and value "0x02" for account <accountName> and hook ID <hookId>
    Then the storage slot "0x01" should have value "0x02" for hook ID <hookId> from account <accountName>
    When I remove the HookStore entry with key "0x01" for account <accountName> and hook ID <hookId>
    Then the storage slot "0x01" should be empty for hook ID <hookId> from account <accountName>
    
    # Test 3: Mapping entries
    When I create a HookStore mapping entry with slot "0x03" key "0x04" and value "0x05" for account <accountName> and hook ID <hookId>
    Then the mapping "0x03" with key "0x04" should have value "0x05" for hook ID <hookId> from account <accountName>
    When I remove HookStore mapping entry with slot "0x03" and key "0x04" for hook ID <hookId> from account <accountName>
    Then the mapping "0x03" with key "0x04" should be empty for hook ID <hookId> from account <accountName>
    
    # Cleanup: Delete hook
    When I delete hook with ID <hookId> from account <accountName>
    Then the account <accountName> should have no hooks attached

    Examples:
      | hookId | accountName | transferAmount |
      | 124    | "BOB"       | 1000           |
