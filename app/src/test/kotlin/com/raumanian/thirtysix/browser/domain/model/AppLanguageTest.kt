package com.raumanian.thirtysix.browser.domain.model

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec 016 T047 — [AppLanguage] tag mapping, and parity with the locales the app declares.
 */
class AppLanguageTest {

    @Test
    fun `a tag with a region or script resolves by its primary language subtag`() {
        assertEquals(AppLanguage.Chinese, AppLanguage.fromLanguageTagOrFollowSystem("zh-Hans-CN"))
        assertEquals(AppLanguage.French, AppLanguage.fromLanguageTagOrFollowSystem("fr-CA"))
    }

    @Test
    fun `matching ignores case`() {
        assertEquals(AppLanguage.English, AppLanguage.fromLanguageTagOrFollowSystem("EN"))
    }

    @Test
    fun `an unsupported, blank or missing tag is Follow system`() {
        listOf("es", "", "   ", null).forEach { tag ->
            assertEquals("tag=$tag", AppLanguage.FollowSystem, AppLanguage.fromLanguageTagOrFollowSystem(tag))
        }
    }

    @Test
    fun `every supported tag maps back to its own entry`() {
        AppLanguage.entries.filter { it.tag != null }.forEach { language ->
            assertEquals(language, AppLanguage.fromLanguageTagOrFollowSystem(language.tag))
        }
    }

    /**
     * Data-model §3 invariant. JVM unit tests run with the `app/` module as their working
     * directory, so the source-tree file is readable directly.
     */
    @Test
    fun `the eight tags equal the locales declared in locales_config`() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(LOCALES_CONFIG_PATH))
        val nodes = document.getElementsByTagName(LOCALE_ELEMENT)
        val declared = (0 until nodes.length).map { index ->
            nodes.item(index).attributes.getNamedItem(LOCALE_NAME_ATTRIBUTE).nodeValue
        }

        assertEquals(declared.toSet(), AppLanguage.entries.mapNotNull { it.tag }.toSet())
        assertEquals("locales_config must not repeat a locale", declared.size, declared.toSet().size)
    }

    private companion object {
        const val LOCALES_CONFIG_PATH = "src/main/res/xml/locales_config.xml"
        const val LOCALE_ELEMENT = "locale"
        const val LOCALE_NAME_ATTRIBUTE = "android:name"
    }
}
