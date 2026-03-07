// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

contract NestedState {

    uint256 salt = 1234;
    string public emptyStorageData = "";

    // External function that has an argument for test value that will be written to contract storage slot
    function writeToStorageSlot(string memory _value) payable external returns (string memory){
        emptyStorageData = _value;
        return emptyStorageData;
    }

    function nestedCall(string memory s, address _state) external returns (string memory) {
        return State(_state).changeState(s);
    }

    function deployContract(string memory s) external returns (string memory) {
        State deployedContract = new State();
        string memory newState = deployedContract.changeState(s);
        deployedContract.changeCallerState(newState);
        return emptyStorageData;
    }

    function deployViaCreate2() external returns (address) {
        State newContract = new State{salt: bytes32(salt)}();

        return address(newContract);
    }
}

contract State {
    string public state = "";

    function changeState(string memory s) external returns (string memory) {
        state = s;
        return state;
    }

    function changeCallerState(string memory s) external returns (string memory) {
        NestedState caller = NestedState(msg.sender);
        caller.writeToStorageSlot(string(abi.encodePacked(s, state)));
        return s;
    }
}