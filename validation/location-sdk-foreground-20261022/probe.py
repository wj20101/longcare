import os
import shutil
"""Isolated SDK foreground experiment; never changes phone power/location settings.

Each run uses --out A or --out B. Preflight requires --sdk-foreground true/false
and --oem-proof (the already captured Unlimited screenshot/user confirmation).
Use --compare-to A for B; the installed base APK hash and providers must match.
anchor captures a live clock/policy baseline. confirm-unplug --confirmed TEXT
starts the fixed window after the user's lock/unplug reply WITHOUT contacting ADB.
remaining also reads host time only. Do not poll the phone during that window.
"""
import argparse
import hashlib
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

SCRIPT_ROOT = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('step')
parser.add_argument('--out', type=Path, required=True)
parser.add_argument('--serial', default=os.environ.get('ANDROID_SERIAL'))
parser.add_argument('--sdk-foreground', choices=['true', 'false'])
parser.add_argument('--compare-to', type=Path)
parser.add_argument('--oem-proof', type=Path)
parser.add_argument('--confirmed')
parser.add_argument('--duration-ms', type=int, default=300_000)
args, controls = parser.parse_known_args()
ROOT = args.out.resolve()
ROOT.mkdir(parents=True, exist_ok=True)
sdk_root = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
adb_binary = os.environ.get('ADB') or (str(Path(sdk_root) / 'platform-tools' / 'adb') if sdk_root else shutil.which('adb'))
ADB = ([adb_binary, '-s', args.serial] if adb_binary and args.serial else None)
PACKAGE = 'com.ytone.longcare'
COMPONENT = PACKAGE + '/com.ytone.longcare.features.location.testing.BackgroundLocationProbeService'
PREF = 'shared_prefs/background_location_probe.xml'


def run(*args, data=None, check=True):
    if ADB is None:
        raise RuntimeError('Set ANDROID_HOME/ANDROID_SDK_ROOT or PATH for adb and --serial/ANDROID_SERIAL for the target device')
    confirmed = ROOT / 'unplug-confirmed.json'
    if confirmed.exists():
        window = json.loads(confirmed.read_text())
        assert time.monotonic() >= window['hostMonotonic'] + window['durationMs'] / 1000, \
            'Fixed unplug window is active: no ADB queries or controls are permitted'
    result = subprocess.run(ADB + list(args), input=data, capture_output=True, timeout=40)
    if check and result.returncode:
        raise RuntimeError((args, result.returncode, result.stdout.decode(errors='replace'), result.stderr.decode(errors='replace')))
    return result


def text(*args, **kwargs):
    return run(*args, **kwargs).stdout.decode(errors='replace')


def real_state():
    raw = run('shell', 'run-as', PACKAGE, 'cat', 'files/datastore/app_prefs.preferences_pb').stdout
    return {'hasRecoveryOrder': b'location_recovery_order' in raw,
            'hasRecoverySession': b'location_recovery_session' in raw,
            'sha256': hashlib.sha256(raw).hexdigest()}


def parse_whitelist(value, package, uid):
    """Support CLI CSV and full dumpsys sections; never substring-guess a grant."""
    lines = value.splitlines()
    csv_rows = [line.strip().split(',') for line in lines if line.startswith(('user,', 'system,', 'system-excidle,'))]
    if csv_rows:
        return any(len(row) >= 2 and row[0] in ('user', 'system') and row[1] == package for row in csv_rows)
    values = {}
    section = None
    for line in lines:
        title = line.strip().removesuffix(':')
        if line.startswith('  ') and not line.startswith('    ') and line.endswith(':'):
            section = title
            values[section] = []
        elif section is not None and line.startswith('    '):
            values[section].append(line.strip())
    required = ('Whitelist system apps', 'Whitelist user apps', 'Whitelist all app ids')
    if not all(key in values for key in required):
        return None
    by_name = package in values['Whitelist system apps'] or package in values['Whitelist user apps']
    by_uid = str(uid % 100_000) in values['Whitelist all app ids']
    # Divergence is evidence of an unreadable/inconsistent capture, not a false grant.
    return by_name if by_name == by_uid else None


