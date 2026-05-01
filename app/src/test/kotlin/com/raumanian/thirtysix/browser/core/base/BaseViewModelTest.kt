package com.raumanian.thirtysix.browser.core.base

import android.database.sqlite.SQLiteException
import com.raumanian.thirtysix.browser.core.error.AppError
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private class FakeViewModel : BaseViewModel() {
        fun runBlock(
            onError: (AppError) -> Unit,
            block: suspend () -> Unit,
        ) = launchSafely(onError = onError) { block() }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `launchSafely maps IOException to AppError Network`() =
        runTest(testDispatcher) {
            val viewModel = FakeViewModel()
            var captured: AppError? = null

            viewModel.runBlock(onError = { captured = it }) {
                throw IOException("offline")
            }
            advanceUntilIdle()

            assertTrue(captured is AppError.Network)
            assertEquals("offline", captured?.throwable?.message)
        }

    @Test
    fun `launchSafely maps SQLiteException to AppError Database`() =
        runTest(testDispatcher) {
            val viewModel = FakeViewModel()
            var captured: AppError? = null

            viewModel.runBlock(onError = { captured = it }) {
                throw SQLiteException("disk corrupt")
            }
            advanceUntilIdle()

            assertTrue(captured is AppError.Database)
        }

    @Test
    fun `launchSafely maps generic exception to AppError Unknown`() =
        runTest(testDispatcher) {
            val viewModel = FakeViewModel()
            var captured: AppError? = null

            viewModel.runBlock(onError = { captured = it }) {
                throw IllegalStateException("oops")
            }
            advanceUntilIdle()

            assertTrue(captured is AppError.Unknown)
        }

    @Test
    fun `launchSafely re-throws CancellationException so job ends Cancelled and onError not invoked`() =
        runTest(testDispatcher) {
            val viewModel = FakeViewModel()
            var captured: AppError? = null

            val job =
                viewModel.runBlock(onError = { captured = it }) {
                    throw CancellationException("cancel")
                }
            job.join()

            assertTrue(job.isCancelled)
            assertNull(captured)
        }
}
