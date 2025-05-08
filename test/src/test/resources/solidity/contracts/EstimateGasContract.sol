// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

contract Caller {
    function pureMultiply() public pure returns (int) {
        return 2 * 2;
    }

    function msgSender() public view returns (address) {
        return msg.sender;
    }

    function txOrigin() public view returns (address) {
        return tx.origin;
    }

    function msgSig() public pure returns (bytes4) {
        return msg.sig;
    }

    function msgValue() public payable returns (uint) {
        return msg.value;
    }

    function addressBalance(address addr) public view returns (uint256) {
        return addr.balance;
    }
}

contract MockContract {
    uint public counter;

    function incrementCounter() public {
        counter += 1;
        if (counter>10){
            revert("Forced revert");
        }
    }

    function getAddress() public view returns (address) {
        return address(this);
    }

    function destroy() public {
        selfdestruct(payable(msg.sender));
    }

    receive() external payable {}
}

contract DeployDummyContract{
    DummyContract dummyContract;
    constructor(bool flag) payable {
        dummyContract = new DummyContract(flag);
    }
}

contract DummyContract {
    constructor(bool shouldFail) payable {
        if (shouldFail) {
            revert("DummyContract deployment failed intentionally.");
        }
    }
}

contract EstimateGasContract is Caller {
    uint256 public salt = 1;
    uint256 public salt2 = 2;
    uint256 public salt_create2 = 1;
    uint256 public counter = 1;
    uint256 public counter2 = 1;
    uint256 public counter3 = 1;
    uint256 public counter4 = 1;
    uint256 public counter5 = 1;
    uint256 public transferType = 1; // 1-transfer, 2-send, 3-call
    MockContract mockContract;
    DeployDummyContract deployDummyContract;
    address[] public deployedMockContracts;


    constructor() payable {
        mockContract = new MockContract();
    }


    /*
     * NB: Gas costs for updating contract storage slots in the EVM vary due to its storage operation logic:
     * 1. **Same Value Update**: Minimal gas consumption (e.g., 27726) due to no effective operation.
     * 2. **Different Non-Zero Value Update**: Increased gas usage (e.g., 30946) for actual computational changes.
     * 3. **Zero to Non-Zero Update**: Highest gas cost (e.g., 50611) because initializing a slot is more complex.
     * 4. **Non-Zero to Zero Update**: Distinct gas cost (e.g, 36452) due to process of clearing the storage slot.
    */
    function updateCounter(uint256 _counter) public returns (uint256) {
        counter = _counter;
        return counter;
    }

    function updateCounter4(uint256 _counter) public returns (uint256) {
        counter4 = _counter;
        return counter4;
    }

    function updateCounter5(uint256 _counter) public returns (uint256) {
        counter5 = _counter;
        return counter5;
    }

    function incrementCounter() public returns (address)  {
        counter2 += 1;
        return address(this);
    }

    function updateType(uint256 _type) public returns (uint256) {
        transferType = _type;
        return transferType;
    }

    function deployAndCallMockContract(uint _numCalls) public {
        MockContract newB = new MockContract();
        deployedMockContracts.push(address(newB));

        for (uint i = 0; i < _numCalls; i++) {
            newB.incrementCounter();
        }
    }

    function createDummyContract() public returns (address) {
        DeployDummyContract newContract = new DeployDummyContract(true);
        return address(newContract);
    }

    function getCounter() public view returns (uint256) {
        return counter;
    }

    function getMockContractAddress() public view returns (address) {
        return address(mockContract);
    }

    function deployViaCreate() public returns (address) {
        MockContract newContract = new MockContract();

        return address(newContract);
    }

    function deployViaCreate2() public returns (address) {
        MockContract newContract = new MockContract{salt: bytes32(salt_create2)}();

        return address(newContract);
    }

    function createClone() public returns (address result) {
        bytes20 targetBytes = bytes20(address(this)); // This contract's address
        // EIP-1167 bytecode for creating a clone
        assembly {
            let clone := mload(0x40)
            mstore(clone, 0x3d80600a3d3981f3) // EIP-1167 bytecode start
            mstore(add(clone, 0x14), targetBytes) // Address of the master contract
            mstore(add(clone, 0x28), 0x5af43d82803e903d91602b57fd5bf3) // EIP-1167 bytecode end
            result := create(0, clone, 0x37) // Deploying a new proxy contract
        }
        require(result != address(0), "Clone creation failed");
    }

    function staticCallToContract(address _address, bytes4 _sig) public view returns (address) {
        bytes memory result;
        bool success;

        assembly {
            let x := mload(0x40)
            mstore(x, _sig)

            success := staticcall(50000, _address, x, 0x4, x, 0x20)

            mstore(0x40, add(x, 0x20))
            mstore(result, x)
        }

        return abi.decode(result, (address));
    }

    function delegateCallToContract(address _address, bytes4 _sig) public returns (address) {
        bytes memory result;
        bool success;

        assembly {
            let x := mload(0x40)
            mstore(x, _sig)

            success := delegatecall(50000, _address, x, 0x4, x, 0x20)

            mstore(0x40, add(x, 0x20))
            mstore(result, x)
        }

        return abi.decode(result, (address));
    }

    function callCodeToContract(address _address, bytes4 _sig) public returns (address) {
        bytes memory result;
        bool success;

        assembly {
            let x := mload(0x40)
            mstore(x, _sig)

            success := callcode(50000, _address, 0, x, 0x4, x, 0x20)

            mstore(0x40, add(x, 0x20))
            mstore(result, x)
        }

        return abi.decode(result, (address));
    }

    function logs() public {
        assembly {
            mstore(0x80, 0x160c)
            log0(0x80, 0x20)
            log1(0x80, 0x20, 0xac3e966f295f2d5312f973dc6d42f30a6dc1c1f76ab8ee91cc8ca5dad1fa60fd)
            log2(0x80, 0x20, 0xac3e966f295f2d5312f973dc6d42f30a6dc1c1f76ab8ee91cc8ca5dad1fa60fd, 0xae85c7887d510d629d8eb59ca412c0bf604c72c550fb0eec2734b12c76f2760b)

            mstore(add(0x80, 0x20), 0x551)
            log3(0x80, 0x40, 0xac3e966f295f2d5312f973dc6d42f30a6dc1c1f76ab8ee91cc8ca5dad1fa60fd, 0xae85c7887d510d629d8eb59ca412c0bf604c72c550fb0eec2734b12c76f2760b, 0xf4cd3854cb47c6b2f68a3a796635d026b9b412a93dfb80dd411c544cbc3c1817)
            log4(0x80, 0x40, 0xac3e966f295f2d5312f973dc6d42f30a6dc1c1f76ab8ee91cc8ca5dad1fa60fd, 0xae85c7887d510d629d8eb59ca412c0bf604c72c550fb0eec2734b12c76f2760b, 0xf4cd3854cb47c6b2f68a3a796635d026b9b412a93dfb80dd411c544cbc3c1817, 0xe32ef46652011110f84325a4871007ee80018c1b6728ee04ffae74eb557e3fbf)
        }
    }

    function destroy() public {
        assembly {
            selfdestruct(caller())
        }
    }

    function callToInvalidContract(address _invalidContract) public {
        _invalidContract.call(abi.encodeWithSignature("invalidFunction()"));
    }

    function delegateCallToInvalidContract(address _invalidContract) public {
        _invalidContract.delegatecall(abi.encodeWithSignature("invalidFunction()"));
    }

    function staticCallToInvalidContract(address _invalidContract) public view {
        _invalidContract.staticcall(abi.encodeWithSignature("invalidFunction()"));
    }

    function callCodeToInvalidContract(address _invalidContract) public {
        bytes memory result;
        bool success;

        bytes4 sig = bytes4(keccak256("invalidFunction()"));
        assembly {
            let x := mload(0x40)
            mstore(x, sig)

            success := callcode(50000, _invalidContract, 0, x, 0x4, x, 0x20)

            mstore(0x40, add(x, 0x20))
            mstore(result, x)
        }
    }

    function callExternalFunctionNTimes(uint256 _n, address _contractAddress) external {
        for (uint256 i = 1; i < _n; i++) {
            _contractAddress.call(abi.encodeWithSignature("updateCounter4(uint256)", i));
        }
    }

    function delegatecallExternalFunctionNTimes(uint256 _n, address _contractAddress) external {
        for (uint256 i = 1; i < _n; i++) {
            _contractAddress.delegatecall(abi.encodeWithSignature("updateCounter5(uint256)", i));
        }
    }

    function delegatecallExternalViewFunctionNTimes(uint256 _n, address _contractAddress) external {
        for (uint256 i = 0; i < _n; i++) {
            _contractAddress.delegatecall(abi.encodeWithSignature("getAddress()"));
        }
    }

    function updateStateNTimes(uint256 _n) external returns (uint256) {
        for (uint256 i = 0; i < _n; i++) {
            counter3 = i;
        }
        return counter3;
    }

    function callExternalViewFunctionNTimes(uint256 _n, address _contractAddress) external {
        for (uint256 i = 0; i < _n; i++) {
            _contractAddress.call(abi.encodeWithSignature("getAddress()"));
        }
    }

    function reentrancyWithTransfer(address _to, uint256 _amount) external {
        payable(_to).transfer(_amount);
    }

    function reentrancyWithCall(address _to, uint256 _amount) external {
        payable(_to).call{value: _amount}("");
    }

    function getGasLeft() external view returns (uint256) {
        return gasleft();
    }

    function nestedCalls(uint256 _it, uint256 _n, address _contractAddress) external returns (uint256) {
        if (_it < _n) {
            (, bytes memory data) = _contractAddress.call(abi.encodeWithSignature("nestedCalls(uint256,uint256,address)", _it + 1, _n, _contractAddress));
            return uint256(bytes32(data));
        }
        return _it;
    }

    function deployNestedContracts() public returns (address, address) {
        MockContract newContract1 = new MockContract();
        MockContract newContract2 = new MockContract();

        return (address(newContract1), address(newContract2));
    }

    function deployNestedContracts2() public returns (address, address) {
        MockContract newContract1 = new MockContract{salt: bytes32(salt)}();
        MockContract newContract2 = new MockContract{salt: bytes32(salt2)}();

        return (address(newContract1), address(newContract2));
    }

    function deployDestroy() public  {
        MockContract newContract = new MockContract{salt: bytes32(salt)}();
        newContract.destroy();
    }

    function reentrancyCallWithGas(address _to, uint256 _amount) external returns (uint256, uint256)  {
        uint256 balanceBefore = address(this).balance;
        payable(_to).call{value: _amount, gas: 2300}("");
        uint256 balanceAfter = address(this).balance;

        return (balanceBefore, balanceAfter);
    }

    function recurse(uint256 depth) external {

        // Each call takes at least 3 slots in the stack
        uint256 placeHolder1 = 1;
        uint256 placeHolder2 = 2;
        uint256 placeHolder3 = 3;

        if(depth > 0) {
            this.recurse(depth - 1);
        }
    }

    event TransferSuccess(bool success);

    receive() external payable {}

    fallback() external payable {
        require(address(mockContract) != address(0), "MockContract address not set");

        address payable mockContractAddress = payable(address(mockContract)); // Cast to payable address

        if (transferType == 1) {
            // Using transfer
            mockContractAddress.transfer(msg.value);
        } else if (transferType == 2) {
            // Using send
            bool sent = mockContractAddress.send(msg.value);
            require(sent, "Failed to send Ether via send");
        } else if (transferType == 3) {
            // Using call
            (bool success, ) = mockContractAddress.call{value: msg.value, gas: 2300}("");
            require(success, "Failed to send Ether via call");
        } else {
            revert("Invalid transfer type");
        }
    }

}

contract ReentrancyHelper {
    address externalContract;

    constructor(address _externalContract) {
        externalContract = _externalContract;
    }

    fallback() external payable {
        address(externalContract).call(abi.encodeWithSignature("reentrancyWithCall(address,uint256)", address(this), 100000000));
    }
}
