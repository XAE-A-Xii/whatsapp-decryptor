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

    @Test
    fun testSingleLineAdWithM3MCapital() {
        val msg = "👑 M3M CAPITAL Sector -113 Size-1665 sqft, 3br+Study, Tower-1B Book Value -10300/- @16500/ all inclusive"
        val rawMsg = RawMessage(
            timestampMs = 1700000000000L,
            senderName = "9560414992@s.whatsapp.net",
            text = msg
        )
        val listings = PropertyListingExtractor.extractListings(sequenceOf(rawMsg))
        assertEquals(1, listings.size)
        val listing = listings[0]
        assertEquals("M3M CAPITAL", listing.projectOrSociety)
        assertEquals("3", listing.bhk)
        assertEquals("1665 sq.ft", listing.size)

        val dealerRow = InventoryDeduplicator.toImportantDealerRow(listing)
        assertEquals("M3M CAPITAL", dealerRow.society)
        assertEquals("IN", dealerRow.projectListStatus)
        assertEquals("113", dealerRow.sec)
        assertEquals("1665", dealerRow.area)
        assertEquals("3BHK + STUDY", dealerRow.acco)
        assertEquals("T 1B", dealerRow.flatNo)
        assertEquals("@ 16500", dealerRow.price)
    }

    @Test
    fun testSingleLineAdWithEmbeddedProject() {
        val msg = "Available for sale in M3m Capital 3 bhk 1665 outer facing middle floor @ 15600 paid 90% call Ghanshyam 98100 72530"
        val rawMsg = RawMessage(
            timestampMs = 1700000000000L,
            senderName = "Ghanshyam",
            text = msg
        )
        val listings = PropertyListingExtractor.extractListings(sequenceOf(rawMsg))
        assertEquals(1, listings.size)
        val dealerRow = InventoryDeduplicator.toImportantDealerRow(listings[0])
        assertEquals("M3M CAPITAL", dealerRow.society)
        assertEquals("IN", dealerRow.projectListStatus)
        assertEquals("MIDDLE", dealerRow.floor)
        assertEquals("9810072530", dealerRow.phoneNo)
        assertEquals("@ 15600", dealerRow.price)
    }

    @Test
    fun testDecimalBhkAndStudyServant() {
        val msg = "2.5 bhk in Puri Diplomatic Sector 111 with servant and study 1800 sqft asking 2.85 cr"
        val rawMsg = RawMessage(
            timestampMs = 1700000000000L,
            senderName = "+91 99999 88888",
            text = msg
        )
        val listings = PropertyListingExtractor.extractListings(sequenceOf(rawMsg))
        assertEquals(1, listings.size)
        val dealerRow = InventoryDeduplicator.toImportantDealerRow(listings[0])
        assertEquals("PURI DIPLOMACTIC", dealerRow.society)
        assertEquals("IN", dealerRow.projectListStatus)
        assertEquals("2.5BHK + SR + STUDY", dealerRow.acco)
        assertEquals("2.85 CR", dealerRow.price)
    }

    @Test
    fun testNonCanonicalProjectOutStatus() {
        val msg = "SS Palladians, ground floor , A55, 3bhk , 305 Sqyd @ 2.75cr"
        val rawMsg = RawMessage(
            timestampMs = 1700000000000L,
            senderName = "Broker",
            text = msg
        )
        val listings = PropertyListingExtractor.extractListings(sequenceOf(rawMsg))
        assertEquals(1, listings.size)
        val dealerRow = InventoryDeduplicator.toImportantDealerRow(listings[0])
        assertTrue(dealerRow.society.contains("SS Palladians", ignoreCase = true))
        assertEquals("OUT", dealerRow.projectListStatus)
        assertEquals("GROUND", dealerRow.floor)
        assertEquals("2.75 CR", dealerRow.price)
    }

    @Test
    fun testDealerNameExtractionSignatures() {
        // Alok Rathour 7217662235
        val msg1 = "Pink apartment sec 18 DDA\n1st floor\nD. 1.58\nAlok Rathour 7217662235"
        assertEquals("Alok Rathour", PropertyListingExtractor.extractDealerName(msg1))

        // Neeraj Arora right above phone
        val msg2 = "👑 Arjun Apartment sector 7\n3bhk Demand - 2.90cr\n*Neeraj Arora*\n*9891902115*"
        assertEquals("Neeraj Arora", PropertyListingExtractor.extractDealerName(msg2))

        // call Ghanshyam
        val msg3 = "M3M Crown 3bhk 1600 sqft asking 2.10 cr call Ghanshyam 98100 72530"
        assertEquals("Ghanshyam", PropertyListingExtractor.extractDealerName(msg3))

        // ALTAF with phone emoji
        val msg4 = "Available for sale Puri Diplomatic 3bhk ALTAF ☎️ 9811962572"
        assertEquals("Altaf", PropertyListingExtractor.extractDealerName(msg4))

        // Agency pattern
        val msg5 = "Available flat 2bhk\n*🏠Goodwill Associate & interiors🏠*\nDwarka New Delhi"
        assertEquals("Goodwill Associate & Interiors", PropertyListingExtractor.extractDealerName(msg5))

        // Phone then name
        val msg6 = "3bhk flat for sale\n📱9821594002 Jaivir"
        assertEquals("Jaivir", PropertyListingExtractor.extractDealerName(msg6))
    }

    @Test
    fun testDealerNameBlacklist() {
        assertFalse(PropertyListingExtractor.isValidDealerName("DLF PARK PLACE"))
        assertFalse(PropertyListingExtractor.isValidDealerName("3BHK SEMI FURNISHED"))
        assertFalse(PropertyListingExtractor.isValidDealerName("Demand 2.90cr"))
        assertFalse(PropertyListingExtractor.isValidDealerName("Sector 18 DDA"))
        assertFalse(PropertyListingExtractor.isValidDealerName("187213916700684"))
    }

    @Test
    fun testLidSenderNeverDisplayedAsDealerName() {
        val msg = "Pink apartment sec 18 DDA\n1st floor 2bhk\nD. 1.58 cr\nDestination Dwarka Realstate\nAlok Rathour 7217662235"
        val rawMsg = RawMessage(
            timestampMs = 1700000000000L,
            senderName = "79482782240846@lid",
            text = msg
        )
        val listings = PropertyListingExtractor.extractListings(sequenceOf(rawMsg))
        assertEquals(1, listings.size)
        val row = InventoryDeduplicator.toImportantDealerRow(listings[0])

        // Dealer name should be the extracted human name, not the LID
        assertEquals("Alok Rathour", row.dealerName)
        assertEquals("7217662235", row.phoneNo)
        assertFalse(row.dealerName.contains("@lid"))
    }

    @Test
    fun testLidFallbackToPhoneWhenNoHumanName() {
        val msg = "M3M GOLF HILLS 79\n420 sqft studio @ 16500\nCall: 9220994908"
        val rawMsg = RawMessage(
            timestampMs = 1700000000000L,
            senderName = "187213916700684@lid",
            text = msg
        )
        val listings = PropertyListingExtractor.extractListings(sequenceOf(rawMsg))
        assertEquals(1, listings.size)
        val row = InventoryDeduplicator.toImportantDealerRow(listings[0])

        // When no human name exists, use the clean 10-digit phone, NEVER the raw LID
        assertEquals("9220994908", row.dealerName)
        assertEquals("9220994908", row.phoneNo)
        assertFalse(row.dealerName.contains("187213916700684"))
    }
}
