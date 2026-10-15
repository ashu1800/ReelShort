"""Generate the Dart port of AppUiState.kt (enums + state class + copyWith).

Hand-transcribing a 60-field state class is where parity silently rots: a single
forgotten field becomes a screen that never updates. This generator reads the
Kotlin source and emits the Dart state class plus a golden JSON snapshot that a
contract test asserts against.

Usage (from android-flutter/):
    python tool/port_app_ui_state.py
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
KOTLIN_SOURCE = (
    REPO_ROOT
    / "android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppUiState.kt"
)
DART_OUTPUT = REPO_ROOT / "android-flutter/lib/state/app_ui_state.dart"
GOLDEN_OUTPUT = REPO_ROOT / "android-flutter/test/state/app_ui_state_golden.json"

ENUM_RE = re.compile(r"enum\s+class\s+(\w+)\s*\{(.*?)\}", re.DOTALL)
FIELD_RE = re.compile(r"^\s*val\s+(\w+)\s*:\s*(.+?)\s*=\s*(.+?),\s*$")

# Kotlin type -> (Dart type, is_nullable)
SIMPLE_TYPES = {
    "Boolean": "bool",
    "String": "String",
    "Long": "int",
    "Int": "int",
    "PlaybackState": "PlaybackState",
    "BookInteractionState": "BookInteractionState",
    "AppLanguage": "AppLanguage",
    "AppScreen": "AppScreen",
    "AuthMode": "AuthMode",
    "AccountOperation": "AccountOperation",
    "UiMessageType": "UiMessageType",
    "ApiHealthStatus": "ApiHealthStatus",
    "AuthSession": "AuthSession",
    "SavedCredentials": "SavedCredentials",
    "EpisodeSummary": "EpisodeSummary",
    "BookSummary": "BookSummary",
    "Comment": "Comment",
    "RechargeOrderSummary": "RechargeOrderSummary",
    "WatchRecord": "WatchRecord",
    "VipOrder": "VipOrder",
    "WithdrawalRecord": "WithdrawalRecord",
    "VideoUrl": "VideoUrl",
    "PointAccount": "PointAccount",
    "WalletInfo": "WalletInfo",
    "WithdrawalSummary": "WithdrawalSummary",
}

# Defaults that need an explicit Dart spelling.
DEFAULT_OVERRIDES = {
    "AppLanguage.DEFAULT": "AppLanguage.defaultLanguage",
}


def camel_case(member: str) -> str:
    parts = [part for part in member.split("_") if part]
    if not parts:
        raise ValueError(f"cannot convert enum member {member!r}")
    return parts[0].lower() + "".join(part.capitalize() for part in parts[1:])


def dart_enum_member(reference: str) -> str:
    """`AppScreen.VIP_RECHARGE` -> `AppScreen.vipRecharge`."""
    if reference in DEFAULT_OVERRIDES:
        return DEFAULT_OVERRIDES[reference]
    enum_name, _, member = reference.partition(".")
    if not member:
        raise ValueError(f"not an enum reference: {reference!r}")
    return f"{enum_name}.{camel_case(member)}"


def dart_type(kotlin_type: str) -> tuple[str, bool]:
    """Returns the Dart type and whether it is nullable."""
    text = kotlin_type.strip()
    nullable = text.endswith("?")
    if nullable:
        text = text[:-1].strip()

    list_match = re.fullmatch(r"List<(.+)>", text)
    if list_match:
        inner, _ = dart_type(list_match.group(1))
        return f"List<{inner}>", nullable

    map_match = re.fullmatch(r"Map<(.+?),\s*(.+)>", text)
    if map_match:
        key_type, _ = dart_type(map_match.group(1))
        value_type, _ = dart_type(map_match.group(2))
        return f"Map<{key_type}, {value_type}>", nullable

    if text not in SIMPLE_TYPES:
        raise ValueError(f"unmapped Kotlin type: {kotlin_type!r}")
    return SIMPLE_TYPES[text], nullable


def dart_default(kotlin_default: str, dart_t: str) -> str:
    text = kotlin_default.strip()
    if text == "null":
        return "null"
    if text == "false":
        return "false"
    if text == "true":
        return "true"
    if text in {"0", "0L"}:
        return "0"
    if text in {'""', "''"}:
        return "''"
    if text == "emptyList()":
        inner = dart_t[len("List<") : -1] if dart_t.startswith("List<") else dart_t
        return f"const <{inner}>[]"
    if text == "emptyMap()":
        return "const <String, BookSummary>{}"
    if text in {"PlaybackState()", "BookInteractionState()"}:
        return f"const {text}"
    if re.fullmatch(r"[A-Za-z_]\w*\.[A-Z_][A-Z0-9_]*", text):
        return dart_enum_member(text)
    raise ValueError(f"unmapped default value: {kotlin_default!r}")


def parse_enums(source: str) -> list[tuple[str, list[str]]]:
    enums: list[tuple[str, list[str]]] = []
    for match in ENUM_RE.finditer(source):
        name = match.group(1)
        body = match.group(2)
        members = [
            candidate.strip()
            for candidate in body.replace("\n", " ").split(",")
            if re.fullmatch(r"[A-Z_][A-Z0-9_]*", candidate.strip())
        ]
        if not members:
            raise ValueError(f"enum {name} has no members")
        enums.append((name, members))
    if not enums:
        raise ValueError("no enums found in AppUiState.kt")
    return enums


def parse_fields(source: str) -> list[tuple[str, str, bool, str]]:
    """Returns (name, dart_type, nullable, dart_default) for the state class."""
    start = source.index("data class AppUiState(")
    body = source[start:]
    fields: list[tuple[str, str, bool, str]] = []
    for line in body.splitlines()[1:]:
        if line.strip().startswith(")"):
            break
        match = FIELD_RE.match(line)
        if not match:
            if line.strip() and not line.strip().startswith("//"):
                raise ValueError(f"unparsed field line: {line!r}")
            continue
        name, kotlin_type, kotlin_default = match.groups()
        dart_t, nullable = dart_type(kotlin_type)
        fields.append((name, dart_t, nullable, dart_default(kotlin_default, dart_t)))
    if not fields:
        raise ValueError("no fields parsed from AppUiState")
    return fields


def render_enums(enums: list[tuple[str, list[str]]]) -> str:
    blocks: list[str] = []
    for name, members in enums:
        rendered = ",\n  ".join(camel_case(member) for member in members)
        blocks.append(f"enum {name} {{\n  {rendered},\n}}")
    return "\n\n".join(blocks)


def render_state_class(fields: list[tuple[str, str, bool, str]]) -> str:
    constructor_lines = []
    for name, dart_t, nullable, default in fields:
        if default == "null":
            constructor_lines.append(f"    this.{name},")
        else:
            constructor_lines.append(f"    this.{name} = {default},")
    fields_block = "\n".join(constructor_lines)

    declarations = "\n".join(
        f"  final {dart_t}{'?' if nullable else ''} {name};"
        for name, dart_t, nullable, _ in fields
    )

    copy_params = []
    for name, dart_t, nullable, _ in fields:
        if nullable:
            copy_params.append(f"    Object? {name} = _noChange,")
        else:
            copy_params.append(f"    {dart_t}? {name},")

    copy_assignments = []
    for name, dart_t, nullable, _ in fields:
        if nullable:
            copy_assignments.append(
                f"    {name}: identical({name}, _noChange)\n"
                f"        ? this.{name}\n"
                f"        : {name} as {dart_t}?,"
            )
        else:
            copy_assignments.append(f"    {name}: {name} ?? this.{name},")

    return f"""class AppUiState {{
  const AppUiState({{
{fields_block}
  }});

{declarations}

  /// Sentinel that lets [copyWith] distinguish "argument omitted" from
  /// "explicitly set this nullable field to null" - the equivalent of Kotlin's
  /// generated `copy()`.
  static const Object _noChange = Object();

  AppUiState copyWith({{
{chr(10).join(copy_params)}
  }}) => AppUiState(
{chr(10).join(copy_assignments)}
  );
}}"""


def main() -> int:
    source = KOTLIN_SOURCE.read_text(encoding="utf-8")
    enums = parse_enums(source)
    fields = parse_fields(source)

    dart = f"""// GENERATED FILE - DO NOT EDIT BY HAND.
