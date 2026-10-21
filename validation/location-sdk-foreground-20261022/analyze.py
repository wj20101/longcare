"""Offline analysis of one fixed SDK foreground trial; never contacts a device."""
import argparse
import json
import math
from pathlib import Path
import re
import subprocess
import sys


def load_rows(root):
    seen, rows = set(), []
    cutoff = json.loads((root / 'original-state.json').read_text())['captureStartElapsedRealtime']
    for name in ('background-location-probe.previous.jsonl', 'background-location-probe.jsonl'):
        path = root / name
        if not path.exists():
            continue
        for line in path.read_text().splitlines():
            try:
                row = json.loads(line)
                if not isinstance(row, dict) or not isinstance(row.get('elapsedRealtime'), (int, float)):
                    continue
            except (ValueError, TypeError):
                continue
            key = json.dumps(row, sort_keys=True)
            if key not in seen and row.get('elapsedRealtime', 0) >= cutoff:
                rows.append(row)
                seen.add(key)
    return sorted(rows, key=lambda item: item['elapsedRealtime'])


def read_policy(root, name):
    path = root / (name + '-policy.json')
    return json.loads(path.read_text()) if path.exists() else None


def sdk_service(policy):
    return next((item for item in (policy or {}).get('foregroundServices', [])
                 if 'APSService' in item['component']), None)


def gap_metrics(rows, start, end):
    points = sorted(set(row['elapsedRealtime'] for row in rows))
    pairs = [b - a for a, b in zip(points, points[1:])]
    boundary_gaps = [b - a for a, b in zip([start] + points, points + [end])]
    maximum = max(boundary_gaps, default=end - start)
    return {'successCount': len(points), 'maxInternalSuccessGapMs': max(pairs) if pairs else None,
            'firstSuccessDelayMs': points[0] - start if points else None,
            'finalTailMs': end - points[-1] if points else end - start,
            'maxBoundaryInclusiveGapMs': maximum,
            'allFullyObservedMinuteWindowsCovered': bool(points) and maximum <= 60_000,
            'note': 'Minute buckets do not prove rolling coverage. Boundary gaps include the observed start and end; time outside this fixed window is unmeasured.'}


