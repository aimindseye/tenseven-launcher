"""Offline regressions plus opt-in qualification against actual Gradle reports."""

import importlib.util
from collections import Counter
from contextlib import ExitStack, redirect_stdout
from dataclasses import replace
import io
import json
import os
import sys
from pathlib import Path
import subprocess
import tempfile
import stat
import time
import unittest
from unittest.mock import patch
from xml.sax.saxutils import escape

SOURCE = Path(__file__).resolve().parents[1] / "deliverable_quality_gate.py"
sys.path.insert(0, str(SOURCE.parent))
SPEC = importlib.util.spec_from_file_location("tenseven_quality_gate", SOURCE)
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)
import quality_gate_runtime as RUNTIME
import quality_gate_policy as POLICY


class RepositoryFixture(unittest.TestCase):
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
        self.base = GATE.GitRepository(self.repo).head()
        self.source = "app/src/main/java/Example.kt"


class DeliveryQualityTests(RepositoryFixture):
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
        self.base = GATE.GitRepository(self.repo).head()
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
        self.base = GATE.GitRepository(self.repo).head()
        path.write_text('# Enforce changed-file quality\nrun: true\n')
        with self.assertRaises(GATE.QualityError):
            GATE.assert_no_quality_bypass({'.github/workflows/android.yml'}, self.repo, self.base)

    def test_exact_approved_compose_policy_and_wiring_required(self):
        path = self.repo / 'build.gradle.kts'
        path.write_text(POLICY.OLD_WIRING)
        subprocess.run(['git', '-C', str(self.repo), 'add', '.'], check=True)
        subprocess.run(['git', '-C', str(self.repo), 'commit', '-qm', 'wiring'], check=True)
        self.base = GATE.GitRepository(self.repo).head()
        (self.repo / 'config').mkdir()
        policy = self.repo / POLICY.COMPOSE_CONFIG
        policy.write_text(POLICY.COMPOSE_POLICY)
        path.write_text(POLICY.NEW_WIRING)
        changed = {'build.gradle.kts', POLICY.COMPOSE_CONFIG}
        with self.assertRaises(GATE.QualityError):
            GATE.assert_no_quality_bypass(changed, self.repo, self.base)
        GATE.assert_no_quality_bypass(changed, self.repo, self.base, True)
        policy.write_text(POLICY.COMPOSE_POLICY + 'style: {}\n')
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
                patch.object(GATE, 'check_quality_reports', return_value=([], [])), \
                patch.object(GATE, 'toolchain', return_value={}), \
                patch.object(GATE, 'analysis_configuration', side_effect=lambda repo, baseline: {'original_parent_source_sha256': GATE.source_digest(baseline)}), redirect_stdout(io.StringIO()):
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

    def test_late_integrity_error_revokes_provisional_pass(self):
        with patch.object(GATE, 'assert_candidate_unchanged',
                          side_effect=[None, None, GATE.QualityError('late integrity failure')]):
            rc, result = self.exercise_cli()
        self.assertEqual(2, rc)
        self.assertEqual('HOLD', result['result'])
        self.assertEqual('late integrity failure', result['blocker'])

    def test_blocker_always_overrides_success_status(self):
        self.assertEqual(2, GATE.result_code({'result': 'PASS_NO_NEW_FINDINGS', 'blocker': 'invalid'}))



class SecurityBoundaryTests(RepositoryFixture):
    def setUp(self):
        super().setUp()
        self.git = RUNTIME.GitRepository(self.repo)
        self.allowed = Path(self.tmp.name).resolve()
        self.roots = [(self.allowed, self.allowed)]

    def reserve(self, name='evidence'):
        evidence = RUNTIME.EvidenceDirectory.reserve(self.allowed / name, self.repo, self.roots)
        self.addCleanup(evidence.close)
        return evidence

    def test_option_like_git_refs_are_rejected_before_execution(self):
        for value in ('--help', '-c', '--output=/tmp/victim', '--config-env=x=y', '-C/tmp'):
            with self.subTest(value=value), patch.object(self.git, '_run') as run:
                with self.assertRaises(GATE.QualityError):
                    self.git.resolve(value)
                run.assert_not_called()

    def test_malformed_revisions_are_rejected(self):
        for value in ('', 'HEAD:secret', 'HEAD^{tree}', 'main..HEAD', 'main@{1}',
                      'refs//heads/main', 'refs/heads/.hidden', 'main.lock', 'main/',
                      'HEAD\n--help', 'a;touch victim', '$(touch victim)', 'a b', 'a\\b'):
            with self.subTest(value=value), patch.object(self.git, '_run') as run:
                with self.assertRaises(GATE.QualityError):
                    self.git.resolve(value)
                run.assert_not_called()

    def test_legitimate_head_full_commit_and_branch_refs_work(self):
        self.assertEqual(self.base, self.git.resolve('HEAD'))
        self.assertEqual(self.base, self.git.resolve(self.base))
        self.assertEqual(self.base, self.git.resolve(self.git.branch()))

    def test_unknown_commit_and_non_commit_object_fail(self):
        for value in ('0' * 40, self.git.blob_id(self.base, self.source)):
            with self.subTest(value=value), self.assertRaises(GATE.QualityError):
                self.git.resolve(value)

    def test_ref_resolution_requires_one_full_object_id(self):
        with patch.object(self.git, '_read', return_value=b'bad\noutput\n'), self.assertRaises(GATE.QualityError):
            self.git.resolve('HEAD')

    def test_git_filename_is_literal_and_cannot_select_other_sources(self):
        filename = 'app/:(glob)*.kt'
        (self.repo / filename).write_text('class Literal\n')
        subprocess.run(['git', '-C', str(self.repo), '--literal-pathspecs', 'add', '--', filename], check=True)
        subprocess.run(['git', '-C', str(self.repo), 'commit', '-qm', 'literal path'], check=True)
        base = self.git.head()
        (self.repo / filename).write_text('class LiteralChanged\n')
        (self.repo / self.source).write_text('class UnrelatedChanged\n')
        diff = self.git.diff(base, filename)
        self.assertIn('LiteralChanged', diff)
        self.assertNotIn('UnrelatedChanged', diff)

    def test_generated_worktree_path_is_data_after_option_terminator(self):
        destination = self.allowed / 'baseline'
        self.git.detached_worktree(destination, self.base)
        baseline = RUNTIME.GitRepository(destination)
        self.assertEqual(self.base, baseline.head())
        self.assertEqual('', baseline.branch())
        with self.assertRaises(GATE.QualityError):
            self.git.detached_worktree(Path('--help'), self.base)

    def test_evidence_is_private_and_writes_fixed_entries(self):
        evidence = self.reserve()
        GATE.write_json(evidence, 'QUALITY_GATE.json', {'result': 'HOLD'})
        self.assertEqual(0o700, stat.S_IMODE(evidence.path.stat().st_mode))
        self.assertEqual(0o600, stat.S_IMODE((evidence.path / 'QUALITY_GATE.json').stat().st_mode))
        self.assertEqual('HOLD', json.loads((evidence.path / 'QUALITY_GATE.json').read_text())['result'])

    def test_cli_output_traversal_is_rejected_without_side_effects(self):
        for suffix in ('../escaped', './escaped', 'one/../../escaped'):
            value = str(self.allowed) + '/' + suffix
            with self.subTest(suffix=suffix), self.assertRaises(GATE.QualityError):
                RUNTIME.EvidenceDirectory.reserve(value, self.repo, self.roots)
        self.assertFalse((self.allowed / 'escaped').exists())

    def test_relative_out_of_root_and_repo_destinations_fail(self):
        for value in ('relative', self.allowed.parent / 'outside-evidence', self.repo / 'evidence', self.allowed):
            with self.subTest(value=value), self.assertRaises(GATE.QualityError):
                RUNTIME.EvidenceDirectory.reserve(value, self.repo, self.roots)

    def test_existing_destination_is_never_overwritten(self):
        evidence = self.reserve()
        evidence.write('sentinel.txt', b'keep')
        with self.assertRaises(GATE.QualityError):
            self.reserve()
        self.assertEqual(b'keep', (evidence.path / 'sentinel.txt').read_bytes())

    def test_symlink_destination_and_ancestor_fail(self):
        outside = self.allowed / 'outside'
        outside.mkdir()
        (self.allowed / 'link').symlink_to(outside, target_is_directory=True)
        for value in (self.allowed / 'link', self.allowed / 'link' / 'nested'):
            with self.subTest(value=value), self.assertRaises((GATE.QualityError, OSError)):
                RUNTIME.EvidenceDirectory.reserve(value, self.repo, self.roots)
        self.assertEqual([], list(outside.iterdir()))

    def test_preplanted_file_symlink_cannot_redirect_receipt(self):
        evidence = self.reserve()
        victim = self.allowed / 'victim'
        victim.write_bytes(b'keep')
        (evidence.path / 'QUALITY_GATE.json').symlink_to(victim)
        with self.assertRaises(FileExistsError):
            GATE.write_json(evidence, 'QUALITY_GATE.json', {'bad': True})
        self.assertEqual(b'keep', victim.read_bytes())

    def test_directory_replacement_after_reservation_fails_closed(self):
        evidence = self.reserve()
        outside = self.allowed / 'outside'
        outside.mkdir()
        evidence.path.rename(self.allowed / 'reserved-original')
        evidence.path.symlink_to(outside, target_is_directory=True)
        with self.assertRaises((GATE.QualityError, OSError)):
            evidence.write('QUALITY_GATE.json', b'bad')
        self.assertEqual([], list(outside.iterdir()))

    def test_directory_swap_between_check_and_open_cannot_redirect_write(self):
        evidence = self.reserve()
        outside = self.allowed / 'outside'
        outside.mkdir()
        original_open = RUNTIME.os.open
        swapped = False

        def race_open(path, flags, *args, **kwargs):
            nonlocal swapped
            if path == 'QUALITY_GATE.json' and not swapped:
                swapped = True
                evidence.path.rename(self.allowed / 'reserved-original')
                evidence.path.symlink_to(outside, target_is_directory=True)
            return original_open(path, flags, *args, **kwargs)

        with patch.object(RUNTIME.os, 'open', side_effect=race_open), \
                self.assertRaises((GATE.QualityError, OSError)):
            evidence.write('QUALITY_GATE.json', b'bad')
        self.assertTrue(swapped)
        self.assertEqual([], list(outside.iterdir()))

    def test_file_creation_race_preserves_attacker_entry(self):
        evidence = self.reserve()
        original_open = RUNTIME.os.open
        planted = False

        def race_open(path, flags, *args, **kwargs):
            nonlocal planted
            if path == 'QUALITY_GATE.json' and not planted:
                planted = True
                (evidence.path / path).write_bytes(b'keep')
            return original_open(path, flags, *args, **kwargs)

        with patch.object(RUNTIME.os, 'open', side_effect=race_open), self.assertRaises(FileExistsError):
            evidence.write('QUALITY_GATE.json', b'bad')
        self.assertEqual(b'keep', (evidence.path / 'QUALITY_GATE.json').read_bytes())

    def test_reservation_race_rejects_swapped_ancestor(self):
        outside = self.allowed / 'outside'
        outside.mkdir()
        original_open = RUNTIME.os.open
        swapped = False

        def race_open(path, flags, *args, **kwargs):
            nonlocal swapped
            if path == 'race-parent' and not swapped and (self.allowed / path).exists():
                swapped = True
                (self.allowed / path).rename(self.allowed / 'original-parent')
                (self.allowed / path).symlink_to(outside, target_is_directory=True)
            return original_open(path, flags, *args, **kwargs)

        with patch.object(RUNTIME.os, 'open', side_effect=race_open), self.assertRaises(OSError):
            self.reserve('race-parent/evidence')
        self.assertEqual([], list(outside.iterdir()))

    def test_archive_rejects_traversal_and_symlink_parent(self):
        evidence = self.reserve()
        outside = self.allowed / 'outside'
        outside.mkdir()
        (evidence.path / 'app').symlink_to(outside, target_is_directory=True)
        with self.assertRaises(GATE.QualityError):
            evidence.archive('../escaped', b'bad')
        with self.assertRaises(OSError):
            evidence.archive('app/report.xml', b'bad')
        self.assertEqual([], list(outside.iterdir()))

    def test_group_writable_existing_parent_is_rejected(self):
        parent = self.allowed / 'mutable'
        parent.mkdir()
        parent.chmod(0o777)
        with self.assertRaises(GATE.QualityError):
            self.reserve('mutable/evidence')

    def test_ci_runner_temp_works_and_user_symlink_alias_fails(self):
        runner = self.allowed / 'runner'
        runner.mkdir()
        with patch.dict(os.environ, {'RUNNER_TEMP': str(runner)}):
            evidence = RUNTIME.EvidenceDirectory.reserve(runner / 'ci-quality', self.repo)
            evidence.close()
        alias = self.allowed / 'platform-alias'
        alias.symlink_to(runner, target_is_directory=True)
        with self.assertRaises(GATE.QualityError):
            RUNTIME.EvidenceDirectory.reserve(alias / 'local-quality', self.repo, [(alias, runner)])

    def test_both_analyses_use_one_candidate_wrapper(self):
        evidence = self.reserve()
        fake = subprocess.CompletedProcess([], 0)
        with patch.object(GATE.subprocess, 'run', return_value=fake) as run:
            rc, _ = GATE.run_gradle(self.base_repo, evidence, GATE.QUALITY_TASKS, self.repo)
        self.assertEqual(0, rc)
        command = run.call_args.args[0]
        self.assertEqual(str(self.repo / 'gradlew'), command[0])
        self.assertEqual(['--project-dir', str(self.base_repo)], command[1:3])
        self.assertIn('--no-build-cache', command)
        self.assertIn('--rerun-tasks', command)


    def test_actual_cli_rejects_option_injection_and_traversal_before_writing(self):
        (self.repo / self.source).write_text('class Changed\n')
        out = self.allowed / 'cli-evidence'
        options = ['--output=' + str(self.allowed / 'victim'), 'HEAD:secret', 'main..HEAD']
        for value in options:
            result = subprocess.run([sys.executable, str(SOURCE), '--repo', str(self.repo),
                                     '--base-ref=' + value, '--out', str(out)], capture_output=True, text=True)
            self.assertEqual(2, result.returncode)
            self.assertIn('Invalid Git reference', result.stderr)
            self.assertFalse(out.exists())
        result = subprocess.run([sys.executable, str(SOURCE), '--repo', str(self.repo),
                                 '--base-ref', self.base, '--out', str(out) + '/../escaped'],
                                capture_output=True, text=True)
        self.assertEqual(2, result.returncode)
        self.assertFalse((self.allowed / 'escaped').exists())

    def test_parent_overlay_rejects_preplanted_symlink_without_writes(self):
        root = self.base_repo
        (root / 'config').mkdir()
        build = root / POLICY.ROOT_BUILD
        build.write_text(POLICY.OLD_WIRING)
        victim = self.allowed / 'victim'
        victim.write_bytes(b'keep')
        (root / POLICY.COMPOSE_CONFIG).symlink_to(victim)
        overlay = POLICY.temporary_compose_overlay(root, True)
        stack = ExitStack()
        self.addCleanup(stack.close)
        with self.assertRaises(FileExistsError):
            stack.enter_context(overlay)
        self.assertEqual(b'keep', victim.read_bytes())
        self.assertEqual(POLICY.OLD_WIRING, build.read_text())

    def test_parent_overlay_only_reverses_its_own_inode_and_bytes(self):
        root = self.base_repo
        (root / 'config').mkdir()
        build = root / POLICY.ROOT_BUILD
        build.write_text(POLICY.OLD_WIRING)
        with POLICY.temporary_compose_overlay(root, True):
            self.assertEqual(POLICY.NEW_WIRING, build.read_text())
        self.assertEqual(POLICY.OLD_WIRING, build.read_text())
        self.assertFalse((root / POLICY.COMPOSE_CONFIG).exists())
        overlay = POLICY.temporary_compose_overlay(root, True)
        stack = ExitStack()
        self.addCleanup(stack.close)
        stack.enter_context(overlay)
        build.rename(root / 'original-build')
        build.write_text('other work\n')
        with self.assertRaises(GATE.QualityError):
            stack.close()
        self.assertEqual('other work\n', build.read_text())


    def test_candidate_head_and_contents_are_bound_across_phases(self):
        result = {'head': self.git.head(), 'candidate_start_source_sha256': GATE.source_digest(self.repo),
                  'base_ref': self.base, 'changed_paths': sorted(self.git.changed(self.base))}
        GATE.assert_candidate_unchanged(self.git, result)
        with patch.object(self.git, 'head', return_value='0' * 40), self.assertRaises(GATE.QualityError):
            GATE.assert_candidate_unchanged(self.git, result)
        (self.repo / self.source).write_text('class ConcurrentEdit\n')
        with self.assertRaises(GATE.QualityError):
            GATE.assert_candidate_unchanged(self.git, result)

    def test_entry_validation_remains_ascii_only(self):
        for entry in ('évidence', 'evidencé', '一', 'evidence_١', 'evidence\n'):
            with self.subTest(entry=entry), self.assertRaises(GATE.QualityError):
                RUNTIME._entry(entry)
        self.assertEqual('Evidence_1-2.xml', RUNTIME._entry('Evidence_1-2.xml'))

    def test_root_permissions_are_checked_before_allocation(self):
        root = self.allowed / 'unsafe-root'
        root.mkdir(mode=0o700)
        root.chmod(0o777)
        roots = [(root, root)]
        with patch.object(RUNTIME.os, 'mkdir') as create, self.assertRaises(GATE.QualityError):
            RUNTIME.EvidenceDirectory.reserve(root / 'evidence', self.repo, roots)
        create.assert_not_called()

    def test_private_leaf_cannot_authorize_unsafe_ancestor(self):
        root = self.allowed / 'unsafe-parent'
        root.mkdir()
        leaf = root / 'private-root'
        leaf.mkdir(mode=0o700)
        root.chmod(0o777)
        with patch.object(RUNTIME.os, 'mkdir') as create, self.assertRaises(GATE.QualityError):
            RUNTIME.EvidenceDirectory.reserve(leaf / 'evidence', self.repo, [(leaf, leaf)])
        create.assert_not_called()

    def test_foreign_owned_authority_is_rejected(self):
        foreign_uid = os.geteuid() + 1
        with patch.object(RUNTIME.os, 'geteuid', return_value=foreign_uid), \
                patch.object(RUNTIME.os, 'mkdir') as create, self.assertRaises(GATE.QualityError):
            self.reserve()
        create.assert_not_called()

    def test_runner_temp_requires_existing_safe_authority(self):
        unsafe = self.allowed / 'runner-unsafe'
        unsafe.mkdir()
        unsafe.chmod(0o777)
        for root in (unsafe, self.allowed / 'missing', Path('relative')):
            with self.subTest(root=root), patch.dict(os.environ, {'RUNNER_TEMP': str(root)}), \
                    self.assertRaises((GATE.QualityError, OSError)):
                RUNTIME.EvidenceDirectory.reserve(root / 'evidence', self.repo)

    def test_runner_temp_symlink_is_rejected(self):
        link = self.allowed / 'runner-alias'
        link.symlink_to(self.allowed, target_is_directory=True)
        with patch.dict(os.environ, {'RUNNER_TEMP': str(link)}), self.assertRaises(GATE.QualityError):
            RUNTIME.EvidenceDirectory.reserve(link / 'evidence', self.repo)

    def test_ci_owner_controlled_755_root_is_supported(self):
        runner = self.allowed / 'ci-runner'
        runner.mkdir(mode=0o755)
        with patch.dict(os.environ, {'RUNNER_TEMP': str(runner)}):
            evidence = RUNTIME.EvidenceDirectory.reserve(runner / 'evidence', self.repo)
        self.addCleanup(evidence.close)
        evidence.write('receipt.json', b'{}')
        self.assertEqual(0o700, stat.S_IMODE(evidence.path.stat().st_mode))

    def test_unsafe_root_permission_change_after_reservation_is_detected(self):
        evidence = self.reserve('private-authority/evidence')
        authority = self.allowed / 'private-authority'
        authority.chmod(0o777)
        with self.assertRaises(GATE.QualityError):
            evidence.write('receipt.json', b'bad')
        self.assertFalse((evidence.path / 'receipt.json').exists())

    def test_authority_swap_during_allocation_cannot_redirect_writes(self):
        root = self.allowed / 'authority'
        root.mkdir(mode=0o700)
        outside = self.allowed / 'outside'
        outside.mkdir()
        original_mkdir = RUNTIME.os.mkdir

        def race_mkdir(name, *args, **kwargs):
            if name == 'evidence':
                root.rename(self.allowed / 'original-authority')
                root.symlink_to(outside, target_is_directory=True)
            return original_mkdir(name, *args, **kwargs)

        with patch.object(RUNTIME.os, 'mkdir', side_effect=race_mkdir), \
                self.assertRaises((GATE.QualityError, OSError)):
            RUNTIME.EvidenceDirectory.reserve(root / 'evidence', self.repo, [(root, root)])
        self.assertEqual([], list(outside.iterdir()))

    def test_world_writable_parent_is_never_used_for_allocation(self):
        root = self.allowed / 'shared'
        root.mkdir()
        root.chmod(0o1777)
        parent = os.open(root, RUNTIME.DIRECTORY_FLAGS)
        self.addCleanup(os.close, parent)
        with patch.object(RUNTIME.os, 'mkdir') as create, self.assertRaises(GATE.QualityError):
            RUNTIME._make_directory(parent, 'unsafe', exclusive=True, parent_path=root)
        create.assert_not_called()

    def test_sticky_system_temp_requires_private_boundary(self):
        system = list(self.allowed.stat())
        system[stat.ST_UID] = 0
        system[stat.ST_MODE] = stat.S_IFDIR | 0o1777
        self.assertFalse(RUNTIME._ancestry_state(os.stat_result(system), True))
        private = list(self.allowed.stat())
        private[stat.ST_MODE] = stat.S_IFDIR | 0o700
        self.assertTrue(RUNTIME._ancestry_state(os.stat_result(private), False))
        private[stat.ST_MODE] = stat.S_IFDIR | 0o755
        self.assertFalse(RUNTIME._ancestry_state(os.stat_result(private), False))

    def shared_workspace(self):
        candidates = [Path(tempfile.gettempdir()).resolve(), *SOURCE.resolve().parents]
        for path in candidates:
            metadata = path.stat()
            if metadata.st_uid == 0 and metadata.st_mode & stat.S_ISVTX and metadata.st_mode & 0o022:
                workspace = tempfile.TemporaryDirectory(dir=path)
                self.addCleanup(workspace.cleanup)
                return path, Path(workspace.name)
        self.skipTest('No root-owned sticky system temp exists on this platform')

    def test_native_temp_without_private_boundary_cannot_allocate_first_child(self):
        _, workspace = self.shared_workspace()
        workspace.chmod(0o755)
        with patch.object(RUNTIME.tempfile, 'gettempdir', return_value=str(workspace)), \
                patch.dict(os.environ, {'RUNNER_TEMP': ''}), patch.object(RUNTIME.os, 'mkdir') as create, \
                self.assertRaises(GATE.QualityError):
            RUNTIME.EvidenceDirectory.reserve(workspace / 'evidence', self.repo)
        create.assert_not_called()
        self.assertFalse((workspace / 'evidence').exists())

    def test_shared_native_temp_accepts_securely_allocated_private_workspace(self):
        shared, workspace = self.shared_workspace()
        evidence = RUNTIME.EvidenceDirectory.reserve(workspace / 'evidence', self.repo, [(shared, shared)])
        self.addCleanup(evidence.close)
        evidence.write('receipt.json', b'{}')
        self.assertEqual(0o700, stat.S_IMODE(evidence.path.stat().st_mode))


