package com.raumanian.thirtysix.browser.feature.bookmarks.data.datasource.local

import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode.Folder
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode.Link
import org.junit.Assert.assertEquals
import org.junit.Test

class NetscapeBookmarkWriterTest {

    private val writer = NetscapeBookmarkWriter()
    private val parser = NetscapeBookmarkParser()

    private fun write(tree: Folder): String = StringBuilder().also { writer.write(tree, it) }.toString()

    @Test
    fun `writes the Chrome layout with nested lists and dates in seconds`() {
        val tree =
            Folder(
                "",
                listOf(
                    Folder("Work", listOf(Link("Docs", "https://docs.example/", 1_700_000_000_123L))),
                    Folder("Empty", emptyList()),
                ),
            )

        val text = write(tree)

        assertEquals(true, text.startsWith("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n"))
        assertEquals(true, "<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">" in text)
        val body = text.substringAfter("<H1>Bookmarks</H1>\n")
        assertEquals(
            """
            <DL><p>
                <DT><H3>Work</H3>
                <DL><p>
                    <DT><A HREF="https://docs.example/" ADD_DATE="1700000000">Docs</A>
                </DL><p>
                <DT><H3>Empty</H3>
                <DL><p>
                </DL><p>
            </DL><p>

            """.trimIndent(),
            body,
        )
    }

    @Test
    fun `escapes markup characters in text and attributes`() {
        val text = write(Folder("", listOf(Link("<b>\"Tom\" & 'Jerry'</b>", "https://x.example/?a=1&b=\"2\"", null))))

        assertEquals(
            true,
            "<A HREF=\"https://x.example/?a=1&amp;b=&quot;2&quot;\">" +
                "&lt;b&gt;&quot;Tom&quot; &amp; &#39;Jerry&#39;&lt;/b&gt;</A>" in text,
        )
    }

    @Test
    fun `a written tree parses back identical, across markup, emoji and all eight app languages`() {
        val words = listOf(
            "English <&>", "Tiếng Việt", "Deutsch ß", "Русский", "한국어",
            "日本語", "中文", "Français « »", "emoji 🎉👩‍💻", "quote \" '",
        )
        var n = 0
        fun links(count: Int) = List(count) {
            Link("${words[n % words.size]} ${n++}", "https://site$n.example/p?q=$n&r=%22x%22'", null)
        }
        val tree = Folder(
            "",
            listOf(
                Folder("Top ${words[1]}", links(300) + Folder("Inner ${words[4]}", links(300))),
                Folder("Empty ${words[8]}", emptyList()),
            ) + links(400),
        )

        val parsed = parser.parse(write(tree), "untitled")!!

        assertEquals(1_000, parsed.linkCount)
        assertEquals(3, parsed.folderCount)
        assertEquals(tree, parsed.root)
    }
}
