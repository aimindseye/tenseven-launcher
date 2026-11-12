"""Offline regressions plus opt-in qualification against actual Gradle reports."""

import importlib.util
from contextlib import redirect_stdout
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest
from unittest.mock import patch
from xml.sax.saxutils import escape

SOURCE = Path(__file__).resolve().parents[1] / "deliverable_quality_gate.py"
SPEC = importlib.util.spec_from_file_location("tenseven_quality_gate", SOURCE)
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


class DeliveryQualityTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.repo = Path(self.tmp.name) / "repo"
        self.base_repo = Path(self.tmp.name) / "base"
        for root in (self.repo, self.base_repo):
            for module in ("app", "design"):
                (root / module / "src/main/java").mkdir(parents=True)
                (root / module / "src/main/java/Example.kt").write_text("fun BadName() = Unit\n")
                (root / module / "build.gradle.kts").write_text("plugins {}\n")
        for args in (("init", "-q"), ("config", "user.email", "test@example.invalid"),
                     ("config", "user.name", "Fixture"), ("add", "."), ("commit", "-qm", "base")):
            subprocess.run(["git", "-C", str(self.repo), *args], check=True)
        self.base = GATE.git(self.repo, "rev-parse", "HEAD")
        self.source = "app/src/main/java/Example.kt"

    def finding(self, **changes):
        item = {"family": "detekt", "file": self.source, "line": 1, "column": 5,
                "rule": "detekt.FunctionNaming", "message": "bad name", "severity": "warning",
                "report": "app/build/reports/detekt/detekt.xml"}
        return {**item, **changes}

    def compare(self, old, new):
        return GATE.compare_findings(old, new, self.base_repo, self.repo)

    def make_reports(self):
        for family, paths in GATE.report_paths(self.repo).items():
            for path in paths:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('<issues />' if family == 'lint' else '<checkstyle />')

    def test_unchanged_compose_naming_finding_is_baseline(self):
        # The comparator does not exempt the naming rule, even for Compose.
        old = [self.finding()]
        added, existing = self.compare(old, old)
        self.assertEqual([], added)
        self.assertEqual(1, len(existing))

    def test_inserted_lines_do_not_manufacture_new_findings(self):
        (self.repo / self.source).write_text("// added\nfun BadName() = Unit\n")
        added, existing = self.compare([self.finding()], [self.finding(line=2)])
        self.assertEqual([], added)
        self.assertEqual(1, existing[0]['baseline_line'])

    def test_manifest_finding_survives_unrelated_activity_addition(self):
        filename = 'app/src/main/AndroidManifest.xml'
        old = '<manifest>\n<uses-permission android:name="READ_MEDIA_IMAGES" />\n</manifest>\n'
        (self.base_repo / filename).write_text(old)
        (self.repo / filename).write_text(old.replace('</manifest>', '<activity />\n</manifest>'))
        item = self.finding(family='lint', file=filename, line=2, rule='SelectedPhotoAccess')
        self.assertEqual([], self.compare([item], [item])[0])

    def test_new_file_cannot_borrow_identical_baseline_debt(self):
        (self.repo / 'app/src/main/java/New.kt').write_text('fun BadName() = Unit\n')
        item = self.finding(file='app/src/main/java/New.kt')
        self.assertEqual([item], self.compare([self.finding()], [item])[0])

    def test_changed_rule_message_severity_column_or_source_is_new(self):
        for changes in ({'rule': 'detekt.MagicNumber'}, {'message': 'worse'},
                        {'severity': 'error'}, {'column': 8}):
            with self.subTest(changes=changes):
                item = self.finding(**changes)
                self.assertEqual([item], self.compare([self.finding()], [item])[0])
        (self.repo / self.source).write_text('fun AnotherBadName() = Unit\n')
        self.assertEqual(1, len(self.compare([self.finding()], [self.finding()])[0]))

    def test_duplicate_finding_cannot_reuse_baseline_allowance(self):
        item = self.finding()
        self.assertEqual([item], self.compare([item], [item, item])[0])

    def test_metric_may_improve_but_not_worsen(self):
        def item(size):
            return self.finding(rule='detekt.LongMethod', message=f'Function is too long ({size}). Maximum: 60')
        self.assertEqual([], self.compare([item(70)], [item(65)])[0])
        self.assertEqual([item(71)], self.compare([item(70)], [item(71)])[0])
        self.assertEqual(1, len(self.compare([item(70)], [self.finding(rule='detekt.LongMethod', message='Maximum: 80')])[0]))

    def test_new_compose_function_is_never_filtered_by_comparator(self):
        self.assertEqual([self.finding()], self.compare([], [self.finding()])[0])

    def test_binary_asset_file_level_lint_finding_requires_identical_bytes(self):
        filename = 'app/src/main/icon.png'
        for root in (self.base_repo, self.repo):
            (root / filename).write_bytes(b'\x89PNG\r\n')
        item = self.finding(family='lint', file=filename, line=0, rule='IconDensities')
        self.assertEqual([], self.compare([item], [item])[0])
        (self.repo / filename).write_bytes(b'\x89PNG\r\nchanged')
        self.assertEqual([item], self.compare([item], [item])[0])

    def test_all_module_reports_required(self):
        self.make_reports()
        issues, inventory = GATE.check_quality_reports(self.repo, 0)
        self.assertEqual([], issues)
        self.assertEqual(8, len(inventory))
        (self.repo / 'design/build/reports/detekt/detekt.xml').unlink()
        with self.assertRaisesRegex(GATE.QualityError, 'Missing required report'):
            GATE.check_quality_reports(self.repo, 0)

    def test_missing_source_set_report_fails_closed(self):
        self.make_reports()
        path = self.repo / 'app/src/test/java/ExampleTest.kt'
        path.parent.mkdir(parents=True)
        path.write_text('class ExampleTest\n')
        with self.assertRaisesRegex(GATE.QualityError, 'Missing required report'):
            GATE.check_quality_reports(self.repo, 0)

    def test_stale_reports_fail_closed(self):
        self.make_reports()
        with self.assertRaisesRegex(GATE.QualityError, 'Stale report'):
            GATE.check_quality_reports(self.repo, time.time() + 10)

    def test_malformed_or_wrong_family_schema_fails_closed(self):
        path = self.repo / 'bad.xml'
        for text, family in (('<unknown />', 'lint'), ('<issues />', 'detekt'),
                             ('<checkstyle><file><error /></file></checkstyle>', 'detekt'),
                             ('<issues><issue id="X" /></issues>', 'lint')):
            path.write_text(text)
            with self.subTest(text=text), self.assertRaises(GATE.QualityError):
                GATE.parse_xml_report(self.repo, path, family)

    def test_primary_lint_location_is_counted_once(self):
        path = self.repo / 'lint.xml'
        source = escape(str(self.repo / self.source))
        path.write_text(f'<issues><issue id="X" message="x" severity="Warning"><location file="{source}" line="1" /><location file="{source}" line="1" /></issue></issues>')
        self.assertEqual(1, len(GATE.parse_xml_report(self.repo, path, 'lint')))

    def test_outside_or_unavailable_source_is_rejected(self):
        with self.assertRaises(GATE.QualityError):
            GATE.relative_path(self.repo, '/elsewhere/Other.kt')
        path = self.repo / 'bad.xml'
        path.write_text('<checkstyle><file name="Missing.kt"><error line="1" source="X" message="x" severity="warning" /></file></checkstyle>')
        with self.assertRaises(GATE.QualityError):
            GATE.parse_xml_report(self.repo, path, 'detekt')

    def test_changed_paths_include_untracked_staged_and_deleted(self):
        (self.repo / self.source).unlink()
        (self.repo / 'app/New.kt').write_text('class New\n')
        subprocess.run(['git', '-C', str(self.repo), 'add', 'app/New.kt'], check=True)
        (self.repo / 'app/Other.kt').write_text('class Other\n')
        self.assertEqual({self.source, 'app/New.kt', 'app/Other.kt'}, GATE.changed_paths(self.repo, self.base))

    def test_new_suppression_rejected_including_untracked_and_file_scope(self):
        for text in ('@Suppress("X")', '@file:Suppress("X")', '// ktlint-disable', '//noinspection X'):
            with self.subTest(text=text):
                path = self.repo / 'app/New.kt'
                path.write_text(text + '\nclass New\n')
                with self.assertRaises(GATE.QualityError):
                    GATE.assert_no_quality_bypass({'app/New.kt'}, self.repo, self.base)

    def test_existing_suppression_untouched_is_not_new(self):
        (self.repo / self.source).write_text('@Suppress("Legacy")\nclass Example\n')
        subprocess.run(['git', '-C', str(self.repo), 'add', '.'], check=True)
        subprocess.run(['git', '-C', str(self.repo), 'commit', '-qm', 'legacy'], check=True)
        self.base = GATE.git(self.repo, 'rev-parse', 'HEAD')
        (self.repo / self.source).write_text('@Suppress("Legacy")\nclass Example2\n')
        GATE.assert_no_quality_bypass({self.source}, self.repo, self.base)

    def test_policy_edits_rejected(self):
        for path in ('config/detekt.yml', 'gradle.properties', 'gradle/libs.versions.toml'):
            with self.subTest(path=path), self.assertRaises(GATE.QualityError):
                GATE.assert_no_quality_bypass({path}, self.repo, self.base)

    def test_ci_bootstrap_keyword_cannot_authorize_weakened_checks(self):
        folder = self.repo / '.github/workflows'
        folder.mkdir(parents=True)
        path = folder / 'android.yml'
        path.write_text('run: checks\n')
        subprocess.run(['git', '-C', str(self.repo), 'add', '.'], check=True)
        subprocess.run(['git', '-C', str(self.repo), 'commit', '-qm', 'workflow'], check=True)
        self.base = GATE.git(self.repo, 'rev-parse', 'HEAD')
        path.write_text('# Enforce changed-file quality\nrun: true\n')
        with self.assertRaises(GATE.QualityError):
            GATE.assert_no_quality_bypass({'.github/workflows/android.yml'}, self.repo, self.base)

    def test_exact_approved_compose_policy_and_wiring_required(self):
        path = self.repo / 'build.gradle.kts'
        path.write_text(GATE.OLD_WIRING)
        subprocess.run(['git', '-C', str(self.repo), 'add', '.'], check=True)
        subprocess.run(['git', '-C', str(self.repo), 'commit', '-qm', 'wiring'], check=True)
        self.base = GATE.git(self.repo, 'rev-parse', 'HEAD')
        (self.repo / 'config').mkdir()
        policy = self.repo / GATE.COMPOSE_CONFIG
        policy.write_text(GATE.COMPOSE_POLICY)
        path.write_text(GATE.NEW_WIRING)
        changed = {'build.gradle.kts', GATE.COMPOSE_CONFIG}
        with self.assertRaises(GATE.QualityError):
            GATE.assert_no_quality_bypass(changed, self.repo, self.base)
        GATE.assert_no_quality_bypass(changed, self.repo, self.base, True)
        policy.write_text(GATE.COMPOSE_POLICY + 'style: {}\n')
        with self.assertRaises(GATE.QualityError):
            GATE.assert_no_quality_bypass(changed, self.repo, self.base, True)

    def test_source_digest_detects_untracked_changes(self):
        before = GATE.source_digest(self.repo)
        (self.repo / 'app/New.kt').write_text('class New\n')
        self.assertNotEqual(before, GATE.source_digest(self.repo))

    def exercise_cli(self, gradle_rc=0, diagnostic=False):
        detached = Path(self.tmp.name) / 'detached'
        subprocess.run(['git', '-C', str(self.repo), 'worktree', 'add', '--detach', str(detached), self.base],
                       check=True, capture_output=True)
        (self.repo / self.source).write_text('// changed\nfun BadName() = Unit\n')
        out = Path(self.tmp.name) / 'evidence'
        argv = ['gate', '--repo', str(self.repo), '--base-ref', self.base,
                '--baseline-repo', str(detached), '--out', str(out)]
        if diagnostic:
            argv.append('--reports-only')
        with patch('sys.argv', argv), patch.object(GATE, 'run_gradle', return_value=(gradle_rc, 0)), \
                patch.object(GATE, 'check_quality_reports', return_value=([], [])), redirect_stdout(io.StringIO()):
            rc = GATE.main()
        return rc, json.loads((out / 'QUALITY_GATE.json').read_text())

    def test_failed_gradle_cannot_pass_even_with_zero_findings(self):
        rc, result = self.exercise_cli(gradle_rc=1)
        self.assertEqual(1, rc)
        self.assertEqual('HOLD', result['result'])

    def test_diagnostic_reports_never_qualify(self):
        rc, result = self.exercise_cli(diagnostic=True)
        self.assertEqual(1, rc)
        self.assertEqual('HOLD', result['result'])


