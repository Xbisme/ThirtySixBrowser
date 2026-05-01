package com.raumanian.thirtysix.browser.core.result

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()

    data class Error(val throwable: Throwable, val message: String? = null) : Result<Nothing>()
}

inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> =
    when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Error -> this
    }

inline fun <T, R> Result<T>.fold(
    onSuccess: (T) -> R,
    onError: (Throwable) -> R,
): R =
    when (this) {
        is Result.Success -> onSuccess(data)
        is Result.Error -> onError(throwable)
    }
