package com.miios.launcher.tv

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class Programme(val start: Long, val stop: Long, val title: String)

/** What's on: programmes per channel, already trimmed to the next few hours. */
class Guide(private val byChannel: Map<String, List<Programme>>) {

    /** The programme on now and the one after it. */
    fun nowAndNext(channel: TvChannel, at: Long): Pair<Programme?, Programme?> {
        val list = listOf(channel.tvgId, channel.tvgName, channel.name)
            .firstNotNullOfOrNull { if (it.isBlank()) null else byChannel[key(it)] }
            ?: return null to null
        val i = list.indexOfFirst { it.stop > at }
        if (i < 0) return null to null
        val now = list[i].takeIf { it.start <= at }
        return now to (if (now != null) list.getOrNull(i + 1) else list[i])
    }

    companion object {
        val Empty = Guide(emptyMap())
        fun key(s: String) = s.trim().lowercase()
    }
}

/**
 * Streams an XMLTV guide. Full guides can be tens of megabytes, so only the playlist's channels
 * and programmes inside [from]..[to] are kept, which keeps memory small on the box's 2 GB.
 */
object Xmltv {
    private val timeFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    fun parse(input: InputStream, channels: List<TvChannel>, from: Long, to: Long): Guide {
        val wanted = HashSet<String>()
        for (c in channels) for (k in listOf(c.tvgId, c.tvgName, c.name)) if (k.isNotBlank()) wanted += Guide.key(k)

        // guide channel id -> the playlist keys it matches (by id or by display name)
        val aliases = HashMap<String, Set<String>>()
        val result = HashMap<String, MutableList<Programme>>()

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> readChannel(parser, wanted, aliases)
                    "programme" -> readProgramme(parser, wanted, aliases, from, to, result)
                }
            }
            event = parser.next()
        }
        for (list in result.values) list.sortBy { it.start }
        return Guide(result)
    }

    private fun readChannel(parser: XmlPullParser, wanted: Set<String>, aliases: MutableMap<String, Set<String>>) {
        val id = Guide.key(parser.getAttributeValue(null, "id") ?: "")
        val keys = mutableSetOf(id)
        val depth = parser.depth
        while (!(parser.next() == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) return
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "display-name") keys += Guide.key(parser.nextText())
        }
        val matched = keys.filterTo(HashSet()) { it in wanted }
        if (matched.isNotEmpty()) aliases[id] = matched
    }

    private fun readProgramme(
        parser: XmlPullParser,
        wanted: Set<String>,
        aliases: Map<String, Set<String>>,
        from: Long,
        to: Long,
        result: MutableMap<String, MutableList<Programme>>,
    ) {
        val id = Guide.key(parser.getAttributeValue(null, "channel") ?: "")
        val targets = aliases[id] ?: if (id in wanted) setOf(id) else null
        val start = time(parser.getAttributeValue(null, "start"))
        val stop = time(parser.getAttributeValue(null, "stop"))
        val keep = targets != null && start != null && stop != null && stop > from && start < to
        var title = ""
        val depth = parser.depth
        while (!(parser.next() == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) return
            if (keep && title.isEmpty() && parser.eventType == XmlPullParser.START_TAG && parser.name == "title") {
                title = parser.nextText().trim()
            }
        }
        if (!keep) return
        val programme = Programme(start!!, stop!!, title)
        for (t in targets!!) result.getOrPut(t) { ArrayList() } += programme
    }

    /** XMLTV times look like "20261021200000 +0300". */
    private fun time(s: String?): Long? {
        if (s == null || s.length < 14) return null
        val local = runCatching { LocalDateTime.parse(s.substring(0, 14), timeFormat) }.getOrNull() ?: return null
        val offset = runCatching { ZoneOffset.of(s.substring(14).trim()) }.getOrDefault(ZoneOffset.UTC)
        return local.toInstant(offset).toEpochMilli()
    }
}