def foreground_services(value):
    result = []
    for block in re.split(r'(?=  \* ServiceRecord\{)', value):
        match = re.search(r'\* ServiceRecord\{[^\n]*? u\d+ ([^}\s]+)', block)
        if match:
            foreground_id = re.search(r'\bforegroundId=(\d+)', block)
            result.append({'component': match.group(1), 'isForeground': 'isForeground=true' in block,
                           'foregroundId': int(foreground_id.group(1)) if foreground_id else None,
                           'bound': 'hasBound=true' in block})
    return result


def live_policy(name):
    dumps = {'package': text('shell', 'dumpsys', 'package', PACKAGE),
             'deviceidle': text('shell', 'dumpsys', 'deviceidle'),
             'netpolicy': text('shell', 'dumpsys', 'netpolicy'),
             'power': text('shell', 'dumpsys', 'power'),
             'services': text('shell', 'dumpsys', 'activity', 'services', PACKAGE)}
    for key, value in dumps.items():
        (ROOT / (name + '-' + key + '.txt')).write_text(value)
    uid = int(re.search(r'\buserId=(\d+)', dumps['package']).group(1))
    paths = [line.removeprefix('package:').strip() for line in text('shell', 'pm', 'path', PACKAGE).splitlines() if line.startswith('package:')]
    assert paths, 'Installed APK path unavailable'
    hashes = {}
    for path in paths:
        result = text('shell', 'sha256sum', path, check=False).split()
        digest = result[0] if result and re.fullmatch('[0-9a-fA-F]{64}', result[0]) else \
            hashlib.sha256(run('exec-out', 'cat', path).stdout).hexdigest()
        hashes[Path(path).name] = digest.lower()
    providers = text('shell', 'settings', 'get', 'secure', 'location_providers_allowed').strip()
    saver = text('shell', 'settings', 'get', 'global', 'low_power').strip()
    restrict = re.search(r'^Restrict background: (\w+)', dumps['netpolicy'], re.MULTILINE)
    result = {'hostWall': time.time(), 'hostMonotonic': time.monotonic(), 'uid': uid,
              'batteryExemptObservedInLiveWhitelist': parse_whitelist(dumps['deviceidle'], PACKAGE, uid),
              'providers': providers, 'powerSaveSetting': saver,
              'dataSaverRestricted': restrict.group(1) if restrict else None,
              'installedApkSha256ByFile': hashes, 'foregroundServices': foreground_services(dumps['services'])}
    (ROOT / (name + '-policy.json')).write_text(json.dumps(result, indent=2))
    return result


def require_baseline(policy):
    config = json.loads((ROOT / 'run-config.json').read_text())
    expected = config['baselinePolicy']
    assert policy['batteryExemptObservedInLiveWhitelist'] is True, 'Baseline system exemption is absent or unknown'
    for key in ('installedApkSha256ByFile', 'providers', 'powerSaveSetting', 'dataSaverRestricted'):
        assert policy[key] == expected[key], f'Unexpected baseline change: {key}'
    return config


def anchor():
    snapshot('before-unplug')
    policy = live_policy('before-unplug-live')
    config = require_baseline(policy)
    app = [s for s in policy['foregroundServices'] if 'BackgroundLocationProbeService' in s['component']]
    sdk = [s for s in policy['foregroundServices'] if 'APSService' in s['component']]
    assert app and app[0]['isForeground'], 'Business foreground service not established'
    assert sdk and sdk[0]['isForeground'] == config['sdkForeground'], 'Actual APS foreground state differs from requested variant'
    if config['sdkForeground']:
        assert sdk[0]['foregroundId'] == 9001, 'APS must own the isolated SDK notification ID 9001'
    trace = ROOT / 'background-location-probe.jsonl'
    values = [json.loads(line) for line in trace.read_text().splitlines()]
    assert values and values[-1].get('batteryExempt') is True, 'App runtime API has not confirmed the same exemption'
    attempts = []
    for _ in range(3):
        before = time.monotonic()
        device = float(text('shell', 'cat', '/proc/uptime').split()[0]) * 1000
        after = time.monotonic()
        attempts.append({'hostMonotonic': (before + after) / 2, 'deviceElapsedRealtime': device,
                         'mappingUncertaintyMs': (after - before) * 500 + 10})
    best = min(attempts, key=lambda item: item['mappingUncertaintyMs'])
    best['durationMs'] = args.duration_ms
    (ROOT / 'unplug-anchor.json').write_text(json.dumps(best, indent=2))
    print(json.dumps({'readyForManualLockAndUnplug': True, 'clockAnchor': best,
                      'sdkForegroundRequested': config['sdkForeground'], 'livePolicy': policy}))


