#!/usr/bin/env python3
"""
Guards the three translations against each other.

Every one of these has broken a build or shipped a crash at some point:

  * a duplicate key, which aapt rejects four minutes into a Gradle build
  * a key present in one language and missing in another, which is a crash the
    moment someone switches language
  * a format specifier that differs between languages, which is an
    IllegalFormatException at the moment the string is shown, in that language
    only, on someone else's phone

Running in seconds here beats finding out in CI, or worse, not finding out.
"""

import re
import sys
import xml.etree.ElementTree as ET

FILES = {
    "he": "app/src/main/res/values/strings.xml",
    "en": "app/src/main/res/values-en/strings.xml",
    "ar": "app/src/main/res/values-ar/strings.xml",
}

SPECIFIER = re.compile(r"%\d+\$[sd]|%[sd]")


def main() -> int:
    failures = []
    keys = {}
    texts = {}

    for lang, path in FILES.items():
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as error:
            failures.append(f"{path} is not valid XML: {error}")
            continue

        names = [
            element.get("name")
            for element in root
            if element.tag in ("string", "plurals", "string-array")
        ]
        duplicates = sorted({name for name in names if names.count(name) > 1})
        if duplicates:
            failures.append(f"{path} defines these keys twice: {', '.join(duplicates)}")

        keys[lang] = set(names)
        texts[lang] = {
            element.get("name"): "".join(element.itertext())
            for element in root
            if element.tag == "string"
        }
        print(f"{lang}: {len(names)} keys")

    if len(keys) < len(FILES):
        return report(failures)

    base = keys["he"]
    for lang in ("en", "ar"):
        missing = sorted(base - keys[lang])
        extra = sorted(keys[lang] - base)
        if missing:
            failures.append(f"{lang} is missing: {', '.join(missing)}")
        if extra:
            failures.append(f"{lang} has keys no other language has: {', '.join(extra)}")

    for name in sorted(texts["he"]):
        found = {
            lang: sorted(SPECIFIER.findall(texts[lang].get(name, "")))
            for lang in FILES
        }
        if len({tuple(value) for value in found.values()}) > 1:
            failures.append(f"{name} has different format specifiers per language: {found}")

    return report(failures)


def report(failures) -> int:
    if not failures:
        print("Strings are consistent across all three languages.")
        return 0
    for failure in failures:
        print(f"ERROR: {failure}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
