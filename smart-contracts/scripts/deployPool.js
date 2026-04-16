const hre = require("hardhat");

async function main() {
  const matsTokenAddress = process.env.MATS_TOKEN_ADDRESS;
  if (!matsTokenAddress) {
    throw new Error("MATS_TOKEN_ADDRESS is required in .env");
  }

  const [deployer] = await hre.ethers.getSigners();
  const MatsSwapPool = await hre.ethers.getContractFactory("MatsSwapPool");
  const pool = await MatsSwapPool.deploy(matsTokenAddress, deployer.address);
  await pool.waitForDeployment();

  console.log("MatsSwapPool deployed to:", await pool.getAddress());
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
