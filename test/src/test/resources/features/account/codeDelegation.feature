@codeDelegations @fullsuite @acceptance @accounts
Feature: HIP-1340 EOA Code Delegation

  Scenario: Create an account with code delegation and execute the delegated contract, then clear the delegation
    Given I successfully create a contract for code delegation
    When I create an account with code delegation to the contract
    Then the mirror node REST API should return the delegation address for the account
    When I call pureMultiply on the delegated account via the mirror node REST API
    Then the contract call result should equal 4
    When I execute pureMultiply on the delegated account via a contract call transaction
    Then the mirror node REST API should return status 200 for the code delegation contract call
    And the mirror node REST API should return the contract result for the delegated account
    When I clear the code delegation on the account
    Then the mirror node REST API should return an empty delegation address for the account
    When I call pureMultiply on the delegated account via the mirror node REST API
    Then the contract call result should be empty
    When I execute pureMultiply on the authority using an EIP-7702 ethereum transaction
    Then the mirror node REST API should return status 200 for the EIP-7702 ethereum transaction
    And the mirror node REST API should return the authorization list for the EIP-7702 transaction
    And the mirror node REST API should return the delegation address for the account
    And the mirror node REST API should return the contract result for the delegated account
