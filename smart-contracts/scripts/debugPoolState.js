const hre = require("hardhat");

async function main() {
  const matsTokenAddress = process.env.MATS_TOKEN_ADDRESS;
  const swapPoolAddress = process.env.MATS_SWAP_POOL_ADDRESS;

  if (!matsTokenAddress) {
    throw new Error("MATS_TOKEN_ADDRESS is required in .env");
  }
  if (!swapPoolAddress) {
    throw new Error("MATS_SWAP_POOL_ADDRESS is required in .env");
  }

  const [signer] = await hre.ethers.getSigners();
  const token = await hre.ethers.getContractAt("MatsToken", matsTokenAddress, signer);
  const pool = await hre.ethers.getContractAt("MatsSwapPool", swapPoolAddress, signer);

  const signerAddress = await signer.getAddress();
  const signerTokenBalance = await token.balanceOf(signerAddress);
  const signerTokenAllowance = await token.allowance(signerAddress, swapPoolAddress);
  const tokenOwner = await token.owner();
  const poolOwner = await pool.owner();
  const poolTokenBalance = await token.balanceOf(swapPoolAddress);
  const poolEthBalance = await hre.ethers.provider.getBalance(swapPoolAddress);

  console.log("Signer:", signerAddress);
  console.log("Pool owner:", poolOwner);
  console.log("Token owner:", tokenOwner);
  console.log("Signer token balance:", hre.ethers.formatUnits(signerTokenBalance, 18), "MATS");
  console.log("Signer allowance to pool:", hre.ethers.formatUnits(signerTokenAllowance, 18), "MATS");
  console.log("Pool token balance:", hre.ethers.formatUnits(poolTokenBalance, 18), "MATS");
  console.log("Pool ETH balance:", hre.ethers.formatEther(poolEthBalance), "ETH");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
