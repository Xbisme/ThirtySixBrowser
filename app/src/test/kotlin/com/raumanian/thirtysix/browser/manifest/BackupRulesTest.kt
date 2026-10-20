package com.raumanian.thirtysix.browser.manifest

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.raumanian.thirtysix.browser.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/** WebView's own files, which hold visited sites and site data, stay out of every backup. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BackupRulesTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    private val webViewFiles = setOf(
        "root" to "app_webview/",
        "sharedpref" to "AwOriginVisitLoggerPrefs.xml",
        "sharedpref" to "WebViewChromiumPrefs.xml",
    )

    /** Excludes per block: the root element for backup_rules, each child block for extraction rules. */
    private fun excludesByBlock(xml: Int, blockDepth: Int): Map<String, Set<Pair<String, String>>> {
        val parser = context.resources.getXml(xml)
        val blocks = mutableMapOf<String, MutableSet<Pair<String, String>>>()
        var block = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.depth == blockDepth) block = parser.name
            if (parser.name == "exclude") {
                val rule = parser.getAttributeValue(null, "domain") to parser.getAttributeValue(null, "path")
                blocks.getOrPut(block) { mutableSetOf() } += rule
            }
        }
        return blocks
    }

    @Test
    fun `auto backup excludes the WebView files`() {
        val blocks = excludesByBlock(R.xml.backup_rules, blockDepth = 1)
        assertTrue(blocks.getValue("full-backup-content").containsAll(webViewFiles))
    }

    @Test
    fun `cloud backup and device transfer exclude the WebView files`() {
        val blocks = excludesByBlock(R.xml.data_extraction_rules, blockDepth = 2)
        assertEquals(setOf("cloud-backup", "device-transfer"), blocks.keys)
        blocks.forEach { (name, rules) -> assertTrue(name, rules.containsAll(webViewFiles)) }
    }
}
