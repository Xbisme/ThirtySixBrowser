package com.raumanian.thirtysix.browser.feature.bookmarks.data.datasource.local

import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode
import com.raumanian.thirtysix.browser.core.domain.util.BookmarkUrlValidator
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkTransferLimits
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.ParsedBookmarkFile
import java.net.IDN
import javax.inject.Inject

/**
 * Reads the Netscape bookmark file: `<DL>` opens a level, `<H3>` names the folder the next `<DL>`
 * opens, `<A HREF>` is a link. Every other tag and attribute is skipped. Broken or truncated markup
 * keeps whatever was read; no markup is ever run or fetched.
 */
class NetscapeBookmarkParser @Inject constructor() {

    /** `null` when [text] is not a bookmark file. */
    fun parse(text: String, untitledFolderName: String): ParsedBookmarkFile? =
        Scan(MarkupReader(text), untitledFolderName).run()

    private sealed interface Entry

    private class LinkEntry(val link: BookmarkTreeNode.Link) : Entry

    private class Level(val name: String, val depth: Int) : Entry {
        val children = mutableListOf<Entry>()
        val urls = HashSet<String>()

        fun build(): BookmarkTreeNode.Folder = BookmarkTreeNode.Folder(
            name = name,
            children = children.map { entry ->
                when (entry) {
                    is Level -> entry.build()
                    is LinkEntry -> entry.link
                }
            },
        )
    }

    private class Scan(private val reader: MarkupReader, private val untitledFolderName: String) {
        private val root = Level(name = "", depth = 0)
        private val stack = ArrayDeque<Level>().apply { addLast(root) }
        private var pendingFolder: Level? = null
        private var rootListSeen = false
        private var sawList = false
        private var sawLink = false
        private var links = 0
        private var folders = 0
        private var duplicates = 0
        private var unsupported = 0

        fun run(): ParsedBookmarkFile? {
            generateSequence { reader.nextTag() }.forEach(::handle)
            val declared = reader.startsWithin(DOCTYPE, BookmarkTransferLimits.SNIFF_CHARS)
            return if (declared || (sawList && sawLink)) {
                ParsedBookmarkFile(
                    root = root.build(),
                    linkCount = links,
                    folderCount = folders,
                    duplicatesSkipped = duplicates,
                    unsupportedSkipped = unsupported,
                )
            } else {
                null
            }
        }

        private fun handle(tag: MarkupReader.Tag) {
            when {
                tag.closing -> if (tag.name == TAG_LIST) closeList()
                tag.name == TAG_LIST -> openList()
                tag.name == TAG_FOLDER -> openFolder(reader.textUntilClose(TAG_FOLDER, stopAtEntry = true))
                tag.name == TAG_LINK ->
                    addLink(tag.attributes[ATTR_HREF], reader.textUntilClose(TAG_LINK, stopAtEntry = true))
                tag.name in SKIPPED_CONTENT -> reader.textUntilClose(tag.name, stopAtEntry = false)
            }
        }

        private fun openList() {
            sawList = true
            val folder = pendingFolder
            pendingFolder = null
            when {
                folder != null -> stack.addLast(folder)
                !rootListSeen -> rootListSeen = true
                // An unnamed nested list still pairs with its own close tag.
                else -> stack.addLast(stack.last())
            }
        }

        private fun closeList() {
            pendingFolder = null
            if (stack.size > 1) stack.removeLast()
        }

        private fun openFolder(rawName: String) {
            val parent = stack.last()
            val name = clean(rawName, BrowserLimits.MAX_FOLDER_NAME_LENGTH).ifEmpty { untitledFolderName }
            pendingFolder = if (parent.depth >= BookmarkTransferLimits.MAX_IMPORT_DEPTH) {
                parent
            } else {
                Level(name, parent.depth + 1).also {
                    parent.children += it
                    folders++
                }
            }
        }

        private fun addLink(rawHref: String?, rawTitle: String) {
            sawLink = sawLink || rawHref != null
            pendingFolder = null
            val encoded = encodeUnsafe(rawHref?.let(::decodeEntities)?.trim().orEmpty())
            val url = encoded?.takeIf(::isSupported)
                ?.let { (BookmarkUrlValidator.validate(it) as? Result.Success)?.data }
            val level = stack.last()
            when {
                url == null -> unsupported++
                !level.urls.add(url) -> duplicates++
                else -> {
                    val title = clean(rawTitle, BrowserLimits.MAX_BOOKMARK_TITLE_LENGTH)
                        .ifEmpty { url.take(BrowserLimits.MAX_BOOKMARK_TITLE_LENGTH) }
                    level.children += LinkEntry(BookmarkTreeNode.Link(title = title, url = url, addedAtMillis = null))
                    links++
                }
            }
        }

