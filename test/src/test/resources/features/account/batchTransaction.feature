@batch @fullsuite
Feature: Batch Transaction Coverage Feature

  @acceptance
  Scenario Outline: Submit a batch transaction containing a hollow account create, a normal crypto transfer, then complete the hollow account
    When I submit a batch transaction containing transfer <normalTransferAmount> tℏ to <recipientAccountName> and a hollow account create with <hollowFundingAmount> tℏ with batch signed by <batchSignerAccountName>
    Then I should be able to resolve the hollow account id
    And I should see the batch transaction in mirror node
    When I submit a transaction that completes the hollow account
    Then I should see the completion transaction in mirror node
    Examples:
      | normalTransferAmount | recipientAccountName | hollowFundingAmount | batchSignerAccountName |
      | 10000                | "ALICE"              | 5000000             | "OPERATOR"             |
