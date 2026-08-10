package com.ytone.longcare.common.utils

import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var enabled: Boolean = true, // 总开关，是否启用日志
    var globalTag: String = "AppLog", // 全局默认Tag
    var currentLevel: LogLevel = LogLevel.VERBOSE, // 当前允许打印的最低日志级别
    var showThreadInfo: Boolean = false, // 是否显示线程信息
    var stackTraceDepth: Int = 1, // 堆栈跟踪深度, 用于获取调用位置，0表示不获取，1表示当前方法，2表示上一层
    var logToFileEnabled: Boolean = false, // (高级功能占位) 是否将日志写入文件
    var logFileConfig: LogFileConfig? = null, // (高级功能占位) 文件日志配置
    var maskSensitiveInfo: Boolean = true // 是否对日志进行脱敏（建议生产环境保持开启）
)

/**
 * (高级功能占位) 文件日志配置
 */
data class LogFileConfig(
    // 建议使用应用私有目录（如 Context.filesDir/cacheDir 的子目录），不要指向外部共享存储
    val directoryPath: String,
    val fileNamePrefix: String = "app_log",
    val maxFileSizeMb: Int = 5,
    val maxHistoryDays: Int = 7
)


object KLogger {

    private var config: LogConfig = LogConfig() // 默认配置
    @Volatile
    private var fileLogWriter: FileLogWriter? = null

    /**
     * 初始化日志配置
     * 建议在 Application.onCreate() 中调用
     */
    fun init(config: LogConfig = LogConfig()) {
        KLogger.config = config
        refreshFileLogger()
    }

    /**
     * 更新部分配置项，例如在Debug和Release版本间切换
     */
    fun updateConfig(block: LogConfig.() -> Unit) {
        config.apply(block)
        refreshFileLogger()
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
        val safeMessage = sanitizeIfNeeded(message)
        val logMessage = buildLogMessage(safeMessage)
        val throwableText = throwable?.let { sanitizeIfNeeded(Log.getStackTraceString(it)) }

        // 实际打印到 Logcat
        val bytesWritten = if (throwableText == null) {
            Log.println(level.priority, finalTag, logMessage)
        } else {
            Log.println(level.priority, finalTag, "$logMessage\n$throwableText")
        }

        // (高级功能占位) 如果启用了文件日志，则写入文件
        if (config.logToFileEnabled && bytesWritten > 0) {
            fileLogWriter?.append(level, finalTag, logMessage, throwableText)
        }
    }

    private fun sanitizeIfNeeded(raw: String): String {
        if (!config.maskSensitiveInfo) return raw
        return sanitizeSensitiveContent(raw)
    }