def analyze(root, compare_to=None):
    tool = Path(__file__).with_name('summarize-device.py')
    report = json.loads(subprocess.check_output([sys.executable, str(tool), str(root)], text=True))
    config = json.loads((root / 'run-config.json').read_text())
    window = report['unplugWindow']
    assert window, 'A user-confirmed fixed unplug window is required'
    start, end = window['startInclusive'], window['endExclusive']
    rows = load_rows(root)
    inside = [row for row in rows if start <= row['elapsedRealtime'] < end]
    callbacks = [row for row in inside if row.get('event') == 'sdk_callback']
    successes = [row for row in callbacks if row.get('errorCode') == 0]
    uploads = [row for row in inside if row.get('event') == 'http_end' and row.get('outcome') == 'success']
    positive = [row for row in successes if (row.get('locationTickNanos') or 0) > 0]
    ages = [row['elapsedRealtime'] - row['locationTickNanos'] / 1_000_000 for row in positive]
    accuracy = [row['accuracy'] for row in successes if isinstance(row.get('accuracy'), (int, float)) and math.isfinite(row['accuracy'])]
    diagnostics = []
    pids = {str(row['pid']) for row in rows}
    for line in (root / 'probe.log').read_text().splitlines():
        try:
            value = json.loads(line[line.index('{'):])
        except (ValueError, json.JSONDecodeError):
            continue
        pid = re.search(r'\(\s*(\d+)\)', line)
        if value.get('category') == 'location' and pid and pid.group(1) in pids:
            diagnostics.append(value)
    before = read_policy(root, 'before-unplug-live')
    after = read_policy(root, 'reconnected-live')
    before_sdk, after_sdk = sdk_service(before), sdk_service(after)
    sdk_flag = config['sdkForeground']
    apk_match = before is not None and before['installedApkSha256ByFile'] == config['baselinePolicy']['installedApkSha256ByFile']
    if after:
        apk_match = apk_match and after['installedApkSha256ByFile'] == config['baselinePolicy']['installedApkSha256ByFile']
    report['sdkForegroundExperiment'] = {
        'requested': sdk_flag,
        'baselineBeforeWindow': before,
        'baselineAfterReconnect': after,
        'businessAndSdkServicesObservedSeparately': True,
        'sdkBeforeWindow': before_sdk,
        'sdkAfterReconnect': after_sdk,
        'sdkBeforeWindowMatchesVariant': bool(before_sdk) and before_sdk['isForeground'] == sdk_flag and
            (not sdk_flag or before_sdk.get('foregroundId') == 9001),
        'installedApkMatchesPreflight': apk_match,
        'sdkForegroundRequests': [row for row in rows if row.get('event', '').startswith('sdk_fg_')],
        'foregroundStateLimit': 'Request trace means the API was called, not that Android acknowledged it. Actual APSService foreground state is observed at endpoints; it is not polled while unplugged.',
    }
    report['unplugWindow']['providerMetadata'] = {
        'successCallbacks': len(successes), 'positiveSourceTicks': len(positive),
        'unknownSourceTicks': len(successes) - len(positive),
        'sourceAgeAtRawCallbackMsRange': [min(ages), max(ages)] if ages else None,
        'futureSourceTicks': sum(age < 0 for age in ages),
        'sourceAgeOverReporting60s': sum(age > 60_000 for age in ages),
        'accuracyMetersRange': [min(accuracy), max(accuracy)] if accuracy else None,
        'callbackTypes': sorted({row.get('locationType') for row in successes if row.get('locationType') is not None}),
        'note': 'Raw trace omits coordinates/mock/coordType for privacy. Do not call a raw point business-valid solely from accuracy or a unique tick. Actual AddPosition local dispatches and diagnostics apply app quality rules.',
    }
    report['unplugWindow']['rollingLocalUploadCoverage'] = gap_metrics(uploads, start, end)
    report['qualityAndHttpDiagnostics'] = diagnostics
    report['diagnosticScope'] = 'Health counters are cumulative per service/session. They are retained evidence, not reconstructed exact per-minute rejection counts.'
    report['captureWarnings'] = []
    if report.get('malformedRows'):
        report['captureWarnings'].append('Malformed persistent trace rows were skipped; zero observed callbacks alone cannot establish complete capture.')
    if not window['traceBracketsWholeWindow']:
        report['captureWarnings'].append('Persistent trace does not bracket the entire fixed window; acceptance is incomplete.')
    report['acceptanceScope'] = 'Successful HTTP records are local isolated repository completions after app validation; they do not prove a remote care API request or geographic correctness.'
    report['comparison'] = None
    if compare_to:
        previous = json.loads((compare_to / 'run-config.json').read_text())
        keys = ('installedApkSha256ByFile', 'providers', 'powerSaveSetting', 'dataSaverRestricted')
        report['comparison'] = {
            'otherRun': str(compare_to),
            'sdkFlagDiffers': sdk_flag != previous['sdkForeground'],
            'baselineDifferences': [key for key in keys if config['baselinePolicy'][key] != previous['baselinePolicy'][key]],
            'oemBaseline': 'Both require user-confirmed unrestricted; no private OEM permission state is inferred.',
        }
    (root / 'summary.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run', type=Path)
    parser.add_argument('--compare-to', type=Path)
    args = parser.parse_args()
    result = analyze(args.run.resolve(), args.compare_to.resolve() if args.compare_to else None)
    print(json.dumps({'report': str(args.run / 'summary.json'),
                      'rawCallbacks': result['unplugWindow']['rawCallbacks'],
                      'uniquePositiveTicks': result['unplugWindow']['uniquePositiveSuccessfulSourceTicks'],
                      'localSuccess': result['unplugWindow']['localHttpSuccess'],
                      'rolling': result['unplugWindow']['rollingLocalUploadCoverage'],
                      'sdkForeground': result['sdkForegroundExperiment']['sdkBeforeWindowMatchesVariant'],
                      'sameApk': result['sdkForegroundExperiment']['installedApkMatchesPreflight']}, ensure_ascii=False))
