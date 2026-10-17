package com.raumanian.thirtysix.browser.feature.bookmarks.fixture

/** Sample bookmark files in `src/test/resources/bookmarks/`. */
object BookmarkFiles {
    fun bytes(name: String): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("bookmarks/$name")) { "missing fixture $name" }
            .use { it.readBytes() }

    fun read(name: String): String = bytes(name).toString(Charsets.UTF_8)
}
