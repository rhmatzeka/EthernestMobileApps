const hre = require("hardhat");

async function main() {
  const idrxTokenAddress = process.env.IDRX_TOKEN_ADDRESS;
  if (!idrxTokenAddress) {
    throw new Error("IDRX_TOKEN_ADDRESS is required in .env");
  }

  const [deployer] = await hre.ethers.getSigners();
  const provider = hre.ethers.provider;
  const nonce = await provider.getTransactionCount(deployer.address, "pending");
  const feeData = await provider.getFeeData();
  const maxPriorityFeePerGas = feeData.maxPriorityFeePerGas
    ? feeData.maxPriorityFeePerGas * 2n
    : hre.ethers.parseUnits("2", "gwei");
  const maxFeePerGas = feeData.maxFeePerGas
    ? feeData.maxFeePerGas * 2n
    : hre.ethers.parseUnits("30", "gwei");

  const MatsSwapPool = await hre.ethers.getContractFactory("MatsSwapPool");
  const pool = await MatsSwapPool.deploy(idrxTokenAddress, deployer.address, {
    nonce,
    maxFeePerGas,
    maxPriorityFeePerGas
  });
  await pool.waitForDeployment();

  console.log("IDRX swap pool deployed to:", await pool.getAddress());
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