        private fun clean(raw: String, maxLength: Int): String =
            decodeEntities(raw).let { if (it.any(Char::isWhitespace)) it.replace(WHITESPACE, " ") else it }
                .trim().take(maxLength)

        private fun isSupported(url: String): Boolean =
            url.length <= BrowserLimits.MAX_BOOKMARK_URL_LENGTH &&
                SUPPORTED_SCHEMES.any { url.startsWith(it, ignoreCase = true) && url.length > it.length }
    }

    /** A forgiving tag tokenizer: unclosed quotes and tags run to the end of the text. */
    private class MarkupReader(private val s: String) {
        private var pos = 0
        private val closeIndex = HashMap<String, NextIndex>()
        private val entryIndex = ENTRY_BOUNDARIES.map { NextIndex(s, it) }

        class Tag(val name: String, val closing: Boolean, val attributes: Map<String, String>)

        fun startsWithin(marker: String, chars: Int): Boolean = s.take(chars).contains(marker, ignoreCase = true)

        fun nextTag(): Tag? {
            while (true) {
                val open = s.indexOf('<', pos)
                if (open < 0) return null
                pos = open + 1
                if (s.startsWith(COMMENT_OPEN, pos)) {
                    pos = indexOrEnd(s.indexOf(COMMENT_CLOSE, pos), COMMENT_CLOSE.length)
                } else {
                    val closing = s.startsWith("/", pos)
                    if (closing) pos++
                    val name = readWhile { it.isLetterOrDigit() }.lowercase()
                    val attributes = readAttributes()
                    if (name.isNotEmpty()) return Tag(name, closing, attributes)
                }
            }
        }

        /**
         * The text up to `</tag>`, or up to the next entry or list when the close tag is missing, with any
         * tags inside dropped. A missing close tag leaves the following tag to be read next.
         */
        fun textUntilClose(tag: String, stopAtEntry: Boolean): String {
            val closeTag = closeIndex.getOrPut(tag) { NextIndex(s, "</$tag") }
            val close = closeTag.from(pos).let { if (it < 0) s.length else it }
            val end = if (stopAtEntry) {
                entryIndex.fold(close) { end, entry -> entry.from(pos).takeIf { it in 0 until end } ?: end }
            } else {
                close
            }
            val text = s.substring(pos, end)
            pos = if (end == close) indexOrEnd(s.indexOf('>', close), 1) else end
            return if ('<' in text) text.replace(INNER_TAG, "") else text
        }

        private fun readAttributes(): Map<String, String> {
            val attributes = HashMap<String, String>()
            skipWhitespace()
            while (pos < s.length && s[pos] != '>') {
                readAttribute(attributes)
                skipWhitespace()
            }
            pos = minOf(pos + 1, s.length)
            return attributes
        }

        private fun readAttribute(into: MutableMap<String, String>) {
            val key = readWhile { !it.isWhitespace() && it != '=' && it != '>' }.lowercase()
            if (key.isEmpty()) {
                pos++
                return
            }
            skipWhitespace()
            if (s.getOrNull(pos) == '=') {
                pos++
                skipWhitespace()
                into[key] = readValue()
            }
        }

        private fun readValue(): String {
            val quote = s.getOrNull(pos)
            if (quote != '"' && quote != '\'') return readWhile { !it.isWhitespace() && it != '>' }
            val end = s.indexOf(quote, pos + 1).let { if (it < 0) s.length else it }
            return s.substring(pos + 1, end).also { pos = minOf(end + 1, s.length) }
        }

        private fun skipWhitespace() {
            readWhile { it.isWhitespace() }
        }

        private inline fun readWhile(predicate: (Char) -> Boolean): String {
            val start = pos
            while (pos < s.length && predicate(s[pos])) pos++
            return s.substring(start, pos)
        }

        private fun indexOrEnd(index: Int, skip: Int): Int = if (index < 0) s.length else index + skip
    }

    /**
     * The next case-insensitive [marker] at or after a position. The reader only moves forward, so a
     * cached hit (or a miss to the end) stays valid and the whole file is scanned once per marker.
     */
    private class NextIndex(private val s: String, private val marker: String) {
        private var searchedFrom = -1
        private var found = -1

