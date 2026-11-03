package com.sportivtv.iptv.data

import com.sportivtv.iptv.model.EpgProgram
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * XMLTV EPG parser.
 * Parses standard XMLTV format for electronic program guide data.
 *
 * XMLTV format:
 * <tv>
 *   <channel id="..."><display-name>...</display-name></channel>
 *   <programme start="20240101120000 +0000" stop="20240101130000 +0000" channel="...">
 *     <title>...</title>
 *     <desc>...</desc>
 *     <category>...</category>
 *   </programme>
 * </tv>
 */
class EpgParser {

    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)

    /**
     * Parse XMLTV XML text into EpgProgram list.
     * @param xmlText Raw XMLTV XML content
     * @return List of programs (empty if none)
     */
    fun parse(xmlText: String): List<EpgProgram> {
        if (xmlText.isBlank()) return emptyList()

        val programs = mutableListOf<EpgProgram>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlText))

        var eventType = parser.eventType
        var inProgramme = false
        var currentChannelId = ""
        var currentTitle = ""
        var currentDesc: String? = null
        var currentCategory: String? = null
        var currentStart: Long = 0
        var currentStop: Long = 0
        var currentTagName = ""

        try {
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTagName = parser.name?.lowercase() ?: ""
                        when (currentTagName) {
                            "programme" -> {
                                inProgramme = true
                                currentChannelId = parser.getAttributeValue(null, "channel") ?: ""
                                currentStart = parseXmltvDate(parser.getAttributeValue(null, "start"))
                                currentStop = parseXmltvDate(parser.getAttributeValue(null, "stop"))
                                currentTitle = ""
                                currentDesc = null
                                currentCategory = null
                            }
                        }
                    }
    
                    XmlPullParser.TEXT -> {
                        if (inProgramme) {
                            val text = parser.text?.trim() ?: ""
                            if (text.isNotEmpty()) {
                                when (currentTagName) {
                                    "title" -> currentTitle += text
                                    "desc" -> currentDesc = (currentDesc ?: "") + text
                                    "category" -> currentCategory = text
                                }
                            }
                        }
                    }
    
                    XmlPullParser.END_TAG -> {
                        val tagName = parser.name?.lowercase() ?: ""
                        if (tagName == "programme" && inProgramme) {
                            if (currentChannelId.isNotEmpty() && currentTitle.isNotEmpty() && currentStart > 0) {
                                programs.add(
                                    EpgProgram(
                                        channelId = currentChannelId,
                                        title = currentTitle.trim(),
                                        description = currentDesc?.trim()?.takeIf { it.isNotEmpty() },
                                        startTime = currentStart,
                                        endTime = if (currentStop > 0) currentStop else currentStart + 3600000,
                                        category = currentCategory
                                    )
                                )
                            }
                            inProgramme = false
                        }
                        currentTagName = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // Malformed XML should not crash EPG loading or unit tests.
            return emptyList()
        }

        return programs
    }

    /**
     * Parse XMLTV date format: "20240101120000 +0000"
     */
    private fun parseXmltvDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0
        return try {
            dateFormat.timeZone = TimeZone.getDefault()
            dateFormat.parse(dateStr.trim())?.time ?: 0
        } catch (e: Exception) {
            // Try alternative format without timezone
            try {
                val simpleFmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                simpleFmt.parse(dateStr.trim())?.time ?: 0
            } catch (e2: Exception) {
                0
            }
        }
    }

    /**
     * Get current/next programs for a channel.
     */
    fun getCurrentAndNext(programs: List<EpgProgram>, channelId: String, now: Long = System.currentTimeMillis()): Pair<EpgProgram?, EpgProgram?> {
        val channelPrograms = programs
            .filter { it.channelId == channelId }
            .sortedBy { it.startTime }

        val current = channelPrograms.find { now >= it.startTime && now < it.endTime }
        val next = channelPrograms.find { it.startTime > now }

        return Pair(current, next)
    }
}