def snapshot(name):
    services = text('shell', 'dumpsys', 'activity', 'services', PACKAGE)
    power = text('shell', 'dumpsys', 'power')
    prefs = run('shell', 'run-as', PACKAGE, 'cat', PREF, check=False)
    counters = {}
    if prefs.returncode == 0:
        counters = {item.attrib['name']: item.text if item.tag == 'string' else item.attrib.get('value')
                    for item in ET.fromstring(prefs.stdout)
                    if item.attrib.get('name') in ('boots', 'clients', 'uploads', 'httpRequests', 'lastUpload', 'realSource', 'orderId', 'orderEnded', 'sdkForeground')}
        (ROOT / (name + '-probe.xml')).write_bytes(prefs.stdout)
    logs = text('logcat', '-d', '-v', 'brief', 'LocationProbe:I', 'DiagnosticEventTracker:I', '*:S')
    boundary = json.loads((ROOT / 'original-state.json').read_text())['captureStartElapsedRealtime']
    parsed = []
    for line in logs.splitlines():
        try:
            value = json.loads(line[line.index('{'):])
        except (ValueError, json.JSONDecodeError):
            continue
        parsed.append((line, value))
    pids = {str(value['pid']) for _, value in parsed if value.get('elapsedRealtime', 0) >= boundary and 'event' in value}
    lines = []
    for line, value in parsed:
        if 'event' in value and value.get('elapsedRealtime', 0) >= boundary:
            lines.append(line)
        elif 'eventCode' in value:
            pid = re.search(r'\(\s*(\d+)\)', line)
            if pid and pid.group(1) in pids:
                lines.append(line)
    for trace_name in ['background-location-probe.previous.jsonl', 'background-location-probe.jsonl']:
        trace_data = run('shell', 'run-as', PACKAGE, 'cat', 'files/' + trace_name, check=False)
        if trace_data.returncode == 0:
            (ROOT / trace_name).write_bytes(trace_data.stdout)
    previous = (ROOT / 'probe.log').read_text().splitlines() if (ROOT / 'probe.log').exists() else []
    (ROOT / 'probe.log').write_text('\n'.join(dict.fromkeys(previous + lines)) + '\n')
    (ROOT / (name + '-services.txt')).write_text(services)
    state = {'phase': name, 'hostWall': time.time(),
             'elapsedRealtime': int(float(text('shell', 'cat', '/proc/uptime').split()[0]) * 1000),
             'asleep': 'mWakefulness=Asleep' in power, 'foregroundService': 'isForeground=true' in services,
             'pid': text('shell', 'pidof', PACKAGE, check=False).strip(), 'counters': counters,
             'realState': real_state()}
    state['foregroundServices'] = foreground_services(services)
    with (ROOT / 'observations.jsonl').open('a') as out:
        out.write(json.dumps(state) + '\n')
    print(json.dumps(state))


def control(*args):
    print(text('shell', 'run-as', PACKAGE, '/system/bin/am', 'startservice', '--user', '0',
               '-n', COMPONENT, '-a', 'debug.location.PROBE_CONTROL', *args).strip())


