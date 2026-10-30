#!/usr/bin/env python3
"""Validate the .lang files of each locale against the source locale (en-US).

The Crowdin exports only the translated strings when skip_untranslated_strings
is enabled. Therefore, missing keys are warnings by default; for a release,
run with --strict to turn them into an error.

Always fatal errors:
- duplicate key;
- key that does not exist in en-US;
- empty value;
- changed placeholder;
- file with BOM or invalid UTF-8;
- line without '='.

Usage:
    python3 tools/check_locales.py
    python3 tools/check_locales.py --strict
"""

import argparse
import os
import re
import sys
from collections import Counter

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG_DIR = os.path.join(REPO, "src/main/resources/Server/Languages")
SOURCE_LOCALE = "en-US"
PLACEHOLDER_RE = re.compile(r"\{[A-Za-z0-9_]+\}")


class LangFile:
    def __init__(self, locale, namespace, path):
        self.locale = locale
        self.namespace = namespace
        self.path = path
        self.entries = []
        self.duplicates = []
        self.empty = []
        self.malformed = []
        self.bom = False
        self.decode_error = None
        self._read()

    def _read(self):
        try:
            raw = open(self.path, "rb").read()
        except OSError as exc:
            self.decode_error = str(exc)
            return

        self.bom = raw.startswith(b"\xef\xbb\xbf")
        try:
            text = raw.decode("utf-8-sig")
        except UnicodeDecodeError as exc:
            self.decode_error = str(exc)
            return

        seen = set()
        for line_no, line in enumerate(text.splitlines(), 1):
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue
            if "=" not in line:
                self.malformed.append((line_no, stripped))
                continue

            key, value = line.split("=", 1)
            key = key.strip()
            value = value.strip()
            if not key:
                self.malformed.append((line_no, stripped))
                continue

            if key in seen:
                self.duplicates.append(key)
            seen.add(key)

            if not value:
                self.empty.append(key)

            self.entries.append((line_no, key, value))

    def values(self):
        # Last occurrence wins, but duplicates are reported separately.
        return {key: value for _, key, value in self.entries}


def locale_files(locale):
    """Return {namespace: path} for the .lang files of a locale."""
    directory = os.path.join(LANG_DIR, locale)
    if not os.path.isdir(directory):
        return {}

    return {
        name[:-5]: os.path.join(directory, name)
        for name in sorted(os.listdir(directory))
        if name.endswith(".lang")
    }


def placeholders(value):
    return Counter(PLACEHOLDER_RE.findall(value))


def format_counter(counter):
    if not counter:
        return "none"
    return ", ".join(
        f"{key} x{count}" if count > 1 else key
        for key, count in sorted(counter.items())
    )


def plural(count, singular, plural_form=None):
    if count == 1:
        return singular
    return plural_form or singular + "s"


