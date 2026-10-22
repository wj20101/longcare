#!/usr/bin/env python3
"""Read retained isolated-device traces; never query or change a device."""
import json
import sys
from collections import Counter
from pathlib import Path

root = Path(sys.argv[1])
if not root.is_dir():
    root = Path(__file__).parent / sys.argv[1]
files = [root / name for name in (
    'background-location-probe.previous.jsonl', 'background-location-probe.jsonl',
    'background-location-probe.current.jsonl') if (root / name).exists()]
rows, seen, duplicates, malformed = [], set(), 0, 0
for file in files:
    for line in file.read_text().splitlines():
        try:
            row = json.loads(line)
            assert isinstance(row, dict) and isinstance(row['elapsedRealtime'], (int, float))
        except (ValueError, KeyError, AssertionError, TypeError):
            malformed += 1
            continue
        key = json.dumps(row, sort_keys=True, separators=(',', ':'))
        if key in seen:
            duplicates += 1
        else:
            seen.add(key)
            rows.append(row)
rows.sort(key=lambda row: row['elapsedRealtime'])
original = root / 'original-state.json'
cutoff = json.loads(original.read_text()).get('captureStartElapsedRealtime', 0) if original.exists() else 0
rows = [row for row in rows if row['elapsedRealtime'] >= cutoff]
tick = lambda row: row.get('locationTickNanos') or 0
time = lambda row: row['elapsedRealtime']
is_success = lambda row: row.get('event') == 'http_end' and row.get('outcome') == 'success'


def identities(events, field):
    result = []
    for row in events:
        value = row.get(field)
        if value is not None and (not result or result[-1]['value'] != value or (field == 'client' and result[-1].get('pid') != row.get('pid'))):
            result.append({'at': time(row), 'value': value, **({'pid': row.get('pid')} if field == 'client' else {})})
    return result


def max_gap(events, start, end):
    points = [start] + sorted(set(time(row) for row in events if start <= time(row) < end)) + [end]
    return max((b - a for a, b in zip(points, points[1:])), default=0)


def window_report(start, end):
    inside = [row for row in rows if start <= time(row) < end]
    callbacks = [row for row in inside if row.get('event') == 'sdk_callback']
    successes = [row for row in callbacks if row.get('errorCode') == 0]
    uploads = [row for row in inside if is_success(row)]
    prior_ticks = {tick(row) for row in rows if time(row) < start and row.get('event') == 'sdk_callback' and tick(row) > 0}
    fresh, visited = [], set(prior_ticks)
    for row in successes:
        value = tick(row)
        if value > 0 and value not in visited:
            fresh.append(row)
            visited.add(value)
    buckets = []
    for left in range(int(start), int(end), 60_000):
        right = min(left + 60_000, end)
        select = lambda events: [row for row in events if left <= time(row) < right]
        buckets.append({'startInclusive': left, 'endExclusive': right,
                        'rawCallbacks': len(select(callbacks)), 'sdkSuccessCallbacks': len(select(successes)),
                        'unseenPositiveSuccessSourceTicks': len(select(fresh)), 'localHttpSuccess': len(select(uploads))})
    before = next((row for row in reversed(rows) if time(row) <= start and isinstance(row.get('uptimeMillis'), (int, float))), None)
    after = next((row for row in rows if time(row) >= end and isinstance(row.get('uptimeMillis'), (int, float))), None)
    cpu = {'windowCpuSleepMs': None, 'note': 'No exact in-window sleep timing is measured; elapsed minus uptime only bounds suspend.'}
    if before is not None and after is not None:
        elapsed = time(after) - time(before)
        uptime = after['uptimeMillis'] - before['uptimeMillis']
        if elapsed >= 0 and uptime >= 0:
            suspend = max(0, elapsed - uptime)
            cpu.update({'coveringTraceElapsedMs': elapsed, 'coveringTraceUptimeMs': uptime,
                        'coveringTraceSuspendEstimateMs': suspend,
                        'coveringStart': time(before), 'coveringEnd': time(after),
                        'windowSuspendLowerBoundMs': max(0, suspend - (elapsed - (end - start))),
                        'windowSuspendUpperBoundMs': min(end - start, suspend),
                        'note': 'Bounds use covering trace endpoints and millisecond clocks; no exact sleep onset, duration or cause is inferred.'})
    baseline = next((row for row in reversed(rows) if time(row) < start), None)
    identity_events = ([baseline] if baseline else []) + inside
    ages = [(time(row) - tick(row) / 1_000_000) for row in successes if tick(row) > 0]
    return {'startInclusive': start, 'endExclusive': end, 'durationMs': end - start,
            'rawCallbacks': len(callbacks), 'sdkSuccessCallbacks': len(successes),
            'nullCallbacks': sum(row.get('errorCode') is None for row in callbacks),
            'sdkErrorCodes': dict(Counter(str(row.get('errorCode')) for row in callbacks if row.get('errorCode') not in (None, 0))),
            'uniquePositiveSourceTicks': len({tick(row) for row in callbacks if tick(row) > 0}),
            'uniquePositiveSuccessfulSourceTicks': len({tick(row) for row in successes if tick(row) > 0}),
            'unseenPositiveSuccessSourceTicks': len(fresh),
            'positiveSuccessfulSourceAgeMsRange': [min(ages), max(ages)] if ages else None,
            'localHttpSuccess': len(uploads), 'buckets': buckets,
            'allCompleteMinuteBucketsHaveLocalSuccess': all(bucket['localHttpSuccess'] > 0 for bucket in buckets if bucket['endExclusive'] - bucket['startInclusive'] == 60_000),
            'boundaryInclusiveMaxGapMs': {'rawCallback': max_gap(callbacks, start, end),
                'sdkSuccessCallback': max_gap(successes, start, end),
                'unseenPositiveSuccessSourceTick': max_gap(fresh, start, end), 'localHttpSuccess': max_gap(uploads, start, end)},
            'pidSequenceIncludingPreWindowBaseline': identities(identity_events, 'pid'),
            'clientSequenceIncludingPreWindowBaseline': identities(identity_events, 'client'),
            'clientCreatesInWindow': [row for row in inside if row.get('event') == 'client_create'],
            'traceBracketsWholeWindow': bool(rows and time(rows[0]) <= start and time(rows[-1]) >= end), 'cpu': cpu}


