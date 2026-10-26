package app.onlysearch

import org.junit.Assert.assertEquals
import org.junit.Test

class SoulverRatesTest {
    @Test fun carriesOverCachedCrypto() =
        assertEquals("USD=1;EUR=0.9;BTC=0.00001", Soulver.withCachedCrypto("USD=1;EUR=0.9", "USD=1;EUR=0.8;BTC=0.00001"))

    @Test fun freshCryptoWins() =
        assertEquals("USD=1;BTC=0.00002", Soulver.withCachedCrypto("USD=1;BTC=0.00002", "USD=1;BTC=0.00001"))

    @Test fun noCacheKeepsFiatOnly() {
        assertEquals("USD=1;EUR=0.9", Soulver.withCachedCrypto("USD=1;EUR=0.9", null))
        assertEquals("USD=1;EUR=0.9", Soulver.withCachedCrypto("USD=1;EUR=0.9", "USD=1;EUR=0.8"))
    }
}
