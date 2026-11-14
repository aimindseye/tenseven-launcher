"""Offline regressions plus opt-in qualification against actual Gradle reports."""

import importlib.util
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
        # R2-G removes Game2048Screen's inherited naming marker; retained markers remain unapproved.
        self.assertEqual(102, len(audit))
        self.assertFalse(any('/extras/games/' in item['file'] for item in audit))
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


if __name__ == '__main__':
    unittest.main()
