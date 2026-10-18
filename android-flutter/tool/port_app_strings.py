#!/usr/bin/env python3
"""Generate the Dart port of android-app's AppStrings.kt.

The Flutter rewrite must preserve the exact English copy of the Kotlin app
(189 fields) without hand-transcribing it. This script reads the Kotlin source
of truth and emits:

  * lib/core/formatting/app_strings.dart   -- the Dart port
  * test/core/app_strings_golden.json      -- a golden snapshot of every field
                                              value, used by the contract test
                                              to prove cross-language parity

Re-run whenever android-app/app/src/main/java/com/reelshort/app/ui/format/AppStrings.kt
changes. Run from the android-flutter directory:

    python tool/port_app_strings.py
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
KOTLIN_SOURCE = (
    REPO_ROOT
    / "android-app"
    / "app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "reelshort"
    / "app"
    / "ui"
    / "format"
    / "AppStrings.kt"
)
DART_OUTPUT = REPO_ROOT / "android-flutter" / "lib" / "core" / "formatting" / "app_strings.dart"
GOLDEN_OUTPUT = REPO_ROOT / "android-flutter" / "test" / "core" / "app_strings_golden.json"

EXPECTED_FIELD_COUNT = 189

KOTLIN_FIELD_RE = re.compile(r"^\s*val\s+(\w+)\s*:\s*String\s*,\s*$")
KOTLIN_PAIR_RE = re.compile(r'(\w+)\s*=\s*"((?:[^"\\]|\\.)*)"')

KOTLIN_ESCAPES = {
    '"': '"',
    "\\": "\\",
    "n": "\n",
    "t": "\t",
    "r": "\r",
    "$": "$",
    "0": "\0",
}


def unescape_kotlin(raw: str) -> str:
    out: list[str] = []
    index = 0
    while index < len(raw):
        char = raw[index]
        if char == "\\" and index + 1 < len(raw):
            index += 1
            out.append(KOTLIN_ESCAPES.get(raw[index], raw[index]))
        else:
            out.append(char)
        index += 1
    return "".join(out)


def escape_dart(value: str) -> str:
    return (
        value.replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("$", "\\$")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    )


def load_source() -> tuple[list[str], dict[str, str], list[tuple[str, list[str]]]]:
    text = KOTLIN_SOURCE.read_text(encoding="utf-8")
    lines = text.splitlines()

    fields: list[str] = []
    pairs: dict[str, str] = {}
    in_data_class = False
    for line in lines:
        if line.startswith("internal data class AppStrings("):
            in_data_class = True
            continue
        if in_data_class and line.startswith(")"):
            in_data_class = False
            continue
        if in_data_class:
            match = KOTLIN_FIELD_RE.match(line)
            if match:
                fields.append(match.group(1))
                continue
        for name, raw in KOTLIN_PAIR_RE.findall(line):
            if name not in pairs:
                pairs[name] = unescape_kotlin(raw)

    groups: list[tuple[str, list[str]]] = []
    for title, tags in re.findall(
        r'SearchDiscoveryGroup\("((?:[^"\\]|\\.)*)",\s*listOf\(([^)]*)\)\)', text
    ):
        group_tags = [
            unescape_kotlin(raw) for raw in re.findall(r'"((?:[^"\\]|\\.)*)"', tags)
        ]
        groups.append((unescape_kotlin(title), group_tags))

    return fields, pairs, groups


def build_dart(fields: list[str], pairs: dict[str, str], groups) -> str:
    missing = [name for name in fields if name not in pairs]
    if missing:
        raise SystemExit(f"Kotlin AppStrings values missing for: {', '.join(missing)}")
    extra = [name for name in pairs if name not in fields]
    if extra:
        raise SystemExit(f"Kotlin AppStrings values without a field: {', '.join(extra)}")

    # Every declared field must carry a value: 1:1 with the Kotlin data class.
    assert len(fields) == EXPECTED_FIELD_COUNT, (
        f"expected {EXPECTED_FIELD_COUNT} AppStrings fields, found {len(fields)}"
    )

    declaration = "\n".join(f"  final String {name};" for name in fields)
    constructor = "\n".join(f"    required this.{name}," for name in fields)
    assignments = "\n".join(
        f"    {name}: '{escape_dart(pairs[name])}'," for name in fields
    )

    group_entries = "\n".join(
        "  SearchDiscoveryGroup(title: '{}', tags: <String>[{}]),".format(
            escape_dart(title),
            ", ".join(f"'{escape_dart(tag)}'" for tag in tags),
        )
        for title, tags in groups
    )

    return f'''// GENERATED FILE - DO NOT EDIT BY HAND.
//
// Source of truth: android-app/app/src/main/java/com/reelshort/app/ui/format/AppStrings.kt
// Regenerate with: python tool/port_app_strings.py
//
// The Flutter rewrite keeps every user-visible English string of the Kotlin app
// in one place, exactly like the original. test/core/app_strings_contract_test.dart
// compares every value below against test/core/app_strings_golden.json, which the
// same generator snapshots straight from the Kotlin source.

import 'package:meta/meta.dart';

import '../../data/models/app_language.dart';

/// All user-visible English copy of the app.
///
/// The app is English-only (see [AppLanguage]); the field set is intentionally
/// identical to the Kotlin `AppStrings` data class so that copy cannot drift
/// between the two clients.
@immutable
class AppStrings {{
{declaration}

  const AppStrings({{
{constructor}
  }});
}}

const AppStrings _english = AppStrings(
{assignments}
);

/// Resolves the copy table for [language].
AppStrings strings(AppLanguage language) => switch (language) {{
  AppLanguage.english => _english,
}};

/// A themed cluster of discovery keywords shown on the Discover screen.
@immutable
class SearchDiscoveryGroup {{
  const SearchDiscoveryGroup({{required this.title, required this.tags}});

  final String title;
  final List<String> tags;
}}

/// Every discovery keyword, flattened.
List<String> searchDiscoveryTags(AppLanguage language) => searchDiscoveryGroups(
  language,
).expand((SearchDiscoveryGroup group) => group.tags).toList();

/// The discovery keyword groups of the Discover screen.
List<SearchDiscoveryGroup> searchDiscoveryGroups(AppLanguage language) =>
    switch (language) {{
      AppLanguage.english => const <SearchDiscoveryGroup>[
{group_entries}
      ],
    }};

/// Whether the Discover screen should render results before the hero/tags.
bool searchShowsResultsFirst(String query, int resultCount) =>
    query.trim().isNotEmpty || resultCount > 0;
'''


def main() -> int:
    if not KOTLIN_SOURCE.exists():
        raise SystemExit(f"Kotlin source not found: {KOTLIN_SOURCE}")

    fields, pairs, groups = load_source()
    dart = build_dart(fields, pairs, groups)

    DART_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    DART_OUTPUT.write_text(dart, encoding="utf-8")

    golden = {
        "source": str(KOTLIN_SOURCE.relative_to(REPO_ROOT)).replace("\\", "/"),
        "field_count": len(fields),
        "fields": {name: pairs[name] for name in fields},
        "search_discovery_groups": [
            {"title": title, "tags": tags} for title, tags in groups
        ],
    }
    GOLDEN_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    GOLDEN_OUTPUT.write_text(
        json.dumps(golden, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    print(f"fields: {len(fields)}")
    print(f"groups: {len(groups)} ({sum(len(t) for _, t in groups)} tags)")
    print(f"wrote  {DART_OUTPUT.relative_to(REPO_ROOT)}")
    print(f"wrote  {GOLDEN_OUTPUT.relative_to(REPO_ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
