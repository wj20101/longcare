#!/usr/bin/env bash

# Shared, read-only parsers for the Android build governance guards.

read_android_settings_release() {
  local settings_file="$1"
  local block_name="$2"

  awk -v wanted_block="${block_name}" '
    function print_release(line) {
      if (line !~ /version[[:space:]]*=[[:space:]]*release\([[:space:]]*[0-9]+[[:space:]]*\)/) {
        return 0
      }
      sub(/^.*release\([[:space:]]*/, "", line)
      sub(/[[:space:]]*\).*$/, "", line)
      print line
      return 1
    }

    $0 ~ "^[[:space:]]*" wanted_block "[[:space:]]*\\{" {
      if (print_release($0)) {
        exit
      }
      in_block = 1
      next
    }

    in_block && $0 ~ /version[[:space:]]*=[[:space:]]*release\([[:space:]]*[0-9]+[[:space:]]*\)/ {
      print_release($0)
      exit
    }

    in_block && $0 ~ /^[[:space:]]*}/ {
      exit
    }
  ' "${settings_file}"
}

read_toml_version() {
  local catalog_file="$1"
  local alias="$2"

  awk -v wanted_alias="${alias}" '
    /^\[versions\][[:space:]]*$/ {
      in_versions = 1
      next
    }

    in_versions && /^\[/ {
      exit
    }

    in_versions && $0 ~ "^[[:space:]]*" wanted_alias "[[:space:]]*=" {
      value = $0
      sub(/^[^=]*=[[:space:]]*"/, "", value)
      sub(/"[[:space:]]*(#.*)?$/, "", value)
      print value
      exit
    }
  ' "${catalog_file}"
}

read_android_settings_plugin_version() {
  local settings_file="$1"

  sed -nE 's/.*id\("com\.android\.settings"\)[[:space:]]+version[[:space:]]+"([^"]+)".*/\1/p' \
    "${settings_file}" | head -n 1
}
