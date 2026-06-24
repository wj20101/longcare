#!/usr/bin/env bash

read_gradle_extra_value() {
  local file_path="$1"
  local key="$2"

  awk -v wanted_key="${key}" '
    function trim(value) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
      return value
    }

    function unquote(value) {
      value = trim(value)
      if (value ~ /^".*"$/) {
        value = substr(value, 2, length(value) - 2)
      }
      return value
    }

    $0 ~ "extra\\.set\\(\"" wanted_key "\"" {
      line = $0
      sub(/^.*extra\.set\("[^"]+"[[:space:]]*,[[:space:]]*/, "", line)
      sub(/\)[[:space:]]*$/, "", line)
      print unquote(line)
      exit
    }

    $0 ~ "^[[:space:]]*val[[:space:]]+" wanted_key "[[:space:]]+by[[:space:]]+extra\\(" {
      line = $0
      sub(/^.*extra\(/, "", line)
      sub(/\)[[:space:]]*$/, "", line)
      print unquote(line)
      exit
    }
  ' "${file_path}"
}
