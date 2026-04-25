import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun localProperty(name: String, defaultValue: String = ""): String {
    return localProperties.getProperty(name, defaultValue)
}

fun localIntProperty(name: String, defaultValue: Int = 18): String {
    return localProperties.getProperty(name)?.trim()?.toIntOrNull()?.toString()
        ?: defaultValue.toString()
}

android {
    namespace = "id.rahmat.projekakhir"
    compileSdk = 36

    defaultConfig {
        applicationId = "id.rahmat.projekakhir"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "DEFAULT_ETH_NETWORK", "\"${localProperty("DEFAULT_ETH_NETWORK", "sepolia")}\"")
        buildConfigField("String", "COINGECKO_BASE_URL", "\"https://api.coingecko.com/api/v3/\"")
        buildConfigField("String", "ETHERSCAN_MAINNET_BASE_URL", "\"https://api.etherscan.io/api/\"")
        buildConfigField("String", "ETHERSCAN_SEPOLIA_BASE_URL", "\"https://api-sepolia.etherscan.io/api/\"")
        buildConfigField("String", "INFURA_PROJECT_ID", "\"${localProperty("INFURA_PROJECT_ID")}\"")
        buildConfigField("String", "BSC_RPC_URL", "\"${localProperty("BSC_RPC_URL")}\"")
        buildConfigField("String", "AVALANCHE_RPC_URL", "\"${localProperty("AVALANCHE_RPC_URL")}\"")
        buildConfigField("String", "POLYGON_RPC_URL", "\"${localProperty("POLYGON_RPC_URL")}\"")
        buildConfigField("String", "ARBITRUM_RPC_URL", "\"${localProperty("ARBITRUM_RPC_URL")}\"")
        buildConfigField("String", "OPTIMISM_RPC_URL", "\"${localProperty("OPTIMISM_RPC_URL")}\"")
        buildConfigField("String", "BASE_RPC_URL", "\"${localProperty("BASE_RPC_URL")}\"")
        buildConfigField("String", "FANTOM_RPC_URL", "\"${localProperty("FANTOM_RPC_URL")}\"")
        buildConfigField("String", "ETHERSCAN_API_KEY", "\"${localProperty("ETHERSCAN_API_KEY")}\"")
        buildConfigField("String", "MATS_TOKEN_ADDRESS", "\"${localProperty("MATS_TOKEN_ADDRESS")}\"")
        buildConfigField("String", "MATS_SWAP_POOL_ADDRESS", "\"${localProperty("MATS_SWAP_POOL_ADDRESS")}\"")
        buildConfigField("String", "IDRX_TOKEN_ADDRESS", "\"${localProperty("IDRX_TOKEN_ADDRESS")}\"")
        buildConfigField("String", "IDRX_SWAP_POOL_ADDRESS", "\"${localProperty("IDRX_SWAP_POOL_ADDRESS")}\"")
        for (index in 1..5) {
            buildConfigField("String", "SWAP_TOKEN_${index}_NAME", "\"${localProperty("SWAP_TOKEN_${index}_NAME")}\"")
            buildConfigField("String", "SWAP_TOKEN_${index}_SYMBOL", "\"${localProperty("SWAP_TOKEN_${index}_SYMBOL")}\"")
            buildConfigField("String", "SWAP_TOKEN_${index}_ADDRESS", "\"${localProperty("SWAP_TOKEN_${index}_ADDRESS")}\"")
            buildConfigField("String", "SWAP_TOKEN_${index}_POOL_ADDRESS", "\"${localProperty("SWAP_TOKEN_${index}_POOL_ADDRESS")}\"")
            buildConfigField("int", "SWAP_TOKEN_${index}_DECIMALS", localIntProperty("SWAP_TOKEN_${index}_DECIMALS"))
        }
        buildConfigField("String", "MIDTRANS_PAYMENT_URL", "\"${localProperty("MIDTRANS_PAYMENT_URL")}\"")
        buildConfigField("String", "BUY_BACKEND_BASE_URL", "\"${localProperty("BUY_BACKEND_BASE_URL")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    implementation(libs.core)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.fragment)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.viewpager2)
    implementation(libs.swiperefreshlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.security.crypto)
    implementation(libs.biometric)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.gson)
    implementation(libs.web3j)
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)
    implementation(libs.lottie)
    implementation(libs.mpandroidchart)
    implementation(libs.zxing.android.embedded)
    implementation(libs.splashscreen)
    implementation(libs.browser)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