step = args.step
if step == 'preflight':
    assert not (ROOT / 'original-state.json').exists(), 'Do not overwrite the original state backup; use a fresh run directory'
    assert args.sdk_foreground is not None and args.oem_proof is not None and args.oem_proof.is_file(), \
        'Specify the SDK foreground variant and an existing OEM Unlimited proof file'
    services = text('shell', 'dumpsys', 'activity', 'services', PACKAGE)
    state = real_state()
    for trace_name in ['background-location-probe.jsonl', 'background-location-probe.previous.jsonl']:
        result = run('shell', 'run-as', PACKAGE, 'cat', 'files/' + trace_name, check=False)
        if result.returncode == 0: (ROOT / ('original-' + trace_name)).write_bytes(result.stdout)
    assert '(nothing)' in services, 'An existing app service is running; do not proceed'
    assert not state['hasRecoveryOrder'] and not state['hasRecoverySession'], 'A real order recovery is pending'
    assert not text('shell', 'pidof', PACKAGE, check=False).strip(), 'Target app process already active'
    existing = run('shell', 'run-as', PACKAGE, 'cat', PREF, check=False)
    if existing.returncode == 0:
        (ROOT / 'probe-original.xml').write_bytes(existing.stdout)
    original = {'hadProbePreferences': existing.returncode == 0, 'realState': state,
                'captureStartElapsedRealtime': int(float(text('shell', 'cat', '/proc/uptime').split()[0]) * 1000),
                'screenOffTimeout': text('shell', 'settings', 'get', 'system', 'screen_off_timeout').strip(),
                'autoTime': text('shell', 'settings', 'get', 'global', 'auto_time').strip(),
                'autoTimeZone': text('shell', 'settings', 'get', 'global', 'auto_time_zone').strip(),
                'power': text('shell', 'dumpsys', 'power'),
                'package': text('shell', 'dumpsys', 'package', PACKAGE)}
    (ROOT / 'original-state.json').write_text(json.dumps(original, indent=2))
    policy = live_policy('preflight-live')
    assert policy['batteryExemptObservedInLiveWhitelist'] is True, 'Keep the user-authorized exempt baseline; do not guess it'
    config = {'sdkForeground': args.sdk_foreground == 'true', 'oemPolicy': 'unrestricted_by_user_confirmation',
              'oemProof': str(args.oem_proof.resolve()), 'baselinePolicy': policy}
    if args.compare_to:
        previous = json.loads((args.compare_to / 'run-config.json').read_text())
        for key in ('installedApkSha256ByFile', 'providers', 'powerSaveSetting', 'dataSaverRestricted'):
            assert policy[key] == previous['baselinePolicy'][key], f'A/B baseline differs: {key}'
        assert config['sdkForeground'] != previous['sdkForeground'], 'A/B must differ only in the debug SDK foreground flag'
        config['compareTo'] = str(args.compare_to.resolve())
    (ROOT / 'run-config.json').write_text(json.dumps(config, indent=2))
    print(json.dumps({'safeToTest': True, 'hadProbePreferences': original['hadProbePreferences'], 'realState': state}))
elif step == 'prepare':
    original = json.loads((ROOT / 'original-state.json').read_text())
    assert not real_state()['hasRecoveryOrder'] and not real_state()['hasRecoverySession']
    assert '(nothing)' in text('shell', 'dumpsys', 'activity', 'services', PACKAGE)
    config = require_baseline(live_policy('prepare-live'))
    xml = ('<map><boolean name="realSource" value="true"/><boolean name="sdkForeground" value="' + str(config['sdkForeground']).lower() + '"/><int name="boots" value="0"/><int name="uploads" value="0"/><int name="clients" value="0"/><int name="httpRequests" value="0"/></map>\n').encode()
    run('shell', 'run-as', PACKAGE, 'tee', PREF, data=xml)
    text('shell', 'run-as', PACKAGE, 'rm', '-f', 'files/background-location-probe.jsonl', 'files/background-location-probe.previous.jsonl')
    print('Only isolated probe preferences initialized; no permissions or system settings changed')
elif step == 'start':
    print(text('shell', 'run-as', PACKAGE, '/system/bin/am', 'start-foreground-service', '--user', '0',
               '-n', COMPONENT, '-a', 'debug.location.PROBE_START').strip())
    snapshot(step)
elif step == 'control':
    control(*controls)
elif step == 'policy':
    print(json.dumps(live_policy('live')))
elif step == 'anchor':
    anchor()