actions, pending = [], []
for row in rows:
    if row.get('event') == 'action_start':
        pending.append(row)
    elif row.get('event') == 'action_end':
        index = next((i for i, start in enumerate(pending) if start.get('pid') == row.get('pid')), None)
        start = pending.pop(index) if index is not None else None
        actions.append({'start': time(start) if start else None, 'end': time(row),
            'durationMs': time(row) - time(start) if start else None, 'pid': row.get('pid'),
            'clientAtStart': start.get('client') if start else None, 'clientAtEnd': row.get('client'),
            'outcome': row.get('outcome'), 'reason': row.get('reason'), 'accuracyMeters': row.get('accuracy')})
for row in pending:
    actions.append({'start': time(row), 'end': None, 'outcome': 'unmatched_start', 'pid': row.get('pid')})
uploads = [row for row in rows if row.get('event') == 'http_end']
starts = {(row.get('pid'), row.get('request')): row for row in rows if row.get('event') == 'http_start'}
orders = {}
for row in uploads:
    outcomes = orders.setdefault(str(row.get('orderId')), Counter())
    outcomes[str(row.get('outcome'))] += 1
ends = []
for i, row in enumerate(rows):
    if row.get('event') != 'gate' or row.get('name') != 'order' or row.get('value') != 'end':
        continue
    order = row.get('orderId')
    restarted = next((time(later) for later in rows[i + 1:] if later.get('event') == 'gate' and later.get('name') == 'order' and later.get('value') == 'switch' and later.get('orderId') == order), None)
    late = [later for later in rows[i + 1:] if later.get('event') == 'http_end' and later.get('orderId') == order and (restarted is None or time(later) < restarted)]
    ends.append({'orderId': order, 'endedAt': time(row), 'observedThrough': restarted if restarted is not None else (time(rows[-1]) if rows else None),
                 'laterHttpEnds': [{'at': time(later), 'outcome': later.get('outcome'),
                    'requestStartedBeforeEnd': (time(starts[(later.get('pid'), later.get('request'))]) <= time(row)) if (later.get('pid'), later.get('request')) in starts else None} for later in late],
                 'localSuccessAfterEnd': sum(is_success(later) for later in late),
                 'observationDurationMs': max(0, (restarted if restarted is not None else time(rows[-1])) - time(row))})
report = {'device': root.name, 'inputFiles': [file.name for file in files], 'traceRows': len(rows),
          'duplicatesRemoved': duplicates, 'malformedRows': malformed, 'captureStartElapsedRealtime': cutoff,
          'firstTraceElapsedRealtime': time(rows[0]) if rows else None, 'lastTraceElapsedRealtime': time(rows[-1]) if rows else None,
          'scope': 'Local isolated repository counters only; no nursing backend request or geographic ground truth verification. Minute bucket coverage alone does not establish a rolling <=60s success gap.',
          'wholeRunCallbacks': {'raw': sum(row.get('event') == 'sdk_callback' for row in rows),
              'sdkSuccess': sum(row.get('event') == 'sdk_callback' and row.get('errorCode') == 0 for row in rows),
              'uniquePositiveSourceTicks': len({tick(row) for row in rows if row.get('event') == 'sdk_callback' and tick(row) > 0})},
          'actions': actions, 'allHttpEndsByOrderId': orders, 'normalEndObservations': ends,
          'allPidSequence': identities(rows, 'pid'), 'allClientSequence': identities(rows, 'client')}
anchor = root / 'unplug-confirmed.json'
if anchor.exists():
    config = json.loads(anchor.read_text())
    start = config['deviceElapsedRealtimeStart']
    report['unplugWindow'] = window_report(start, start + config['durationMs'])
    report['unplugWindow']['mappingUncertaintyMs'] = config.get('mappingUncertaintyMs')
else:
    report['unplugWindow'] = None
    report['windowNote'] = 'No confirmed unplug window; whole-run counts are not unplugged acceptance evidence.'
print(json.dumps(report, ensure_ascii=False, indent=2))
