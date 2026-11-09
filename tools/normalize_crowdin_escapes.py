#!/usr/bin/env python3
"""Remove Java-Properties escapes that Crowdin exports into Hytale .lang values.

Hytale's language parser treats these backslashes as visible text. Crowdin's
Properties export has added them before punctuation such as !, :, # and = even
though this repository's .lang files are not Java Properties at runtime.
"""

from pathlib import Path
import re

LANG_ROOT = Path("src/main/resources/Server/Languages")
SOURCE_LOCALE = "en-US"
PROPERTIES_ESCAPE = re.compile(r"\\([!:#=])")


def main() -> int:
    changed_files = 0
    removed_escapes = 0
    for path in sorted(LANG_ROOT.glob("*/*.lang")):
        if path.parent.name == SOURCE_LOCALE:
            continue
        original = path.read_text(encoding="utf-8")
        normalized, count = PROPERTIES_ESCAPE.subn(r"\1", original)
        if count:
            path.write_text(normalized, encoding="utf-8")
            changed_files += 1
            removed_escapes += count
            print(f"{path}: removed {count} visible escape(s)")

    print(f"Removed {removed_escapes} Crowdin escape(s) from {changed_files} locale file(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
