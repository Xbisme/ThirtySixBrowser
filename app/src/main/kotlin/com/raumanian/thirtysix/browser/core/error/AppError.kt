package com.raumanian.thirtysix.browser.core.error

import android.database.sqlite.SQLiteException
import java.io.IOException

sealed class AppError(open val throwable: Throwable) {
    data class Network(override val throwable: Throwable) : AppError(throwable)

    data class Database(override val throwable: Throwable) : AppError(throwable)

    data class Unknown(override val throwable: Throwable) : AppError(throwable)

    companion object {
        fun from(throwable: Throwable): AppError =
            when (throwable) {
                is IOException -> Network(throwable)
                is SQLiteException -> Database(throwable)
                else -> Unknown(throwable)
            }
    }
}
