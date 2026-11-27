package ru.epbloger.zaberi

import org.junit.Assert.*
import org.junit.Test

class ShareImportTest {
    @Test fun textShareRetainsNameStoreLinkAndExplicitRublePrice(){
        val input="Наушники беспроводные\nЦена: 1\u00a0299,50 ₽\nhttps://www.ozon.ru/product/headphones-123/?utm_source=share"
        val seed=sharedProductSeed(input)
        assertEquals("Наушники беспроводные",seed.name)
        assertEquals("Ozon",seed.store)
        assertEquals(129950L,seed.price)
        assertEquals("https://www.ozon.ru/product/headphones-123/?utm_source=share",seed.link)
        assertEquals("https://www.ozon.ru/product/headphones-123/",publicProductUrl(seed.link,seed.store)?.toString())
    }
    @Test fun priceIsNeverGuessedFromProductIdOrForeignCurrency(){
        assertNull(sharedProductSeed("Товар 1234\nhttps://www.wildberries.ru/catalog/1234/detail.aspx").price)
        assertNull(sharedProductSeed("Товар\n15 USD\nhttps://www.ozon.ru/product/item-123/").price)
        assertNull(sharedProductSeed("Товар\nот 999 ₽\nhttps://www.ozon.ru/product/item-123/").price)
        assertNull(sharedProductSeed("Товар\n999 ₽ — 1299 ₽").price)
        assertNull(sharedProductSeed("Товар\n999 ₽ вместо 1299 ₽").price)
        assertNull(sharedProductSeed("Товар\n-100 ₽").price)
        assertNull(sharedProductSeed("Товар\n− 100 ₽").price)
        assertNull(sharedProductSeed("Товар\n12 34 ₽").price)
        assertNull(sharedProductSeed("Товар\n1.234,50 ₽").price)
        assertEquals(123450L,sharedProductSeed("Товар\n1 234,50 ₽").price)
    }
    @Test fun onlyKnownPublicProductRoutesCanBeRequested(){
        assertNotNull(publicProductUrl("https://ozon.ru/t/example","Ozon"))
        assertNotNull(publicProductUrl("https://www.wildberries.ru/catalog/123/detail.aspx?foo=secret","Wildberries"))
        assertNotNull(publicProductUrl("https://market.yandex.ru/product--item/123","Яндекс Маркет"))
        assertNull(publicProductUrl("http://ozon.ru/product/item-123","Ozon"))
        assertNull(publicProductUrl("https://ozon.ru.evil.test/product/item-123","Ozon"))
        assertNull(publicProductUrl("https://user:pass@ozon.ru/product/item-123","Ozon"))
        assertNull(publicProductUrl("https://ozon.ru:8443/product/item-123","Ozon"))
        assertNull(publicProductUrl("https://ozon.ru/my/order/123","Ozon"))
        assertNull(publicProductUrl("https://127.0.0.1/product/item-123","Ozon"))
        assertNull(publicImageUrl("https://example.com/image.jpg","Ozon"))
        assertNull(publicImageUrl("https://ozone.ru.evil.test/image.jpg","Ozon"))
        assertNotNull(publicImageUrl("https://ir.ozone.ru/s3/multimedia-a/image.jpg","Ozon"))
    }
}
