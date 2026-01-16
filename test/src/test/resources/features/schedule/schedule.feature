@schedulebase @fullsuite
Feature: Schedule Base Coverage Feature

  @critical @release @acceptance
  Scenario Outline: Validate Base Schedule Flow - ScheduleCreate of CryptoTransfer and ScheduleSign
    Given I successfully schedule a HBAR transfer from treasury to ALICE with expiration time "null" and wait for expiry "false"
    Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the "NON_EXECUTED" schedule entity with expiration time "null" and wait for expiry "false"
    Given I successfully delete the schedule
    Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the "DELETED" schedule entity with expiration time "null" and wait for expiry "false"
    Given I successfully schedule a HBAR transfer from treasury to ALICE with expiration time "5s" and wait for expiry "true"
    Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    Then I wait until the schedule's expiration time
    And the mirror node REST API should verify the "EXPIRED" schedule entity with expiration time "5s" and wait for expiry "true"
    Given I successfully schedule a HBAR transfer from treasury to ALICE with expiration time "60s" and wait for expiry "false"
    Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the "NON_EXECUTED" schedule entity with expiration time "60s" and wait for expiry "false"
    When the scheduled transaction is signed by treasuryAccount
    And the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the "EXECUTED" schedule entity with expiration time "60s" and wait for expiry "false"
    Given I successfully schedule a HBAR transfer from treasury to ALICE with expiration time "25s" and wait for expiry "true"
    Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the "NON_EXECUTED" schedule entity with expiration time "25s" and wait for expiry "true"
    When the scheduled transaction is signed by treasuryAccount
    And the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    Then I wait until the schedule's expiration time
    And the mirror node REST API should verify the "EXECUTED" schedule entity with expiration time "25s" and wait for expiry "true"
    Then the mirror node REST API should list all schedules

    Examples:
      | httpStatusCode |
      | 200            |
