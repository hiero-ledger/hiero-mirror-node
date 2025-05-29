# Modularized EVM

The `/api/v1/contracts/call` endpoint has been updated to support a modularized
execution flow. The modularized Web3 codebase replaces the legacy monolithic version
and integrates directly with the `hedera-app` from the consensus node. It enables
broader operation support and better alignment with consensus node behavior but may
introduce breaking changes due to differences between the new modularized logic and
the old monolithic implementation, which is being deprecated.

## Breaking Changes

### 1. Address Representation and Resolution

**Impact**:
Contract calls that previously succeeded may now fail with `CONTRACT_REVERT_EXECUTED` or other execution-related errors
when addresses are provided in the long-zero format **and the referenced account has an alias**.

**Reason for change**:
The modularized flow introduces stricter address resolution logic that prioritizes EVM aliases
over long-zero format addresses when the target account has an alias.
This change aligns with consensus node behavior and ensures consistent and accurate address resolution.

**Resolution**:
When passing account or contract addresses in **any part of the request payload**—such as calldata,
constructor arguments, or encoded function parameters—clients must use the **EVM alias format** (e.g., `0xc5b7…`)
rather than the long-zero format (e.g., `0x0000000000000000000000000000000000abc123`) **if the account has an alias**.
Using the long-zero format in such cases could result in failed execution under the modularized flow.

### 2. Payer Balance Validation

**Impact**:
Contract calls that previously succeeded under the monolithic flow may now fail in the modularized flow
with `INSUFFICIENT_PAYER_BALANCE` if the payer account does not have sufficient funds,
even when the call is a simulation (e.g., using `eth_call`).

**Reason for change**:
The modularized execution flow enforces a stricter validation of the payer's balance before executing contract calls.
This change aligns with consensus node behavior,
ensuring that contract calls fail early if the payer cannot cover the required cost.

**Details**:
In the monolithic flow, balance checks were more lenient or deferred, allowing some calls to succeed
even when the payer lacked sufficient balance.
Under the modularized flow, the same calls now fail immediately if the balance check fails,
resulting in `INSUFFICIENT_PAYER_BALANCE` being returned.

**Resolution**:
Ensure that payer accounts have sufficient balances to cover contract call costs.
Update client-side logic and test cases to account for stricter balance validation in the modularized flow.

### 3. Contract Call Behavior on Invalid Input

**Impact**: Error responses may differ from the previous monolithic flow when handling malformed or
invalid input.

**Reason for change**: The modularized execution flow introduces more granular validation and status
reporting aligned with consensus node behavior.

**Details**: Some statuses like `CONTRACT_REVERT_EXECUTED`, `INSUFFICIENT_GAS`, and
`INVALID_SOLIDITY_ADDRESS` are common to both flows. However, the modularized flow introduces more
specific statuses such as `INVALID_ALIAS_KEY`, `INVALID_CONTRACT_ID`,
and `MAX_CHILD_RECORDS_EXCEEDED`, providing clearer failure reasons.

One example is when sending a non-zero value to the exchange rate precompile:

- **Modularized**: fails with `INVALID_CONTRACT_ID`
- **Monolithic**: fails with `CONTRACT_REVERT_EXECUTED`

**Resolution**:
Update client-side logic to handle a wider range of status codes and to expect HTTP `400` responses
with more descriptive error messages.
Ensure precompile calls like the exchange rate query are made with no value,
or expect `INVALID_CONTRACT_ID` under modularized behavior.

### 4. Gas Estimation Logic

**Impact**: Gas estimation may now return slightly different results due to improved modeling
especially for contract deploy.

**Reason for change**: Estimation logic has been updated to better reflect actual execution cost as in consensus node.

**Resolution**: If comparing to old estimates, expect minor differences except for contract deployment.

### 5. Default KYC Status Behavior

**Impact**: The result of `getDefaultKycStatus` may differ between the monolithic and modularized
flows, potentially affecting token-related contract interactions.

**Reason for change**: The modularized flow retrieves KYC status directly from the consensus node's
state via `hedera-app`, whereas the monolithic flow used separate internal logic.

**Details**: In some cases, tokens that returned a default `false` KYC status in the monolithic flow
may now return `true` (or vice versa) based on the actual token configuration in state.

**Resolution**: Review any tests or client logic that depend on the default KYC status returned by
contract calls and adjust expectations to reflect the consensus-backed behavior in the modularized
flow.

### 6. Behavior Change: Return Values vs Exceptions

**Impact**:
In some scenarios, contract calls that previously returned a default value (e.g., `0x`) may now result in an error,
or conversely, calls that previously threw an error may now return a benign fallback value (e.g., `false` or `0x`),
depending on the modularized flow’s interpretation of the state.

**Reason for change**:
The modularized flow improves internal consistency with the consensus node by aligning behavior
with smart contract execution semantics, particularly for missing or uninitialized state entries.
Rather than throwing exceptions for all unexpected conditions,
modularized flow may return fallback values when appropriate.

**Examples**:

- `eth_call` with a sender address that does not exist:

  - **Modularized**: Throws a `PAYER_ACCOUNT_NOT_FOUND` error
  - **Monolithic**: Returns `0x`

- Precompiled HTS contract call (e.g., `isToken`) on a token that has not yet been persisted:
  - **Modularized**: returns `false` or an empty result
  - **Monolithic**: reverts the call with `CONTRACT_REVERT_EXECUTED`

**Resolution**: Update error handling logic to support both patterns.

- When using `eth_call`, handle both exception-based and value-based responses,
  depending on the context and behavior in the modularized flow.

### 7. Error on Call to Non-Existent Contract

**Impact**: Calling a contract that does not exist may return a different status in the modularized
flow compared to the monolithic implementation.

**Reason for change**: The modularized flow validates contract existence directly against the
consensus node state and returns `INVALID_CONTRACT_ID`, while the monolithic flow previously would have returned
`INVALID_TRANSACTION` in this scenario.

**Details**: Client applications relying on a specific error code for missing contracts may behave
differently depending on the flow used.

**Resolution**: Update any error handling logic or tests expecting `INVALID_TRANSACTION` to also
handle `INVALID_CONTRACT_ID` when running against the modularized flow.

### 8. Negative Redirect Calls Return Different Errors

**Impact**: Contract calls that redirect and fail due to invalid input may produce
different error statuses between the monolithic and modularized flows.

**Reason for change**: The modularized flow execution logic results in standard EVM reverts
(e.g., `CONTRACT_REVERT_EXECUTED`) instead mono errors result in `INVALID_TOKEN_ID`.

**Details**: Affected functions include:

- `decimalsRedirect`
- `ownerOfRedirect`
- `tokenURIRedirect`

In these and similar cases:

- **Modularized**: Failing redirects result in `CONTRACT_REVERT_EXECUTED`
- **Monolithic**: Returned specific status codes such as `INVALID_TOKEN_ID`

**Resolution**: Update tests and error handling logic to account for `CONTRACT_REVERT_EXECUTED` and
`INVALID_TOKEN_ID`
