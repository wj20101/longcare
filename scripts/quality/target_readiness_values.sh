#!/usr/bin/env bash

read_target_readiness_value() {
  local policy_file="$1"
  local wanted_key="$2"

  awk -F'=' -v wanted_key="${wanted_key}" '
    /^[[:space:]]*#/ || /^[[:space:]]*$/ { next }
    {
      key = $1
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", key)
      if (key == wanted_key) {
        value = substr($0, index($0, "=") + 1)
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
        print value
        exit
      }
    }
  ' "${policy_file}"
}
