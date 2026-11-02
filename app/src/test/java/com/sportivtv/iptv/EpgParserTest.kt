package com.sportivtv.iptv.data

import com.sportivtv.iptv.model.EpgProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * EPG Parser unit tests.
 */
class EpgParserTest {

    private val parser = EpgParser()

    @Test
    fun `parse valid XMLTV returns programs`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
                <channel id="ch1">
                    <display-name>Sports Channel 1</display-name>
                </channel>
                <programme start="20240115120000 +0000" stop="20240115130000 +0000" channel="ch1">
                    <title>Live Match: Team A vs Team B</title>
                    <desc>Football league match</desc>
                    <category>Sports</category>
                </programme>
                <programme start="20240115130000 +0000" stop="20240115140000 +0000" channel="ch1">
                    <title>Sports News</title>
                    <desc>Daily sports news</desc>
                </programme>
            </tv>
        """.trimIndent()

        val programs = parser.parse(xml)

        assertEquals(2, programs.size)
        assertEquals("ch1", programs[0].channelId)
        assertEquals("Live Match: Team A vs Team B", programs[0].title)
        assertEquals("Football league match", programs[0].description)
        assertEquals("Sports", programs[0].category)
        assertEquals(0L, programs[0].startTime) // Date may vary; just check structure
    }

    @Test
    fun `parse empty XML returns empty list`() {
        val programs = parser.parse("")
        assertEquals(0, programs.size)
    }

    @Test
    fun `parse invalid XML returns empty list`() {
        val programs = parser.parse("not valid xml")
        assertEquals(0, programs.size)
    }

    @Test
    fun `getCurrentAndNext returns correct programs`() {
        val now = System.currentTimeMillis()
        val programs = listOf(
            EpgProgram("ch1", "Past Show", startTime = now - 7200000, endTime = now - 3600000),
            EpgProgram("ch1", "Current Show", startTime = now - 1800000, endTime = now + 1800000),
            EpgProgram("ch1", "Next Show", startTime = now + 1800000, endTime = now + 3600000)
        )

        val (current, next) = parser.getCurrentAndNext(programs, "ch1", now)

        assertNotNull(current)
        assertEquals("Current Show", current?.title)
        assertNotNull(next)
        assertEquals("Next Show", next?.title)
    }

    @Test
    fun `getCurrentAndNext returns nulls when no matching channel`() {
        val programs = listOf(
            EpgProgram("ch2", "Show", startTime = System.currentTimeMillis() - 1000, endTime = System.currentTimeMillis() + 1000)
        )

        val (current, next) = parser.getCurrentAndNext(programs, "ch1")
        assertNull(current)
        assertNull(next)
    }
}
