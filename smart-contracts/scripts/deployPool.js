const hre = require("hardhat");

async function main() {
  const tokenAddress = process.env.POOL_TOKEN_ADDRESS || process.env.MATS_TOKEN_ADDRESS;
  const tokenSymbol = process.env.POOL_TOKEN_SYMBOL || process.env.TOKEN_SYMBOL || "MATS";

  if (!tokenAddress) {
    throw new Error("POOL_TOKEN_ADDRESS or MATS_TOKEN_ADDRESS is required in .env");
  }

  const [deployer] = await hre.ethers.getSigners();
  const MatsSwapPool = await hre.ethers.getContractFactory("MatsSwapPool");
  const pool = await MatsSwapPool.deploy(tokenAddress, deployer.address);
  await pool.waitForDeployment();

  console.log(`${tokenSymbol} swap pool deployed to:`, await pool.getAddress());
  console.log("Android local.properties:");
  console.log(`SWAP_TOKEN_1_SYMBOL=${tokenSymbol}`);
  console.log(`SWAP_TOKEN_1_ADDRESS=${tokenAddress}`);
  console.log(`SWAP_TOKEN_1_POOL_ADDRESS=${await pool.getAddress()}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
