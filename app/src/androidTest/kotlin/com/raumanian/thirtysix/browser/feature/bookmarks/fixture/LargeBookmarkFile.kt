package com.raumanian.thirtysix.browser.feature.bookmarks.fixture

import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode.Folder
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode.Link
import com.raumanian.thirtysix.browser.feature.bookmarks.data.datasource.local.NetscapeBookmarkWriter

/** A bookmark file at the app's bookmark limit: chains of nested folders, the same output every run. */
object LargeBookmarkFile {
    const val LINKS: Int = 10_000
    const val FOLDERS: Int = 200
    const val MAX_DEPTH: Int = 5

    private const val LINKS_PER_FOLDER = LINKS / FOLDERS
    private const val CHAINS = FOLDERS / MAX_DEPTH

    fun text(): String = StringBuilder().also { NetscapeBookmarkWriter().write(tree(), it) }.toString()

    private fun tree(): Folder {
        var next = 0
        fun links() = List(LINKS_PER_FOLDER) {
            next++
            Link("Bookmark $next", "https://site$next.example/page/$next", null)
        }
        fun chain(chainIndex: Int, depth: Int): Folder {
            val inner = if (depth < MAX_DEPTH) listOf(chain(chainIndex, depth + 1)) else emptyList()
            return Folder("Folder $chainIndex.$depth", links() + inner)
        }
        return Folder("", List(CHAINS) { chain(it, depth = 1) })
    }
}
