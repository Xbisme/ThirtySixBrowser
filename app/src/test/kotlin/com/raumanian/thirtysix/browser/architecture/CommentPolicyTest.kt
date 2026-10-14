package com.raumanian.thirtysix.browser.architecture

import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/** detekt ForbiddenComment checks what a comment says; this test checks how much of it there is. */
class CommentPolicyTest {

    private val files = productionScope().files

    @Test
    fun `comment blocks stay short`() {
        files.assertTrue(
            additionalMessage = "a comment over $MAX_BLOCK_LINES lines belongs in docs/adr/, the PR, or a better name",
        ) { file -> CommentStats.of(file.text).longestBlock <= MAX_BLOCK_LINES }
    }

    @Test
    fun `files are mostly code`() {
        files.assertTrue(
            additionalMessage = "more than ${(MAX_DENSITY * 100).toInt()}% of this file is comments",
        ) { file ->
            val stats = CommentStats.of(file.text)
            stats.codeLines + stats.commentLines < MIN_LINES_FOR_DENSITY || stats.density <= MAX_DENSITY
        }
    }

    private companion object {
        const val MAX_BLOCK_LINES = 5
        const val MAX_DENSITY = 0.15
        const val MIN_LINES_FOR_DENSITY = 30
    }
}

/** Counts comment text lines; `/**`, `*/` and bare `*` lines are punctuation and don't count. */
internal data class CommentStats(val commentLines: Int, val codeLines: Int, val longestBlock: Int) {

    val density: Double get() = commentLines.toDouble() / (commentLines + codeLines)

    private enum class Kind { SKIP, COMMENT, CODE }

    companion object {
        fun of(source: String): CommentStats {
            var comment = 0
            var code = 0
            var block = 0
            var longest = 0
            var inBlockComment = false
            for (line in source.lines().map(String::trim)) {
                val kind = classify(line, inBlockComment)
                if (line.startsWith("/*") && !line.contains("*/")) inBlockComment = true
                if (inBlockComment && line.contains("*/")) inBlockComment = false
                when (kind) {
                    Kind.SKIP -> Unit

                    Kind.COMMENT -> {
                        comment++
                        block++
                        longest = maxOf(longest, block)
                    }

                    Kind.CODE -> {
                        code++
                        block = 0
                    }
                }
            }
            return CommentStats(comment, code, longest)
        }

        private fun classify(line: String, inBlockComment: Boolean): Kind {
            val isComment = inBlockComment || line.startsWith("//") || line.startsWith("/*")
            val isSkipped = line.isEmpty() || line.startsWith("import ") || line.startsWith("package ")
            return when {
                isSkipped || (isComment && line.trimStart('/', '*').isBlank()) -> Kind.SKIP
                isComment -> Kind.COMMENT
                else -> Kind.CODE
            }
        }
    }
}