    private fun sanitizeSensitiveContent(raw: String): String {
        var sanitized = raw
        val faceImageQuotedValueRegex = Regex(
            pattern = """(?i)(["']?face[_-]?img(?:[_-]?url)?["']?\s*[:=]\s*)(?:"[^"]*"|'[^']*')"""
        )
        sanitized = faceImageQuotedValueRegex.replace(sanitized) { match ->
            "${match.groupValues[1]}\"***\""
        }

        val sensitiveKeyValueRegex = Regex(
            pattern = """(?i)(["']?(?:password|pwd|token|access_token|refresh_token|secret|api[_-]?key|authorization|face[_-]?img(?:[_-]?url)?)["']?\s*[:=]\s*["']?)([^"',\s}]+)(["']?)"""
        )
        sanitized = sensitiveKeyValueRegex.replace(sanitized) { match ->
            "${match.groupValues[1]}***${match.groupValues[3]}"
        }

        val bearerTokenRegex = Regex("""(?i)(bearer\s+)([A-Za-z0-9\-._~+/]+=*)""")
        sanitized = bearerTokenRegex.replace(sanitized) { match ->
            "${match.groupValues[1]}***"
        }

        val phoneRegex = Regex("""(?<!\d)1\d{10}(?!\d)""")
        sanitized = phoneRegex.replace(sanitized) { match ->
            val phone = match.value
            "${phone.substring(0, 3)}****${phone.substring(7)}"
        }

        val emailRegex = Regex("""(?i)\b([A-Z0-9._%+-]{1,64})@([A-Z0-9.-]+\.[A-Z]{2,})\b""")
        sanitized = emailRegex.replace(sanitized) { match ->
            val localPart = match.groupValues[1]
            val domain = match.groupValues[2]
            val maskedLocal = when {
                localPart.length <= 1 -> "*"
                localPart.length == 2 -> "${localPart[0]}*"
                else -> "${localPart.take(1)}***${localPart.takeLast(1)}"
            }
            "$maskedLocal@$domain"
        }

        val idCardRegex = Regex("""(?i)(?<![0-9A-Z])[1-9]\d{16}[0-9X](?![0-9A-Z])""")
        sanitized = idCardRegex.replace(sanitized) { match ->
            val id = match.value
            "${id.take(6)}********${id.takeLast(4)}"
        }

        return sanitized
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
        // BEWARE: This method of getting caller info is fragile and performance-intensive.
        // It relies on a fixed call stack depth, which can break if the internal logging call structure changes.
        // Consider alternative approaches for production-critical logging if precise caller info is paramount
        // and performance is a concern (e.g., passing caller info explicitly, or using a library that handles this robustly).

        // Stack trace indices:
        // 0: Thread.getStackTrace()
        // 1: getCallerStackTraceElement() (this method)
        // 2: buildLogMessage()
        // 3: log() (the private core logging method)
        // 4: The public KLogger methods (v, d, i, w, e, wtf)
        // 5: The Any.logX extension function OR the klogX global function
        // 6: The actual call site in user code, if stackTraceDepth is 1 (meaning we want the immediate caller of logX/klogX)
        // So, to get the caller of logX/klogX, we need index 5 + config.stackTraceDepth (where depth 1 means caller of logX)
        // However, if stackTraceDepth is 0 (don't show), this logic is skipped.
        // If stackTraceDepth is 1, we want the element at index 5.
        // If stackTraceDepth is 2, we want the element at index 6 (caller of the caller of logX).
        // The original N = 4 + config.stackTraceDepth was targeting the public KLogger methods (v,d,i..) as depth 1.
        // To target the caller of the extension/global functions:
        val baseDepth = 5 // Depth to reach the logX/klogX call itself
        val targetDepth = baseDepth + config.stackTraceDepth

        val stackTrace = Thread.currentThread().stackTrace
        return if (config.stackTraceDepth > 0 && stackTrace.size > targetDepth) {
            stackTrace[targetDepth]
        } else {
            null
        }
    }

    private fun refreshFileLogger() {
        val fileConfig = config.logFileConfig
        if (!config.logToFileEnabled || fileConfig == null) {
            fileLogWriter = null
            return
        }

        fileLogWriter = try {
            FileLogWriter(fileConfig)
        } catch (e: Exception) {
            Log.e("KLogger", "Failed to initialize file logger: ${e.message}", e)
            null
        }
    }