class HistoricalTransitionTests(RepositoryFixture):
    def setUp(self):
        super().setUp()
        self.git = RUNTIME.GitRepository(self.repo)
        self.path = 'sonar-project.properties'
        (self.repo / self.path).write_text('before\n')
        self.commit('before policy')
        self.before = self.git.head()
        (self.repo / self.path).write_text('after\n')
        self.commit('exact policy transition')
        self.origin = self.git.head()
        self.transition = POLICY.PolicyTransition(
            self.path, self.origin, self.git.blob_id(self.before, self.path),
            self.git.blob_id(self.origin, self.path), 'fixture explicit approval', 'fixture effect',
        )

    def commit(self, message):
        subprocess.run(['git', '-C', str(self.repo), 'add', '.'], check=True)
        subprocess.run(['git', '-C', str(self.repo), 'commit', '-qm', message], check=True)

    def test_exact_endpoints_origin_and_parent_are_verified(self):
        record = POLICY.verify_transition(self.git, self.before, self.transition)
        self.assertEqual(self.origin, record['origin'])
        self.assertEqual(self.transition.after, record['after'])

    def test_mutated_current_bytes_or_either_hash_are_rejected(self):
        for change in ({'before': '0' * 40}, {'after': '0' * 40}):
            with self.subTest(change=change), self.assertRaises(GATE.QualityError):
                POLICY.verify_transition(self.git, self.before, replace(self.transition, **change))
        (self.repo / self.path).write_text('after\nsonar.exclusions=**\n')
        with self.assertRaises(GATE.QualityError):
            POLICY.verify_transition(self.git, self.before, self.transition)

    def test_citing_an_unrelated_later_commit_cannot_authorize_transition(self):
        (self.repo / 'README.md').write_text('unrelated\n')
        self.commit('unrelated')
        forged = replace(self.transition, origin=self.git.head())
        with self.assertRaises(GATE.QualityError):
            POLICY.verify_transition(self.git, self.before, forged)

    def test_known_path_without_exact_registered_transition_is_rejected(self):
        with self.assertRaises(GATE.QualityError):
            POLICY.verify_protected_policy(self.git, {self.path}, self.before)

    def test_unknown_protected_policy_and_symlink_are_rejected(self):
        with self.assertRaises(GATE.QualityError):
            POLICY.verify_protected_policy(self.git, {'.editorconfig'}, self.before)
        (self.repo / self.path).unlink()
        (self.repo / self.path).symlink_to(self.repo / 'app/build.gradle.kts')
        with self.assertRaises(GATE.QualityError):
            POLICY.verify_transition(self.git, self.before, self.transition)

    def test_no_policy_flag_can_waive_a_new_source_suppression(self):
        p = self.repo / self.source
        p.write_text('@Suppress("FunctionNaming")\nfun NewName() = Unit\n')
        with patch.object(POLICY, 'STARTING_SHA', self.git.head()):
            audit = POLICY.audit_suppressions(self.git, {self.source}, self.before)
        self.assertEqual(1, len(audit))
        self.assertEqual('NOT_APPROVED', audit[0]['approval'])
        self.assertEqual('HOLD', audit[0]['result'])

    def test_suppression_audit_prevents_pass_even_with_zero_report_findings(self):
        result = {'baseline': {'findings': [], 'gradle_rc': 0},
                  'candidate': {'findings': [], 'gradle_rc': 0}, 'reports_only': False,
                  'policy': {'suppression_audit': [{'approval': 'NOT_APPROVED'}]}, 'result': 'HOLD'}
        GATE.apply_comparison(result, self.base_repo, self.repo)
        self.assertEqual('HOLD', result['result'])



# Exact historical test data, not a runtime policy exception. No Git objects are written.
_HISTORICAL_FIXTURE = 'tools/tests/fixtures/l0_87_marker_history.json'
_HISTORICAL_14bd8ea975dfde4c5ac336b09a2b387b173736b96aed76badd1c6f2b7ec17527 = '14bd8ea975dfde4c5ac336b09a2b387b173736b96aed76badd1c6f2b7ec17527'
_HISTORICAL_BASE = '09d331d9f9b278ba1bed1323056cf1504e456629'
_HISTORICAL_MAIN = 'b27162819a1611fc0abae8c389deb54cb9352b44'
_HISTORICAL_TREE = '918c2f9cf7bb2184321ebf0c16e2882ee584b650'
_HISTORICAL_INVENTORY_SHA = 'aa84e2ec1e4bc6e44380f51835d26627c159aec94458eefae94b74ec8baad721'
_PHONE_PATH = 'phone/src/main/java/io/github/aimindseye/tenseven/features/phone/TenSevenTelecom.kt'
_PHONE_BLOB = '8ad78380f3444840f4a2a347fb4bd19225f9c1ec'
_PHONE_METHOD_SHA = '7a84890369ed0850da01c102bbe0f72a641d9c18c1d271c07e0aeafae7cd05b8'
_SABLE_INVENTORY_SHA = '407f7d83c868bb63df1594d8e313d02c84354df32084e3d69f9a366d584b3d99'


def _require_snapshot(condition, message):
    if not condition:
        raise GATE.QualityError(message)


def _read_historical_object(repo, embedded, reachable, used, oid, kind):
    import base64
    RUNTIME.object_id(oid)
    if oid in embedded:
        item = embedded[oid]
        _require_snapshot(item['kind'] == kind and oid not in reachable, 'Historical object kind/origin differs')
        _require_snapshot(item['encoding'] in {'utf8', 'base64'}, 'Historical encoding differs')
        raw = item['data'].encode() if item['encoding'] == 'utf8' else base64.b64decode(item['data'], validate=True)
        used.add(oid)
    else:
        _require_snapshot(oid in reachable, 'Historical object missing from portable fixture/base')
        raw = repo._read(['cat-file', kind, oid])
    actual = subprocess.check_output(
        [RUNTIME.GIT, 'hash-object', '-t', kind, '--stdin'], input=raw).decode().strip()
    _require_snapshot(actual == oid, 'Historical Git object differs')
    return raw


def _historical_tree_entries(name, raw):
    offset = 0
    while offset < len(raw):
        space, end = raw.index(b' ', offset), raw.index(b'\0', offset)
        mode, part = raw[offset:space].decode(), raw[space + 1:end].decode()
        _require_snapshot(mode in {'40000', '100644', '100755'}, 'Unsupported historical file type')
        _require_snapshot('/' not in part and part not in {'.', '..'}, 'Unsafe historical entry')
        path = RUNTIME.repository_path(name + '/' + part if name else part)
        child = raw[end + 1:end + 21]
        _require_snapshot(len(child) == 20, 'Truncated historical tree')
        yield path, child.hex(), 'tree' if mode == '40000' else 'blob'
        offset = end + 21


