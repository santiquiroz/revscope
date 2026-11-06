#!/usr/bin/env python3.12
import importlib.util
import tempfile
import unittest
from pathlib import Path
from types import ModuleType


def load_module(name: str, filepath: Path) -> ModuleType:
    spec = importlib.util.spec_from_file_location(name, filepath)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class HardcodedTests(unittest.TestCase):
    def setUp(self) -> None:
        self.test_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.test_dir.name)
        self.hardcoded = load_module(
            "hardcoded",
            Path(__file__).parent / "hardcoded.py"
        )

    def tearDown(self) -> None:
        self.test_dir.cleanup()

    def write_kt(self, rel_path: str, content: str) -> Path:
        filepath = self.root / rel_path
        filepath.parent.mkdir(parents=True, exist_ok=True)
        filepath.write_text(content, encoding="utf-8")
        return filepath

    def run_hardcoded(self, module_path: Path) -> int:
        return self.hardcoded.main([str(module_path)])

    def test_basic_hardcoded_string(self) -> None:
        self.write_kt("mod/src/main/kotlin/A.kt", 'Text("Hola mundo")')
        result = self.run_hardcoded(self.root / "mod")
        self.assertEqual(result, 1)

    def test_comment_lines_skipped(self) -> None:
        self.write_kt("mod/src/main/kotlin/A.kt", '// dice "hola mundo"\n * el botón "Probar conexión"')
        result = self.run_hardcoded(self.root / "mod")
        self.assertEqual(result, 0)

    def test_log_line_skipped(self) -> None:
        self.write_kt("mod/src/main/kotlin/A.kt", 'Log.d(TAG, "conectado ok")')
        result = self.run_hardcoded(self.root / "mod")
        self.assertEqual(result, 0)

    def test_snake_case_identifier_skipped(self) -> None:
        self.write_kt("mod/src/main/kotlin/A.kt", 'val k = "velocidad_kmh"')
        result = self.run_hardcoded(self.root / "mod")
        self.assertEqual(result, 0)

    def test_i18n_ignore_comment(self) -> None:
        self.write_kt("mod/src/main/kotlin/A.kt", 'Text("Hola mundo") // i18n-ignore')
        result = self.run_hardcoded(self.root / "mod")
        self.assertEqual(result, 0)

    def test_accented_character_reported(self) -> None:
        self.write_kt("mod/src/main/kotlin/A.kt", 'Text("Conexión")')
        result = self.run_hardcoded(self.root / "mod")
        self.assertEqual(result, 1)

    def test_under_src_test_skipped(self) -> None:
        self.write_kt("mod/src/test/kotlin/A.kt", 'Text("Hola mundo")')
        result = self.run_hardcoded(self.root / "mod")
        self.assertEqual(result, 0)

    def test_under_mcp_skipped(self) -> None:
        self.write_kt("mod/src/main/kotlin/x/mcp/B.kt", 'Text("Hola mundo")')
        result = self.run_hardcoded(self.root / "mod")
        self.assertEqual(result, 0)


class ParityTests(unittest.TestCase):
    def setUp(self) -> None:
        self.test_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.test_dir.name)
        self.parity = load_module(
            "parity",
            Path(__file__).parent / "parity.py"
        )

    def tearDown(self) -> None:
        self.test_dir.cleanup()

    def write_strings(self, module: str, qual: str, content: str) -> Path:
        filepath = self.root / module / "src" / "main" / "res" / f"values{qual}" / "strings.xml"
        filepath.parent.mkdir(parents=True, exist_ok=True)
        filepath.write_text(content, encoding="utf-8")
        return filepath

    def run_parity(self, module_path: Path) -> int:
        return self.parity.main([str(module_path)])

    def test_key_only_in_values(self) -> None:
        self.write_strings("mod", "", """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="hello">Hello</string>
</resources>""")
        self.write_strings("mod", "-es", """<?xml version="1.0" encoding="utf-8"?>
<resources>
</resources>""")
        result = self.run_parity(self.root / "mod")
        self.assertEqual(result, 1)

    def test_format_args_mismatch(self) -> None:
        self.write_strings("mod", "", """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="speed">Speed %1$s</string>
</resources>""")
        self.write_strings("mod", "-es", """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="speed">Velocidad %1$d</string>
</resources>""")
        result = self.run_parity(self.root / "mod")
        self.assertEqual(result, 1)

    def test_same_keys_and_args(self) -> None:
        self.write_strings("mod", "", """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="speed">Speed %1$s</string>
</resources>""")
        self.write_strings("mod", "-es", """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="speed">Velocidad %1$s</string>
</resources>""")
        result = self.run_parity(self.root / "mod")
        self.assertEqual(result, 0)

    def test_values_en_rgb_partial_ok(self) -> None:
        self.write_strings("mod", "", """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="hello">Hello</string>
    <string name="bye">Bye</string>
</resources>""")
        self.write_strings("mod", "-en-rGB", """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="hello">Hello</string>
</resources>""")
        result = self.run_parity(self.root / "mod")
        self.assertEqual(result, 0)


if __name__ == "__main__":
    unittest.main()