    private class FileLogWriter(
        private val fileConfig: LogFileConfig
    ) {
        private val lock = Any()
        private val directory = File(fileConfig.directoryPath)
        private val maxFileSizeBytes = fileConfig.maxFileSizeMb.coerceAtLeast(1) * 1024L * 1024L
        private val maxHistoryMillis = fileConfig.maxHistoryDays.coerceAtLeast(1) * 24L * 60L * 60L * 1000L
        private val dayFormatter = SimpleDateFormat("yyyyMMdd", Locale.US)
        private val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

        private var activeDate: String? = null
        private var activeIndex: Int = 0
        private var activeFile: File? = null

        init {
            ensureLogDirectory()
            cleanupExpiredFiles(System.currentTimeMillis())
        }

        fun append(level: LogLevel, tag: String, message: String, throwableText: String?) {
            synchronized(lock) {
                val now = Date()
                val formatted = formatLine(now, level, tag, message, throwableText)
                val bytesNeeded = formatted.toByteArray(Charsets.UTF_8).size + 1
                val targetFile = ensureActiveFile(now, bytesNeeded)

                try {
                    targetFile.appendText(formatted, Charsets.UTF_8)
                    targetFile.appendText("\n", Charsets.UTF_8)
                } catch (e: IOException) {
                    Log.e("KLogger", "Failed to write file log: ${e.message}", e)
                }
            }
        }

        private fun ensureLogDirectory() {
            if (directory.exists()) {
                if (!directory.isDirectory) {
                    throw IllegalStateException("Log directory path is not a directory: ${directory.absolutePath}")
                }
                return
            }
            if (!directory.mkdirs()) {
                throw IllegalStateException("Unable to create log directory: ${directory.absolutePath}")
            }
        }

        private fun cleanupExpiredFiles(nowMillis: Long) {
            val expiry = nowMillis - maxHistoryMillis
            directory.listFiles()?.forEach { file ->
                if (!file.isFile) return@forEach
                if (!file.name.startsWith(fileConfig.fileNamePrefix)) return@forEach
                if (file.lastModified() < expiry) {
                    file.delete()
                }
            }
        }

        private fun ensureActiveFile(now: Date, bytesNeeded: Int): File {
            val currentDate = dayFormatter.format(now)
            val shouldSwitchByDate = activeDate != currentDate
            if (shouldSwitchByDate) {
                activeDate = currentDate
                activeIndex = 0
                cleanupExpiredFiles(now.time)
            }

            var candidate = if (activeFile == null || shouldSwitchByDate) {
                buildLogFile(currentDate, activeIndex)
            } else {
                activeFile!!
            }

            while (candidate.exists() && candidate.length() + bytesNeeded > maxFileSizeBytes) {
                activeIndex += 1
                candidate = buildLogFile(currentDate, activeIndex)
            }

            activeFile = candidate
            return candidate
        }

        private fun buildLogFile(dateToken: String, index: Int): File {
            val suffix = if (index == 0) "" else "_$index"
            return File(directory, "${fileConfig.fileNamePrefix}_${dateToken}$suffix.log")
        }

        private fun formatLine(
            now: Date,
            level: LogLevel,
            tag: String,
            message: String,
            throwableText: String?
        ): String {
            val timestamp = timeFormatter.format(now)
            val base = "$timestamp [${level.name}] [$tag] $message"
            return if (throwableText == null) {
                base
            } else {
                "$base\n$throwableText"
            }
        }
    }
}

// --- Kotlin 扩展函数，方便调用 ---

private fun Any.getClassNameTag(): String = this.javaClass.simpleName

// Unified extension functions with optional custom tag
fun Any.logV(message: String, tag: String? = null, throwable: Throwable? = null) {
    KLogger.v(tag ?: getClassNameTag(), message, throwable)
}

fun Any.logD(message: String, tag: String? = null, throwable: Throwable? = null) {
    KLogger.d(tag ?: getClassNameTag(), message, throwable)
}

fun Any.logI(message: String, tag: String? = null, throwable: Throwable? = null) {
    KLogger.i(tag ?: getClassNameTag(), message, throwable)
}

fun Any.logW(message: String, tag: String? = null, throwable: Throwable? = null) {
    KLogger.w(tag ?: getClassNameTag(), message, throwable)
}

fun Any.logE(message: String, tag: String? = null, throwable: Throwable? = null) {
    KLogger.e(tag ?: getClassNameTag(), message, throwable)
}

fun Any.logWtf(message: String, tag: String? = null, throwable: Throwable? = null) {
    KLogger.wtf(tag ?: getClassNameTag(), message, throwable)
}


// --- 全局函数，如果不方便使用 `this.javaClass.simpleName` 作为 Tag ---
fun klogV(message: String, throwable: Throwable? = null) {
    KLogger.v(null, message, throwable) // 使用全局 Tag
}

fun klogD(message: String, throwable: Throwable? = null) {
    KLogger.d(null, message, throwable)
}

fun klogI(message: String, throwable: Throwable? = null) {
    KLogger.i(null, message, throwable)
}

fun klogW(message: String, throwable: Throwable? = null) {
    KLogger.w(null, message, throwable)
}

fun klogE(message: String, throwable: Throwable? = null) {
    KLogger.e(null, message, throwable)
}

fun klogWtf(message: String, throwable: Throwable? = null) {
    KLogger.wtf(null, message, throwable)
}