def _historical_objects(repo, data):
    reachable = {line.split()[0] for line in repo._read(
        ['rev-list', '--objects', _HISTORICAL_BASE, '--']).decode().splitlines()}
    embedded, used, files = data['objects'], set(), {}
    pending = [('', _HISTORICAL_TREE, 'tree')]
    while pending:
        name, oid, kind = pending.pop()
        raw = _read_historical_object(repo, embedded, reachable, used, oid, kind)
        if kind == 'blob':
            _require_snapshot(name not in files, 'Duplicate historical source path')
            files[name] = (oid, raw)
        else:
            pending.extend(_historical_tree_entries(name, raw))
    _require_snapshot(used == set(embedded), 'Unused opaque historical fixture objects')
    return files


def _historical_added_lines(before, after, diff):
    import re
    old, new, added, cursor, target = before.decode().splitlines(), after.decode().splitlines(), [], 0, []
    for line in diff.splitlines():
        header = re.match(r'@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@', line)
        if header:
            start = int(header[1]) - (int(header[2] or 1) != 0)
            _require_snapshot(start >= cursor, 'Overlapping historical diff')
            target.extend(old[cursor:start])
            cursor = start
            expected = int(header[3]) - (int(header[4] or 1) != 0)
            _require_snapshot(len(target) == expected, 'Historical diff position differs')
        elif line.startswith('--- ') or line.startswith('+++ '):
            continue
        elif line.startswith('-'):
            _require_snapshot(cursor < len(old) and old[cursor] == line[1:], 'Historical removal differs')
            cursor += 1
        elif line.startswith('+'):
            target.append(line[1:])
            added.append((len(target), line[1:]))
        elif line.startswith(' '):
            _require_snapshot(cursor < len(old) and old[cursor] == line[1:], 'Historical context differs')
            target.append(line[1:])
            cursor += 1
    target.extend(old[cursor:])
    _require_snapshot(target == new, 'Historical diff does not reconstruct authenticated source')
    return added


def _verify_historical_inventory(repo):
    import hashlib
    with POLICY._source_snapshot(repo, {_HISTORICAL_FIXTURE}) as snapshot:
        raw = snapshot[_HISTORICAL_FIXTURE]
        _require_snapshot(hashlib.sha256(raw).hexdigest() == _HISTORICAL_14bd8ea975dfde4c5ac336b09a2b387b173736b96aed76badd1c6f2b7ec17527, 'Historical fixture differs')
        data = json.loads(raw)
    _require_snapshot((data['format'], data['base'], data['main'], data['tree'], data['inventory_sha256']) ==
                      (1, _HISTORICAL_BASE, _HISTORICAL_MAIN, _HISTORICAL_TREE, _HISTORICAL_INVENTORY_SHA),
                      'Historical identities differ')
    files = _historical_objects(repo, data)
    canonical, checkout = hashlib.sha256(), hashlib.sha256()
    for name in sorted(set(files) | repo.tree_files(_HISTORICAL_BASE)):
        value = files[name][1] if name in files else b'<deleted>'
        canonical.update(name.encode() + b'\0' + value)
        checkout.update(name.encode() + b'\0' + (value.replace(b'\n', b'\r\n') if name == 'gradlew.bat' else value))
    _require_snapshot(canonical.hexdigest() == 'b08df0883a5d55d7339235a8e4dab8aaabb2a06b341e16c50b5551310905278e',
                      'Canonical historical source differs')
    _require_snapshot(checkout.hexdigest() == '53aad6be2ab1a4767c8c06b64797726cd2a2c537be420b7ccd2d3ddee7303887',
                      'Historical checkout source differs')
    main = repo.tree_files(_HISTORICAL_MAIN)
    changed = {name for name in set(files) | main if name.endswith(('.kt', '.kts')) and
               (name not in files or name not in main or files[name][0] != repo.blob_id(_HISTORICAL_MAIN, name))}
    _require_snapshot(changed == set(data['patches']), 'Historical diff scope differs')
    inventory = []
    for name in sorted(changed):
        after = files[name][1] if name in files else b''
        before = repo.blob(_HISTORICAL_MAIN, name) if name in main else b''
        for number, text in _historical_added_lines(before, after, data['patches'][name]):
            if any(marker in text for marker in POLICY.MARKERS):
                inventory.append(dict(file=name, line=number, text=text,
                                      classification=POLICY.suppression_risk(text)[1],
                                      source_blob=POLICY.blob_id(after), source_sha256=hashlib.sha256(after).hexdigest()))
    encoded = json.dumps(inventory, sort_keys=True, separators=(',', ':')).encode()
    _require_snapshot(hashlib.sha256(encoded).hexdigest() == _HISTORICAL_INVENTORY_SHA,
                      'Historical marker identities differ')
    _require_snapshot(len(inventory) == 87 and Counter(item['classification'] for item in inventory) ==
                      {'compose-naming-redundancy-review-required': 80,
                       'unused-parameter-contract-review-required': 6, 'permission-contract-review-required': 1},
                      'Historical 80/6/1 classification differs')
    return inventory


def _verify_current_phone_inventory(repo, base, audit):
    import hashlib
    from quality_gate_module_policy import PHONE_CONTRACT, review_permission_contract, verify
    verified = verify(repo, base)
    expected = [(_PHONE_PATH, 498, '    @SuppressLint("MissingPermission")', 'permission-contract-review-required', 'high')]
    _require_snapshot([(i['file'], i['line'], i['text'], i['classification'], i['risk']) for i in audit
                       if i['file'] == _PHONE_PATH] == expected,
                      'Candidate raw inventory differs from sole reviewed Phone marker')
    reviewed = sorted((i['file'], i['text'].strip(), i['classification']) for i in audit if i['file'] != _PHONE_PATH)
    _require_snapshot(hashlib.sha256('\n'.join('\t'.join(row) for row in reviewed).encode()).hexdigest() ==
                      _SABLE_INVENTORY_SHA and len(reviewed) == 34,
                      'Sable launcher marker inventory differs from the reviewed 34 structural markers')
    _require_snapshot(all(i['approval'] == 'NOT_APPROVED' and i['result'] == 'HOLD' for i in audit),
                      'Raw audit granted approval')
    with POLICY._source_snapshot(repo, {_PHONE_PATH}) as snapshot:
        source = snapshot[_PHONE_PATH]
        _require_snapshot(POLICY.blob_id(source) == _PHONE_BLOB and hashlib.sha256(source).hexdigest() ==
                          '260f477d149d3fe3caa4eea8311a9d58b2551dffc9170a4f9821365b326ee24e', 'Phone endpoint differs')
        method = PHONE_CONTRACT['method'].encode()
        _require_snapshot(hashlib.sha256(method).hexdigest() == _PHONE_METHOD_SHA and source.count(method) == 1,
                          'Phone method guard differs')
    pending, approved = review_permission_contract(repo, base, audit)
    _require_snapshot(not pending and len(approved) == 35 and
                      [i['result'] for i in approved] == ['EXACT_REVIEWED_CONTRACT'] + ['EXACT_REVIEWED_INVENTORY'] * 34,
                      'Phone contract or reviewed Sable inventory differs')
    item = approved[0]
    _require_snapshot((item['original_blob'], item['candidate_blob'], item['method_sha256'],
                       item['original_annotation_commit'], item['module_origin']) ==
                      ('60a113f13c46c9088c2ff7fdf7c077b526b15be3', _PHONE_BLOB, _PHONE_METHOD_SHA,
                       'c42fb4b221e2ede2dce35315eca24de413b75077', verified['origin']), 'Phone provenance differs')
    return item


@unittest.skipUnless(os.environ.get('TENSEVEN_POLICY_REPO'), 'Real policy repository not supplied')
class RealHistoricalPolicyTests(unittest.TestCase):
    def setUp(self):
        self.repo = RUNTIME.GitRepository(os.environ['TENSEVEN_POLICY_REPO'])
        self.base = self.repo.resolve('b27162819a1611fc0abae8c389deb54cb9352b44')

    def test_actual_history_is_bound_to_both_exact_endpoints_and_origins(self):
        records = POLICY.verify_protected_policy(self.repo, self.repo.changed(self.base), self.base)
        historical = [item for item in records if 'verification_mode' not in item]
        self.assertEqual(2, len(historical))
        self.assertEqual({item.path for item in POLICY.TRANSITIONS}, {item['path'] for item in historical})
        harness = [item for item in records if 'verification_mode' in item]
        expected = self.repo.changed(self.base) & POLICY.TEST_HARNESS_ENDPOINTS.keys()
        self.assertEqual(expected, {item['path'] for item in harness})
        self.assertEqual(len(expected), len(harness))

    def test_real_suppression_inventory_never_grants_automatic_approval(self):
        audit = POLICY.audit_suppressions(self.repo, self.repo.changed(self.base), self.base)
        from quality_gate_module_policy import active
        if active(self.repo):
            _verify_historical_inventory(self.repo)
            _verify_current_phone_inventory(self.repo, self.base, audit)
        else:
            # Preserve the original pre-L0 historical branch and its independent origin assertions.
            self.assertEqual(89, len(audit))
            self.assertFalse(any('/extras/games/' in item['file'] for item in audit))
            self.assertFalse(any(item['file'].endswith('/tasks/TasksActivity.kt') for item in audit))
            self.assertFalse(any(item['file'].endswith('/ui/AppPrivacyScreen.kt') for item in audit))
            self.assertTrue(all(item['approval'] == 'NOT_APPROVED' and item['result'] == 'HOLD' for item in audit))
            self.assertTrue(all(item['inherited_before_r1'] and item['origin_commit'] for item in audit))

    def test_one_byte_change_in_either_actual_transition_is_rejected(self):
        original = POLICY.source_bytes
        for transition in POLICY.TRANSITIONS:
            def altered(repo, filename, transition=transition):
                data = original(repo, filename)
                return data + b'# altered\n' if filename == transition.path else data
            with self.subTest(path=transition.path), patch.object(POLICY, 'source_bytes', side_effect=altered), \
                    self.assertRaises(GATE.QualityError):
                POLICY.verify_transition(self.repo, self.base, transition)


@unittest.skipUnless(os.environ.get('TENSEVEN_POLICY_REPO'), 'Real policy repository not supplied')
class RemediatedSuppressionSnapshotTests(unittest.TestCase):
    def setUp(self):
        self.repo = RUNTIME.GitRepository(os.environ['TENSEVEN_POLICY_REPO'])
        self.base = self.repo.resolve(_HISTORICAL_MAIN)
        self.changed = self.repo.changed(self.base)
        self.audit = POLICY.audit_suppressions(self.repo, self.changed, self.base)

    def assert_added_marker_blocks(self, marker):
        path = 'app/src/main/java/io/github/aimindseye/tenseven/ui/SableStartScreen.kt'
        original = POLICY.added_lines

        def extra(repo, base, filename):
            lines = original(repo, base, filename)
            return lines + [(1, marker)] if filename == path else lines

        with patch.object(POLICY, 'added_lines', side_effect=extra):
            policy = POLICY.review_policy(self.repo, self.changed, self.base, approved_compose=True)
        # Any change to the reviewed inventory voids its approval: all markers go back to pending review.
        reviewed = Counter(item['text'] for item in self.audit if item['file'] != _PHONE_PATH)
        added = Counter(item['text'] for item in policy['suppression_audit']) - reviewed
        self.assertEqual(Counter([marker]), added)
        self.assertEqual(35, len(policy['suppression_audit']))
        result = dict(baseline=dict(findings=[], gradle_rc=0), candidate=dict(findings=[], gradle_rc=0),
                      reports_only=False, policy=policy, result='HOLD')
        GATE.apply_comparison(result, self.repo.path, self.repo.path)
        self.assertEqual('HOLD', result['result'])

    def test_reintroduced_naming_marker_still_blocks_genuine_policy_and_comparator(self):
        self.assert_added_marker_blocks('@Suppress("FunctionNaming")')

    def test_reintroduced_unused_parameter_marker_still_blocks_genuine_policy_and_comparator(self):
        self.assert_added_marker_blocks('@Suppress("UNUSED_PARAMETER")')

    def test_unknown_marker_still_blocks_genuine_policy_and_comparator(self):
        self.assert_added_marker_blocks('@Suppress("UnknownRule")')

    def test_removed_phone_marker_cannot_pass_the_current_inventory_proof(self):
        self.assertRaises(GATE.QualityError, _verify_current_phone_inventory, self.repo, self.base, [])

    def test_duplicate_phone_marker_is_rejected_by_the_actual_contract_reviewer(self):
        from quality_gate_module_policy import review_permission_contract
        self.assertRaises(GATE.QualityError, review_permission_contract, self.repo, self.base, self.audit * 2)

    def assert_phone_mutation_rejected(self, before, after):
        from contextlib import contextmanager
        original = POLICY._source_snapshot

        @contextmanager
        def altered(repo, paths):
            with original(repo, paths) as values:
                copy = dict(values)
                if _PHONE_PATH in copy:
                    self.assertIn(before, copy[_PHONE_PATH])
                    copy[_PHONE_PATH] = copy[_PHONE_PATH].replace(before, after, 1)
                yield copy

        with patch.object(POLICY, '_source_snapshot', side_effect=altered):
            self.assertRaises(GATE.QualityError, POLICY.review_policy,
                              self.repo, self.changed, self.base, approved_compose=True)

    def test_broadened_phone_annotation_is_rejected(self):
        self.assert_phone_mutation_rejected(b'@SuppressLint("MissingPermission")',
                                            b'@SuppressLint("MissingPermission", "NewRule")')

    def test_changed_phone_role_guard_is_rejected(self):
        self.assert_phone_mutation_rejected(b'isRoleHeld(RoleManager.ROLE_DIALER) != true',
                                            b'isRoleHeld(RoleManager.ROLE_DIALER) == true')

    def test_changed_phone_source_with_unchanged_method_is_rejected(self):
        self.assert_phone_mutation_rejected(b'package io.github.', b'// unrelated endpoint mutation\npackage io.github.')

    def test_pre_l0_ancestry_cannot_authorize_the_candidate(self):
        with patch.object(self.repo, 'head', return_value=self.base):
            self.assertRaises(GATE.QualityError, POLICY.verify_protected_policy,
                              self.repo, self.changed, self.base)

    def test_unknown_ancestry_cannot_authorize_the_candidate(self):
        with patch.object(self.repo, 'head', return_value='0' * 40):
            self.assertRaises(GATE.QualityError, POLICY.verify_protected_policy,
                              self.repo, self.changed, self.base)

    def test_forged_historical_fixture_fails_before_parsing(self):
        from contextlib import contextmanager
        original = POLICY._source_snapshot

        @contextmanager
        def altered(repo, paths):
            with original(repo, paths) as values:
                copy = dict(values)
                if _HISTORICAL_FIXTURE in copy:
                    copy[_HISTORICAL_FIXTURE] += b' '
                yield copy

        with patch.object(POLICY, '_source_snapshot', side_effect=altered):
            self.assertRaises(GATE.QualityError, _verify_historical_inventory, self.repo)

    def test_historical_git_object_mutation_fails_immutable_tree_verification(self):
        with POLICY._source_snapshot(self.repo, {_HISTORICAL_FIXTURE}) as values:
            data = json.loads(values[_HISTORICAL_FIXTURE])
        import base64
        item = data['objects'][_HISTORICAL_TREE]
        raw = item['data'].encode() if item['encoding'] == 'utf8' else base64.b64decode(item['data'])
        # Change an object-ID byte, preserving the tree's names, modes, shape and encoding.
        item['encoding'] = 'base64'
        item['data'] = base64.b64encode(raw[:-1] + bytes([raw[-1] ^ 1])).decode()
        self.assertRaises(GATE.QualityError, _historical_objects, self.repo, data)

    def test_historical_diff_cannot_drop_or_reclassify_a_marker(self):
        self.assertRaises(GATE.QualityError, _historical_added_lines,
                          b'fun Test() = Unit\n', b'@Suppress("FunctionNaming")\nfun Test() = Unit\n',
                          '@@ -0,0 +1 @@\n+@Suppress("UnusedParameter")\n')

    def test_historical_fixture_has_no_dependency_on_unreachable_local_git_objects(self):
        # The reader deliberately permits Git fallback only for objects reachable from the frozen BASE.
        # Every historical-only object must come from the pinned portable fixture, including its root.
        with POLICY._source_snapshot(self.repo, {_HISTORICAL_FIXTURE}) as values:
            data = json.loads(values[_HISTORICAL_FIXTURE])
        del data['objects'][_HISTORICAL_TREE]
        self.assertRaises(GATE.QualityError, _historical_objects, self.repo, data)


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
        # A real baseline finding cannot grant a waiver to a different source file.
        # R2 closed the final parent FunctionNaming findings, so do not require that legacy debt.
        target = 'app/src/main/java/io/github/aimindseye/tenseven/CompanionWizardActivity.kt'
        borrowed = next(item for item in old if item['file'] != target)
        injected = {**borrowed, 'file': target, 'line': 61}
        self.assertEqual(1, len(GATE.compare_findings(old, new + [injected], base, repo)[0]))


