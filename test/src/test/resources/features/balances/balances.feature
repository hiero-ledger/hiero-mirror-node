@balances @fullsuite @acceptance
Feature: Balances API Coverage Feature

  @basic @acceptance
  Scenario Outline: Basic balances API validation
    Then the mirror node REST API should return balances list
    And the mirror node REST API should return balances list with limit <limit>
    Examples:
      | limit |
      | 10    |
      | 25    |
      | 50    |

  @parameters @acceptance  
  Scenario Outline: Balances API with query parameters
    Then the mirror node REST API should return balances filtered by account ID <accountId>
    And the mirror node REST API should return balances filtered by account balance <balance>
    And the mirror node REST API should return balances with order <order>
    Examples:
      | accountId | balance | order |
      | "0.0.2"   | "gt:0"  | "asc" |
      | "0.0.3"   | "lt:1000000000000" | "desc" |

  @historical @acceptance
  Scenario Outline: Historical balances API validation  
    Then the mirror node REST API should return balances at timestamp <timestamp>
    Examples:
      | timestamp |
      | "lt:1234567890.000000000" |
      | "gte:1000000000.000000000" |

  @publickey @acceptance
  Scenario Outline: Balances API with public key filter
    Then the mirror node REST API should return balances filtered by public key <publicKey>
    Examples:
      | publicKey |
      | "3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be" |

  @comprehensive @acceptance
  Scenario Outline: Comprehensive balances API validation with all parameters
    Then the mirror node REST API should return balances with all parameters account <accountId> balance <balance> publickey <publicKey> limit <limit> order <order> timestamp <timestamp>
    Examples:
      | accountId | balance | publicKey | limit | order | timestamp |
      | "0.0.2"   | "gt:0"  | "3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be" | 5 | "desc" | "lt:9999999999.000000000" |