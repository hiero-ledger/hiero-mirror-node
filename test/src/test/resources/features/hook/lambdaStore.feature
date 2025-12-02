@lambdastore @fullsuite
Feature: LambdaStore Transaction Coverage Feature

  @lambdastore @acceptance
  Scenario Outline: Complete LambdaStore transaction lifecycle
    # Setup: Attach hook to account
    When I attach a hook with ID <hookId> using existing contract to account "<accountName>"
    Then the mirror node REST API should return the account hooks
    
    # Test 1: Create and remove LambdaStore transactions
    When I create a LambdaStore transaction with key "key1" and value "value1" for account "<accountName>" and hook ID <hookId>
    Then the storage slot "key1" should have value "value1" for hook ID <hookId> from account "<accountName>"
    When I remove the LambdaStore entry with key "key1" for account "<accountName>" and hook ID <hookId>
    Then the storage slot "key1" should be empty for hook ID <hookId> from account "<accountName>"
    
    # Test 2: Mapping entries
    When I create a LambdaStore mapping entry with slot "mappingSlot1" key "entryKey1" and value "entryValue1" for account "<accountName>" and hook ID <hookId>
    Then the mapping "mappingSlot1" with key "entryKey1" should have value "entryValue1" for hook ID <hookId> from account "<accountName>"
    When I remove LambdaStore mapping entry with slot "mappingSlot1" and key "entryKey1" for hook ID <hookId> from account "<accountName>"
    Then the mapping "mappingSlot1" with key "entryKey1" should be empty for hook ID <hookId> from account "<accountName>"
    
    # Cleanup: Delete hook
    When I delete hook with ID <hookId> from account "<accountName>"
    Then the account "<accountName>" should have no hooks attached

    Examples:
      | hookId | accountName |
      | 124    | BOB         |
