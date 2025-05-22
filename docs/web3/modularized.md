# Modularized EVM

The `/api/v1/contracts/call` endpoint has been updated to support a modularized
execution flow. The modularized Web3 codebase replaces the legacy monolithic version
and integrates directly with the `hedera-app` from the consensus node. It enables
broader operation support and better alignment with consensus node behavior but may
introduce breaking changes due to differences between the new modularized logic and
the old monolithic implementation, which is being deprecated.


## Breaking Changes

### 1. Contract Call Behavior on Invalid Input

**Impact**: Error responses may differ from the previous monolithic flow when handling malformed or
invalid input.

**Reason for change**: The modularized execution flow introduces more granular validation and status
reporting aligned with consensus node behavior.

**Details**: Some statuses like `CONTRACT_REVERT_EXECUTED`, `INSUFFICIENT_GAS`, and
`INVALID_SOLIDITY_ADDRESS` are common to both flows. However, the modularized flow introduces more
specific statuses such as `INSUFFICIENT_PAYER_BALANCE`, `INVALID_ALIAS_KEY`, `INVALID_CONTRACT_ID`,
and `MAX_CHILD_RECORDS_EXCEEDED`, providing clearer failure reasons.

**Resolution**: Update client-side logic to handle a wider range of status codes and to expect HTTP
`400` responses with more descriptive error messages.


### 2. Gas Estimation Logic

**Impact**: Gas estimation may now return slightly different results due to improved modeling
especially for contract deploy.

**Reason for change**: Estimation logic has been updated to better reflect actual execution cost as in consensus node.  
**Resolution**: If comparing to old estimates, expect minor differences except for contract deployment.

### 3. Default KYC Status Behavior

**Impact**: The result of `getDefaultKycStatus` may differ between the monolithic and modularized
flows, potentially affecting token-related contract interactions.

**Reason for change**: The modularized flow retrieves KYC status directly from the consensus node's
state via `hedera-app`, whereas the monolithic flow used separate internal logic.

**Details**: In some cases, tokens that returned a default `false` KYC status in the monolithic flow
may now return `true` (or vice versa) based on the actual token configuration in state.

**Resolution**: Review any tests or client logic that depend on the default KYC status returned by
contract calls and adjust expectations to reflect the consensus-backed behavior in the modularized
flow.

### 4. Error on Call to Non-Existent Contract

**Impact**: Calling a contract that does not exist may return a different status in the modularized
flow compared to the monolithic implementation.

**Reason for change**: The modularized flow validates contract existence directly against the
consensus node state and returns `INVALID_CONTRACT_ID`, while the monolithic flow previously would have returned
`INVALID_TRANSACTION` in this scenario.

**Details**: Client applications relying on a specific error code for missing contracts may behave
differently depending on the flow used. 

**Resolution**: Update any error handling logic or tests expecting `INVALID_TRANSACTION` to also
handle `INVALID_CONTRACT_ID` when running against the modularized flow.

### 5. Negative Redirect Calls Return Different Errors

**Impact**: Contract calls that redirect and fail due to invalid input may produce
different error statuses between the monolithic and modularized flows.  
**Reason for change**: The modularized flow executes logic is resulting in standard EVM reverts
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

### 6. Exchange Rate Precompile Called With Value Fails Differently

**Impact**: Sending non-zero `value` to the exchange rate precompile results in different errors.  
**Reason for change**: The modularized flow rejects the call early with `INVALID_CONTRACT_ID`, while the
monolithic flow returns `CONTRACT_REVERT_EXECUTED`.  
**Resolution**: Update tests and client logic to expect `INVALID_CONTRACT_ID` when calling precompiles
with a non-zero value in the modularized flow.





