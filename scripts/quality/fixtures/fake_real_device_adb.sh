#!/usr/bin/env bash
set -euo pipefail

: "${FAKE_ADB_LOG:?FAKE_ADB_LOG is required}"
: "${FAKE_DEVICE_SERIAL:?FAKE_DEVICE_SERIAL is required}"
printf '%s\n' "$*" >> "${FAKE_ADB_LOG}"

if [[ "${1:-}" == "devices" ]]; then
  printf 'List of devices attached\n%s\tdevice\n' "${FAKE_DEVICE_SERIAL}"
  exit 0
fi

if [[ "${1:-}" != "-s" || "${2:-}" != "${FAKE_DEVICE_SERIAL}" ]]; then
  echo "fake adb requires the explicit fixture serial" >&2
  exit 2
fi
shift 2

if [[ "${1:-}" == "install" ]]; then
  echo "Success"
  exit 0
fi

if [[ "${1:-}" == "logcat" ]]; then
  if [[ "${2:-}" == "-c" ]]; then
    exit 0
  fi
  if [[ "${FAKE_LOG_MODE:-clean}" == "forbidden" ]]; then
    echo "08-31 10:00:00.000 FATAL EXCEPTION token=top-secret phone=13800138000 https://example.test/path?access_token=leak ${FAKE_DEVICE_SERIAL}"
  else
    echo "08-31 10:00:00.000 I LongCare: registered target reached"
  fi
  exit 0
fi

if [[ "${1:-}" == "shell" && "${2:-}" == "getprop" ]]; then
  case "${3:-}" in
    ro.kernel.qemu) echo "0" ;;
    ro.build.version.sdk) echo "36" ;;
    ro.product.cpu.abi) echo "arm64-v8a" ;;
    ro.build.fingerprint) echo "google/fixture/fixture:16/ABC/123:user/release-keys" ;;
    ro.product.model) echo "Fixture Phone" ;;
    *) exit 1 ;;
  esac
  exit 0
fi

if [[ "${1:-}" == "shell" && "${2:-}" == "cat" ]]; then
  if [[ "${3:-}" == "/sys/devices/system/cpu/present" ]]; then
    echo "0-7"
  else
    printf 'processor\t: 0\nprocessor\t: 1\n'
  fi
  exit 0
fi

if [[ "${1:-}" == "shell" && "${2:-}" == "dumpsys" ]]; then
  if [[ "${3:-}" == "battery" ]]; then
    printf 'AC powered: false\nUSB powered: false\nWireless powered: false\nstatus: 3\nlevel: 90\n'
  else
    echo "Thermal Status: 0"
  fi
  exit 0
fi

if [[ "${1:-}" == "shell" && "${2:-}" == "pidof" ]]; then
  echo "4242"
  exit 0
fi

if [[ "${1:-}" == "shell" && ( "${2:-}" == "am" || "${2:-}" == "monkey" ) ]]; then
  exit 0
fi

echo "unsupported fake adb command: $*" >&2
exit 2
