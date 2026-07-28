package com.ytone.longcare.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkApiBoundaryTest {

    @Test
    fun `every Retrofit API is an explicitly reviewed network boundary`() {
        val appSourceRoot = File("src/main/kotlin").canonicalFile
        assertTrue("App source root not found: ${appSourceRoot.path}", appSourceRoot.exists())
        val repositoryRoot = File(appSourceRoot, "../../../..").canonicalFile

        val retrofitApiFiles =
            listOf("app", "core", "feature")
                .map { File(repositoryRoot, it) }
                .filter(File::exists)
                .flatMap { sourceGroup ->
                    sourceGroup
                        .walkTopDown()
                        .filter { file ->
                            file.isFile &&
                                file.extension == "kt" &&
                                "/src/main/" in file.invariantSeparatorsPath
                        }
                        .filter { file ->
                            file.useLines { lines ->
                                lines.any { line ->
                                    line.trim().startsWith("import retrofit2.http.")
                                }
                            }
                        }
                        .map { file ->
                            file.relativeTo(repositoryRoot).invariantSeparatorsPath
                        }
                        .toList()
                }
                .sorted()

        assertEquals(
            listOf(
                "app/src/main/kotlin/com/ytone/longcare/worker/DownloadWorker.kt",
                "core/data/src/main/kotlin/com/ytone/longcare/api/LongCareApiService.kt",
                "core/data/src/main/kotlin/com/ytone/longcare/api/TencentFaceApiService.kt",
            ),
            retrofitApiFiles,
        )
    }
}
