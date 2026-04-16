const hre = require("hardhat");

async function main() {
  const matsTokenAddress = process.env.MATS_TOKEN_ADDRESS;
  const mintAmount = process.env.MINT_MATS_AMOUNT || "100000";

  if (!matsTokenAddress) {
    throw new Error("MATS_TOKEN_ADDRESS is required in .env");
  }

  const [deployer] = await hre.ethers.getSigners();
  const token = await hre.ethers.getContractAt("MatsToken", matsTokenAddress, deployer);
  const deployerAddress = await deployer.getAddress();
  const mintUnits = hre.ethers.parseUnits(mintAmount, 18);

  const mintTx = await token.mint(deployerAddress, mintUnits);
  await mintTx.wait();

  console.log("Minted", mintAmount, "MATS to", deployerAddress);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
