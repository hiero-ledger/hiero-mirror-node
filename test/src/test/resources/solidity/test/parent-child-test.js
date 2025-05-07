// SPDX-License-Identifier: Apache-2.0

const expect = chai.expect;
const {ethers, waffle} = require('hardhat');
const provider = waffle.provider;

const childArtifactJson = require('../artifacts/contracts/Parent.sol/Child.json');

describe('ParentChild', function () {
  it('Should contain zero balances on deployment', async function () {
    const Parent = await ethers.getContractFactory('contracts/Parent.sol:Parent');
    const parent = await Parent.deploy();
    await parent.deployed();

    // verify passed in defaults
    expect(await provider.getBalance(parent.address)).to.equal(0);
  });

  it('Should contain non zero parent balance on funded deployment', async function () {
    const Parent = await ethers.getContractFactory('contracts/Parent.sol:Parent');
    const parent = await Parent.deploy({value: ethers.utils.parseEther('0.1')});
    await parent.deployed();

    // verify passed in defaults
    expect(await provider.getBalance(parent.address)).to.equal(100000000000000000n);
  });

  it('Should contain zero child balance post createChild', async function () {
    const Parent = await ethers.getContractFactory('contracts/Parent.sol:Parent');
    const parent = await Parent.deploy({value: ethers.utils.parseEther('0.4')});
    await parent.deployed();

    const [account1] = await ethers.getSigners();

    const submitCreateChildTx = await parent.connect(account1).createChild(0);

    // wait until the transaction is mined
    var contractFunctionResult = await submitCreateChildTx.wait();

    // verify updated emergent defaults
    expect(await provider.getBalance(contractFunctionResult.to)).to.equal(400000000000000000n); // parent from transaction
  });

  it('Should contain non zero child balance post createChild with transfer amount', async function () {
    const Parent = await ethers.getContractFactory('contracts/Parent.sol:Parent');
    const Child = await ethers.getContractFactory('contracts/Parent.sol:Child');
    const parent = await Parent.deploy({value: ethers.utils.parseEther('0.4')});
    await parent.deployed();

    const [account1] = await ethers.getSigners();

    const submitCreateChildTx = await parent.connect(account1).createChild(5);

    // wait until the transaction is mined
    var contractFunctionResult = await submitCreateChildTx.wait();

    // verify updated emergent defaults
    expect(await provider.getBalance(contractFunctionResult.to)).to.equal(399999999999999995n); // parent from transaction
    const event0 = contractFunctionResult.events[0];
    expect(event0.args[0]).to.equal('Created second child contract');
    expect(event0.event).to.equal('ParentActivityLog');
    expect(event0.logIndex).to.equal(0);

    const event1 = contractFunctionResult.events[1];
    expect(event1.logIndex).to.equal(1);
    expect(event1.data).to.equal('0x0000000000000000000000000000000000000000000000000000000000000005');

    var childAddress = event1.address;
    expect(ethers.utils.isAddress(childAddress)).to.equal(true);
    expect(await provider.getBalance(childAddress)).to.equal(5); // child from transaction
  });

  it('Should contain zero balances on create2Deploy to known EVM address', async function () {
    const Parent = await ethers.getContractFactory('contracts/Parent.sol:Parent');
    const parent = await Parent.deploy();
    await parent.deployed();

    const salt = 42;

    const childBytecode = await parent.getBytecode();
    const childEvmAddress = await parent.getAddress(childBytecode, salt);
    expect(ethers.utils.isAddress(childEvmAddress)).to.equal(true);

    const [account1] = await ethers.getSigners();
    const submitCreate2DeployTx = await parent.connect(account1).create2Deploy(childBytecode, salt);

    // wait until the transaction is mined
    const contractDeployResult = await submitCreate2DeployTx.wait();

    // Verify child contract was deployed to the expected EVM address
    const event0 = contractDeployResult.events[0];
    expect(event0.logIndex).to.equal(0);
    expect(event0.event).to.equal('Create2Deploy');
    expect(event0.args.addr).to.equal(childEvmAddress);
    +(
      // verify balance defaults
      expect(await provider.getBalance(contractDeployResult.to)).to.equal(0)
    ); // parent from transaction
    expect(await provider.getBalance(childEvmAddress)).to.equal(0);

    // Vacate/self-destruct the child contract so its EVM address can be used for something else in the future.
    const childContract = new ethers.Contract(childEvmAddress, childArtifactJson.abi, account1);
    const submitDeleteChildTx = await childContract.vacateAddress();

    // wait until the transaction is mined
    const contractDeleteResult = await submitDeleteChildTx.wait();

    const vacateEvent = contractDeleteResult.events[0];
    expect(vacateEvent.logIndex).to.equal(0);
    expect(vacateEvent.event).to.equal('Create2Vacate');
    expect(vacateEvent.args.addr).to.equal(childEvmAddress);
  });
});
