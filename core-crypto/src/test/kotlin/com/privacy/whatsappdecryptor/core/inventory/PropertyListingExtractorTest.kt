package com.privacy.whatsappdecryptor.core.inventory

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class PropertyListingExtractorTest {

    @Test
    fun testRentInLakhsStaysRent() {
        val text = "DLF Park Place, Sector 54\n1983 sq.ft. | 3BHK\n1.80L + M | Fully Furnished"
        val money = PropertyListingExtractor.money(text, "Rent")
        assertTrue(money.sale.isEmpty(), "No sale price expected")
        assertEquals(listOf(180000.0), money.rent)
    }

    @Test
    fun testPerUnitRateIsNotSalePrice() {
        val text = "Mapsko Aspr Hills Sec 78\nPlot Size 150 Sq.yd\nPrice 2.32 lac Sq.yd slightly Nego"
        val money = PropertyListingExtractor.money(text, "Sale")
        assertTrue(money.sale.isEmpty(), "2.32 lac/sq.yd is a rate, not total sale price")
        assertEquals(listOf("2.32 L/unit"), money.rates)
    }

    @Test
    fun testCrorePricesReadInOrder() {
        val text = "Size - 1585 SF\nBooks-1.58 Cr\nAsking - 1.90 Cr"
        val money = PropertyListingExtractor.money(text, "Sale")
        assertEquals(listOf(1.58, 1.9), money.sale)
    }

    @Test
    fun testProjectExtraction() {
        val text = "*DLF Garden City Enclave-93*\nBlock-D1\nFloor - 4th\nSize - 1585 SF"
        val project = PropertyListingExtractor.project(text)
        assertEquals("DLF Garden City Enclave-93", project)
    }

    @Test
    fun testBoldUnicodeFontNormalisation() {
        val text = "𝐒𝐔𝐍𝐂𝐈𝐓𝐘 𝐏𝐋𝐀𝐓𝐈𝐍𝐔𝐌 𝐓𝐎𝐖𝐄𝐑𝐒\n𝟑𝐁𝐇𝐊 (𝟑𝟏𝟓𝟎SQFT)\n𝐀𝐒𝐊𝐈𝐍𝐆 𝟒.𝟓 𝐂𝐑"
        val rawMsg = RawMessage(
            timestampMs = System.currentTimeMillis(),
            senderName = "+91 93132 18887",
            text = text
        )
        val listings = PropertyListingExtractor.extractListings(sequenceOf(rawMsg))
        assertEquals(1, listings.size)
        assertEquals("3", listings[0].bhk)
        assertEquals("3150 sq.ft", listings[0].size)
        assertEquals("4.5", listings[0].salePriceCr)
    }

    @Test
    fun testCanonicalProjectMatching() {
        assertEquals("SMART WORLD DXP", ProjectRegistry.canonicalSelectedProject("Smartworld DXP sec 113"))
        assertEquals("M3M WOODSHIRE", ProjectRegistry.canonicalSelectedProject("M3M woodshire sector 107"))
        assertEquals("PURI DIPLOMACTIC", ProjectRegistry.canonicalSelectedProject("Puri Diplomatic Residences"))
        assertEquals("ENGIMA", ProjectRegistry.canonicalSelectedProject("Enigma sector 110"))
    }

    @Test
    fun testCsvWriterWith12Columns() {
        val row = ImportantDealerRow(
            society = "SMART WORLD DXP",
            projectListStatus = "IN",
            sec = "113",
            area = "1850",
            acco = "3BHK",
            floor = "4",
            flatNo = "402",
            dealerName = "Rahul Sharma",
            phoneNo = "9811000000",
            price = "2.5 CR",
            fullMessage = "Smart World DXP\nFloor 4\nCall now",
            isDuplicate = false
        )

        val out = ByteArrayOutputStream()
        InventoryCsvWriter.writeCsv(listOf(row), out)
        val csv = out.toString(StandardCharsets.UTF_8)

        // Must start with UTF-8 BOM
        assertTrue(csv.startsWith("\uFEFF"))

        // Header must match the 12 columns
        val expectedHeader = "SOCIETY,PROJECT LIST STATUS,SEC ,AREA ,ACCO,FLOOR,FLAT NO,DEALER NAME ,PHONE NO,PRICE,FULL MESSAGE,is_duplicate"
        assertTrue(csv.contains(expectedHeader))

        // Message newlines must be converted to " | "
        assertTrue(csv.contains("Smart World DXP | Floor 4 | Call now"))
        assertTrue(csv.contains("Rahul Sharma"))
        assertTrue(csv.contains("9811000000"))
        assertTrue(csv.contains("false"))
    }
}
