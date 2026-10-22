import os
import shutil
import hashlib
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

import argparse
parser = argparse.ArgumentParser(description='Retained Huawei isolated test capture helper; new output directory required.')
parser.add_argument('step')
parser.add_argument('--out', type=Path, required=True)
parser.add_argument('--serial', default=os.environ.get('ANDROID_SERIAL'))
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
    (ROOT / (name + '-power.txt')).write_text(power)
    (ROOT / (name + '-deviceidle.txt')).write_text(text('shell', 'dumpsys', 'deviceidle'))
    notifications = text('shell', 'dumpsys', 'notification')
    records = re.split(r'(?=    NotificationRecord\()', notifications)
    owned = [block.split('  Historical notifications:')[0] for block in records if re.match(r'    NotificationRecord\([^\n]*pkg=' + re.escape(PACKAGE) + r'\b', block)]
    (ROOT / (name + '-notifications.txt')).write_text('\n'.join(owned))
    foreground = []
    for block in re.split(r'(?=  \* ServiceRecord\{)', services):
        match = re.search(r'\* ServiceRecord\{[^\n]*? u\d+ ([^}\s]+)', block)
        if match:
            notif = re.search(r'foregroundId=(\S+)', block)
            foreground.append({'component': match.group(1), 'isForeground': 'isForeground=true' in block, 'notificationId': notif.group(1) if notif else None})
    state = {'phase': name, 'hostWall': time.time(),
             'elapsedRealtime': int(float(text('shell', 'cat', '/proc/uptime').split()[0]) * 1000),
             'asleep': 'mWakefulness=Asleep' in power, 'foregroundService': 'isForeground=true' in services,
             'pid': text('shell', 'pidof', PACKAGE, check=False).strip(), 'counters': counters,
             'realState': real_state(), 'foregroundServices': foreground}
    with (ROOT / 'observations.jsonl').open('a') as out:
        out.write(json.dumps(state) + '\n')
    print(json.dumps(state))


def control(*args):
    print(text('shell', 'run-as', PACKAGE, '/system/bin/am', 'startservice', '--user', '0',
               '-n', COMPONENT, '-a', 'debug.location.PROBE_CONTROL', *args).strip())


step = args.step
if step == 'preflight':
    assert not (ROOT / 'original-state.json').exists(), 'Never overwrite the original state backup'
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
                'deviceidle': text('shell', 'dumpsys', 'deviceidle'),
                'powerSave': text('shell', 'settings', 'get', 'global', 'low_power').strip(),
                'providers': text('shell', 'settings', 'get', 'secure', 'location_providers_allowed').strip(),
                'power': text('shell', 'dumpsys', 'power'),
                'package': text('shell', 'dumpsys', 'package', PACKAGE)}
    (ROOT / 'original-state.json').write_text(json.dumps(original, indent=2))
    print(json.dumps({'safeToTest': True, 'hadProbePreferences': original['hadProbePreferences'], 'realState': state}))
elif step == 'prepare':
    original = json.loads((ROOT / 'original-state.json').read_text())
    assert not real_state()['hasRecoveryOrder'] and not real_state()['hasRecoverySession']
    assert '(nothing)' in text('shell', 'dumpsys', 'activity', 'services', PACKAGE)
    xml = b'<map><boolean name="realSource" value="true"/><boolean name="sdkForeground" value="true"/><int name="boots" value="0"/><int name="uploads" value="0"/><int name="clients" value="0"/><int name="httpRequests" value="0"/></map>\n'
    run('shell', 'run-as', PACKAGE, 'tee', PREF, data=xml)
    text('shell', 'run-as', PACKAGE, 'rm', '-f', 'files/background-location-probe.jsonl', 'files/background-location-probe.previous.jsonl')
    print('Only isolated probe preferences initialized; no permissions or system settings changed')
elif step == 'start':
    print(text('shell', 'run-as', PACKAGE, '/system/bin/am', 'start-foreground-service', '--user', '0',
               '-n', COMPONENT, '-a', 'debug.location.PROBE_START').strip())
    snapshot(step)
elif step == 'control':
    control(*controls)
elif step == 'home':
    text('shell', 'am', 'start', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')
    snapshot(step)
elif step == 'lock':
    assert 'mWakefulness=Asleep' in text('shell', 'dumpsys', 'power'), 'Wait for the manually locked screen'
    snapshot(step)
elif step == 'interrupt':
    snapshot('before-interrupt')
    original = json.loads((ROOT / 'original-state.json').read_text())
    assert real_state()['sha256'] == original['realState']['sha256'], 'Real DataStore changed unexpectedly'
    pid = text('shell', 'pidof', PACKAGE).strip()
    assert pid and ' ' not in pid
    assert not real_state()['hasRecoveryOrder'] and not real_state()['hasRecoverySession']
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
    assert real_state()['sha256'] == original['realState']['sha256'], 'Real DataStore changed unexpectedly'
    assert not real_state()['hasRecoveryOrder'] and not real_state()['hasRecoverySession']
    for trace_name in ['background-location-probe.jsonl', 'background-location-probe.previous.jsonl']:
        original_trace = ROOT / ('original-' + trace_name)
        if original_trace.exists():
            run('shell', 'run-as', PACKAGE, 'tee', 'files/' + trace_name, data=original_trace.read_bytes())
        else:
            text('shell', 'run-as', PACKAGE, 'rm', '-f', 'files/' + trace_name)
    for namespace, name, key in [('system', 'screen_off_timeout', 'screenOffTimeout'),
                                  ('global', 'auto_time', 'autoTime'), ('global', 'auto_time_zone', 'autoTimeZone'),
                                  ('global', 'low_power', 'powerSave'),
                                  ('secure', 'location_providers_allowed', 'providers')]:
        assert text('shell', 'settings', 'get', namespace, name).strip() == original[key]
    print('Original probe preferences restored; real order recovery absent; system settings unchanged')
else:
    snapshot(step)