class JointTestHarnessTests(RepositoryFixture):
    def setUp(self):
        super().setUp()
        self.git = RUNTIME.GitRepository(self.repo)
        self.paths = ('app/build.gradle.kts', 'gradle/libs.versions.toml')
        (self.repo / 'gradle').mkdir()
        (self.repo / self.paths[1]).write_bytes(b'catalog before\n')
        for name in ('.editorconfig', 'config/detekt.yml', 'config/DETEKT_PROVENANCE.env',
                     'gradle.properties', 'settings.gradle.kts', 'gradlew',
                     'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties'):
            target = self.repo / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(b'unchanged synthetic analyzer input\n')
        self.commit('approved base')
        self.approved_base = self.git.head()
        self.before = {path: (self.repo / path).read_bytes() for path in self.paths}
        self.after = {path: b'approved test harness: ' + path.encode() + b'\n' for path in self.paths}
        self.endpoints = {path: (POLICY.blob_id(self.before[path]), POLICY.blob_id(self.after[path]))
                          for path in self.paths}
        self.fixture = 'app/src/test/java/HarnessTest.kt'
        self.fixture_bytes = b'fun behavioralAssertion() = check(2 + 2 == 4)\n'
        (self.repo / self.fixture).parent.mkdir(parents=True)
        (self.repo / self.fixture).write_bytes(self.fixture_bytes)
        for path in self.paths:
            (self.repo / path).write_bytes(self.after[path])
        fixtures = {self.fixture: POLICY.hashlib.sha256(self.fixture_bytes).hexdigest()}
        for name, value in [('TEST_HARNESS_BASE', self.approved_base),
                            ('TEST_HARNESS_ENDPOINTS', self.endpoints),
                            ('TEST_HARNESS_FIXTURES', fixtures)]:
            replacement = patch.object(POLICY, name, value)
            replacement.start()
            self.addCleanup(replacement.stop)

    def commit(self, message):
        subprocess.run(['git', '-C', str(self.repo), 'add', '.'], check=True)
        subprocess.run(['git', '-C', str(self.repo), 'commit', '-qm', message], check=True)

    def verify(self):
        return POLICY.verify_protected_policy(self.git, set(self.paths), self.approved_base)

    def assert_rejected(self):
        with self.assertRaises(GATE.QualityError):
            self.verify()

    def test_pre_commit_is_exact_joint_approval_without_an_invented_origin(self):
        records = self.verify()
        self.assertEqual(set(self.paths), {item['path'] for item in records})
        self.assertEqual(2, len(records))
        self.assertTrue(all(item['origin'] is None and item['verification_mode'] == 'pre_commit'
                            and item['origin_parent'] == self.approved_base for item in records))

    def test_actual_origin_and_later_descendant_are_verified(self):
        self.commit('joint origin')
        origin = self.git.head()
        (self.repo / 'README.md').write_text('later independent change\n')
        self.commit('descendant')
        records = self.verify()
        self.assertTrue(all(item['origin'] == origin and item['verification_mode'] == 'immutable_origin'
                            for item in records))
        self.assertEqual(self.approved_base, self.git.parent(origin))

    def test_merge_commit_cannot_be_the_origin_even_with_approved_first_parent(self):
        subprocess.run(['git', '-C', str(self.repo), 'add', '.'], check=True)
        tree = self.git._read(['write-tree']).decode().strip()
        merge = subprocess.check_output(
            ['git', '-C', str(self.repo), 'commit-tree', tree,
             '-p', self.approved_base, '-p', self.base], input=b'not a single-parent origin\n',
        ).decode().strip()
        with patch.object(self.git, 'head', return_value=merge):
            self.assert_rejected()

    def test_ci_merge_can_find_origin_on_second_parent_lineage(self):
        self.commit('joint origin')
        origin = self.git.head()
        tree = self.git._read(['rev-parse', 'HEAD^{tree}']).decode().strip()
        merge = subprocess.check_output(
            ['git', '-C', str(self.repo), 'commit-tree', tree, '-p', self.base, '-p', origin],
            input=b'synthetic CI merge\n',
        ).decode().strip()
        with patch.object(self.git, 'head', return_value=merge):
            records = self.verify()
        self.assertTrue(all(item['origin'] == origin for item in records))

    def test_two_matching_origins_are_ambiguous_and_rejected(self):
        subprocess.run(['git', '-C', str(self.repo), 'add', '.'], check=True)
        tree = self.git._read(['write-tree']).decode().strip()
        origins = [subprocess.check_output(
            ['git', '-C', str(self.repo), 'commit-tree', tree, '-p', self.approved_base],
            input=message,
        ).decode().strip() for message in (b'first origin\n', b'second origin\n')]
        merge = subprocess.check_output(
            ['git', '-C', str(self.repo), 'commit-tree', tree, '-p', origins[0], '-p', origins[1]],
            input=b'ambiguous merge\n',
        ).decode().strip()
        with patch.object(self.git, 'head', return_value=merge):
            self.assert_rejected()

    def test_one_byte_change_to_either_endpoint_is_rejected(self):
        for path in self.paths:
            with self.subTest(path=path):
                (self.repo / path).write_bytes(self.after[path] + b'!')
                self.assert_rejected()
                (self.repo / path).write_bytes(self.after[path])

    def test_partial_application_and_missing_endpoint_are_rejected(self):
        for path in self.paths:
            with self.subTest(path=path):
                (self.repo / path).write_bytes(self.before[path])
                self.assert_rejected()
                (self.repo / path).unlink()
                self.assert_rejected()
                (self.repo / path).write_bytes(self.after[path])

    def test_reporting_only_one_changed_protected_path_cannot_authorize_pair(self):
        protected = {self.paths[0]}
        with self.assertRaises(GATE.QualityError):
            POLICY.verify_protected_policy(self.git, protected, self.approved_base)

    def test_dependency_repository_signing_and_version_substitutions_are_rejected(self):
        mutations = [b'testImplementation("org.robolectric:robolectric:4.19")\n',
                     b'implementation("attacker:runtime:1")\n', b'repositories { mavenLocal() }\n',
                     b'signingConfigs { create("replacement") }\n']
        for extra in mutations:
            with self.subTest(extra=extra):
                (self.repo / self.paths[0]).write_bytes(self.after[self.paths[0]] + extra)
                self.assert_rejected()

    def test_bad_registered_parent_and_nonancestor_base_are_rejected(self):
        invalid = {path: ('0' * 40, pair[1]) for path, pair in self.endpoints.items()}
        with patch.object(POLICY, 'TEST_HARNESS_ENDPOINTS', invalid):
            self.assert_rejected()
        with patch.object(self.git, 'ancestor', return_value=False):
            self.assert_rejected()

    def test_head_change_during_verification_is_rejected(self):
        with patch.object(self.git, 'head', side_effect=[self.approved_base, '1' * 40]):
            self.assert_rejected()

    def test_missing_or_modified_approved_fixture_is_rejected(self):
        target = self.repo / self.fixture
        target.write_bytes(self.fixture_bytes + b'// changed assertion\n')
        self.assert_rejected()
        target.unlink()
        self.assert_rejected()

    def test_fixture_symlink_is_rejected_even_with_approved_target_bytes(self):
        target = self.repo / self.fixture
        outside = Path(self.tmp.name) / 'outside-fixture'
        outside.write_bytes(self.fixture_bytes)
        target.unlink()
        target.symlink_to(outside)
        self.assert_rejected()
        self.assertEqual(self.fixture_bytes, outside.read_bytes())

    def test_symlinked_endpoint_or_ancestor_cannot_redirect_reads(self):
        target = self.repo / self.paths[0]
        outside = Path(self.tmp.name) / 'victim'
        outside.write_bytes(self.after[self.paths[0]])
        target.unlink()
        target.symlink_to(outside)
        self.assert_rejected()
        target.unlink()
        target.write_bytes(self.after[self.paths[0]])
        app = self.repo / 'app'
        app.rename(self.repo / 'original-app')
        app.symlink_to(self.repo / 'original-app', target_is_directory=True)
        self.assert_rejected()
        self.assertEqual(self.after[self.paths[0]], outside.read_bytes())

    def test_executable_and_nonregular_policy_files_are_rejected(self):
        target = self.repo / self.paths[0]
        target.chmod(0o755)
        self.assert_rejected()
        target.unlink()
        os.mkfifo(target)
        self.assert_rejected()

    def test_traversal_in_a_fixture_path_cannot_read_outside_repository(self):
        invalid = {'../outside.kt': '0' * 64}
        with patch.object(POLICY, 'TEST_HARNESS_FIXTURES', invalid):
            self.assert_rejected()

    def test_replacement_after_read_is_detected_without_touching_victim(self):
        target = self.repo / self.paths[0]
        outside = Path(self.tmp.name) / 'victim'
        outside.write_bytes(b'private victim\n')
        original = POLICY._descriptor_bytes
        replaced = False

        def replace_after_read(descriptor):
            nonlocal replaced
            data = original(descriptor)
            if not replaced:
                replaced = True
                target.rename(self.repo / 'saved-build')
                target.symlink_to(outside)
            return data

        with patch.object(POLICY, '_descriptor_bytes', side_effect=replace_after_read):
            self.assert_rejected()
        self.assertTrue(replaced)
        self.assertEqual(b'private victim\n', outside.read_bytes())

    def test_same_inode_change_after_joint_consumption_is_detected(self):
        self.commit('joint origin')
        original = POLICY._harness_origin

        def change_during_history(repo, head):
            origin = original(repo, head)
            target = self.repo / self.paths[0]
            target.write_bytes(target.read_bytes() + b'changed during history\n')
            return origin

        with patch.object(POLICY, '_harness_origin', side_effect=change_during_history):
            self.assert_rejected()

    def test_root_path_swap_is_detected(self):
        self.commit('joint origin')
        original = POLICY._harness_origin
        replacement = Path(self.tmp.name) / 'replacement'
        replacement.mkdir()

        def replace_root(repo, head):
            origin = original(repo, head)
            self.repo.rename(Path(self.tmp.name) / 'original-repository')
            self.repo.symlink_to(replacement, target_is_directory=True)
            return origin

        with patch.object(POLICY, '_harness_origin', side_effect=replace_root):
            self.assert_rejected()
        self.assertEqual([], list(replacement.iterdir()))

    def test_split_commits_do_not_create_a_joint_origin(self):
        (self.repo / self.paths[1]).write_bytes(self.before[self.paths[1]])
        self.commit('partial transition')
        (self.repo / self.paths[1]).write_bytes(self.after[self.paths[1]])
        self.commit('later completion')
        self.assert_rejected()

    def test_an_unrelated_commit_before_transition_is_not_approved_parent(self):
        (self.repo / 'README.md').write_text('unrelated first commit\n')
        subprocess.run(['git', '-C', str(self.repo), 'add', 'README.md'], check=True)
        subprocess.run(['git', '-C', str(self.repo), 'commit', '-qm', 'unrelated first'], check=True)
        self.commit('late transition')
        self.assert_rejected()

    def test_modified_then_restored_policy_history_still_fails(self):
        self.commit('joint origin')
        target = self.repo / self.paths[0]
        target.write_bytes(b'unauthorized runtime dependency\n')
        self.commit('unknown policy')
        target.write_bytes(self.after[self.paths[0]])
        self.commit('restore endpoints')
        self.assert_rejected()

    def test_modified_then_restored_fixture_history_still_fails(self):
        self.commit('joint origin')
        target = self.repo / self.fixture
        target.write_bytes(b'fun assertionRemoved() = Unit\n')
        self.commit('weaken fixture')
        target.write_bytes(self.fixture_bytes)
        self.commit('restore fixture')
        self.assert_rejected()

    def analysis_parent(self, commit=None):
        path = Path(self.tmp.name) / 'analysis-parent'
        self.git.detached_worktree(path, self.approved_base if commit is None else commit)
        return RUNTIME.GitRepository(path)

    def activated_parent(self):
        self.commit('valid immutable harness origin')
        origin = self.git.head()
        return origin, self.analysis_parent(origin)

    def assert_entry_points_reject(self, parent):
        base = parent.head()
        changed = self.git.changed(base)
        self.assertFalse(changed & set(self.paths))
        with self.assertRaises(GATE.QualityError):
            POLICY.verify_protected_policy(self.git, changed, base)
        with self.assertRaises(GATE.QualityError):
            GATE.analysis_configuration(self.git, parent.path)

    def assert_entry_points_accept_activation(self, parent, origin):
        base = parent.head()
        records = POLICY.verify_protected_policy(self.git, self.git.changed(base), base)
        configuration = GATE.analysis_configuration(self.git, parent.path)
        self.assertEqual(set(self.paths), {item['path'] for item in records})
        self.assertEqual(set(self.paths), set(configuration['approved_test_harness_analysis']))
        self.assertTrue(all(item['origin'] == origin and item['verification_mode'] == 'immutable_origin'
                            for item in records))
        self.assertTrue(all(item['origin'] == origin for item in
                            configuration['approved_test_harness_analysis'].values()))

    def test_rev01_modified_fixture_descendant_rejects_through_both_entry_points(self):
        _, parent = self.activated_parent()
        (self.repo / self.fixture).write_bytes(b'fun weakenedAssertion() = Unit\n')
        self.commit('fixture only modified descendant')
        self.assert_entry_points_reject(parent)

    def test_rev01_deleted_fixture_descendant_rejects_through_both_entry_points(self):
        _, parent = self.activated_parent()
        (self.repo / self.fixture).unlink()
        self.commit('fixture only deleted descendant')
        self.assert_entry_points_reject(parent)

    def test_rev01_restored_fixture_history_rejects_through_both_entry_points(self):
        _, parent = self.activated_parent()
        target = self.repo / self.fixture
        target.write_bytes(b'fun weakenedAssertion() = Unit\n')
        self.commit('fixture tampering')
        target.write_bytes(self.fixture_bytes)
        self.commit('restore fixture bytes')
        self.assertEqual(self.fixture_bytes, target.read_bytes())
        self.assert_entry_points_reject(parent)

    def test_rev01_unrelated_descendant_accepts_through_both_entry_points(self):
        origin, parent = self.activated_parent()
        (self.repo / 'README.md').write_text('unrelated descendant\n')
        self.commit('unrelated descendant')
        self.assert_entry_points_accept_activation(parent, origin)

    def test_rev01_pre_harness_descendant_needs_no_origin_through_both_entry_points(self):
        for name in self.paths:
            (self.repo / name).write_bytes(self.before[name])
        (self.repo / self.fixture).unlink()
        (self.repo / 'README.md').write_text('before harness activation\n')
        self.commit('ordinary pre-harness descendant')
        parent = self.analysis_parent()
        records = POLICY.verify_protected_policy(self.git, self.git.changed(parent.head()), parent.head())
        configuration = GATE.analysis_configuration(self.git, parent.path)
        self.assertEqual([], records)
        self.assertEqual({}, configuration['approved_test_harness_analysis'])

    def test_rev01_ci_second_parent_origin_accepts_through_both_entry_points(self):
        origin, parent = self.activated_parent()
        (self.repo / 'README.md').write_text('candidate descendant\n')
        self.commit('candidate descendant')
        descendant = self.git.head()
        tree = self.git._read(['rev-parse', 'HEAD^{tree}']).decode().strip()
        merge = subprocess.check_output(
            ['git', '-C', str(self.repo), 'commit-tree', tree, '-p', self.base, '-p', descendant],
            input=b'synthetic CI merge after activation\n',
        ).decode().strip()
        with patch.object(self.git, 'head', return_value=merge):
            self.assert_entry_points_accept_activation(parent, origin)

    def test_rev01_restored_before_endpoints_cannot_erase_activation(self):
        self.commit('valid immutable harness origin')
        for name in self.paths:
            (self.repo / name).write_bytes(self.before[name])
        self.commit('unauthorized deactivation')
        parent = self.analysis_parent(self.git.head())
        (self.repo / 'README.md').write_text('descendant after hidden deactivation\n')
        self.commit('unrelated descendant')
        self.assert_entry_points_reject(parent)

    def test_rev01_uncommitted_fixture_change_rejects_through_both_entry_points(self):
        _, parent = self.activated_parent()
        (self.repo / self.fixture).write_bytes(b'fun weakenedAssertion() = Unit\n')
        self.assert_entry_points_reject(parent)

    def test_rev01_unknown_unactivated_endpoints_do_not_gain_a_filename_waiver(self):
        for name in self.paths:
            (self.repo / name).write_bytes(b'unknown unapproved build policy\n')
        parent = self.analysis_parent()
        base = parent.head()
        changed = self.git.changed(base)
        with self.assertRaises(GATE.QualityError):
            POLICY.verify_protected_policy(self.git, changed, base)
        with self.assertRaises(GATE.QualityError):
            GATE.analysis_configuration(self.git, parent.path)

    def test_analysis_configuration_records_exact_different_inputs(self):
        parent = self.analysis_parent()
        records = POLICY.verify_test_harness_analysis(self.git, parent)
        self.assertEqual(set(self.paths), set(records))
        for path, record in records.items():
            self.assertEqual(POLICY.hashlib.sha256(self.before[path]).hexdigest(), record['parent_sha256'])
            self.assertEqual(POLICY.hashlib.sha256(self.after[path]).hexdigest(), record['candidate_sha256'])

    def test_analysis_read_race_cannot_authorize_bytes_outside_approved_pair(self):
        parent = self.analysis_parent()
        original = POLICY.source_bytes

        def changed_bytes(repo, filename):
            data = original(repo, filename)
            return data + b'unauthorized\n' if repo.path == self.repo.resolve() and filename == self.paths[0] else data

        with ExitStack() as contexts:
            contexts.enter_context(patch.object(POLICY, 'source_bytes', side_effect=changed_bytes))
            contexts.enter_context(self.assertRaises(GATE.QualityError))
            POLICY.verify_test_harness_analysis(self.git, parent)

    def test_other_analysis_input_differences_still_fail_closed(self):
        parent = self.analysis_parent()

        def other_input(repo, filename):
            if filename in self.paths:
                return (repo.path / filename).read_bytes()
            return b'changed rules\n' if repo.path == self.repo.resolve() and filename == '.editorconfig' else b'same\n'

        with ExitStack() as contexts:
            contexts.enter_context(patch.object(GATE, 'source_bytes', side_effect=other_input))
            contexts.enter_context(self.assertRaises(GATE.QualityError))
            GATE.analysis_configuration(self.git, parent.path)

    def test_committed_executable_mode_is_not_hidden_by_worktree_permission_fix(self):
        subprocess.run(['git', '-C', str(self.repo), 'config', 'core.filemode', 'true'], check=True)
        target = self.repo / self.paths[0]
        target.chmod(0o755)
        self.commit('executable origin is not approved')
        target.chmod(0o644)
        self.assert_rejected()

    def test_unrelated_quality_policy_and_suppression_controls_remain_active(self):
        (self.repo / 'gradle.properties').write_text('tenseven.strictQuality=false\n')
        protected = set(self.paths) | {'gradle.properties'}
        with self.assertRaises(GATE.QualityError):
            POLICY.verify_protected_policy(self.git, protected, self.approved_base)
        (self.repo / self.source).write_text('@Suppress("FunctionNaming")\nfun NewName() = Unit\n')
        audit = POLICY.audit_suppressions(self.git, {self.source}, self.approved_base)
        self.assertEqual(1, len(audit))
        self.assertEqual('NOT_APPROVED', audit[0]['approval'])
        self.assertEqual('HOLD', audit[0]['result'])


