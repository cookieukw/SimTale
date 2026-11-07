#!/usr/bin/env python3
"""Keep locale keys that Crowdin omitted from a translations download.

Crowdin is configured with ``skip_untranslated_strings: true``. That means the
downloaded target files only contain keys that already have a translation in
Crowdin. Any key that exists in the repository but is not in Crowdin yet would
be deleted from the target file when the bot PR is created.

This script merges the downloaded files with the base branch: every key that was
already present in the base branch is restored if the download dropped it.
Translations that Crowdin did provide win; only missing keys are copied back.
"""

import argparse
import subprocess
import sys
from pathlib import Path

LANG_ROOT = Path("src/main/resources/Server/Languages")
SOURCE_LOCALE = "en-US"


def parse_entries(text):
    """Return {key: line} in file order, ignoring comments and malformed lines."""
    entries = {}
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in line:
            continue
        key = line.split("=", 1)[0].strip()
        if key:
            entries[key] = line
    return entries


def base_text(ref, path):
    try:
        return subprocess.check_output(
            ["git", "show", f"{ref}:{path}"], text=True, stderr=subprocess.DEVNULL
        )
    except subprocess.CalledProcessError:
        return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--base",
        default="origin/main",
        help="Git ref with the current target files (default: origin/main)",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="only report what would be restored; do not write files",
    )
    args = parser.parse_args()

    if not LANG_ROOT.is_dir():
        print(f"ERROR: {LANG_ROOT} not found", file=sys.stderr)
        return 1

    restored_total = 0
    changed_files = 0

    for path in sorted(LANG_ROOT.glob("*/*.lang")):
        locale = path.parent.name
        if locale == SOURCE_LOCALE:
            continue

        reference_text = base_text(args.base, path.as_posix())
        if reference_text is None:
            continue

        reference = parse_entries(reference_text)
        current_text = path.read_text(encoding="utf-8")
        current = parse_entries(current_text)

        missing = [key for key in reference if key not in current]
        if not missing:
            continue

        restored_total += len(missing)
        changed_files += 1
        print(f"{path}: restoring {len(missing)} missing key(s)")

        if args.check:
            continue

        addition = "".join(reference[key] + "\n" for key in missing)
        if current_text and not current_text.endswith("\n"):
            current_text += "\n"
        path.write_text(current_text + addition, encoding="utf-8")

    if args.check:
        print(f"\nWould restore {restored_total} key(s) across {changed_files} file(s).")
    else:
        print(f"\nRestored {restored_total} key(s) across {changed_files} file(s).")

    return 0


if __name__ == "__main__":
    sys.exit(main())
