"""Prepare the next release version locally; never commits or pushes."""
import os
from pathlib import Path
import re

constants = Path("constants.gradle.kts")
source = constants.read_text()
match = re.search(r'extra\.set\("appVersionCode",\s*(\d+)\)', source)
if not match:
    raise SystemExit("Cannot parse appVersionCode")
previous = int(match[1])
version = previous + 1
source = source[:match.start(1)] + str(version) + source[match.end(1):]
constants.write_text(source)
with open(os.environ["GITHUB_OUTPUT"], "a") as output:
    output.write(f"previous_version_code={previous}\nversion_code={version}\n")