@unittest.skipUnless(os.environ.get('TENSEVEN_POLICY_REPO'), 'Real policy repository not supplied')
class RealJointTestHarnessTests(unittest.TestCase):
    def test_real_owner_endpoints_and_fixture_hashes_are_bound(self):
        repo = RUNTIME.GitRepository(os.environ['TENSEVEN_POLICY_REPO'])
        base = repo.resolve(POLICY.TEST_HARNESS_BASE)
        protected = repo.changed(base) & POLICY.QUALITY_CONFIG_FILES
        records = POLICY.verify_test_harness(repo, protected, base)
        self.assertEqual(2, len(records))
        expected_mode = 'pre_commit' if repo.head() == base else 'immutable_origin'
        self.assertTrue(all(item['verification_mode'] == expected_mode for item in records))
        self.assertEqual(11, len(POLICY.TEST_HARNESS_FIXTURES))


# Synthetic identifiers below exist only in test models, never in runtime approval/origin records.
import quality_gate_ci_policy as POSTCOMMIT_CI
import quality_gate_module_policy as POSTCOMMIT_MODULE
from contextlib import contextmanager


class PostCommitHistoryModel:
    """Read-only ancestry/state model layered on real immutable Git objects; no checkout or writes."""
    FIX = 'a' * 40
    DESCENDANT = 'b' * 40
    BAD = 'c' * 40
    MERGE = 'd' * 40
    SECOND = 'e' * 40
    _reads = {}

    def __init__(self, real):
        self.real, self.path = real, real.path
        self.parents, self.states, self.overrides = {}, {}, {}
        self.current = POSTCOMMIT_CI._L0_CORRECTION_BASE
        self.data = {name: real.blob(self.current, name) for name in POSTCOMMIT_CI._L0_PATHS}
        helper = real.blob("6b3918755f7ac4945b59e7278c4dbb2da7a2e78a", POSTCOMMIT_CI.HELPER)
        controller = self.data[POSTCOMMIT_CI.POLICY]
        old = POSTCOMMIT_CI._L0_CORRECTION_OLD_HELPER.encode()
        self.original = POSTCOMMIT_CI._l0_state(real, self.current)
        self.corrected_data = dict(self.data)
        self.corrected_data[POSTCOMMIT_CI.HELPER] = helper
        self.corrected_data[POSTCOMMIT_CI.POLICY] = controller.replace(old, POLICY.blob_id(helper).encode(), 1)
        self.corrected = {name: POLICY.blob_id(value) for name, value in self.corrected_data.items()}
        self.ancestor_cache = {}

    def files(self):
        if self.current not in self.states:
            return self.real.tree_files(self.current)
        original = self.real.tree_files(POSTCOMMIT_CI._L0_CORRECTION_BASE)
        changed = self.states[self.current]
        return (original - set(changed)) | {name for name, oid in changed.items() if oid is not None}

    def resolve(self, value):
        return RUNTIME.object_id(value) if value in self.parents else self.real.resolve(value)

    def head(self):
        return self.current

    def ancestors(self, commit):
        if commit in self.parents:
            result = {commit}
            for parent in self.parents[commit]:
                result.update(self.ancestors(parent))
            return result
        if commit not in self.ancestor_cache:
            self.ancestor_cache[commit] = set(self.real._read(['rev-list', commit, '--']).decode().splitlines())
        return self.ancestor_cache[commit]

    def ancestor(self, older, newer):
        return older in self.ancestors(newer)

    def add(self, commit, parents, state):
        self.parents[commit], self.states[commit] = parents, dict(state)
        self.current = commit

    def blob(self, commit, name):
        if (commit, name) in self.overrides:
            return self.overrides[commit, name]
        if commit in self.states:
            if name == POSTCOMMIT_CI.WORKFLOW and self.states[commit][name] == POSTCOMMIT_CI._L0_JAVA_WORKFLOW_BLOB:
                return self.java_workflow()
            if name in self.corrected_data and self.states[commit][name] == self.corrected[name]:
                return self.corrected_data[name]
            if name in self.data and self.states[commit][name] == self.original[name]:
                return self.data[name]
            raise GATE.QualityError('Unknown synthetic source identity')
        return self.real.blob(commit, name)

    def blob_id(self, commit, name):
        return POLICY.blob_id(self.blob(commit, name))

    def java_workflow(self):
        raw = self.data[POSTCOMMIT_CI.WORKFLOW]
        value = raw.replace(b'b843ebf9b913146b38da2eaa7aeddf76074ae1ad',
                            b'77dd5b2ce225ea11469928ac317850245a74ae78').replace(b'v6.1.0', b'v6.2.0')
        if POLICY.blob_id(value) != POSTCOMMIT_CI._L0_JAVA_WORKFLOW_BLOB:
            raise GATE.QualityError('Synthetic Java variant differs from reviewed bytes')
        return value

    def _previous_identity(self, parent, name):
        if parent in self.states:
            return self.states[parent].get(name)
        if name in self.real.tree_files(parent):
            return self.real.blob_id(parent, name)
        return None

    def _changed_edges(self, head, paths):
        changed, real_roots = set(), set()
        for commit in self.ancestors(head):
            if commit in self.states:
                self._inspect_parent_edges(commit, paths, changed, real_roots)
        return changed, real_roots

    def _inspect_parent_edges(self, commit, paths, changed, real_roots):
        for parent in self.parents[commit]:
            if parent not in self.states:
                real_roots.add(parent)
            for name in paths:
                if self.states[commit].get(name) != self._previous_identity(parent, name):
                    changed.add(commit)

    def _read_log(self, args):
        paths = args[args.index('--') + 1:]
        changed, real_roots = self._changed_edges(args[2], paths)
        for root in real_roots:
            changed.update(self.real._read(['log', '--format=%H', root, '--', *paths]).decode().splitlines())
        return ('\n'.join(sorted(changed)) + ('\n' if changed else '')).encode()

    def _read_revisions(self, args):
        if '--parents' in args:
            commit = args[-2]
            if commit in self.parents:
                return (' '.join([commit, *self.parents[commit]]) + '\n').encode()
        if '--ancestry-path' in args:
            revision = next(value for value in args if '..' in value)
            root, head = revision.split('..')
            if head in self.parents:
                values = self.ancestors(head) - self.ancestors(root)
                return ('\n'.join(sorted(c for c in values if self.ancestor(root, c))) + '\n').encode()
        if len(args) == 3 and args[1] in self.parents:
            return ('\n'.join(sorted(self.ancestors(args[1]))) + '\n').encode()
        return None

    def _read_tree(self, args):
        commit = next(value for value in args[1:] if RUNTIME.OID.fullmatch(value))
        if commit not in self.states:
            return None
        return b''.join(('100644 blob ' + oid + '\t' + name + '\0').encode()
                        for name, oid in self.states[commit].items() if oid is not None)

    def _read(self, args):
        if args[0] == 'log' and args[2] in self.parents:
            return self._read_log(args)
        value = None
        if args[0] == 'rev-list':
            value = self._read_revisions(args)
        elif args[0] == 'ls-tree':
            value = self._read_tree(args)
        if value is not None:
            return value
        key = tuple(args)
        if key not in self._reads:
            self._reads[key] = self.real._read(args)
        return self._reads[key]

    @contextmanager
    def snapshot(self, repo, names):
        yield {name: self.data[name] for name in names}


