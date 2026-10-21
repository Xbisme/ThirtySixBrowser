package com.raumanian.thirtysix.browser.feature.bookmarks.data.datasource.local

import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode.Folder
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode.Link
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.testing.fake.FakeBookmarkRepository
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkTransferLimits
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.ParsedBookmarkFile
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase.UpdateBookmarkUseCase
import com.raumanian.thirtysix.browser.feature.bookmarks.fixture.BookmarkFiles
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetscapeBookmarkParserTest {

    private val parser = NetscapeBookmarkParser()

    private fun parse(text: String): ParsedBookmarkFile? = parser.parse(text, UNTITLED)

    private fun parseFixture(name: String): ParsedBookmarkFile =
        checkNotNull(parse(BookmarkFiles.read(name))) { "$name was not read as a bookmark file" }

    private fun ParsedBookmarkFile.counts() = listOf(linkCount, folderCount, duplicatesSkipped, unsupportedSkipped)

    private fun Folder.folder(name: String): Folder = children.filterIsInstance<Folder>().single { it.name == name }

    private fun Folder.titles(): List<String> = children.filterIsInstance<Link>().map { it.title }

    @Test
    fun `chrome export keeps the tree, decodes entities and skips duplicates and bookmarklets`() {
        val file = parseFixture("chrome.html")

        assertEquals(listOf(4, 3, 1, 2), file.counts())
        val bar = file.root.folder("Bookmarks bar")
        assertEquals(listOf("Wikipedia"), bar.titles())
        assertEquals(listOf("VnExpress – Tin nhanh", "BBC & News"), bar.folder("News").titles())
        val other = file.root.folder("Other bookmarks").children.single() as Link
        assertEquals("Example <A> 'quoted'", other.title)
        assertEquals("https://example.com/a?x=1&y=2", other.url)
    }

    @Test
    fun `chromium writer output keeps raw ampersands, quoted hrefs and unwrapped other bookmarks`() {
        val file = parseFixture("chrome-writer.html")

        assertEquals(listOf(5, 3, 0, 0), file.counts())
        assertEquals(
            listOf("Bookmarks bar", "Other root item", "From other bookmarks"),
            file.root.children.map { (it as? Folder)?.name ?: (it as Link).title },
        )
        val bar = file.root.folder("Bookmarks bar")
        val search = bar.children.first() as Link
        assertEquals("https://www.google.com/search?q=a&b=1&copy=2", search.url)
        assertEquals("Search & <more>", search.title)
        val quoted = bar.folder("Tin tức").children.map { it as Link }.last()
        assertEquals("https://example.com/say?q=%22hi%22", quoted.url)
        assertEquals(listOf("Mobile item"), file.root.folder("From other bookmarks").titles())
    }

    @Test
    fun `firefox writer output reads the entry that shares a line with a separator`() {
        val file = parseFixture("firefox-writer.html")

        assertEquals(listOf(4, 2, 0, 1), file.counts())
        assertEquals(listOf("About Us"), file.root.titles())
        val toolbar = file.root.folder("Bookmarks Toolbar").children.map { it as Link }
        assertEquals(listOf("Example & Co", "Next"), toolbar.map { it.title })
        assertEquals("https://example.com/?a=1&b=2", toolbar.first().url)
        assertEquals(listOf("Unfiled"), file.root.folder("Other Bookmarks").titles())
    }

    @Test
    fun `firefox export ignores separators, descriptions, icons and place queries`() {
        val file = parseFixture("firefox.html")

        assertEquals(listOf(4, 3, 0, 1), file.counts())
        assertEquals(
            listOf("Mozilla Firefox", "MDN Web Docs", "Bookmarks Toolbar", "Other Bookmarks"),
            file.root.children.map { (it as? Folder)?.name ?: (it as Link).title },
        )
        assertEquals(emptyList<Any>(), file.root.folder("Other Bookmarks").children)
    }

    @Test
    fun `safari export without a top-level list still nests each folder`() {
        val file = parseFixture("safari.html")

        assertEquals(listOf(3, 3, 0, 0), file.counts())
        assertEquals(listOf("Apple", "iCloud"), file.root.folder("Favourites").titles())
        assertEquals(listOf("A long read"), file.root.folder("Reading List").titles())
    }

    @Test
    fun `edge export keeps non-Latin titles and titles an untitled link with its address`() {
        val file = parseFixture("edge.html")

        assertEquals(listOf(5, 2, 0, 0), file.counts())
        assertEquals(listOf("日本語のページ", "Русский", "한국어"), file.root.folder("Favorites bar").titles())
        assertEquals(listOf("中文 🎉", "https://untitled.example/"), file.root.folder("Other favorites").titles())
    }

    @Test
    fun `samsung export reads single-quoted and unquoted attributes`() {
        val file = parseFixture("samsung.html")

        assertEquals(listOf(2, 1, 0, 1), file.counts())
        val links = file.root.folder("Mobile bookmarks").children.map { (it as Link).url }
        assertEquals(listOf("https://www.samsung.com/", "https://m.facebook.com/"), links)
    }

    @Test
    fun `duckduckgo export keeps a nested folder and a top-level link`() {
        val file = parseFixture("duckduckgo.html")

        assertEquals(listOf(3, 2, 0, 0), file.counts())
        assertEquals(listOf("GitHub"), file.root.folder("DuckDuckGo Bookmarks").folder("Work").titles())
        assertEquals(listOf("Hacker News"), file.root.titles())
    }

    @Test
    fun `a real duckduckgo android export keeps its order and unescaped ampersands`() {
        val file = parseFixture("duckduckgo-android-export.html")

        assertEquals(listOf(4, 1, 0, 0), file.counts())
        val links = file.root.folder("DuckDuckGo Bookmarks").children.map { it as Link }
        assertEquals("https://duckduckgo.com/?q=go&atb=v500-3ru&ko=-1&t=ddg_android&ia=web", links.first().url)
        assertEquals("Báo VnExpress - Báo tiếng Việt nhiều người xem nhất", links[2].title)
    }

    @Test
    fun `a truncated file keeps every link read before the cut`() {
        val file = parseFixture("truncated.html")

        assertEquals(listOf(3, 2, 0, 1), file.counts())
        assertEquals(
            listOf("VnExpress – Tin nhanh", "BBC & News"),
            file.root.folder("Bookmarks bar").folder("News").titles(),
        )
    }

    @Test
    fun `folders deeper than the limit are flattened into their deepest allowed ancestor`() {
        val file = parseFixture("deep-100.html")

        assertEquals(100, file.linkCount)
        assertEquals(BookmarkTransferLimits.MAX_IMPORT_DEPTH, file.folderCount)
        var level = file.root
        repeat(BookmarkTransferLimits.MAX_IMPORT_DEPTH) { level = level.children.filterIsInstance<Folder>().single() }
        assertEquals(100 - BookmarkTransferLimits.MAX_IMPORT_DEPTH + 1, level.titles().size)
    }

    @Test
    fun `files that are not bookmark files are refused`() {
        assertNull(parse(BookmarkFiles.read("empty.html")))
        assertNull(parse(BookmarkFiles.read("photo.jpg")))
        assertNull(parse("Just a note about <b>bold</b> things."))
    }

    @Test
    fun `a declared file with no links parses to an empty tree`() {
        val file = parseFixture("no-links.html")

        assertEquals(listOf(0, 1, 0, 0), file.counts())
    }

    @Test
    fun `an undeclared file with a list and a link is accepted`() {
        val file = parse("<dl><dt><a href=\"https://a.example/\">A</a></dl>")

        assertNotNull(file)
        assertEquals(1, file!!.linkCount)
    }

    @Test
    fun `a one-line file, a folder without a list and a stray close tag all parse`() {
        val text = "<!DOCTYPE NETSCAPE-Bookmark-file-1></DL><DL><DT><H3>Lonely</H3>" +
            "<DT><A HREF=\"https://x.example/\">X</A><DT><H3>F</H3>" +
            "<DL><DT><A HREF=\"https://y.example/\">Y</A></DL></DL>"

        val file = parse(text)!!

        assertEquals(listOf("Lonely", "X", "F"), file.root.children.map { (it as? Folder)?.name ?: (it as Link).title })
        assertEquals(listOf("Y"), file.root.folder("F").titles())
    }

    @Test
    fun `script and style content is never read as bookmarks`() {
        val text = "<!DOCTYPE NETSCAPE-Bookmark-file-1>" +
            "<script>var a='<A HREF=\"https://evil.example/\">x</A>'</script>" +
            "<style>a{}</style><DL><DT><A HREF=\"https://ok.example/\">ok</A></DL>"

        assertEquals(listOf("ok"), parse(text)!!.root.titles())
    }

    @Test
    fun `titles collapse whitespace, blank folder names fall back and long values are cut`() {
        val longTitle = "t".repeat(BrowserLimits.MAX_BOOKMARK_TITLE_LENGTH + 10)
        val longUrl = "https://x.example/" + "p".repeat(BrowserLimits.MAX_BOOKMARK_URL_LENGTH)
        val text = "<!DOCTYPE NETSCAPE-Bookmark-file-1><DL><DT><H3>  </H3><DL>" +
            "<DT><A HREF=\"https://a.example/\">  two\n  words </A>" +
            "<DT><A HREF=\"https://b.example/\">$longTitle</A>" +
            "<DT><A HREF=\"$longUrl\">too long</A>" +
            "<DT><A HREF=\"HTTPS://C.example/\">upper</A>" +
            "<DT><A HREF=\"https://\">no host</A></DL></DL>"

        val file = parse(text)!!

        val folder = file.root.folder(UNTITLED)
        assertEquals("two words", folder.titles()[0])
        assertEquals(BrowserLimits.MAX_BOOKMARK_TITLE_LENGTH, folder.titles()[1].length)
        assertEquals("upper", folder.titles()[2])
        assertEquals(2, file.unsupportedSkipped)
    }

    @Test
    fun `a folder name without its close tag ends at the next list`() {
        val file = parse("<DT><H3>Work<DL><p><DT><A HREF=\"https://a.example\">a</A></DL>")!!

        assertEquals(listOf("a"), file.root.folder("Work").titles())
    }

    @Test
    fun `a link title without its close tag ends at the next entry`() {
        val file = parse("<DL><DT><A HREF=\"https://1.example\">t1<DT><A HREF=\"https://2.example\">t2</A></DL>")!!

        assertEquals(listOf("t1", "t2"), file.root.titles())
    }

    @Test
    fun `characters illegal in an address are encoded, and the result passes an edit`() = runTest {
        val file = parse("<DL><DT><A HREF=\"https://maps.example/a b|c?q=%41{x}^`<y>\\\">m</A></DL>")!!

        val url = (file.root.children.single() as Link).url
        assertEquals("https://maps.example/a%20b%7Cc?q=%41%7Bx%7D%5E%60%3Cy%3E%5C", url)
        val repo = FakeBookmarkRepository()
        val id = repo.seedBookmark(Bookmark(0, "m", url, null, 1L, 1L))
        assertTrue(UpdateBookmarkUseCase(repo)(id, "m", url, null) is Result.Success)
    }

    @Test
    fun `an address the bookmark validator rejects is skipped`() {
        val file = parse("<DL><DT><A HREF=\"https:// x\">bad</A><DT><A HREF=\"https://ok.example\">ok</A></DL>")!!

        assertEquals(listOf("ok"), file.root.titles())
        assertEquals(1, file.unsupportedSkipped)
    }

    @Test
    fun `numeric and named entities decode, unknown ones stay`() {
        assertEquals(
            "a & b < c > \"d\" 'e'   é 😀 &bogus; &#xFFFFFFF;",
            NetscapeBookmarkParser.decodeEntities(
                "a &amp; b &lt; c &gt; &quot;d&quot; &apos;e&#39; &nbsp; &#233; &#x1F600; &bogus; &#xFFFFFFF;",
            ),
        )
    }

    private companion object {
        const val UNTITLED = "Untitled folder"
    }
}
