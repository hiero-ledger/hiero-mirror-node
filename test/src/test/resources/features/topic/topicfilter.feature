@topic @topicmessagesfilter @fullsuite
Feature: HCS Message Filter Coverage Feature

  @critical @release @acceptance @filter
  Scenario Outline: Validate topic filtering with past date and get X previous
    Given I successfully create a new topic id
    And I publish and verify <numMessages> messages sent
    Then the mirror node should successfully observe the transaction
    When I provide a starting timestamp <startTimestamp> and a number of messages <numMessages> I want to receive
    And I subscribe with a filter to retrieve messages
    Then the network should successfully observe these messages
    Examples:
      | startTimestamp            | numMessages |
      | "1970-01-01T00:00:00.00Z" | 2           |
      | "-60"                     | 5           |

  Scenario Outline: Validate resubscribe topic filtering
    Given I successfully create a new topic id
    And I publish and verify <numMessages> messages sent
    Then the mirror node should successfully observe the transaction
    When I provide a starting timestamp <startTimestamp> and a number of messages <numMessages> I want to receive
    And I subscribe with a filter to retrieve messages
    And I unsubscribe from a topic
    And I subscribe with a filter to retrieve messages
    Then the network should successfully observe these messages
    Examples:
      | startTimestamp            | numMessages |
      | "1970-01-01T00:00:00.00Z" | 2           |
      | "2000-01-01T00:00:00.00Z" | 5           |

  @edge
  Scenario Outline: Validate topic filtering with start and end time in between min and max messages (e.g. if 100 messages get 25-30)
    Given I successfully create a new topic id
    And I publish and verify <publishCount> messages sent
    Then the mirror node should successfully observe the transaction
    When I provide a startSequence <startSequence> and endSequence <endSequence> and a number of messages <numMessages> I want to receive
    And I subscribe with a filter to retrieve messages
    Then the network should successfully observe <numMessages> messages
    Examples:
      | publishCount | startSequence | endSequence | numMessages |
      | 50           | 25            | 30          | 5           |

  @acceptance
  Scenario Outline: Validate topic filtering with past date and a specified limit
    Given I successfully create a new topic id
    And I publish and verify <numMessages> messages sent
    Then the mirror node should successfully observe the transaction
    When I provide a starting timestamp <startTimestamp> and ending timestamp <endTimestamp> and a limit of <limit> messages I want to receive
    And I subscribe with a filter to retrieve messages
    Then the network should successfully observe these messages
    Examples:
      | numMessages | startTimestamp            | endTimestamp | limit |
      | 10          | "2020-01-01T00:00:00.00Z" | "180"        | 5     |
