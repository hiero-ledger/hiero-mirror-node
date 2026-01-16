// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;

import "./IPrngSystemContractHistorical.sol";

contract PrngSystemContractHistorical {
    address constant PRECOMPILE_ADDRESS = address(0x169);

    function getPseudorandomSeed() external payable returns (bytes32 randomBytes) {
        (bool success, bytes memory result) = PRECOMPILE_ADDRESS.call{value: msg.value}(
            abi.encodeWithSelector(IPrngSystemContractHistorical.getPseudorandomSeed.selector));
        require(success);
        randomBytes = abi.decode(result, (bytes32));
    }
}