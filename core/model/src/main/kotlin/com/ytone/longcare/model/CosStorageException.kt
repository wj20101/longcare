package com.ytone.longcare.model

/**
 * COS SDK 与本项目文件服务之间统一的结构化错误类型。
 */
enum class CosStorageFailureKind {
    NOT_FOUND,
    AUTHORIZATION,
    NETWORK,
    SERVICE,
    CLIENT,
    BACKEND,
    INVALID_RESPONSE,
    UNKNOWN,
}

class CosStorageException(
    val kind: CosStorageFailureKind,
    val errorCode: String,
    val retryable: Boolean,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
