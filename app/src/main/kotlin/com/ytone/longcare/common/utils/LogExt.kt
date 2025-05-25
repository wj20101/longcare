package com.ytone.longcare.common.utils

import android.util.Log
import com.ytone.longcare.BuildConfig

/**
 * 定义日志级别
 */
enum class LogLevel(val priority: Int) {
    VERBOSE(Log.VERBOSE), DEBUG(Log.DEBUG), INFO(Log.INFO), WARN(Log.WARN), ERROR(Log.ERROR), ASSERT(
        Log.ASSERT
    ),
    NONE(Log.ASSERT + 1) // 特殊级别，表示不打印任何日志
}

/**
 * 日志配置类
 */
data class LogConfig(
    var enabled: Boolean = BuildConfig.DEBUG, // 总开关，是否启用日志
    var globalTag: String = "AppLog", // 全局默认Tag
    var currentLevel: LogLevel = LogLevel.VERBOSE, // 当前允许打印的最低日志级别
    var showThreadInfo: Boolean = false, // 是否显示线程信息
    var stackTraceDepth: Int = 1, // 堆栈跟踪深度, 用于获取调用位置，0表示不获取，1表示当前方法，2表示上一层
    var logToFileEnabled: Boolean = false, // (高级功能占位) 是否将日志写入文件
    var logFileConfig: LogFileConfig? = null // (高级功能占位) 文件日志配置
)

/**
 * (高级功能占位) 文件日志配置
 */
data class LogFileConfig(
    val directoryPath: String,
    val fileNamePrefix: String = "app_log",
    val maxFileSizeMb: Int = 5,
    val maxHistoryDays: Int = 7
)


object KLogger {

    private var config: LogConfig = LogConfig() // 默认配置

    /**
     * 初始化日志配置
     * 建议在 Application.onCreate() 中调用
     */
    fun init(config: LogConfig = LogConfig()) {
        KLogger.config = config
        // 如果需要，可以在这里初始化文件日志记录器等
        if (config.logToFileEnabled && config.logFileConfig != null) {
            // TODO: Initialize file logger (e.g., using a library or custom implementation)
            println("File logging would be initialized here with: ${config.logFileConfig}")
        }
    }

    /**
     * 更新部分配置项，例如在Debug和Release版本间切换
     */
    fun updateConfig(block: LogConfig.() -> Unit) {
        config.apply(block)
    }

    fun getConfig(): LogConfig = config


    // --- 核心日志打印方法 ---

    fun v(tag: String? = null, message: String, throwable: Throwable? = null) {
        log(LogLevel.VERBOSE, tag, message, throwable)
    }

    fun d(tag: String? = null, message: String, throwable: Throwable? = null) {
        log(LogLevel.DEBUG, tag, message, throwable)
    }

    fun i(tag: String? = null, message: String, throwable: Throwable? = null) {
        log(LogLevel.INFO, tag, message, throwable)
    }

    fun w(tag: String? = null, message: String, throwable: Throwable? = null) {
        log(LogLevel.WARN, tag, message, throwable)
    }

