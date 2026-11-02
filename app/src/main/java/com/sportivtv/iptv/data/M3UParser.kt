package com.sportivtv.iptv.data

import com.sportivtv.iptv.model.Channel
import com.sportivtv.iptv.model.ChannelCategory

/**
 * M3U/M3U8 playlist parser.
 * Parses IPTV playlists in standard #EXTM3U format.
 *
 * Supports:
 * - #EXTINF metadata (tvg-id, tvg-name, tvg-logo, group-title)
 * - Stream URLs (http, https, rtmp, rtsp, udp)
 * - Auto-categorization based on name/group keywords
 *
 * Does NOT invent or fabricate channels. Only parses real data from the playlist.
 */
class M3UParser {

    /**
     * Parse M3U playlist text into Channel objects.
     * @param m3uText Raw M3U file content
     * @param subscriptionId The subscription these channels belong to
     * @return List of parsed channels (empty if no valid entries)
     */
    fun parse(m3uText: String, subscriptionId: Long): List<Channel> {
        if (m3uText.isBlank()) return emptyList()

        val lines = m3uText.lines()
        val channels = mutableListOf<Channel>()

        // Verify M3U header
        if (lines.isNotEmpty() && !lines[0].trim().startsWith("#EXTM3U")) {
            // Not a standard M3U file; try to parse anyway
        }

        var currentName = ""
        var currentLogo: String? = null
        var currentTvgId: String? = null
        var currentGroup: String? = null
        var currentTvgName: String? = null

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTINF") -> {
                    // Parse #EXTINF line: #EXTINF:-1 tvg-id="..." tvg-name="..." tvg-logo="..." group-title="...",Channel Name
                    val extInfData = parseExtInf(line)
                    currentName = extInfData.name
                    currentLogo = extInfData.logo
                    currentTvgId = extInfData.tvgId
                    currentTvgName = extInfData.tvgName
                    currentGroup = extInfData.groupTitle
                }

                line.startsWith("#EXTGRP:") -> {
                    // Some playlists use #EXTGRP for group
                    currentGroup = line.removePrefix("#EXTGRP:").trim()
                }

                !line.startsWith("#") -> {
                    // This is a URL line (stream URL)
                    if (line.startsWith("http://") || line.startsWith("https://") ||
                        line.startsWith("rtmp://") || line.startsWith("rtsp://") ||
                        line.startsWith("udp://") || line.startsWith("rtp://")
                    ) {
                        if (currentName.isNotBlank()) {
                            val category = Channel.categorize(currentName, currentGroup)
                            channels.add(
                                Channel(
                                    id = currentTvgId ?: "ch_${channels.size}",
                                    name = currentName.trim(),
                                    logoUrl = currentLogo,
                                    streamUrl = line,
                                    groupTitle = currentGroup,
                                    category = category,
                                    epgChannelId = currentTvgId,
                                    subscriptionId = subscriptionId
                                )
                            )
                        }
                        // Reset for next entry
                        currentName = ""
                        currentLogo = null
                        currentTvgId = null
                        currentTvgName = null
                        currentGroup = null
                    }
                }
            }
        }

        return channels
    }

    /**
     * Parse a single #EXTINF line.
     */
    private fun parseExtInf(line: String): ExtInfData {
        // Remove #EXTINF: prefix and duration
        val commaIndex = line.indexOf(',')
        val metadataPart = if (commaIndex >= 0) line.substring(0, commaIndex) else line
        val namePart = if (commaIndex >= 0) line.substring(commaIndex + 1).trim() else ""

        val name = namePart

        // Extract attributes: tvg-id="value"
        val tvgId = extractAttribute(metadataPart, "tvg-id")
        val tvgName = extractAttribute(metadataPart, "tvg-name")
        val tvgLogo = extractAttribute(metadataPart, "tvg-logo")
        val groupTitle = extractAttribute(metadataPart, "group-title")

        return ExtInfData(
            name = name,
            tvgId = tvgId,
            tvgName = tvgName,
            logo = tvgLogo,
            groupTitle = groupTitle
        )
    }

    /**
     * Extract attribute value from metadata string.
     * Pattern: attrName="value"
     */
    private fun extractAttribute(text: String, attrName: String): String? {
        val pattern = "$attrName=\""
        val start = text.indexOf(pattern)
        if (start < 0) return null
        val valueStart = start + pattern.length
        val valueEnd = text.indexOf('"', valueStart)
        if (valueEnd < 0) return null
        return text.substring(valueStart, valueEnd).takeIf { it.isNotBlank() }
    }

    private data class ExtInfData(
        val name: String,
        val tvgId: String?,
        val tvgName: String?,
        val logo: String?,
        val groupTitle: String?
    )
}
