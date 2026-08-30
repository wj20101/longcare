#!/usr/bin/env python3
"""Verify Debug first-party Mock safety and Release source-set isolation."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


DEBUG_ONLY_MARKERS = (
    "DebugPhotoCloudUploader",
    "MissingMockRouteException",
    "MockInterceptor",
    "MockRouteRegistry",
    "MockScenarioProvider",
    "mock-only/not-for-production",
)

REQUIRED_FILES = (
    "app/src/debug/kotlin/com/ytone/longcare/network/interceptor/MockInterceptor.kt",
    "app/src/debug/kotlin/com/ytone/longcare/network/interceptor/MockRouteRegistry.kt",
    "app/src/debug/kotlin/com/ytone/longcare/di/PhotoCloudUploadModule.kt",
    "app/src/release/kotlin/com/ytone/longcare/di/PhotoCloudUploadModule.kt",
    "app/src/test/kotlin/com/ytone/longcare/network/interceptor/MockInterceptorTest.kt",
    "app/src/test/kotlin/com/ytone/longcare/network/interceptor/MockFixtureContractTest.kt",
    "app/src/testDebug/kotlin/com/ytone/longcare/di/DebugPhotoCloudUploadModuleTest.kt",
    "app/src/test/kotlin/com/ytone/longcare/di/AppFlavorInterceptorApplierTest.kt",
    "app/src/test/kotlin/com/ytone/longcare/worker/UpdateWorkerTest.kt",
    "app/src/androidTest/kotlin/com/ytone/longcare/navigation/IdentificationPostVerificationNavigationTest.kt",
    "app/src/androidTest/kotlin/com/ytone/longcare/features/update/ui/AppUpdatePromptTest.kt",
    "app/src/androidTest/kotlin/com/ytone/longcare/di/DebugPhotoCloudUploaderDeviceTest.kt",
    "app/src/androidTest/kotlin/com/ytone/longcare/platform/webview/WebViewEntryInstrumentationTest.kt",
    "feature/location/src/test/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacadeOfflineTest.kt",
)

REQUIRED_DOCUMENT_SNIPPETS = {
    "README.md": (
        "debug.useMockData=false",
        "未知第一方 method/path 会在本地 fail-closed",
        "WebView 仍允许任意格式合法且有 host 的 HTTP(S) 业务地址",
    ),
    "docs/README.md": ("Debug Mock 路由、fixture、第三方/WebView 边界或构建开关",),
    "docs/architecture/tech-stack.md": (
        "MissingMockRouteException",
        ":app:reportDebugMockMode",
        ":app:verifyDebugMockMode",
    ),
    "docs/architecture/system-overview.md": (
        "与 `BASE_URL` 同源的未知请求立即抛出",
        "`DebugPhotoCloudUploader`",
        "第一方 Debug Mock 不限制 WebView host",
    ),
    "docs/architecture/ci-quality-gates.md": (
        "verify_debug_mock_network.py",
        "run_debug_mock_network_contracts.sh",
    ),
    "docs/architecture/roadmap-and-open-gaps.md": (
        "它不替代 AMap、腾讯人脸、QLZ 等第三方 SDK",
    ),
}


def fail(errors: list[str]) -> None:
    for error in errors:
        print(f"[debug-mock-network][FAIL] {error}", file=sys.stderr)
    raise SystemExit(1)


def read_required(root: Path, relative: str, errors: list[str]) -> str:
    path = root / relative
    if not path.is_file():
        errors.append(f"required file is missing: {relative}")
        return ""
    return path.read_text(encoding="utf-8")


def verify_build_configuration(root: Path, errors: list[str]) -> None:
    gradle = read_required(root, "app/build.gradle.kts", errors)
    properties = read_required(root, "gradle.properties", errors)
    requirements = (
        (
            r'gradleProperty\("debug\.useMockData"\)\s*\.orElse\("false"\)',
            "debug.useMockData fallback must be false",
        ),
        (
            r'buildConfigField\("boolean",\s*"USE_MOCK_DATA",\s*"false"\)',
            "Release USE_MOCK_DATA must be hard-coded false",
        ),
        (
            r'buildConfigField\("boolean",\s*"USE_MOCK_DATA",\s*debugUseMockData\.toString\(\)\)',
            "Debug USE_MOCK_DATA must use the single resolved property",
        ),
        (r'register<Exec>\("reportDebugMockMode"\)', "Debug mode report task is missing"),
        (r'register<Exec>\("verifyDebugMockMode"\)', "Debug mode verifier task is missing"),
    )
    for pattern, message in requirements:
        if not re.search(pattern, gradle, flags=re.DOTALL):
            errors.append(message)
    if not re.search(r"(?m)^debug\.useMockData=false\s*$", properties):
        errors.append("gradle.properties must default debug.useMockData=false")


def verify_source_sets(root: Path, errors: list[str]) -> None:
    for relative in REQUIRED_FILES:
        read_required(root, relative, errors)

    assets = root / "app/src/debug/assets/mock"
    if not assets.is_dir() or not any(assets.glob("*.json")):
        errors.append("Debug Mock fixture assets are missing")
    for relative in ("app/src/main/assets/mock", "app/src/release/assets/mock"):
        if (root / relative).exists():
            errors.append(f"Debug Mock fixture directory leaked outside Debug: {relative}")

    release_module = read_required(
        root,
        "app/src/release/kotlin/com/ytone/longcare/di/PhotoCloudUploadModule.kt",
        errors,
    )
    if "DefaultPhotoCloudUploader" not in release_module or "@Binds" not in release_module:
        errors.append("Release photo upload DI must bind DefaultPhotoCloudUploader")
    for marker in ("BuildConfig", "DebugPhotoCloudUploader", "USE_MOCK_DATA"):
        if marker in release_module:
            errors.append(f"Release photo upload DI contains Debug selector marker: {marker}")

    fake = read_required(
        root,
        "app/src/debug/kotlin/com/ytone/longcare/di/PhotoCloudUploadModule.kt",
        errors,
    )
    if "mock-only/not-for-production" not in fake:
        errors.append("Debug upload fake must return an unmistakably non-production key")
    for forbidden_dependency in ("CosRepository", "CosUtils", "TencentCos", "COSClient"):
        if forbidden_dependency in fake:
            errors.append(
                f"Debug upload fake must not initialize or call a vendor upload boundary: {forbidden_dependency}"
            )

    production_roots = (
        root / "app/src/main",
        root / "app/src/release",
        root / "feature",
    )
    for production_root in production_roots:
        if not production_root.exists():
            continue
        for path in production_root.rglob("*"):
            if not path.is_file() or "/src/test" in path.as_posix() or "/src/androidTest" in path.as_posix():
                continue
            if production_root.name == "feature" and "/src/main/" not in path.as_posix():
                continue
            try:
                content = path.read_text(encoding="utf-8")
            except (UnicodeDecodeError, OSError):
                continue
            for marker in DEBUG_ONLY_MARKERS:
                if marker in content:
                    errors.append(
                        f"Debug-only marker leaked into production source {path.relative_to(root)}: {marker}"
                    )

    test_owned_marker = "identification-test-owned-verified"
    for source_root in (root / "app/src/main", root / "app/src/debug", root / "app/src/release"):
        if not source_root.exists():
            continue
        for path in source_root.rglob("*.kt"):
            if test_owned_marker in path.read_text(encoding="utf-8"):
                errors.append(
                    f"test-owned verified identity state leaked into app source: {path.relative_to(root)}"
                )


def verify_release_build_config(path: Path, errors: list[str]) -> None:
    if not path.is_file():
        errors.append(f"Release BuildConfig is missing: {path}")
        return
    content = path.read_text(encoding="utf-8")
    expected = "public static final boolean USE_MOCK_DATA = false;"
    if expected not in content:
        errors.append("generated Release BuildConfig must contain USE_MOCK_DATA = false")
    if "USE_MOCK_DATA = true" in content:
        errors.append("generated Release BuildConfig enables Debug Mock data")


def verify_documents(root: Path, errors: list[str]) -> None:
    for relative, snippets in REQUIRED_DOCUMENT_SNIPPETS.items():
        content = read_required(root, relative, errors)
        for snippet in snippets:
            if snippet not in content:
                errors.append(f"Debug Mock documentation is stale in {relative}: missing {snippet}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-root", type=Path, default=Path("."))
    parser.add_argument("--release-build-config", type=Path)
    args = parser.parse_args()

    root = args.project_root.resolve()
    errors: list[str] = []
    verify_build_configuration(root, errors)
    verify_source_sets(root, errors)
    verify_documents(root, errors)
    if args.release_build_config is not None:
        build_config = args.release_build_config
        if not build_config.is_absolute():
            build_config = root / build_config
        verify_release_build_config(build_config, errors)
    if errors:
        fail(errors)
    print(
        "[debug-mock-network][PASS] default-off mode, Debug-only routes/assets/fake, "
        "Release real uploader binding, focused test boundaries, and documentation are intact."
    )


if __name__ == "__main__":
    main()
