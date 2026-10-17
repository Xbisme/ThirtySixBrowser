package com.raumanian.thirtysix.browser.feature.bookmarks.data.datasource.local

import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode
import javax.inject.Inject

/** Writes the layout Chrome exports, which every browser's importer reads. */
class NetscapeBookmarkWriter @Inject constructor() {

    fun write(tree: BookmarkTreeNode.Folder, out: Appendable) {
        out.append(HEADER)
        out.append(LIST_OPEN).append('\n')
        writeChildren(tree.children, out, depth = 1)
        out.append(LIST_CLOSE).append('\n')
    }

    private fun writeChildren(children: List<BookmarkTreeNode>, out: Appendable, depth: Int) {
        val indent = INDENT.repeat(depth)
        for (child in children) {
            when (child) {
                is BookmarkTreeNode.Folder -> {
                    out.append(indent).append("<DT><H3>").append(escape(child.name)).append("</H3>\n")
                    out.append(indent).append(LIST_OPEN).append('\n')
                    writeChildren(child.children, out, depth + 1)
                    out.append(indent).append(LIST_CLOSE).append('\n')
                }
                is BookmarkTreeNode.Link -> {
                    out.append(indent).append("<DT><A HREF=\"").append(escape(child.url)).append('"')
                    child.addedAtMillis?.let { millis ->
                        out.append(" ADD_DATE=\"").append((millis / MILLIS_PER_SECOND).toString()).append('"')
                    }
                    out.append('>').append(escape(child.title)).append("</A>\n")
                }
            }
        }
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000L
        private const val INDENT = "    "
        private const val LIST_OPEN = "<DL><p>"
        private const val LIST_CLOSE = "</DL><p>"
        private val HEADER = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <!-- This is an automatically generated file.
                 It will be read and overwritten.
                 DO NOT EDIT! -->
            <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
            <TITLE>Bookmarks</TITLE>
            <H1>Bookmarks</H1>

        """.trimIndent()

        fun escape(text: String): String = buildString(text.length) {
            for (c in text) {
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(c)
                }
            }
        }
    }
}