@unittest.skipUnless(os.environ.get('TENSEVEN_REAL_BASE'), 'Real report roots not supplied')
class RealGradleReportTests(unittest.TestCase):
    def test_real_reports_and_injected_regressions(self):
        base, repo = Path(os.environ['TENSEVEN_REAL_BASE']), Path(os.environ['TENSEVEN_REAL_CANDIDATE'])
        old, _ = GATE.check_quality_reports(base, 0)
        new, _ = GATE.check_quality_reports(repo, 0)
        added, existing = GATE.compare_findings(old, new, base, repo)
        self.assertEqual([], added)
        # Both historically misclassified examples must actually exist in real reports.
        manifest = next(item for item in existing if item['rule'] == 'SelectedPhotoAccess')
        self.assertEqual('app/src/main/AndroidManifest.xml', manifest['file'])
        self.assertFalse(any(item['rule'] == 'detekt.FunctionNaming' and 'CompanionWizard' in item['file'] for item in new))
        self.assertEqual(1, len(GATE.compare_findings(old, new + [manifest], base, repo)[0]))
        changed_message = {**manifest, 'message': manifest['message'] + ' regression'}
        self.assertEqual(1, len(GATE.compare_findings(old, new + [changed_message], base, repo)[0]))
        # A real baseline naming finding cannot grant a waiver to a new composable file.
        naming = next(item for item in old if item['rule'] == 'detekt.FunctionNaming')
        injected = {**naming, 'file': 'app/src/main/java/io/github/aimindseye/tenseven/CompanionWizardActivity.kt', 'line': 61}
        self.assertEqual(1, len(GATE.compare_findings(old, new + [injected], base, repo)[0]))


if __name__ == '__main__':
    unittest.main()
