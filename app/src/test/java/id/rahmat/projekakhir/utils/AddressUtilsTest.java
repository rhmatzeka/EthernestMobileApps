package id.rahmat.projekakhir.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AddressUtilsTest {

    @Test
    public void acceptsChecksummedAndLowercaseAddresses() {
        assertTrue(AddressUtils.isEvmAddress("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"));
        assertTrue(AddressUtils.isEvmAddress(" 0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed "));
    }

    @Test
    public void rejectsBlankPlaceholderAndMalformedValues() {
        assertFalse(AddressUtils.isEvmAddress(null));
        assertFalse(AddressUtils.isEvmAddress(""));
        assertFalse(AddressUtils.isEvmAddress("your_mats_token_address"));
        assertFalse(AddressUtils.isEvmAddress("0x1234"));
        assertFalse(AddressUtils.isEvmAddress("5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"));
        assertFalse(AddressUtils.isEvmAddress("0xZZAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"));
    }
}