        fun from(pos: Int): Int {
            val stale = searchedFrom < 0 || pos < searchedFrom || (found in 0 until pos)
            if (stale) {
                found = scan(pos)
                searchedFrom = pos
            }
            return found
        }

        private fun scan(start: Int): Int {
            var i = s.indexOf(marker[0], start)
            while (i >= 0 && !s.regionMatches(i, marker, 0, marker.length, ignoreCase = true)) {
                i = s.indexOf(marker[0], i + 1)
            }
            return i
        }
    }

    companion object {
        private const val DOCTYPE = "NETSCAPE-Bookmark-file-1"
        private const val COMMENT_OPEN = "!--"
        private const val COMMENT_CLOSE = "-->"
        private const val TAG_LIST = "dl"
        private const val TAG_FOLDER = "h3"
        private const val TAG_LINK = "a"
        private const val ATTR_HREF = "href"
        private val SKIPPED_CONTENT = setOf("script", "style")
        private val SUPPORTED_SCHEMES = listOf("http://", "https://")
        private val ENTRY_BOUNDARIES = listOf("<dt", "<dl")
        private const val UNSAFE_CHARS = " \"|{}^`<>\\"
        private const val BRACKETS = "[]"
        private const val SCHEME_SEPARATOR = "://"
        private val AUTHORITY_END = charArrayOf('/', '?', '#')
        private const val ASCII_LIMIT = 0x80
        private const val PERCENT_FORMAT = "%%%02X"
        private const val HEX_PAIR_END = 3
        private val HEX_PAIR = Regex("[0-9A-Fa-f]{2}")
        private val INNER_TAG = Regex("<[^>]*>")
        private val WHITESPACE = Regex("\\s+")
        private val ENTITY = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z]{2,6});")
        private val NAMED_ENTITIES = mapOf(
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'",
            "nbsp" to " ",
        )

        /**
         * Browsers export some characters a URI forbids; encoding them keeps the link editable later.
         * `null` when the host is not a valid international name.
         */
        private fun encodeUnsafe(raw: String): String? {
            val authorityStart = raw.indexOf(SCHEME_SEPARATOR).let { if (it < 0) 0 else it + SCHEME_SEPARATOR.length }
            val authorityEnd = raw.indexOfAny(AUTHORITY_END, authorityStart).let { if (it < 0) raw.length else it }
            val authority = asciiAuthority(raw.substring(authorityStart, authorityEnd)) ?: return null
            val url = raw.replaceRange(authorityStart, authorityEnd, authority)
            val pathStart = authorityStart + authority.length
            val firstHash = url.indexOf('#')
            fun unsafe(i: Int): Boolean = needsEscape(url, i, firstHash, pathStart)
            return if (url.indices.none(::unsafe)) {
                url
            } else {
                buildString(url.length) {
                    url.forEachIndexed { i, c -> if (unsafe(i)) append(PERCENT_FORMAT.format(c.code)) else append(c) }
                }
            }
        }

        private fun needsEscape(url: String, i: Int, firstHash: Int, pathStart: Int): Boolean {
            val c = url[i]
            return when {
                c.isLetterOrDigit() -> false
                c == '%' -> !HEX_PAIR.matches(url.substring(i + 1, minOf(i + HEX_PAIR_END, url.length)))
                c == '#' -> i != firstHash
                c in BRACKETS -> i >= pathStart
                else -> c in UNSAFE_CHARS
            }
        }

        private fun asciiAuthority(authority: String): String? {
            if (authority.all { it.code < ASCII_LIMIT }) return authority
            val hostStart = authority.lastIndexOf('@') + 1
            val hostEnd = authority.indexOf(':', hostStart).let { if (it < 0) authority.length else it }
            return try {
                authority.replaceRange(
                    hostStart,
                    hostEnd,
                    IDN.toASCII(authority.substring(hostStart, hostEnd), IDN.ALLOW_UNASSIGNED),
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        fun decodeEntities(text: String): String {
            if ('&' !in text) return text
            return ENTITY.replace(text) { match ->
                val body = match.groupValues[1]
                val codePoint = when {
                    body.startsWith("#x", ignoreCase = true) -> body.substring(2).toIntOrNull(radix = 16)
                    body.startsWith("#") -> body.substring(1).toIntOrNull()
                    else -> null
                }
                when {
                    codePoint != null && Character.isValidCodePoint(codePoint) -> String(Character.toChars(codePoint))
                    codePoint != null -> match.value
                    else -> NAMED_ENTITIES[body.lowercase()] ?: match.value
                }
            }
        }
    }
}