    fun e(tag: String? = null, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, throwable)
    }

    fun wtf(tag: String? = null, message: String, throwable: Throwable? = null) {
        log(LogLevel.ASSERT, tag, message, throwable)
    }

    private fun log(level: LogLevel, customTag: String?, message: String, throwable: Throwable?) {
        if (!config.enabled || level.priority < config.currentLevel.priority) {
            return
        }

        val finalTag = customTag ?: config.globalTag
        val logMessage = buildLogMessage(message)

        // 实际打印到 Logcat
        when (level) {
            LogLevel.VERBOSE -> if (throwable == null) Log.v(finalTag, logMessage) else Log.v(
                finalTag, logMessage, throwable
            )

            LogLevel.DEBUG -> if (throwable == null) Log.d(
                finalTag, logMessage
            ) else Log.d(finalTag, logMessage, throwable)

            LogLevel.INFO -> if (throwable == null) Log.i(finalTag, logMessage) else Log.i(
                finalTag, logMessage, throwable
            )

            LogLevel.WARN -> if (throwable == null) Log.w(finalTag, logMessage) else Log.w(
                finalTag, logMessage, throwable
            )

            LogLevel.ERROR -> if (throwable == null) Log.e(
                finalTag, logMessage
            ) else Log.e(finalTag, logMessage, throwable)

            LogLevel.ASSERT -> if (throwable == null) Log.wtf(finalTag, logMessage) else Log.wtf(
                finalTag, logMessage, throwable
            )

            LogLevel.NONE -> Unit // Do nothing
        }

        // (高级功能占位) 如果启用了文件日志，则写入文件
        if (config.logToFileEnabled) {
            // TODO: Write to file: formatLogForFile(level, finalTag, logMessage, throwable)
        }
    }

    private fun buildLogMessage(message: String): String {
        val builder = StringBuilder()
        if (config.showThreadInfo) {
            builder.append("[${Thread.currentThread().name}] ")
        }
        if (config.stackTraceDepth > 0) {
            val stackTraceElement = getCallerStackTraceElement()
            if (stackTraceElement != null) {
                builder.append("(${stackTraceElement.fileName}:${stackTraceElement.lineNumber}) ")
                // 你可以进一步格式化，例如只显示方法名
                // builder.append("${stackTraceElement.methodName}(): ")
            }
        }
        builder.append(message)
        return builder.toString()
    }

    /**
     * 获取调用日志方法的堆栈信息
     * 注意：这是一个耗性能的操作，谨慎开启或调整 stackTraceDepth
     */
    private fun getCallerStackTraceElement(): StackTraceElement? {
        // 0: getThreadStackTrace()
        // 1: getCallerStackTraceElement() (this method)
        // 2: buildLogMessage()
        // 3: log()
        // 4: v(), d(), i(), w(), e(), wtf() (the public log function)
        // 5: The actual caller of v(), d(), etc.
        val N = 4 + config.stackTraceDepth // 调整这个索引以匹配调用层级
        val stackTrace = Thread.currentThread().stackTrace
        return if (stackTrace.size > N) {
            stackTrace[N]
        } else {
            null
        }
    }
}

// --- Kotlin 扩展函数，方便调用 ---

fun Any.logV(message: String, throwable: Throwable? = null) {
    KLogger.v(this.javaClass.simpleName, message, throwable)
}

fun Any.logD(message: String, throwable: Throwable? = null) {
    KLogger.d(this.javaClass.simpleName, message, throwable)
}

fun Any.logI(message: String, throwable: Throwable? = null) {
    KLogger.i(this.javaClass.simpleName, message, throwable)
}

fun Any.logW(message: String, throwable: Throwable? = null) {
    KLogger.w(this.javaClass.simpleName, message, throwable)
}

fun Any.logE(message: String, throwable: Throwable? = null) {
    KLogger.e(this.javaClass.simpleName, message, throwable)
}

fun Any.logWtf(message: String, throwable: Throwable? = null) {
    KLogger.wtf(this.javaClass.simpleName, message, throwable)
}

// --- 允许自定义 Tag 的扩展函数 ---
fun Any.logV(tag: String, message: String, throwable: Throwable? = null) {
    KLogger.v(tag, message, throwable)
}

fun Any.logD(tag: String, message: String, throwable: Throwable? = null) {
    KLogger.d(tag, message, throwable)
}

fun Any.logI(tag: String, message: String, throwable: Throwable? = null) {
    KLogger.i(tag, message, throwable)
}

fun Any.logW(tag: String, message: String, throwable: Throwable? = null) {
    KLogger.w(tag, message, throwable)
}

fun Any.logE(tag: String, message: String, throwable: Throwable? = null) {
    KLogger.e(tag, message, throwable)
}

fun Any.logWtf(tag: String, message: String, throwable: Throwable? = null) {
    KLogger.wtf(tag, message, throwable)
}
// ... 为其他级别也添加带自定义 tag 的版本

// --- 全局函数，如果不方便使用 `this.javaClass.simpleName` 作为 Tag ---
fun klogV(message: String, throwable: Throwable? = null) {
    KLogger.v(null, message, throwable) // 使用全局 Tag
}

fun klogD(message: String, throwable: Throwable? = null) {
    KLogger.d(null, message, throwable)
}
// ... 为其他级别也添加全局版本