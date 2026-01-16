@filebase @fullsuite
Feature: File Base Coverage Feature

  @critical @release @acceptance
  Scenario Outline: Validate File Flow - FileCreate, FileAppend, FileUpdate, FileDelete
    Given I successfully create a file
    Then the mirror node REST API should return status <httpStatusCode> for the file transaction
    When I successfully update the file
    Then the mirror node REST API should return status <httpStatusCode> for the file transaction
    When I successfully append to the file
    Then the mirror node REST API should return status <httpStatusCode> for the file transaction
    When I successfully delete the file
    Then the mirror node REST API should return status <httpStatusCode> for the file transaction
    Examples:
      | httpStatusCode |
      | 200            |
