package com.privacy.whatsappdecryptor.core.database

import com.privacy.whatsappdecryptor.core.database.model.WhatsAppMessageType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager

class ModernCrypt15AdapterTest {

    private lateinit var conn: Connection
    private val adapter = ModernCrypt15Adapter()

    @BeforeEach
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        conn.createStatement().use { stmt ->
            stmt.execute("CREATE TABLE jid (_id INTEGER PRIMARY KEY, raw_string TEXT, user TEXT, server TEXT)")
            stmt.execute("CREATE TABLE chat (_id INTEGER PRIMARY KEY, jid_row_id INTEGER, subject TEXT, sort_timestamp INTEGER, archived INTEGER)")
            stmt.execute("CREATE TABLE message (_id INTEGER PRIMARY KEY, chat_row_id INTEGER, sender_jid_row_id INTEGER, from_me INTEGER, key_id TEXT, timestamp INTEGER, message_type INTEGER, text_data TEXT)")

            // Insert JIDs
            stmt.execute("INSERT INTO jid VALUES (1, '12036302918237@g.us', '12036302918237', 'g.us')")
            stmt.execute("INSERT INTO jid VALUES (2, '15551234567@s.whatsapp.net', '15551234567', 's.whatsapp.net')")
            stmt.execute("INSERT INTO jid VALUES (3, '15559876543@s.whatsapp.net', '15559876543', 's.whatsapp.net')")

            // Insert Chats (1 Group, 1 Direct)
            stmt.execute("INSERT INTO chat VALUES (10, 1, 'Project Alpha', 1773646800000, 0)")
            stmt.execute("INSERT INTO chat VALUES (20, 2, NULL, 1773646900000, 0)")

            // Insert Messages
            stmt.execute("INSERT INTO message VALUES (101, 10, 3, 0, 'KEY1', 1773646700000, 0, 'First group message')")
            stmt.execute("INSERT INTO message VALUES (102, 10, NULL, 1, 'KEY2', 1773646750000, 0, 'My reply in group')")
            stmt.execute("INSERT INTO message VALUES (103, 10, 3, 0, 'KEY3', 1773646800000, 1, 'Check this diagram')") // Image
            stmt.execute("INSERT INTO message VALUES (201, 20, NULL, 0, 'KEY4', 1773646900000, 0, 'Direct message text')")
        }
    }

    @AfterEach
    fun tearDown() {
        conn.close()
    }

    @Test
    fun `test canHandle detects modern schema`() {
        val tables = setOf("chat", "message", "jid")
        val cols = setOf("text_data", "chat_row_id", "timestamp")
        assertTrue(adapter.canHandle(tables, cols))

        assertFalse(adapter.canHandle(setOf("chat"), cols))
        assertFalse(adapter.canHandle(tables, setOf("data"))) // Legacy schema with 'data'
    }

    @Test
    fun `test listChats returns groups and DMs with counts`() {
        val chats = adapter.listChats(conn)
        assertEquals(2, chats.size)

        val directChat = chats[0] // Latest sort_timestamp
        assertEquals(20L, directChat.id)
        assertEquals("+15551234567", directChat.title)
        assertFalse(directChat.isGroup)
        assertEquals(1L, adapter.countMessages(conn, directChat.id))

        val groupChat = chats[1]
        assertEquals(10L, groupChat.id)
        assertEquals("Project Alpha", groupChat.title)
        assertTrue(groupChat.isGroup)
        assertEquals(3L, adapter.countMessages(conn, groupChat.id))
    }

    @Test
    fun `test listChats with onlyGroups filter`() {
        val groups = adapter.listChats(conn, onlyGroups = true)
        assertEquals(1, groups.size)
        assertEquals("Project Alpha", groups[0].title)
    }

    @Test
    fun `test getChatMessages maps senders and non-text types`() {
        val messages = adapter.getChatMessages(conn, 10L)
        assertEquals(3, messages.size)

        val msg1 = messages[0]
        assertEquals("First group message", msg1.text)
        assertFalse(msg1.isFromMe)
        assertEquals("+15559876543", msg1.sender.displayLabel)
        assertEquals(WhatsAppMessageType.TEXT, msg1.messageType)

        val msg2 = messages[1]
        assertEquals("My reply in group", msg2.text)
        assertTrue(msg2.isFromMe)
        assertEquals("You", msg2.sender.displayLabel)

        val msg3 = messages[2]
        assertEquals("Check this diagram", msg3.text)
        assertEquals(WhatsAppMessageType.IMAGE, msg3.messageType)
        assertEquals("[Photo]", msg3.metadataSummary)
    }

    @Test
    fun `test searchMessages finds matching text`() {
        val found = adapter.searchMessages(conn, "diagram")
        assertEquals(1, found.size)
        assertEquals(103L, found[0].id)
    }
}