@unittest.skipUnless(os.environ.get('TENSEVEN_POLICY_REPO'), 'Real policy repository not supplied')
class PostCommitControllerTests(unittest.TestCase):
    def setUp(self):
        self.real = RUNTIME.GitRepository(os.environ['TENSEVEN_POLICY_REPO'])
        self.model = PostCommitHistoryModel(self.real)

    def verify(self, base=None):
        repo = self.model
        return POSTCOMMIT_CI.verify_l0_controller(
            repo, base or repo.head(), repo.snapshot, POLICY.blob_id)

    def corrected(self):
        self.model.data = dict(self.model.corrected_data)

    def committed(self):
        self.corrected()
        self.model.add(self.model.FIX, [POSTCOMMIT_CI._L0_CORRECTION_BASE], self.model.corrected)

    def reject(self, base=None):
        self.assertRaises(GATE.QualityError, self.verify, base)

    def test_real_committed_head_report_reader_loads_all_four_modules(self):
        root = self.real.path
        findings, reports = GATE.check_quality_reports(root, 0)
        self.assertEqual({'app', 'design', 'phone', 'calendar'},
                         {item['path'].split('/')[0] for item in reports})
        self.assertTrue(findings)
        self.assertEqual(sorted(POSTCOMMIT_MODULE.MODULES), POSTCOMMIT_MODULE.modules(root))

    def test_real_current_self_comparison_is_an_exact_approved_endpoint(self):
        blob = self.real.blob_id(self.real.head(), POSTCOMMIT_CI.WORKFLOW)
        self.assertIn(blob, {POSTCOMMIT_CI._L0_WORKFLOW_BLOB, POSTCOMMIT_CI._L0_JAVA_WORKFLOW_BLOB})
        result = POSTCOMMIT_CI.verify_l0_controller(
            self.real, self.real.head(), POLICY._source_snapshot, POLICY.blob_id)
        self.assertEqual(POSTCOMMIT_CI._L0_CORRECTION_BASE, result['origin'])

    def test_real_historical_pre_transition_and_legacy_ci_baselines_remain_verified(self):
        for base, expected in [(POSTCOMMIT_CI.APPROVAL_BASE, POSTCOMMIT_CI._WORKFLOW_BEFORE),
                               (POSTCOMMIT_CI._L0_BASE, POSTCOMMIT_CI._WORKFLOW_AFTER)]:
            with self.subTest(base=base):
                self.assertEqual(expected, POLICY._expected_ci_baseline(self.real, base))
                result = POSTCOMMIT_CI.verify_l0_controller(
                    self.real, base, POLICY._source_snapshot, POLICY.blob_id)
                self.assertEqual(POSTCOMMIT_CI._L0_CORRECTION_BASE, result['origin'])

    def test_real_frozen_main_legacy_bootstrap_still_uses_exact_proof(self):
        base = 'b27162819a1611fc0abae8c389deb54cb9352b44'
        result = POSTCOMMIT_CI.verify_l0_controller(
            self.real, base, POLICY._source_snapshot, POLICY.blob_id)
        self.assertEqual(POSTCOMMIT_CI._L0_CORRECTION_BASE, result['origin'])
        self.assertEqual(POSTCOMMIT_CI._WORKFLOW_BEFORE, POLICY._expected_ci_baseline(self.real, base))

    def test_original_precommit_l0_mode_does_not_require_a_correction_origin(self):
        self.model.current = POSTCOMMIT_CI._L0_BASE
        result = self.verify()
        self.assertIsNone(result['origin'])
        self.assertIsNone(result['correction_origin'])

    def test_corrected_precommit_preserves_the_real_original_l0_origin(self):
        self.corrected()
        result = self.verify()
        self.assertEqual(POSTCOMMIT_CI._L0_CORRECTION_BASE, result['origin'])
        self.assertIsNone(result['correction_origin'])
        self.assertEqual(POSTCOMMIT_CI._L0_CORRECTION_BASE, result['correction_parent'])
        self.assertEqual('pre_commit', result['correction_verification_mode'])

    def test_exact_single_parent_followup_and_unrelated_descendant_are_valid(self):
        self.committed()
        self.model.add(self.model.DESCENDANT, [self.model.FIX], self.model.corrected)
        result = self.verify()
        self.assertEqual(self.model.FIX, result['correction_origin'])
        self.assertEqual(POSTCOMMIT_CI._L0_CORRECTION_BASE, result['origin'])
        self.assertEqual('immutable_origin', result['correction_verification_mode'])

    def test_pr133_variant_uses_real_origin_and_blob_proofs_on_second_parent_merge(self):
        self.committed()
        variant = dict(self.model.corrected, **{POSTCOMMIT_CI.WORKFLOW: POSTCOMMIT_CI._L0_JAVA_WORKFLOW_BLOB})
        self.model.add(self.model.MERGE, ['2fa46c0ed9b5cfea43fd7b52322e592caab170a7', self.model.FIX], variant)
        self.model.data[POSTCOMMIT_CI.WORKFLOW] = self.model.java_workflow()
        result = self.verify('2fa46c0ed9b5cfea43fd7b52322e592caab170a7')
        self.assertEqual(self.model.FIX, result['correction_origin'])

    def test_unrelated_synthetic_merge_preserves_an_original_workflow(self):
        self.committed()
        self.model.add(self.model.SECOND, [POSTCOMMIT_CI._L0_CORRECTION_BASE], self.model.original)
        self.model.add(self.model.MERGE, [self.model.SECOND, self.model.FIX], self.model.corrected)
        self.assertEqual(self.model.FIX, self.verify()['correction_origin'])

    def test_unknown_workflow_hash_is_rejected(self):
        self.model.data[POSTCOMMIT_CI.WORKFLOW] += b'\n# unknown workflow\n'
        self.reject()

    def test_duplicate_helper_glob_is_rejected(self):
        name = POSTCOMMIT_CI.WORKFLOW
        self.model.data[name] = self.model.data[name].replace(b"      - 'tools/quality_gate_*.py'\n",
                                                           b"      - 'tools/quality_gate_*.py'\n" * 2)
        self.reject()

    def test_missing_helper_glob_is_rejected(self):
        name = POSTCOMMIT_CI.WORKFLOW
        self.model.data[name] = self.model.data[name].replace(b"      - 'tools/quality_gate_*.py'\n", b'')
        self.reject()

    def test_modified_helper_glob_is_rejected(self):
        name = POSTCOMMIT_CI.WORKFLOW
        self.model.data[name] = self.model.data[name].replace(b'tools/quality_gate_*.py', b'tools/*.py')
        self.reject()

    def test_reordered_unauthorized_paths_are_rejected(self):
        name = POSTCOMMIT_CI.WORKFLOW
        self.model.data[name] = self.model.data[name].replace(POLICY.CI_PATHS.encode(),
            ("      - 'tools/quality_gate_*.py'\n" + POLICY.OLD_CI_PATHS).encode())
        self.reject()

    def test_mutated_mandatory_step_is_rejected(self):
        name = POSTCOMMIT_CI.WORKFLOW
        self.model.data[name] = self.model.data[name].replace(b'Enforce changed-file quality', b'Skip changed-file quality')
        self.reject()

    def test_controller_only_change_is_rejected(self):
        self.model.data[POSTCOMMIT_CI.POLICY] += b'\n# unknown controller\n'
        self.reject()

    def test_helper_only_change_is_rejected(self):
        self.corrected()
        self.model.data[POSTCOMMIT_CI.HELPER] += b'\n# unknown helper\n'
        self.reject()

    def test_modified_helper_and_matching_unreviewed_pin_are_rejected(self):
        self.corrected()
        self.model.data[POSTCOMMIT_CI.HELPER] += b'\n# unknown helper\n'
        old = POLICY.CI_COMPLETENESS_APPROVAL['helper_after'].encode()
        new = POLICY.blob_id(self.model.data[POSTCOMMIT_CI.HELPER]).encode()
        self.model.data[POSTCOMMIT_CI.POLICY] = self.model.data[POSTCOMMIT_CI.POLICY].replace(old, new)
        self.reject()

    def test_partial_controller_pin_transition_is_rejected(self):
        self.model.data[POSTCOMMIT_CI.POLICY] = self.model.corrected_data[POSTCOMMIT_CI.POLICY]
        self.reject()

    def test_partial_helper_transition_is_rejected(self):
        self.model.data[POSTCOMMIT_CI.HELPER] = self.model.corrected_data[POSTCOMMIT_CI.HELPER]
        self.reject()

    def test_module_validator_change_is_rejected(self):
        self.corrected()
        self.model.data['tools/quality_gate_module_policy.py'] += b'\n# unapproved module validator\n'
        self.reject()

    def test_report_gate_change_is_rejected(self):
        self.corrected()
        self.model.data['tools/deliverable_quality_gate.py'] += b'\n# unapproved report reader\n'
        self.reject()

    def test_corrected_source_before_the_frozen_l0_origin_is_rejected(self):
        self.corrected()
        self.model.current = POSTCOMMIT_CI._L0_BASE
        self.reject()

    def test_followup_with_wrong_parent_is_rejected(self):
        self.corrected()
        self.model.add(self.model.BAD, [POSTCOMMIT_CI._L0_CORRECTION_BASE], self.model.original)
        self.model.add(self.model.FIX, [self.model.BAD], self.model.corrected)
        self.reject()

    def test_duplicate_followup_origins_are_rejected(self):
        self.committed()
        self.model.add(self.model.SECOND, [POSTCOMMIT_CI._L0_CORRECTION_BASE], self.model.corrected)
        self.model.add(self.model.MERGE, [self.model.FIX, self.model.SECOND], self.model.corrected)
        self.reject()

    def test_merge_cannot_be_the_followup_origin(self):
        self.corrected()
        self.model.add(self.model.FIX, [POSTCOMMIT_CI._L0_CORRECTION_BASE, POSTCOMMIT_CI._L0_BASE], self.model.corrected)
        self.reject()

    def test_modified_then_restored_followup_history_is_rejected(self):
        self.committed()
        bad = dict(self.model.corrected, **{POSTCOMMIT_CI.POLICY: '0' * 40})
        self.model.add(self.model.BAD, [self.model.FIX], bad)
        self.model.add(self.model.DESCENDANT, [self.model.BAD], self.model.corrected)
        self.reject()

    def test_missing_helper_in_history_is_rejected(self):
        self.committed()
        bad = dict(self.model.corrected, **{POSTCOMMIT_CI.HELPER: None})
        self.model.add(self.model.BAD, [self.model.FIX], bad)
        self.model.add(self.model.DESCENDANT, [self.model.BAD], self.model.corrected)
        self.reject()

    def test_restoring_old_current_files_cannot_erase_activation(self):
        self.committed()
        self.model.add(self.model.DESCENDANT, [self.model.FIX], self.model.original)
        self.model.data = {name: self.real.blob(POSTCOMMIT_CI._L0_CORRECTION_BASE, name)
                           for name in POSTCOMMIT_CI._L0_PATHS}
        self.reject()

    def test_pr133_variant_without_its_real_ancestry_is_rejected(self):
        self.corrected()
        self.model.data[POSTCOMMIT_CI.WORKFLOW] = self.model.java_workflow()
        self.reject()

    def test_unapproved_main_workflow_change_is_rejected(self):
        self.committed()
        variant = dict(self.model.corrected, **{POSTCOMMIT_CI.WORKFLOW: POSTCOMMIT_CI._L0_JAVA_WORKFLOW_BLOB})
        self.model.add(self.model.MERGE, ['2fa46c0ed9b5cfea43fd7b52322e592caab170a7', self.model.FIX], variant)
        self.model.data[POSTCOMMIT_CI.WORKFLOW] = self.model.java_workflow() + b'\n# unknown main change\n'
        self.reject()

    def test_mainline_origin_parent_proof_cannot_be_forged(self):
        self.committed()
        self.model.parents[POSTCOMMIT_MODULE.MAINLINE_ORIGIN] = [POSTCOMMIT_CI._L0_BASE]
        self.model.add(self.model.MERGE, [POSTCOMMIT_MODULE.MAINLINE_ORIGIN, self.model.FIX], self.model.corrected)
        self.reject()

    def test_invalid_comparison_identity_is_rejected(self):
        self.reject('0' * 40)

    def test_unrelated_valid_comparison_identity_is_rejected(self):
        self.reject('2fa46c0ed9b5cfea43fd7b52322e592caab170a7')

    def test_invalid_source_identity_is_rejected(self):
        self.model.current = '0' * 40
        self.reject()

    def test_unknown_baseline_bytes_cannot_borrow_a_normalization_allowance(self):
        base = 'b27162819a1611fc0abae8c389deb54cb9352b44'
        self.model.overrides[base, POSTCOMMIT_CI.WORKFLOW] = self.real.blob(base, POSTCOMMIT_CI.WORKFLOW) + b'\n# changed\n'
        self.reject(base)

    def test_known_current_endpoint_never_enters_legacy_normalization(self):
        with patch.object(POLICY, '_expected_ci_baseline', side_effect=AssertionError('Already approved endpoint')):
            self.assertEqual(POSTCOMMIT_CI._L0_CORRECTION_BASE, self.verify()['origin'])

    def test_partial_baseline_cannot_normalize_into_the_approved_l0_endpoint(self):
        base = POSTCOMMIT_CI._L0_CORRECTION_BASE
        self.model.overrides[base, POSTCOMMIT_CI.WORKFLOW] = self.model.data[POSTCOMMIT_CI.WORKFLOW].replace(
            b"      - 'tools/quality_gate_*.py'\n", b'')
        self.assertEqual(POSTCOMMIT_CI._L0_WORKFLOW_BLOB, POLICY._expected_ci_baseline(self.model, base))
        self.reject(base)

    def test_partial_legacy_baseline_cannot_borrow_the_approved_ci_endpoint(self):
        base = POSTCOMMIT_CI._L0_BASE
        self.model.overrides[base, POSTCOMMIT_CI.WORKFLOW] = self.real.blob(base, POSTCOMMIT_CI.WORKFLOW).replace(
            b"      - 'tools/quality_gate_*.py'\n", b'')
        self.assertEqual(POSTCOMMIT_CI._WORKFLOW_AFTER, POLICY._expected_ci_baseline(self.model, base))
        self.reject(base)

    def test_baseline_only_path_and_step_mutations_are_rejected(self):
        base = POSTCOMMIT_CI._L0_CORRECTION_BASE
        original = self.model.data[POSTCOMMIT_CI.WORKFLOW]
        mutations = [original.replace(b'tools/quality_gate_*.py', b'tools/*.py'),
                     original.replace(POLICY.CI_PATHS.encode(),
                         ("      - 'tools/quality_gate_*.py'\n" + POLICY.OLD_CI_PATHS).encode()),
                     original.replace(b'Enforce changed-file quality', b'Skip changed-file quality'),
                     original.replace(b"      - 'tools/quality_gate_*.py'\n",
                                      b"      - 'tools/quality_gate_*.py'\n" * 2)]
        for value in mutations:
            with self.subTest(blob=POLICY.blob_id(value)):
                self.model.overrides[base, POSTCOMMIT_CI.WORKFLOW] = value
                self.reject(base)

    def test_unknown_raw_baseline_is_rejected_even_if_a_normalizer_claims_approval(self):
        base = POSTCOMMIT_CI._L0_CORRECTION_BASE
        self.model.overrides[base, POSTCOMMIT_CI.WORKFLOW] = b'unknown workflow fixture\n'
        with patch.object(POLICY, '_expected_ci_baseline', return_value=POSTCOMMIT_CI._L0_WORKFLOW_BLOB):
            self.reject(base)

    def test_a_derived_main_bootstrap_is_not_an_authorized_raw_workflow(self):
        self.committed()
        base = '2fa46c0ed9b5cfea43fd7b52322e592caab170a7'
        variant = dict(self.model.corrected, **{POSTCOMMIT_CI.WORKFLOW: POSTCOMMIT_CI._L0_JAVA_WORKFLOW_BLOB})
        self.model.add(self.model.MERGE, [base, self.model.FIX], variant)
        self.model.data[POSTCOMMIT_CI.WORKFLOW] = self.model.java_workflow()
        bootstrap = POLICY.ci_bootstrap(self.real.blob(base, POSTCOMMIT_CI.WORKFLOW).decode()).encode()
        self.assertEqual(POSTCOMMIT_CI._L0_MAIN_BOOTSTRAP, POLICY.blob_id(bootstrap))
        self.model.overrides[base, POSTCOMMIT_CI.WORKFLOW] = bootstrap
        self.reject(base)

    def test_current_head_race_is_rejected(self):
        calls = iter([POSTCOMMIT_CI._L0_CORRECTION_BASE, self.model.BAD])
        with patch.object(self.model, 'head', side_effect=lambda: next(calls, self.model.BAD)):
            self.reject(POSTCOMMIT_CI._L0_CORRECTION_BASE)



