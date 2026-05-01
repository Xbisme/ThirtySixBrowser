package com.raumanian.thirtysix.browser.core.result

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ResultTest {
    @Test
    fun `map on Success applies transform`() {
        val result: Result<Int> = Result.Success(5)

        val mapped = result.map { it * 2 }

        assertTrue(mapped is Result.Success)
        assertEquals(10, (mapped as Result.Success).data)
    }

    @Test
    fun `map on Error preserves Error and skips transform`() {
        val cause = IOException("boom")
        val result: Result<Int> = Result.Error(cause)

        val mapped =
            result.map<Int, Int> {
                fail("transform must not run on Error")
                it
            }

        assertTrue(mapped is Result.Error)
        assertEquals(cause, (mapped as Result.Error).throwable)
    }

    @Test
    fun `fold on Success calls onSuccess branch`() {
        val result: Result<Int> = Result.Success(7)

        val folded =
            result.fold(
                onSuccess = { "ok:$it" },
                onError = { "err:${it.message}" },
            )

        assertEquals("ok:7", folded)
    }

    @Test
    fun `fold on Error calls onError branch`() {
        val cause = IllegalStateException("bad")
        val result: Result<Int> = Result.Error(cause)

        val folded =
            result.fold(
                onSuccess = { "ok:$it" },
                onError = { "err:${it.message}" },
            )

        assertEquals("err:bad", folded)
    }
}