//
// Produced by tool/port_app_ui_state.py from
// android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppUiState.kt
// Regenerate with: python tool/port_app_ui_state.py
//
// Field names, types and defaults are a 1:1 port of the Kotlin state class.
library;

import '../data/models/app_language.dart';
import '../data/models/app_models.dart';
import 'playback_state.dart';

{render_enums(enums)}

{render_state_class(fields)}
"""

    DART_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    DART_OUTPUT.write_text(dart, encoding="utf-8", newline="\n")

    golden = {
        "source": str(KOTLIN_SOURCE.relative_to(REPO_ROOT)).replace("\\", "/"),
        "enum_count": len(enums),
        "field_count": len(fields),
        "enums": {name: [camel_case(m) for m in members] for name, members in enums},
        "fields": [
            {"name": name, "type": dart_t, "nullable": nullable, "default": default}
            for name, dart_t, nullable, default in fields
        ],
    }
    GOLDEN_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    GOLDEN_OUTPUT.write_text(
        json.dumps(golden, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
        newline="\n",
    )

    print(f"enums: {len(enums)} -> {', '.join(name for name, _ in enums)}")
    print(f"fields: {len(fields)}")
    print(f"wrote {DART_OUTPUT.relative_to(REPO_ROOT)}")
    print(f"wrote {GOLDEN_OUTPUT.relative_to(REPO_ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