# PR144 exact Robolectric transition regressions.
"""Execute draft source against immutable real Git plus in-memory future nodes.

Synthetic OIDs below exist only inside this test process. Nothing creates a Git
object, commit, index, checkout or worktree. They are never approval records.
"""
from contextlib import contextmanager
import copy
import hashlib
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

import os
ROOT = Path(os.environ.get('TENSEVEN_POLICY_REPO', str(Path(__file__).resolve().parents[2])))
CANDIDATE_SOURCE_ROOT = ROOT / os.environ.get('PR144_POLICY_PROPOSAL', '')
import quality_gate_policy as legacy
import quality_gate_module_policy as modules
import quality_gate_ci_policy as ci
import quality_gate_robolectric_policy as candidate
from quality_gate_runtime import GitRepository, QualityError

REAL = GitRepository(ROOT)
READS = {}


class CandidateRepository:
    def __init__(self):
        self.path = ROOT
        self.current_head = candidate.BASE
        self.parents = {}
        self.states = {}
        self.real_ancestors = set(REAL._read(['rev-list', candidate.BASE, '--']).decode().splitlines())
        self.before = {name: REAL.blob(candidate.BASE, name) for name in candidate.BEFORE}
        self.proposed = dict(self.before)
        for name in (candidate.NEW, candidate.CATALOG, candidate.MODULE, ci.HELPER, candidate.TESTS):
            self.proposed[name] = REAL.blob(candidate.DEPENDENCY_ORIGIN, name)
        self.current = dict(self.proposed)

    def head(self):
        return self.current_head

    def resolve(self, value):
        return value if value in self.parents else REAL.resolve(value)

    def files(self):
        return (set(REAL.files()) - set(candidate.BEFORE) - {candidate.NEW}) | set(self.current)

    def ancestors(self, commit):
        if commit in self.parents:
            result = {commit}
            for parent in self.parents[commit]:
                result.update(self.ancestors(parent))
            return result
        if commit == candidate.BASE:
            return self.real_ancestors
        return set(REAL._read(['rev-list', commit, '--']).decode().splitlines())

    def ancestor(self, older, newer):
        return older in self.ancestors(newer)

    def add(self, label, data=None, parents=None):
        identifier = hashlib.sha1(('TEST ONLY:' + label).encode(), usedforsecurity=False).hexdigest()
        self.parents[identifier] = parents if parents is not None else [self.head()]
        self.states[identifier] = dict(self.proposed if data is None else data)
        self.current_head = identifier
        return identifier

    def blob(self, commit, name):
        if commit in self.states:
            return self.states[commit][name]
        return self._read(['show', commit + ':' + name])

    def blob_id(self, commit, name):
        try:
            return legacy.blob_id(self.blob(commit, name))
        except KeyError:
            return None

    def _previous_source(self, parent, name):
        if parent in self.states:
            return self.states[parent].get(name)
        if name in REAL.tree_files(parent):
            return REAL.blob(parent, name)
        return None

    def _changed_log_commits(self, head, paths):
        changed = set()
        for commit in self.ancestors(head):
            if commit in self.states and self._has_changed_edge(commit, paths):
                changed.add(commit)
        return sorted(changed)

    def _has_changed_edge(self, commit, paths):
        comparisons = [self.states[commit].get(name) != self._previous_source(parent, name)
                       for parent in self.parents[commit] for name in paths]
        return any(comparisons)

    def _read_log(self, args):
        paths = args[args.index('--') + 1:]
        changed = self._changed_log_commits(args[2], paths)
        return ('\n'.join(changed) + ('\n' if changed else '')).encode()

    def _read_revisions(self, args):
        if '--parents' in args and args[-2] in self.parents:
            commit = args[-2]
            return (' '.join([commit, *self.parents[commit]]) + '\n').encode()
        revisions = [value for value in args if '..' in value]
        if revisions and revisions[0].split('..')[1] in self.parents:
            root, head = revisions[0].split('..')
            values = self.ancestors(head) - self.ancestors(root)
            return ('\n'.join(c for c in self.parents if c in values) + '\n').encode()
        if len(args) == 3 and args[1] in self.parents:
            return ('\n'.join(sorted(self.ancestors(args[1]))) + '\n').encode()
        return None

    def _read_tree(self, args):
        commit = next(value for value in args if len(value) == 40 and all(c in '0123456789abcdef' for c in value))
        if commit not in self.states:
            return None
        names = args[args.index('--') + 1:]
        return b''.join(('100644 blob ' + legacy.blob_id(raw) + '\t' + name + '\0').encode()
                        for name, raw in sorted(self.states[commit].items()) if name in names)

    def _read(self, args):
        if args[0] == 'log' and args[2] in self.parents:
            return self._read_log(args)
        value = None
        if args[0] == 'ls-tree':
            value = self._read_tree(args)
        elif args[0] == 'rev-list':
            value = self._read_revisions(args)
        if value is not None:
            return value
        key = tuple(args)
        if key not in READS:
            READS[key] = REAL._read(args)
        return READS[key]

    @contextmanager
    def snapshot(self, repo, names):
        if not set(names) <= self.current.keys():
            raise QualityError('Missing simulated source')
        yield {name: self.current[name] for name in names}


class JointProposalTests(unittest.TestCase):
    def setUp(self):
        self.repo = CandidateRepository()

    def verify(self, base=None):
        with patch.object(legacy, '_source_snapshot', self.repo.snapshot):
            return modules.verify(self.repo, base or self.repo.head())

    def reject(self):
        self.assertRaises(QualityError, self.verify)

    def test_real_frozen_history_precommit_and_all_original_origins(self):
        result = self.verify()
        self.assertIsNone(result['dependency_origin'])
        self.assertEqual('pre_commit', result['dependency_verification_mode'])
        self.assertEqual('925b08da25802d8acb5b9e770d42297b20f64a8f', result['origin'])
        self.assertEqual(candidate.BASE, result['dependency_parent'])

    def test_actual_controller_entry_uses_same_precommit_verification(self):
        with patch.object(legacy, '_source_snapshot', self.repo.snapshot):
            result = ci.verify_l0_controller(self.repo, self.repo.head(), legacy._source_snapshot, legacy.blob_id)
        self.assertIsNone(result['dependency_origin'])
        self.assertEqual(candidate.BASE, result['dependency_parent'])

    def test_harness_records_report_new_catalog_without_rewriting_old_approval(self):
        with patch.object(legacy, '_source_snapshot', self.repo.snapshot):
            records = modules.harness_records(self.repo, candidate.BASE)
        catalog = next(r for r in records if r['path'] == candidate.CATALOG)
        self.assertEqual('2d634ead94e415f54f013df883b4c4077f2ecd70', catalog['after'])
        self.assertEqual('cef0f4dde1afc5d33114b5701ed13752f425a783', catalog['original_after'])

    def test_joint_origin_is_discovered_from_future_history_model(self):
        origin = self.repo.add('joint-origin')
        self.assertEqual(origin, self.verify()['dependency_origin'])

    def test_unchanged_descendant_preserves_origin(self):
        origin = self.repo.add('joint-origin')
        self.repo.add('unchanged-descendant')
        self.assertEqual(origin, self.verify()['dependency_origin'])

    def test_main_relative_comparison_keeps_original_ancestry(self):
        self.repo.add('joint-origin')
        self.assertEqual('immutable_origin', self.verify('2fa46c0ed9b5cfea43fd7b52322e592caab170a7')['dependency_verification_mode'])

    def test_catalog_only_edit_is_rejected(self):
        self.repo.current = dict(self.repo.before, **{candidate.NEW: self.repo.proposed[candidate.NEW], candidate.CATALOG: self.repo.proposed[candidate.CATALOG]})
        self.reject()

    def test_controller_only_edit_is_rejected(self):
        self.repo.current[candidate.CATALOG] = self.repo.before[candidate.CATALOG]
        self.reject()

    def test_missing_helper_is_rejected(self):
        self.repo.current.pop(candidate.NEW)
        self.reject()

    def test_helper_one_byte_change_is_rejected(self):
        self.repo.current[candidate.NEW] += b'\n'
        self.reject()

    def test_controller_one_byte_change_is_rejected(self):
        self.repo.current[ci.HELPER] += b'\n'
        self.reject()

    def test_module_one_byte_change_is_rejected(self):
        self.repo.current[candidate.MODULE] += b'\n'
        self.reject()

    def test_frozen_assertion_mutation_is_rejected(self):
        self.repo.current[next(iter(modules.FIXTURES))] += b'\n'
        self.reject()

    def test_split_commits_fail(self):
        partial = dict(self.repo.before, **{candidate.CATALOG: self.repo.proposed[candidate.CATALOG]})
        self.repo.add('partial', partial)
        self.repo.add('completion')
        self.reject()

    def test_late_origin_fails(self):
        self.repo.add('unrelated', self.repo.before)
        self.repo.add('late-transition')
        self.reject()

    def test_late_precommit_fails(self):
        self.repo.add('unrelated', self.repo.before)
        self.reject()

    def test_two_parent_origin_fails(self):
        self.repo.add('merge-origin', parents=[candidate.BASE, REAL.parent(candidate.BASE)])
        self.reject()

    def test_duplicate_origins_fail(self):
        first = self.repo.add('first-origin')
        second = self.repo.add('second-origin', parents=[candidate.BASE])
        self.repo.add('merge', parents=[first, second])
        self.reject()

    def test_restored_catalog_history_fails(self):
        self.repo.add('joint-origin')
        mutated = dict(self.repo.proposed, **{candidate.CATALOG: self.repo.proposed[candidate.CATALOG] + b'\n'})
        self.repo.add('mutated', mutated)
        self.repo.add('restored')
        self.reject()

    def test_restored_fixture_history_fails(self):
        self.repo.add('joint-origin')
        mutated = dict(self.repo.proposed)
        mutated[next(iter(modules.FIXTURES))] += b'\n'
        self.repo.add('mutated', mutated)
        self.repo.add('restored')
        self.reject()

    def test_revert_cannot_erase_activation(self):
        self.repo.add('joint-origin')
        self.repo.add('revert', self.repo.before)
        self.repo.current = dict(self.repo.before)
        self.reject()

    def test_all_original_policy_anchors_are_preserved(self):
        changed = {name for name, raw in self.repo.proposed.items() if self.repo.before.get(name) != raw}
        self.assertEqual({candidate.NEW, candidate.CATALOG, candidate.MODULE, ci.HELPER, candidate.TESTS}, changed)
        self.assertEqual(11, len(modules.FIXTURES))
        self.assertEqual(2, len(modules.COPIES))

    def test_test_model_adjustment_preserves_all_test_definitions(self):
        import ast
        def methods(raw):
            return [ast.dump(n) for n in ast.walk(ast.parse(raw))
                    if isinstance(n, ast.FunctionDef) and n.name.startswith('test_')]
        original_methods = methods(self.repo.before[candidate.TESTS])
        self.assertEqual(original_methods, [item for item in methods(self.repo.proposed[candidate.TESTS]) if item in original_methods])

    def test_catalog_record_has_separate_dependency_provenance(self):
        origin = self.repo.add('joint-origin')
        with patch.object(legacy, '_source_snapshot', self.repo.snapshot):
            records = modules.harness_records(self.repo, candidate.BASE)
        catalog = next(r for r in records if r['path'] == candidate.CATALOG)
        self.assertEqual(origin, catalog['dependency_origin'])
        self.assertEqual(candidate.BASE, catalog['dependency_parent'])
        self.assertEqual('immutable_origin', catalog['dependency_verification_mode'])
        self.assertIn('Robolectric 4.18.1', catalog['approval'])
        self.assertIn('no analyzer overlay', catalog['analysis_effect'])

    def test_analysis_records_match_both_actual_catalog_endpoints(self):
        class Parent:
            path = ROOT

            def head(self):
                return candidate.BASE

            def blob_id(self, commit, name):
                return REAL.blob_id(commit, name)

        parent = Parent()
        def read(repository, name):
            return self.repo.current[name] if repository is self.repo else self.repo.before[name]
        with patch.object(legacy, '_source_snapshot', self.repo.snapshot), patch.object(legacy, 'source_bytes', read):
            records = modules.analysis(self.repo, parent)
        catalog = records[candidate.CATALOG]
        self.assertEqual('e3daaf8f266a03f85e77de9861f628299b88f148604f8c9773813c5303af069a', catalog['parent_sha256'])
        self.assertEqual('c7d73b4526858fc3fe0dd4038706173ee6b12abea5c27c65fa8342fae18e8ba9', catalog['candidate_sha256'])
        self.assertIsNone(catalog['dependency_origin'])
        self.assertEqual('pre_commit', catalog['dependency_verification_mode'])

    def test_every_unchanged_anchor_remains_individually_pinned(self):
        changed = {candidate.NEW, candidate.CATALOG, candidate.MODULE, ci.HELPER, candidate.TESTS}
        for name in self.repo.current.keys() - changed:
            with self.subTest(path=name):
                original = self.repo.current[name]
                self.repo.current[name] = original + b'\n'
                self.reject()
                self.repo.current[name] = original

    def test_wrong_catalog_version_cannot_match_reviewed_pin(self):
        self.repo.current[candidate.CATALOG] = self.repo.current[candidate.CATALOG].replace(b'4.18.1', b'4.18.2')
        self.reject()

    def test_current_head_drift_is_rejected(self):
        with patch.object(self.repo, 'head', side_effect=[candidate.BASE, 'f' * 40]):
            self.reject()




