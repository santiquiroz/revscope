#!/usr/bin/env python3.12
import re
import sys
from pathlib import Path


LATIN1_EXTRAS = set("áéíóúüñÁÉÍÓÚÜÑ¿¡")
SKIP_LINE_PATTERNS = (
    "Log.",
    "Timber.",
    "TAG",
    "// i18n-ignore",
    "@Query",
    "Regex(",
    "http://",
    "https://",
    "execSQL",
)
LITERAL_SKIP_REGEX = re.compile(r"^[A-Z0-9_]+$|^[a-z0-9_.\-/]+$")
TEMPLATE_REGEX = re.compile(r"\$\w+|\$\{.*?\}")
STRING_LITERAL_REGEX = re.compile(r'"(?:[^"\\]|\\.)*"')


def has_latin1_extra(s: str) -> bool:
    return any(ch in LATIN1_EXTRAS for ch in s)


def count_letters(s: str) -> int:
    return sum(ch.isalpha() for ch in s)


def strip_templates(s: str) -> str:
    return TEMPLATE_REGEX.sub("", s)


def should_skip_line(line: str) -> bool:
    return any(pattern in line for pattern in SKIP_LINE_PATTERNS)


def extract_string_literals(line: str) -> list[str]:
    return [match.group(0)[1:-1] for match in STRING_LITERAL_REGEX.finditer(line)]


def should_report_literal(content: str) -> bool:
    if count_letters(content) < 2:
        return False
    if " " not in content and not has_latin1_extra(content):
        return False
    if LITERAL_SKIP_REGEX.match(content):
        return False
    stripped = strip_templates(content)
    if count_letters(stripped) < 2:
        return False
    return True


def scan_file(filepath: Path) -> list[tuple[int, str]]:
    findings: list[tuple[int, str]] = []
    try:
        text = filepath.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return findings

    for i, line in enumerate(text.splitlines(), start=1):
        if should_skip_line(line):
            continue
        for content in extract_string_literals(line):
            if should_report_literal(content):
                findings.append((i, content))
    return findings


def should_scan_file(filepath: Path) -> bool:
    normalized = str(filepath).replace("\\", "/")
    if "/src/main/" not in normalized:
        return False
    if "/mcp/" in normalized or "/build/" in normalized:
        return False
    return filepath.suffix == ".kt"


def main(argv: list[str]) -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    total_findings = 0
    for arg in argv:
        root = Path(arg)
        if not root.exists():
            continue
        for kt_file in root.rglob("*.kt"):
            if not should_scan_file(kt_file):
                continue
            findings = scan_file(kt_file)
            for line_num, content in findings:
                print(f"{kt_file}:{line_num}: \"{content}\"")
                total_findings += 1
    print(f"{total_findings} literals", file=sys.stderr)
    return 1 if total_findings > 0 else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))