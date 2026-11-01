package id.rahmat.projekakhir.data.repository;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class PriceRepositoryTest {

    @Test
    public void joinsIdsIntoOneCoinGeckoQuery() {
        assertEquals("ethereum,binancecoin,tether",
                PriceRepository.joinIds(Arrays.asList("ethereum", "binancecoin", "tether")));
        assertEquals("ethereum", PriceRepository.joinIds(Collections.singletonList("ethereum")));
        assertEquals("", PriceRepository.joinIds(Collections.emptyList()));
    }
}