class ActivationAndMergeReviewTests(unittest.TestCase):
    setUp = JointProposalTests.setUp
    verify = JointProposalTests.verify
    reject = JointProposalTests.reject
    def physical_source(self, repository, installed):
        import tempfile
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        repository.path = Path(directory.name)
        for module in modules.MODULES:
            p = repository.path / module / 'build.gradle.kts'
            p.parent.mkdir(parents=True)
            p.write_text('// synthetic filesystem fixture only\n')
        if installed:
            p = repository.path / candidate.NEW
            p.parent.mkdir(parents=True)
            p.write_bytes(self.repo.proposed[candidate.NEW])
        return repository

    def main_branch(self, label='main-unrelated', mutate=None):
        main = '2fa46c0ed9b5cfea43fd7b52322e592caab170a7'
        source = {name: REAL.blob(main, name) for name in candidate.BEFORE if name in REAL.tree_files(main)}
        if mutate is not None:
            mutate(source)
        return self.repo.add(label, source, parents=[main])

    def test_historical_inventory_ignores_both_physical_helper_conditions(self):
        for installed in [False, True]:
            with self.subTest(installed=installed):
                historical = self.physical_source(PostCommitHistoryModel(REAL), installed)
                self.assertNotIn(candidate.NEW, historical.files())
                self.assertFalse(candidate.active(historical))
                self.assertEqual(historical.head(), historical.resolve(historical.head()))

    def test_actual_legacy_controller_verifies_with_physical_installed_helper(self):
        for installed in [False, True]:
            with self.subTest(installed=installed):
                historical = self.physical_source(PostCommitHistoryModel(REAL), installed)
                result = ci.verify_l0_controller(historical, historical.head(), historical.snapshot, legacy.blob_id)
                self.assertEqual('925b08da25802d8acb5b9e770d42297b20f64a8f', result['origin'])
                self.assertIsNone(result['correction_verification_mode'])

    def test_proposed_precommit_verifies_with_or_without_physical_helper(self):
        for installed in [False, True]:
            with self.subTest(installed=installed):
                self.physical_source(self.repo, installed)
                self.assertTrue(candidate.active(self.repo))
                self.assertIsNone(self.verify()['dependency_origin'])

    def test_deleted_helper_history_still_activates_and_rejects_restoration(self):
        self.repo.add('dependency-origin')
        self.repo.add('unreviewed-revert', self.repo.before)
        self.repo.current = dict(self.repo.before)
        self.assertNotIn(candidate.NEW, self.repo.files())
        self.assertTrue(candidate.active(self.repo))
        self.reject()

    def test_new_main_side_and_second_parent_source_are_compatible(self):
        origin = self.repo.add('dependency-origin')
        side = self.main_branch()
        self.repo.add('synthetic-ci-merge', parents=[side, origin])
        self.assertEqual(origin, self.verify(side)['dependency_origin'])

    def test_first_parent_source_merge_is_compatible(self):
        origin = self.repo.add('dependency-origin')
        side = self.main_branch()
        self.repo.add('integration-merge', parents=[origin, side])
        self.assertEqual(origin, self.verify()['dependency_origin'])

    def test_unrelated_pre_origin_sibling_preserves_unique_origin(self):
        origin = self.repo.add('dependency-origin')
        sibling = self.repo.add('ordinary-sibling', self.repo.before, parents=[candidate.BASE])
        self.repo.add('sibling-merge', parents=[origin, sibling])
        self.assertEqual(origin, self.verify()['dependency_origin'])

    def test_side_catalog_modified_then_restored_is_rejected(self):
        origin = self.repo.add('dependency-origin')
        def mutate(source):
            source[candidate.CATALOG] += b'\n'
        changed = self.main_branch('changed-side', mutate)
        main = '2fa46c0ed9b5cfea43fd7b52322e592caab170a7'
        restored = {name: REAL.blob(main, name) for name in candidate.BEFORE if name in REAL.tree_files(main)}
        side = self.repo.add('restored-side', restored, parents=[changed])
        self.repo.add('restored-merge', parents=[origin, side])
        self.reject()

    def test_side_workflow_mutation_is_not_authorized_by_final_merge(self):
        origin = self.repo.add('dependency-origin')
        def mutate(source):
            source[ci.WORKFLOW] += b'# unauthorized step\n'
        side = self.main_branch('workflow-side', mutate)
        self.repo.add('workflow-merge', parents=[origin, side])
        self.reject()

    def test_unrelated_git_root_cannot_borrow_an_origin(self):
        origin = self.repo.add('dependency-origin')
        side = self.repo.add('unrelated-git-root', {}, parents=[])
        self.repo.add('foreign-root-merge', parents=[origin, side])
        self.reject()

    def test_side_partial_dependency_cannot_borrow_a_joint_approval(self):
        origin = self.repo.add('dependency-origin')
        def mutate(source):
            source[candidate.CATALOG] = self.repo.proposed[candidate.CATALOG]
        side = self.main_branch('catalog-only-side', mutate)
        self.repo.add('partial-merge', parents=[origin, side])
        self.reject()

    def test_side_fixture_mutation_is_not_authorized_by_final_merge(self):
        origin = self.repo.add('dependency-origin')
        def mutate(source):
            source[next(iter(modules.FIXTURES))] = b'fun removedAssertions() = Unit\n'
        side = self.main_branch('fixture-side', mutate)
        self.repo.add('fixture-merge', parents=[origin, side])
        self.reject()


_R3_CONTROLLER_BLOB = 'c69855b82795e70f58bda1a8c1fbfe304cda4c08'


class MaintainabilityRepository(CandidateRepository):
    """Future review nodes over real R2 history; never creates a Git object."""

    def __init__(self):
        super().__init__()
        self.dependency_source = dict(self.proposed)
        proposal = Path(candidate.__file__).resolve().parents[1]
        # R3 reviewed state, read by its frozen blob IDs; the working-tree files are the R4 state.
        for name, blob in ((candidate.NEW, candidate.R3_HELPER_BLOB), (ci.HELPER, _R3_CONTROLLER_BLOB),
                           (candidate.TESTS, candidate.REVIEW_TESTS_BLOB)):
            self.proposed[name] = subprocess.check_output(['git', 'cat-file', 'blob', blob], cwd=proposal)
        self.current = dict(self.proposed)
        self.current_head = candidate.REVIEW_BASE
        self.r4 = dict(self.proposed)
        for name in (candidate.NEW, ci.HELPER, candidate.TESTS, candidate.MODULE):
            self.r4[name] = (proposal / name).read_bytes()

    def _read_revisions(self, args):
        revisions = [value for value in args if '..' in value]
        if revisions and revisions[0].split('..')[1] in self.parents:
            root, head = revisions[0].split('..')
            values = self.ancestors(head) - self.ancestors(root)
            real = sorted(values - self.parents.keys())
            modeled = [commit for commit in self.parents if commit in values]
            return ('\n'.join([*real, *modeled]) + '\n').encode()
        return super()._read_revisions(args)


class MaintainabilityTransitionTests(unittest.TestCase):
    def setUp(self):
        self.repo = MaintainabilityRepository()

    def verify(self, base=None):
        with patch.object(legacy, '_source_snapshot', self.repo.snapshot):
            return modules.verify(self.repo, base or self.repo.head())

    def reject(self):
        self.assertRaises(QualityError, self.verify)

    def commit_review(self):
        return self.repo.add('review-origin')

    def mutate(self, name):
        source = dict(self.repo.proposed)
        source[name] += b'\n'
        return source

    def test_exact_precommit_retains_actual_dependency_origin(self):
        record = self.verify()
        self.assertEqual(candidate.DEPENDENCY_ORIGIN, record['dependency_origin'])
        self.assertIsNone(record['maintainability_origin'])
        self.assertEqual(candidate.REVIEW_BASE, record['maintainability_parent'])

    def test_original_dependency_state_is_still_verified(self):
        self.repo.current_head = candidate.DEPENDENCY_ORIGIN
        self.repo.current = dict(self.repo.dependency_source)
        record = self.verify()
        self.assertEqual(candidate.DEPENDENCY_ORIGIN, record['dependency_origin'])
        self.assertIsNone(record['maintainability_origin'])

    def test_real_tls_state_remains_valid_before_activation(self):
        self.repo.current = dict(self.repo.dependency_source)
        self.assertIsNone(self.verify()['maintainability_origin'])

    def test_exact_joint_origin_is_discovered(self):
        origin = self.commit_review()
        record = self.verify()
        self.assertEqual(origin, record['maintainability_origin'])
        self.assertEqual(candidate.DEPENDENCY_ORIGIN, record['dependency_origin'])

    def test_unrelated_descendant_preserves_both_origins(self):
        origin = self.commit_review()
        self.repo.add('ordinary-descendant')
        self.assertEqual(origin, self.verify()['maintainability_origin'])

    def test_second_parent_ci_merge_preserves_origin(self):
        origin = self.commit_review()
        main = '2fa46c0ed9b5cfea43fd7b52322e592caab170a7'
        self.repo.add('ci-merge', parents=[main, origin])
        self.assertEqual(origin, self.verify(main)['maintainability_origin'])

    def test_first_parent_integration_merge_preserves_origin(self):
        origin = self.commit_review()
        main = '2fa46c0ed9b5cfea43fd7b52322e592caab170a7'
        self.repo.add('integration-merge', parents=[origin, main])
        self.assertEqual(origin, self.verify(main)['maintainability_origin'])

    def commit_r4(self, data=None, parents=None):
        return self.repo.add('r4-origin', self.repo.r4 if data is None else data, parents=parents)

    def test_r4_origin_directly_after_r3_is_accepted(self):
        review = self.commit_review()
        self.repo.current = dict(self.repo.r4)
        origin = self.commit_r4()
        self.repo.add('ordinary-descendant', self.repo.r4)
        record = self.verify()
        self.assertEqual(review, record['maintainability_origin'])
        self.assertNotEqual(origin, review)

    def test_r4_precommit_requires_the_r3_state(self):
        self.commit_review()
        self.repo.current = dict(self.repo.r4)
        self.assertEqual(candidate.REVIEW_BASE, self.verify()['maintainability_parent'])

    def test_r4_precommit_without_r3_is_rejected(self):
        self.repo.current = dict(self.repo.r4)
        self.reject()

    def test_partial_r4_state_is_rejected(self):
        self.commit_review()
        for name in (candidate.NEW, ci.HELPER, candidate.TESTS, candidate.MODULE):
            with self.subTest(name=name):
                partial = dict(self.repo.proposed)
                partial[name] = self.repo.r4[name]
                self.repo.current = dict(partial)
                head = self.repo.head()
                self.repo.add('partial-r4', partial)
                self.reject()
                self.repo.current_head = head

    def test_r3_state_cannot_be_restored_after_r4(self):
        self.commit_review()
        self.repo.current = dict(self.repo.r4)
        self.commit_r4()
        self.repo.add('restore-r3', self.repo.proposed)
        self.repo.current = dict(self.repo.proposed)
        self.reject()

    def test_two_r4_origins_are_rejected(self):
        review = self.commit_review()
        self.repo.current = dict(self.repo.r4)
        first = self.commit_r4()
        second = self.repo.add('second-r4-origin', self.repo.r4, parents=[review])
        self.repo.add('r4-merge', self.repo.r4, parents=[first, second])
        self.reject()

    def test_wrong_direct_parent_is_rejected(self):
        self.repo.add('wrong-parent', parents=[candidate.DEPENDENCY_ORIGIN])
        self.reject()

    def test_two_parent_origin_is_rejected(self):
        self.repo.add('merge-origin', parents=[candidate.REVIEW_BASE, candidate.DEPENDENCY_ORIGIN])
        self.reject()

    def test_late_origin_is_rejected(self):
        self.repo.add('unrelated-first', self.repo.dependency_source)
        self.commit_review()
        self.reject()

    def test_late_precommit_is_rejected(self):
        self.repo.add('unrelated-first', self.repo.dependency_source)
        self.reject()

    def test_duplicate_origins_are_rejected(self):
        first = self.commit_review()
        second = self.repo.add('second-origin', parents=[candidate.REVIEW_BASE])
        self.repo.add('merged-origins', parents=[first, second])
        self.reject()

    def test_controller_only_transition_is_rejected(self):
        self.repo.current = dict(self.repo.dependency_source)
        self.repo.current[ci.HELPER] = self.repo.proposed[ci.HELPER]
        self.reject()

    def test_split_transition_is_rejected(self):
        partial = dict(self.repo.dependency_source)
        partial[ci.HELPER] = self.repo.proposed[ci.HELPER]
        self.repo.add('partial-origin', partial)
        self.repo.add('completed-later')
        self.reject()

    def test_unknown_test_bytes_are_rejected(self):
        self.repo.current[candidate.TESTS] += b'\n'
        self.reject()

    def test_unknown_helper_bytes_are_rejected(self):
        self.repo.current[candidate.NEW] += b'\n'
        self.reject()

    def test_unknown_controller_bytes_are_rejected(self):
        self.repo.current[ci.HELPER] += b'\n'
        self.reject()

    def test_test_history_modified_then_restored_is_rejected(self):
        self.commit_review()
        self.repo.add('modified-tests', self.mutate(candidate.TESTS))
        self.repo.add('restored-tests')
        self.reject()

    def test_helper_history_modified_then_restored_is_rejected(self):
        self.commit_review()
        self.repo.add('modified-helper', self.mutate(candidate.NEW))
        self.repo.add('restored-helper')
        self.reject()

    def test_controller_history_modified_then_restored_is_rejected(self):
        self.commit_review()
        self.repo.add('modified-controller', self.mutate(ci.HELPER))
        self.repo.add('restored-controller')
        self.reject()

    def test_restoring_old_current_bytes_cannot_erase_activation(self):
        self.commit_review()
        self.repo.current = dict(self.repo.dependency_source)
        self.reject()

    def test_revert_history_cannot_erase_activation(self):
        self.commit_review()
        self.repo.add('revert', self.repo.dependency_source)
        self.repo.current = dict(self.repo.dependency_source)
        self.reject()

    def test_deleted_helper_is_rejected(self):
        self.commit_review()
        del self.repo.current[candidate.NEW]
        self.assertTrue(candidate.active(self.repo))
        self.reject()

    def test_catalog_change_is_not_part_of_approval(self):
        self.repo.current[candidate.CATALOG] += b'\n'
        self.reject()

    def test_frozen_fixture_change_is_not_part_of_approval(self):
        self.repo.current[next(iter(modules.FIXTURES))] += b'\n'
        self.reject()

    def test_new_loaded_helper_pin_is_independent(self):
        with patch.object(ci, '_ROBOLECTRIC_R4_REVIEWED_HELPER_BLOB', 'f' * 40):
            self.reject()

    def test_loaded_controller_must_match_exact_derived_source(self):
        historical = REAL.blob(candidate.DEPENDENCY_ORIGIN, ci.HELPER)
        self.assertEqual('4a82823a1b4bf8d7755d3314cca040f60bee6b4a', legacy.blob_id(historical))
        with ExitStack() as stack:
            directory = Path(stack.enter_context(tempfile.TemporaryDirectory()))
            controller = directory / 'historical_r2_controller.py'
            controller.write_bytes(historical)
            stack.enter_context(patch.object(ci, '__file__', str(controller)))
            self.reject()

    def test_current_head_race_is_rejected(self):
        calls = iter([candidate.REVIEW_BASE, 'f' * 40])
        with patch.object(self.repo, 'head', side_effect=lambda: next(calls, 'f' * 40)):
            self.reject()

    def test_unrelated_comparison_is_rejected(self):
        origin = self.commit_review()
        unrelated = self.repo.add('foreign-branch', self.repo.dependency_source, parents=[])
        self.repo.current_head = origin
        self.assertRaises(QualityError, self.verify, unrelated)

    def test_side_protected_change_cannot_borrow_merge_endpoint(self):
        origin = self.commit_review()
        main = '2fa46c0ed9b5cfea43fd7b52322e592caab170a7'
        source = {name: REAL.blob(main, name) for name in candidate.BEFORE if name in REAL.tree_files(main)}
        source[candidate.CATALOG] += b'\n'
        side = self.repo.add('modified-main-side', source, parents=[main])
        self.repo.add('side-merge', parents=[origin, side])
        self.reject()

if __name__ == '__main__':
    unittest.main()