def main():
    parser = argparse.ArgumentParser(
        description="Validate the .lang files against the source locale (en-US)."
    )
    parser.add_argument(
        "--strict",
        action="store_true",
        help="fail also when a key from en-US is missing in a locale",
    )
    args = parser.parse_args()

    source_files = locale_files(SOURCE_LOCALE)
    if not source_files:
        print(f"ERROR: source locale {SOURCE_LOCALE} not found in {LANG_DIR}")
        return 1

    source_data = {}
    source_errors = []
    for namespace, path in sorted(source_files.items()):
        lang = LangFile(SOURCE_LOCALE, namespace, path)
        if lang.decode_error:
            source_errors.append(f"{SOURCE_LOCALE}/{namespace}.lang: {lang.decode_error}")
            continue
        if lang.bom:
            source_errors.append(f"{SOURCE_LOCALE}/{namespace}.lang: BOM UTF-8 found")
        for line_no, line in lang.malformed:
            source_errors.append(
                f"{SOURCE_LOCALE}/{namespace}.lang:{line_no}: line without '=': {line!r}"
            )
        if lang.duplicates:
            source_errors.append(
                f"{SOURCE_LOCALE}/{namespace}.lang: duplicate keys: "
                + ", ".join(lang.duplicates)
            )
        if lang.empty:
            source_errors.append(
                f"{SOURCE_LOCALE}/{namespace}.lang: empty values: "
                + ", ".join(lang.empty)
            )

        values = lang.values()
        source_data[namespace] = values
        print(
            f"Source {SOURCE_LOCALE}/{namespace}.lang: "
            f"{len(values)} {plural(len(values), 'key')}"
        )

    if source_errors:
        print("\nErrors in source locale:\n")
        for error in source_errors:
            print("  " + error)
        return 1

    locales = sorted(
        name
        for name in os.listdir(LANG_DIR)
        if os.path.isdir(os.path.join(LANG_DIR, name)) and name != SOURCE_LOCALE
    )
    if not locales:
        print(f"No target locale found in {LANG_DIR}.")
        return 0

    all_errors = []
    all_warnings = []

    for locale in locales:
        target_files = locale_files(locale)
        locale_errors = []
        locale_warnings = []
        total_keys = 0

        extra_files = sorted(set(target_files) - set(source_files))
        missing_files = sorted(set(source_files) - set(target_files))

        for namespace in extra_files:
            locale_errors.append(
                f"{locale}/{namespace}.lang: file does not exist in {SOURCE_LOCALE}"
            )
        for namespace in missing_files:
            locale_warnings.append(
                f"{locale}/{namespace}.lang: entire file missing"
            )

        for namespace, source_path in sorted(source_files.items()):
            target_path = target_files.get(namespace)
            if target_path is None:
                continue

            source_values = source_data[namespace]
            target = LangFile(locale, namespace, target_path)
            target_values = target.values()
            total_keys += len(target_values)

            if target.decode_error:
                locale_errors.append(f"{locale}/{namespace}.lang: {target.decode_error}")
                continue
            if target.bom:
                locale_errors.append(f"{locale}/{namespace}.lang: BOM UTF-8 found")
            for line_no, line in target.malformed:
                locale_errors.append(
                    f"{locale}/{namespace}.lang:{line_no}: line without '=': {line!r}"
                )
            if target.duplicates:
                locale_errors.append(
                    f"{locale}/{namespace}.lang: duplicate keys: "
                    + ", ".join(target.duplicates)
                )
            if target.empty:
                locale_errors.append(
                    f"{locale}/{namespace}.lang: empty values: "
                    + ", ".join(target.empty)
                )

            extra_keys = sorted(set(target_values) - set(source_values))
            if extra_keys:
                locale_errors.append(
                    f"{locale}/{namespace}.lang: keys that do not exist in "
                    f"{SOURCE_LOCALE}: " + ", ".join(extra_keys)
                )

            missing_keys = sorted(set(source_values) - set(target_values))
            if missing_keys:
                shown = ", ".join(missing_keys[:10])
                if len(missing_keys) > 10:
                    shown += " ..."
                locale_warnings.append(
                    f"{locale}/{namespace}.lang: {len(missing_keys)} "
                    f"{plural(len(missing_keys), 'key')} missing: {shown}"
                )

            for key in sorted(set(source_values) & set(target_values)):
                expected = placeholders(source_values[key])
                actual = placeholders(target_values[key])
                if expected == actual:
                    continue

                details = []
                missing_placeholders = expected - actual
                extra_placeholders = actual - expected
                if missing_placeholders:
                    details.append(
                        "missing " + format_counter(missing_placeholders)
                    )
                if extra_placeholders:
                    details.append(
                        "extra " + format_counter(extra_placeholders)
                    )
                locale_errors.append(
                    f"{locale}/{namespace}.lang: placeholders "
                    f"for '{key}' do not match: " + "; ".join(details)
                )

        status = f"{total_keys} {plural(total_keys, 'key')}"
        if locale_warnings:
            status += (
                f", {len(locale_warnings)} "
                f"{plural(len(locale_warnings), 'warning')}"
            )
        if locale_errors:
            status += (
                f", {len(locale_errors)} {plural(len(locale_errors), 'error')}"
            )
        print(f"Locale {locale}: {status}")

        for warning in locale_warnings:
            print("  WARNING: " + warning)
        for error in locale_errors:
            print("  ERROR: " + error)

        all_warnings.extend(locale_warnings)
        all_errors.extend(locale_errors)

    if args.strict:
        all_errors.extend("strict: " + warning for warning in all_warnings)

    if all_errors:
        print(
            f"\n{len(all_errors)} {plural(len(all_errors), 'problem')} found."
        )
        return 1

    if all_warnings:
        print(
            "\nWarnings do not block the build. Use --strict to require "
            "complete parity before a release."
        )
        return 0

    print("\nNo problems found.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
