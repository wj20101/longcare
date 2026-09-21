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
name = re.search(r'extra\.set\("appVersionName",\s*"([^"]+)"\)', source)[1]
doc = Path("docs/architecture/tech-stack.md")
updated, count = re.subn(r'(?m)^\| 版本 \| `[^`]+` \|',
                         f'| 版本 | `{name} ({version})` |', doc.read_text())
if count != 1:
    raise SystemExit("Cannot update version documentation")
constants.write_text(source)
doc.write_text(updated)
with open(os.environ["GITHUB_OUTPUT"], "a") as output:
    output.write(f"previous_version_code={previous}\nversion_code={version}\n")
