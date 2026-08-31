#!/usr/bin/env python3
"""
Catches two whole-project mistakes that no single file looks wrong for.

1. Two files in the same package declaring the same top-level thing. Kotlin
   rejects it as "conflicting overloads", but only once the compiler has the
   whole package in front of it — so it sails past any per-file reading and
   fails four minutes into a Gradle build. A duplicate Context.findActivity()
   did exactly that.

2. A string key used from Kotlin that no strings.xml defines. That one is not a
   build failure at all on some paths; it is a missing resource at run time.

Neither needs the Android SDK, which is the point: this runs in seconds, here,
before anything is pushed.
"""

import collections
import os
import re
import sys

SOURCE_ROOT = "app/src/main/java"
STRINGS = "app/src/main/res/values/strings.xml"

PACKAGE = re.compile(r"^package\s+([\w.]+)", re.MULTILINE)
# Top-level declarations only: no leading whitespace, so nested members are out.
TOP_LEVEL = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:public\s+|internal\s+|private\s+)?"
    r"(?:(?:class|object|interface)\s+(\w+)"
    r"|fun\s+(?:<[^>]+>\s*)?(?:([\w.]+)\.)?(\w+)\s*\()",
    re.MULTILINE,
)


def kotlin_files():
    for root, _, names in os.walk(SOURCE_ROOT):
        for name in names:
            if name.endswith(".kt"):
                yield os.path.join(root, name)


def check_conflicts():
    seen = collections.defaultdict(list)
    for path in kotlin_files():
        text = open(path, encoding="utf-8").read()
        package_match = PACKAGE.search(text)
        package = package_match.group(1) if package_match else ""
        for match in TOP_LEVEL.finditer(text):
            type_name, receiver, fun_name = match.groups()
            if type_name:
                key = (package, type_name)
            else:
                # An extension function is only the same declaration as another
                # when the receiver matches too.
                key = (package, f"{receiver or ''}.{fun_name}()")
            seen[key].append(os.path.basename(path))

    failures = []
    for (package, name), files in sorted(seen.items()):
        distinct = sorted(set(files))
        if len(distinct) > 1:
            failures.append(
                f"{package}.{name} is declared in more than one file: "
                + ", ".join(distinct)
            )
    return failures


def check_string_keys():
    defined = set(re.findall(r'name="([\w.]+)"', open(STRINGS, encoding="utf-8").read()))
    used = set()
    for path in kotlin_files():
        text = open(path, encoding="utf-8").read()
        used |= set(re.findall(r"\bR\.string\.(\w+)", text))
    missing = sorted(used - defined)
    return [f"R.string.{name} is used in Kotlin but no strings.xml defines it" for name in missing]


def main() -> int:
    failures = check_conflicts() + check_string_keys()
    if not failures:
        print("No conflicting declarations, and every R.string key exists.")
        return 0
    for failure in failures:
        print(f"ERROR: {failure}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
