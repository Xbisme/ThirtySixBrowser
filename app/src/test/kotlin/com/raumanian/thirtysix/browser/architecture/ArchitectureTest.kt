package com.raumanian.thirtysix.browser.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/** presentation → domain ← data, and features never see each other. */
class ArchitectureTest {

    @Test
    fun `layers depend only inward`() {
        productionScope().assertArchitecture {
            val domain = Layer("Domain", "$ROOT..domain..")
            val data = Layer("Data", "$ROOT..data..")
            val presentation = Layer("Presentation", "$ROOT..presentation..")
            val di = Layer("DI", "$ROOT..di..")

            domain.dependsOnNothing()
            data.dependsOn(domain)
            presentation.dependsOn(domain)
            di.dependsOn(domain, data)
        }
    }

    @Test
    fun `domain is pure Kotlin`() {
        productionScope().files
            .filter { it.packagee?.name.orEmpty().contains(".domain") }
            .assertTrue(additionalMessage = "domain/ is pure Kotlin: no Android, Retrofit or serialization") { file ->
                file.imports.none { import -> FORBIDDEN_IN_DOMAIN.any { import.name.startsWith(it) } }
            }
    }

    @Test
    fun `features do not import other features`() {
        productionScope().files
            .filter { featureOf(it.packagee?.name) != null }
            .assertTrue(additionalMessage = "move the shared part to core/") { file ->
                val own = featureOf(file.packagee?.name)
                file.imports.none { import -> featureOf(import.name).let { it != null && it != own } }
            }
    }

    @Test
    fun `core does not import features`() {
        productionScope().files
            .filter { it.packagee?.name.orEmpty().startsWith("$ROOT.core") }
            .assertTrue { file -> file.imports.none { it.name.startsWith("$ROOT.feature.") } }
    }

    @Test
    fun `dispatchers are injected`() {
        productionScope().files
            .filterNot { it.packagee?.name.orEmpty().contains(".di") }
            .assertTrue(additionalMessage = "inject a DispatcherProvider; only di/ names a dispatcher") { file ->
                NAMED_DISPATCHERS.none { file.text.contains(it) }
            }
    }

    @Test
    fun `main dispatcher is set only by the rule`() {
        Konsist.scopeFromTest(moduleName = "app").files
            .filterNot { it.name == "MainDispatcherRule" || it.packagee?.name == "$ROOT.architecture" }
            .assertTrue(additionalMessage = "use MainDispatcherRule") { file -> !file.text.contains(SET_MAIN) }
    }

    private fun featureOf(packageName: String?): String? = packageName
        ?.substringAfter("$ROOT.feature.", missingDelimiterValue = "")
        ?.substringBefore('.')
        ?.ifEmpty { null }

    private companion object {
        // detekt's InjectDispatcher needs type resolution, which the plain detekt task does not run.
        val NAMED_DISPATCHERS = listOf("Dispatchers.IO", "Dispatchers.Default")

        const val SET_MAIN = "Dispatchers.setMain"

        val FORBIDDEN_IN_DOMAIN = listOf(
            "android.",
            "androidx.",
            "retrofit2.",
            "okhttp3.",
            "kotlinx.serialization.",
        )
    }
}
