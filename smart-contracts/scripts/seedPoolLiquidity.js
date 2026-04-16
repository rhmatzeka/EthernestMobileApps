const hre = require("hardhat");

async function main() {
  const matsTokenAddress = process.env.MATS_TOKEN_ADDRESS;
  const swapPoolAddress = process.env.MATS_SWAP_POOL_ADDRESS;
  const tokenAmount = process.env.POOL_TOKEN_LIQUIDITY || "10000";
  const ethAmount = process.env.POOL_ETH_LIQUIDITY || "0.5";

  if (!matsTokenAddress) {
    throw new Error("MATS_TOKEN_ADDRESS is required in .env");
  }
  if (!swapPoolAddress) {
    throw new Error("MATS_SWAP_POOL_ADDRESS is required in .env");
  }

  const [deployer] = await hre.ethers.getSigners();
  const token = await hre.ethers.getContractAt("MatsToken", matsTokenAddress, deployer);
  const pool = await hre.ethers.getContractAt("MatsSwapPool", swapPoolAddress, deployer);

  const tokenUnits = hre.ethers.parseUnits(tokenAmount, 18);
  const ethUnits = hre.ethers.parseEther(ethAmount);

  const approveTx = await token.approve(swapPoolAddress, tokenUnits);
  await approveTx.wait();

  const liquidityTx = await pool.addLiquidity(tokenUnits, { value: ethUnits });
  await liquidityTx.wait();

  console.log("Liquidity added.");
  console.log("Token amount:", tokenAmount, "MATS");
  console.log("ETH amount:", ethAmount, "ETH");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
