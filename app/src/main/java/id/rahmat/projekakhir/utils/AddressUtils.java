package id.rahmat.projekakhir.utils;

import java.util.regex.Pattern;

public final class AddressUtils {

    private static final Pattern EVM_ADDRESS = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    private AddressUtils() {
    }

    /**
     * True for a real 0x address. Blank values and the "your_..." placeholders
     * from local.properties.example are not, so unconfigured tokens stay off.
     */
    public static boolean isEvmAddress(String value) {
        return value != null && EVM_ADDRESS.matcher(value.trim()).matches();
    }
}
