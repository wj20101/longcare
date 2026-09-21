#!/usr/bin/env bash
set -euo pipefail
if [[ ! -e /dev/kvm ]]; then
  echo "::error::/dev/kvm is unavailable; refusing to run without hardware acceleration."
  exit 1
fi
echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' \
  | sudo tee /etc/udev/rules.d/99-kvm4all.rules
sudo udevadm control --reload-rules
sudo udevadm trigger --name-match=kvm
sudo udevadm settle --timeout=30
if ! test -r /dev/kvm || ! test -w /dev/kvm; then
  echo "::error::/dev/kvm is not readable and writable by the current user."
  exit 1
fi
