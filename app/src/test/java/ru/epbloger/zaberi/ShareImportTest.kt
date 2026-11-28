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
    @Test fun explicitlyLabeledRegularPriceWinsOverCardPrice(){
        val url="https://www.ozon.ru/product/item-123/"
        assertEquals(129900L,sharedProductSeed("Чайник\n999 ₽ с картой\n1 299 ₽ без карты\n2 000 ₽ старая цена\n"+url).price)
        assertEquals(129900L,ordinaryPriceFromText("Обычная цена: 1 299 ₽"))
        assertEquals(129900L,ordinaryPriceFromText("Без карты Яндекс Пэй\n1 299 ₽"))
        assertNull(ordinaryPriceFromText("999 ₽ с картой\n2 000 ₽ старая цена"))
        assertNull(ordinaryPriceFromText("без карты от 100 ₽"))
        assertEquals(129900L,ordinaryPriceFromText("1 299 ₽ БЕЗ КАРТЫ"))
        assertNull(ordinaryPriceFromText("необычная цена 100 ₽"))
        assertNull(ordinaryPriceFromText("100 ₽ без карты\n200 ₽ без карты"))
        assertEquals(188200L,ordinaryPriceFromText("1\u2009882\u2009₽\nС другими банками"))
        assertNull(ordinaryPriceFromText("1\u2009882\u2009₽ 3\u2009282\u2009₽\nС другими банками"))
        assertNull(ordinaryPriceFromText("100 ₽ без Сплита"))
    }
    @Test fun conditionalSharePriceCannotMasqueradeAsRegular(){
        assertNull(sharedProductSeed("Чайник\n999 ₽ с картой\nhttps://www.ozon.ru/product/item-123/").price)
        assertNull(sharedProductSeed("Чайник\n999 ₽ с кошельком\nhttps://www.wildberries.ru/catalog/123/detail.aspx").price)
        assertEquals(129900L,sharedProductSeed("Чайник\n1 299 ₽ без карты\nhttps://www.ozon.ru/product/item-123/").price)
    }
    @Test fun scopedCurrentPriceIsNotConfusedWithOtherAmounts(){
        assertEquals(129900L,scopedProductPrice("1 299 ₽"))
        assertEquals(129900L,scopedProductPrice("999 ₽ с картой\n1 299 ₽ без Ozon Карты"))
        assertEquals(129900L,scopedProductPrice("1 299 руб. с любой картой"))
        assertNull(scopedProductPrice("999 ₽ с картой"))
        assertNull(scopedProductPrice("от 999 ₽"))
        assertNull(scopedProductPrice("999 ₽ в месяц"))
        assertNull(scopedProductPrice("999 ₽\n1 299 ₽"))
        assertNull(scopedProductPrice("999 ₽ доставка"))
        assertNull(scopedProductPrice("1 299 ₽ старая цена"))
        assertEquals("arrived",quickOrderEvent(Status.TRANSIT))
        assertEquals("received",quickOrderEvent(Status.READY))
        assertEquals("keep",quickOrderEvent(Status.RECEIVED))
        assertNull(quickOrderEvent(Status.REFUND))
        assertNull(quickOrderEvent(Status.RETURN_PLANNED))
        assertNull(quickOrderEvent(Status.DONE))
        assertNull(quickOrderEvent(Status.CANCELLED))
    }
    @Test fun incomingTextAndSubjectAreBoundedBeforeParsing(){
        val url="https://www.ozon.ru/product/item-123/"
        assertEquals("Чайник\n"+url,combinedSharedText(url,"Чайник"))
        assertEquals("Чайник",sharedProductSeed(combinedSharedText(url,"Чайник")).name)
        assertEquals(url,sharedProductSeed(combinedSharedText(url,"Чайник")).link)
        assertEquals(8192,combinedSharedText("A".repeat(100000),"").length)
        assertEquals(8192,combinedSharedText(url+"\n"+" ".repeat(100000),"B".repeat(100000)).length)
        assertNull(sharedProductSeed("A".repeat(8192)+"\n100 ₽").price)
    }
    @Test fun picksProductLinkBeforeUnrelatedLink(){
        val seed=sharedProductSeed("https://example.org\nhttps://www.wildberries.ru/catalog/330535596/detail.aspx")
        assertEquals("Wildberries",seed.store)
        assertEquals("https://www.wildberries.ru/catalog/330535596/detail.aspx",seed.link)
        assertEquals("https://www.wildberries.ru/catalog/330535596/detail.aspx",publicProductUrl("https://www.wildberries.ru/catalog/330535596","Wildberries")?.toString())
    }
    @Test fun everyAdvertisedStoreHasAnImportRouteAndPhotoHost(){
        val routes=listOf(
            Triple("Ozon","https://ozon.ru/product/item-123/","https://ir.ozone.ru/s3/first.jpg"),
            Triple("Wildberries","https://www.wildberries.ru/catalog/123/detail.aspx","https://basket-01.wbbasket.ru/vol0/part0/123/images/big/1.webp"),
            Triple("Яндекс Маркет","https://market.yandex.ru/card/item/103683837950","https://avatars.mds.yandex.net/get-mpic/123/orig"),
            Triple("Мегамаркет","https://megamarket.ru/catalog/details/item-123/","https://main-cdn.sbermegamarket.ru/big2/first.jpg"),
            Triple("Авито","https://www.avito.ru/moskva/telefony/item_123","https://images.avito.st/first.jpg"),
            Triple("AliExpress","https://sl.aliexpress.ru/p/key/abc","https://ae-pic-a1.aliexpress-media.com/kf/first.jpg"),
            Triple("Lamoda","https://www.lamoda.ru/p/mp002xm0abcd/","https://a.lmcdn.ru/product/first.jpg"),
            Triple("DNS","https://www.dns-shop.ru/product/1234abcd/item/","https://c.dns-shop.ru/thumb/st1/first.jpg"))
        for((store,link,image) in routes){
            assertEquals(store,sharedProductSeed(link).store)
            assertNotNull(store,publicProductUrl(link,store))
            assertNotNull(store,publicImageUrl(image,store))
            assertNull(store,publicImageUrl("https://evil.test/"+image,store))
        }
        assertNotNull(publicProductUrl("https://a.aliexpress.com/_abc","AliExpress"))
        assertNotNull(publicProductUrl("https://aliexpress.ru/item/1005001234567890.html","AliExpress"))
        assertEquals("abc",publicProductUrl("https://sl.aliexpress.ru/p?key=abc&token=secret","AliExpress")?.queryParameter("key"))
        assertNull(publicProductUrl("https://sl.aliexpress.ru/p?token=secret","AliExpress"))
        val url=publicProductUrl("https://market.yandex.ru/card/item/123?sku=321&do-waremd5=abc_123&token=secret&offerid=xyz","Яндекс Маркет")!!
        assertEquals("321",url.queryParameter("sku"))
        assertEquals("abc_123",url.queryParameter("do-waremd5"))
        assertNull(url.queryParameter("token"))
        assertNull(publicProductUrl("https://www.dns-shop.ru/personal/orders/","DNS"))
        assertNull(publicProductUrl("https://sl.aliexpress.ru/account/","AliExpress"))
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