elif step == 'confirm-unplug':
    assert args.confirmed, 'Record the actual user confirmation of physical lock/unplug'
    assert not (ROOT / 'unplug-confirmed.json').exists(), 'A fixed window already exists'
    value = json.loads((ROOT / 'unplug-anchor.json').read_text())
    now = time.monotonic()
    assert 0 <= now - value['hostMonotonic'] <= 300, 'Clock anchor is too old; take a fresh anchor before unplug'
    config = json.loads((ROOT / 'run-config.json').read_text())
    confirmed = {'hostMonotonic': now, 'hostWall': time.time(),
                 'deviceElapsedRealtimeStart': round(value['deviceElapsedRealtime'] + (now - value['hostMonotonic']) * 1000),
                 'durationMs': value['durationMs'], 'mappingUncertaintyMs': value['mappingUncertaintyMs'],
                 'boundary': 'after_user_confirmed_physical_unplug_and_lock', 'userConfirmation': args.confirmed,
                 'sdkForegroundRequested': config['sdkForeground']}
    (ROOT / 'unplug-confirmed.json').write_text(json.dumps(confirmed, indent=2))
    print(json.dumps(confirmed))
elif step == 'remaining':
    value = json.loads((ROOT / 'unplug-confirmed.json').read_text())
    print(json.dumps({'remainingMs': max(0, round((value['hostMonotonic'] + value['durationMs'] / 1000 - time.monotonic()) * 1000)),
                      'hostClockOnly': True}))
elif step == 'reconnected':
    snapshot(step)
    print(json.dumps(live_policy('reconnected-live')))
    (ROOT / 'battery-history.txt').write_text(text('shell', 'dumpsys', 'batterystats', '--history'))
elif step == 'home':
    text('shell', 'am', 'start', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')
    snapshot(step)
elif step == 'lock':
    assert 'mWakefulness=Asleep' in text('shell', 'dumpsys', 'power'), 'Wait for the manually locked screen'
    snapshot(step)
elif step == 'interrupt':
    snapshot('before-interrupt')
    pid = text('shell', 'pidof', PACKAGE).strip()
    assert pid and ' ' not in pid
    assert not real_state()['hasRecoveryOrder'] and not real_state()['hasRecoverySession']
    assert real_state()['sha256'] == json.loads((ROOT / 'original-state.json').read_text())['realState']['sha256'], 'Real DataStore unexpectedly changed'
    text('shell', 'run-as', PACKAGE, 'kill', '-9', pid)
    print('Killed only isolated probe app process; awaiting sticky recovery')
elif step == 'stop':
    snapshot('before-stop')
    print(text('shell', 'run-as', PACKAGE, '/system/bin/am', 'startservice', '--user', '0',
               '-n', COMPONENT, '-a', 'debug.location.PROBE_STOP').strip())
elif step == 'restore':
    original = json.loads((ROOT / 'original-state.json').read_text())
    assert '(nothing)' in text('shell', 'dumpsys', 'activity', 'services', PACKAGE), 'Stop isolated probe first'
    # The probe is stopped and the instrumentation/application process must be gone before
    # restoring its on-disk preferences, so a cached SharedPreferences editor cannot overwrite them.
    assert not text('shell', 'pidof', PACKAGE, check=False).strip(), 'App process still active'
    if original['hadProbePreferences']:
        run('shell', 'run-as', PACKAGE, 'tee', PREF, data=(ROOT / 'probe-original.xml').read_bytes())
    else:
        text('shell', 'run-as', PACKAGE, 'rm', '-f', PREF, PREF + '.bak')
    assert not real_state()['hasRecoveryOrder'] and not real_state()['hasRecoverySession']
    assert real_state()['sha256'] == original['realState']['sha256'], 'Real DataStore unexpectedly changed'
    for trace_name in ['background-location-probe.jsonl', 'background-location-probe.previous.jsonl']:
        original_trace = ROOT / ('original-' + trace_name)
        if original_trace.exists():
            run('shell', 'run-as', PACKAGE, 'tee', 'files/' + trace_name, data=original_trace.read_bytes())
        else:
            text('shell', 'run-as', PACKAGE, 'rm', '-f', 'files/' + trace_name)
    for namespace, name, key in [('system', 'screen_off_timeout', 'screenOffTimeout'),
                                  ('global', 'auto_time', 'autoTime'), ('global', 'auto_time_zone', 'autoTimeZone')]:
        assert text('shell', 'settings', 'get', namespace, name).strip() == original[key]
    print('Original probe preferences restored; real order recovery absent; system settings unchanged')
else:
    snapshot(step)
