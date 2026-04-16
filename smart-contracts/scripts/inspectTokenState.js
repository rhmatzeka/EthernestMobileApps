const hre = require("hardhat");

async function main() {
  const tokenAddress = process.env.INSPECT_TOKEN_ADDRESS || process.env.IDRX_TOKEN_ADDRESS;
  if (!tokenAddress) {
    throw new Error("INSPECT_TOKEN_ADDRESS or IDRX_TOKEN_ADDRESS is required in .env");
  }

  const [signer] = await hre.ethers.getSigners();
  const signerAddress = await signer.getAddress();

  const erc20 = await hre.ethers.getContractAt(
    [
      "function name() view returns (string)",
      "function symbol() view returns (string)",
      "function decimals() view returns (uint8)",
      "function balanceOf(address) view returns (uint256)"
    ],
    tokenAddress,
    signer
  );

  let ownerValue = "owner() not available";
  try {
    const ownable = await hre.ethers.getContractAt(["function owner() view returns (address)"], tokenAddress, signer);
    ownerValue = await ownable.owner();
  } catch (error) {
    ownerValue = "owner() not available";
  }

  const name = await erc20.name();
  const symbol = await erc20.symbol();
  const decimals = await erc20.decimals();
  const balance = await erc20.balanceOf(signerAddress);

  console.log("Token:", tokenAddress);
  console.log("Signer:", signerAddress);
  console.log("Name:", name);
  console.log("Symbol:", symbol);
  console.log("Decimals:", decimals.toString());
  console.log("Signer balance:", hre.ethers.formatUnits(balance, decimals), symbol);
  console.log("Owner:", ownerValue);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
