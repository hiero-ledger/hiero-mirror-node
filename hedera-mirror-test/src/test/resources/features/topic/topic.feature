@topic @fullsuite @acceptance
Feature: HCS Base Coverage Feature

  @critical @release @basicsubscribe
  Scenario Outline: Validate Topic message submission
    Given I create Fungible token and a key
    Given I successfully create a new topic id
    And I publish and verify <numMessages> messages sent
    Then the mirror node should successfully observe the transaction
    Then the mirror node should retrieve the topic
    When I successfully update an existing topic
    Then the mirror node should successfully observe the transaction
    Then the mirror node should retrieve the topic
    When I provide a number of messages <numMessages> I want to receive
    And I subscribe with a filter to retrieve messages
    Then the network should successfully observe these messages
    When I successfully delete the topic
    Then the mirror node should successfully observe the transaction
    Then the mirror node should retrieve the topic
#    Fixed Fees tests
    And I associate ALICE as payer, DAVE as collector and CAROL as exempt with fungible token
    Then I successfully create a new topic with fixed HTS and HBAR fee. DAVE is collector and CAROL is exempt
    Then ALICE is exempt - "false", publishes message to topic with fixed fee. DAVE is a fixed fees collector
    Then I verify the published message from ALICE in mirror node REST API
    Then CAROL is exempt - "true", publishes message to topic with fixed fee. DAVE is a fixed fees collector
    Then I verify the published message from CAROL in mirror node REST API
    And I subscribe with a filter to retrieve messages
    Then the network should successfully observe these messages
    When I successfully delete the topic
    Then the mirror node should successfully observe the transaction
    Then the mirror node should retrieve the topic

    Examples:
      | numMessages |
      | 2           |

  @negative
  Scenario Outline: Validate topic subscription with missing topic id
    Given I provide a topic num <topicNum>
    Then the network should observe an error <errorCode>
    Examples:
      | topicNum | errorCode                                                           |
      | ""       | "INVALID_ARGUMENT: subscribeTopic.filter.topicId: must not be null" |
      | "-1"     | "INVALID_ARGUMENT: Invalid entity ID"                               |
