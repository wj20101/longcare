package com.ytone.longcare.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prevents product copy from bypassing Android resources at presentation boundaries. */
class UserFacingCopyBoundaryTest {

    private val repositoryRoot =
        File("src/main/kotlin")
            .canonicalFile
            .resolve("../../../..")
            .canonicalFile

    @Test
    fun `production presentation code must not contain hardcoded product copy`() {
        val violations =
            productionKotlinSources()
                .filter(::isPresentationSource)
                .flatMap { file -> violationsIn(file, hardcodedPresentationCopy) }
                .sorted()
                .toList()

        assertTrue(
            "User-facing copy must be resolved from string resources: " +
                violations.joinToString(),
            violations.isEmpty(),
        )
    }

    @Test
    fun `direct message sinks must not receive hardcoded Chinese copy`() {
        val violations =
            productionKotlinSources()
                .flatMap { file -> violationsIn(file, hardcodedMessageSink) }
                .sorted()
                .toList()

        assertTrue(
            "Toast/dialog/message queue copy must be resolved from string resources: " +
                violations.joinToString(),
            violations.isEmpty(),
        )
    }

    private fun violationsIn(
        file: File,
        pattern: Regex,
    ): Sequence<String> {
        val source = file.readText()
        val relativePath = file.relativeTo(repositoryRoot).invariantSeparatorsPath
        return pattern.findAll(source).map { match ->
            val lineNumber = source.substring(0, match.range.first).count { it == '\n' } + 1
            "$relativePath:$lineNumber"
        }
    }

    private fun isPresentationSource(file: File): Boolean {
        val path = file.invariantSeparatorsPath
        return "/ui/" in path ||
            "/presentation/" in path ||
            "/navigation/" in path ||
            file.name.endsWith("ViewModel.kt") ||
            "@Composable" in file.readText()
    }

    private fun productionKotlinSources(): Sequence<File> =
        listOf("app", "core", "feature")
            .asSequence()
            .map { File(repositoryRoot, it) }
            .filter(File::exists)
            .flatMap { root -> root.walkTopDown().asSequence() }
            .filter { file ->
                file.isFile &&
                    file.extension == "kt" &&
                    "/src/main/" in file.invariantSeparatorsPath &&
                    "/build/" !in file.invariantSeparatorsPath &&
                    "Preview" !in file.name
            }

    private companion object {
        val hardcodedPresentationCopy =
            Regex(
                pattern =
                    """(?:Text\s*\(\s*|(?:text|contentDescription|placeholder|title)\s*=\s*)"(?=[^"\n]*[^\s"][^"\n]*")(?!\s*(?:\$\w+|\$\{[^}]+})\s*")[^"\n]*""",
            )

        val hardcodedMessageSink =
            Regex(
                pattern =
                    """(?:showToast|showShort|showLong|enqueue|setTitle|setMessage|setContentTitle|setContentText)\s*\(\s*"(?=[^"\n]*[^\s"][^"\n]*")(?!\s*(?:\$\w+|\$\{[^}]+})\s*")[^"\n]*""",
            )
    }
}
