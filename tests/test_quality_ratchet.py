import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("quality_ratchet", Path(__file__).parents[1] / "tools/quality_ratchet.py")
quality = importlib.util.module_from_spec(spec)
spec.loader.exec_module(quality)


class QualityRatchetTest(unittest.TestCase):
    def test_new_rule_or_duplicate_fails_but_fixed_findings_do_not(self):
        from collections import Counter
        old = Counter({("lint", "rule", "message", "source"): 1})
        self.assertFalse(quality.compare(old, Counter()))
        self.assertEqual(1, sum(quality.compare(old, old + old).values()))
        self.assertEqual(1, sum(quality.compare(old, Counter({("lint", "new", "message", "source"): 1})).values()))

    def test_renamed_file_and_visibility_change_preserve_anchor(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "old.kt").write_text("private fun fixture() = 1\n")
            (root / "new.kt").write_text("internal fun fixture() = 1\n")
            self.assertEqual(quality.anchor(root, "old.kt", 1), quality.anchor(root, "new.kt", 1))

    def test_missing_reports_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "Required reports missing"):
                quality.findings(Path(directory))

    def test_reports_are_read_and_repeated_findings_are_not_hidden(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "fixture.kt").write_text("val fixture = 1\n")
            for tool in ("lint", "detekt", "ktlint"):
                report = root / f"app/build/reports/{tool}/result.xml"
                report.parent.mkdir(parents=True)
                if tool == "lint":
                    report.write_text('<issues><issue id="Rule" severity="Warning" message="message">'
                                      '<location file="fixture.kt" line="1"/></issue></issues>')
                else:
                    report.write_text('<checkstyle><file name="fixture.kt"><error source="rule" '
                                      'message="message" line="1"/></file></checkstyle>')
            self.assertEqual(3, sum(quality.findings(root).values()))


if __name__ == "__main__":
    unittest.main()
