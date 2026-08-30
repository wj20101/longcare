#!/usr/bin/env python3
"""Verify every mutually exclusive startup root owns an explicit fully-drawn point."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path


ROOT_CONTRACTS = (
    (
        "first_run_privacy",
        "app/src/main/kotlin/com/ytone/longcare/navigation/PrivacyConsentDialog.kt",
        "expectedRoot = StartupRoot.Privacy",
    ),
    (
        "logged_out",
        "app/src/main/kotlin/com/ytone/longcare/navigation/AppNavGraphsEntry.kt",
        "expectedRoot = StartupRoot.Login",
    ),
    (
        "care_home",
        "app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreen.kt",
        "expectedRoot = StartupRoot.CareHome",
    ),
    (
        "sales_home",
        "app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreen.kt",
        "expectedRoot = StartupRoot.SalesHome",
    ),
)
HELPER = "app/src/main/kotlin/com/ytone/longcare/navigation/StartupFullyDrawn.kt"
SPLASH = "app/src/main/kotlin/com/ytone/longcare/navigation/AppNavigation.kt"
INSTRUMENTATION = (
    "app/src/androidTest/kotlin/com/ytone/longcare/navigation/"
    "StartupFullyDrawnInstrumentationTest.kt"
)


def fail(message: str) -> None:
    print(f"[fully-drawn][FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def read(root: Path, relative: str) -> str:
    path = root / relative
    try:
        return path.read_text(encoding="utf-8")
    except OSError as error:
        fail(f"cannot read {relative}: {error}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-root", type=Path, default=Path(__file__).parents[2])
    args = parser.parse_args()
    root = args.project_root.resolve()

    helper = read(root, HELPER)
    if "ReportDrawnWhen" not in helper or "isExpectedStartupRootReady" not in helper:
        fail("shared fully-drawn helper must gate Activity reporting through root readiness")

    for scenario, relative, marker in ROOT_CONTRACTS:
        source = read(root, relative)
        if source.count(marker) != 1:
            fail(f"missing fully-drawn root for scenario {scenario}: {marker}")

    splash = read(root, SPLASH)
    resolving_marker = "expectedRoot = StartupRoot.ResolvingSession"
    if splash.count(resolving_marker) != 1:
        fail("session resolving Splash must hold fully-drawn reporting")

    relevant_files = {relative for _, relative, _ in ROOT_CONTRACTS} | {SPLASH}
    for relative in relevant_files:
        if "ReportDrawn()" in read(root, relative):
            fail(f"direct unconditional ReportDrawn is forbidden outside the readiness helper: {relative}")

    instrumentation = read(root, INSTRUMENTATION)
    instrumentation_contracts = (
        "fullyDrawn_isHeldUntilExpectedRootThenReportsOnceAcrossStateChanges",
        "fullyDrawn_activityRecreationGetsOneFreshCompletionWithoutBlocking",
        "fullyDrawnReporter.isFullyDrawnReported",
        "scenario.recreate()",
        "assertEquals(1, reportCount.get())",
    )
    for marker in instrumentation_contracts:
        if marker not in instrumentation:
            fail(f"fully-drawn instrumentation contract is missing: {marker}")

    print(
        "[fully-drawn][PASS] resolving holds reporting and privacy, Login, care Home, "
        "and sales Home each release through the shared readiness predicate; instrumentation "
        "covers state changes and Activity recreation."
    )


if __name__ == "__main__":
    main()
