#!/usr/bin/env python3
"""Run fictional Phase 8 fixtures on an explicitly selected Android emulator."""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device', default='emulator-5554')
    parser.add_argument('--mode', choices=['denied', 'inexact', 'granted', 'tap', 'persistTap'], default='granted')
    args = parser.parse_args()
    if not args.device.startswith('emulator-'):
        parser.error('This helper is restricted to Android emulators.')
    sdk = Path(os.environ.get('ANDROID_SDK_ROOT', str(Path.home() / 'Library/Android/sdk')))
    adb = shutil.which('adb') or str(sdk / 'platform-tools/adb')
    mobile = Path(__file__).resolve().parents[1]
    device = [adb, '-s', args.device]
    package = 'com.medtrack.medtrack'
    define = f'--dart-define=NOTIFICATION_TEST_MODE={args.mode}'
    subprocess.run(['flutter', 'build', 'apk', '--debug', '--target=integration_test/phase8_test.dart', define], cwd=mobile, check=True)
    subprocess.run(device + ['install', '-r', str(mobile / 'build/app/outputs/flutter-apk/app-debug.apk')], check=True)
    if args.mode == 'denied':
        subprocess.run(device + ['shell', 'pm', 'revoke', package, 'android.permission.POST_NOTIFICATIONS'], check=True)
    else:
        subprocess.run(device + ['shell', 'pm', 'grant', package, 'android.permission.POST_NOTIFICATIONS'], check=True)
    subprocess.run(device + ['shell', 'appops', 'set', package, 'SCHEDULE_EXACT_ALARM', 'ignore' if args.mode == 'inexact' else 'allow'], check=True)
    log = Path(tempfile.gettempdir()) / f'medtrack-phase8-{args.mode}-{time.time_ns()}.log'
    with log.open('w') as output:
        process = subprocess.Popen(['flutter', 'test', 'integration_test/phase8_test.dart', '-d', args.device, '--no-uninstall', define], cwd=mobile, stdout=output, stderr=subprocess.STDOUT)
        tapped = False
        try:
            deadline = time.monotonic() + 180
            while process.poll() is None:
                if time.monotonic() > deadline:
                    raise TimeoutError('Native test exceeded its observation window.')
                if args.mode in ('tap', 'persistTap') and not tapped and 'PHASE8_NOTIFICATION_READY_FOR_TAP' in log.read_text():
                    subprocess.run(device + ['shell', 'cmd', 'statusbar', 'expand-notifications'], check=True)
                    time.sleep(0.8)
                    subprocess.run(device + ['shell', 'uiautomator', 'dump', '/sdcard/medtrack-notification-test.xml'], check=True, capture_output=True)
                    raw = subprocess.check_output(device + ['shell', 'cat', '/sdcard/medtrack-notification-test.xml'])
                    for node in ET.fromstring(raw).iter('node'):
                        if node.get('text') == 'MedTrack hatırlatması':
                            x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
                            subprocess.run(device + ['shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2)], check=True)
                            tapped = True
                            break
                    if not tapped:
                        raise RuntimeError('The fictional redacted notification was not visible.')
                time.sleep(0.3)
        finally:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=10)
        print(log.read_text())
        print(f'Native test log: {log}')
        if process.returncode != 0:
            raise SystemExit(process.returncode)
        if args.mode in ('tap', 'persistTap') and not tapped:
            raise SystemExit('No OS notification tap was observed.')


if __name__ == '__main__':
    main()
