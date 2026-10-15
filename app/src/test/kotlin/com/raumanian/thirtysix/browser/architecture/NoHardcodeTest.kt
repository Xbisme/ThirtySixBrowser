package com.raumanian.thirtysix.browser.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test

/** Android Lint only sees XML layouts, so Compose literals are checked here. Previews are exempt. */
class NoHardcodeTest {

    private val composables = productionScope().functions()
        .filter { function -> function.annotations.any { it.name == "Composable" } }
        .filterNot { function -> function.annotations.any { it.name.endsWith("Preview") } }

    @Test
    fun `composables take text from string resources`() {
        composables.assertFalse(additionalMessage = "use stringResource(R.string.…)") {
            HARDCODED_TEXT.containsMatchIn(it.text)
        }
    }

    @Test
    fun `colors come from the theme`() {
        productionScope().files
            .filterNot { it.packagee?.name.orEmpty().endsWith(".designsystem.theme") }
            .assertFalse(additionalMessage = "use MaterialTheme.colorScheme or a theme token") {
                it.text.contains("Color(0x")
            }
    }

    private companion object {
        val HARDCODED_TEXT = Regex("""\bText\(\s*(text\s*=\s*)?"|contentDescription\s*=\s*"""")
    }
}
