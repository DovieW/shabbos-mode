#!/usr/bin/env python3
"""Validate release inputs before building or accessing signing credentials."""
import re
import sys
from pathlib import Path

root = Path(__file__).resolve().parent.parent
properties = dict(line.strip().split("=", 1) for line in
                  (root / "version.properties").read_text().splitlines()
                  if line.strip() and not line.lstrip().startswith("#"))
version = properties["versionName"]
if not re.fullmatch(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", version):
    sys.exit("versionName must be a three-part version, such as 0.1.0.")
if not properties["versionCode"].isdigit() or int(properties["versionCode"]) < 1:
    sys.exit("versionCode must be a positive integer.")
if len(sys.argv) > 2 or (len(sys.argv) == 2 and sys.argv[1] != f"v{version}"):
    sys.exit(f"The release tag must match version.properties: v{version}.")
if not (root / "docs" / "releases" / f"{version}.md").is_file():
    sys.exit(f"Add release notes at docs/releases/{version}.md.")
print(version)
