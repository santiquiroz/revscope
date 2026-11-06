#!/usr/bin/env python3.12
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


FORMAT_ARG_REGEX = re.compile(r"%(\d+\$)?[sdf]")


def extract_format_args(text: str) -> set[str]:
    return {match.group(0) for match in FORMAT_ARG_REGEX.finditer(text)}


def plural_other_text(elem: ET.Element) -> str:
    for item in elem:
        if item.get("quantity") == "other":
            return item.text or ""
    return ""


def entry_text(elem: ET.Element) -> str | None:
    if elem.tag == "string":
        return elem.text or ""
    if elem.tag == "plurals":
        return plural_other_text(elem)
    return None


def parse_strings_xml(path: Path) -> dict[str, set[str]]:
    entries: dict[str, set[str]] = {}
    for elem in ET.parse(path).getroot():
        name = elem.get("name")
        text = entry_text(elem)
        if name is None or text is None or elem.get("translatable") == "false":
            continue
        entries[name] = extract_format_args(text)
    return entries


def strings_files(values_dir: Path) -> list[Path]:
    return sorted(values_dir.glob("strings*.xml"))


def parse_values_dir(values_dir: Path) -> dict[str, set[str]]:
    entries: dict[str, set[str]] = {}
    for path in strings_files(values_dir):
        entries.update(parse_strings_xml(path))
    return entries


def locale_values_dirs(res_dir: Path) -> list[Path]:
    return sorted(d for d in res_dir.glob("values-*") if strings_files(d))


def is_regional(values_dir: Path) -> bool:
    return "-r" in values_dir.name[len("values-"):]


def key_problems(base: dict[str, set[str]], other: dict[str, set[str]]) -> list[tuple[str, str]]:
    missing = [("missing", key) for key in base if key not in other]
    extra = [("extra", key) for key in other if key not in base]
    return missing + extra


def format_problems(base: dict[str, set[str]], other: dict[str, set[str]]) -> list[tuple[str, str]]:
    return [
        ("format-args", key)
        for key, args in other.items()
        if key in base and base[key] != args
    ]


def check_locale(base: dict[str, set[str]], values_dir: Path) -> list[str]:
    other = parse_values_dir(values_dir)
    problems = [] if is_regional(values_dir) else key_problems(base, other)
    problems += format_problems(base, other)
    return [f"{values_dir}: {kind}: {key}" for kind, key in problems]


def check_module(module_dir: Path) -> list[str]:
    res_dir = module_dir / "src" / "main" / "res"
    base_dir = res_dir / "values"
    if not strings_files(base_dir):
        return []
    base = parse_values_dir(base_dir)
    problems: list[str] = []
    for values_dir in locale_values_dirs(res_dir):
        problems += check_locale(base, values_dir)
    return problems


def main(argv: list[str]) -> int:
    problems: list[str] = []
    for arg in argv:
        problems += check_module(Path(arg))
    for problem in problems:
        print(problem)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
