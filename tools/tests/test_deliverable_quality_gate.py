"""Offline regressions plus opt-in qualification against actual Gradle reports."""

import importlib.util
from contextlib import redirect_stdout
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
            with self.subTest(text=text):
                with self.assertRaises(GATE.QualityError):
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
        with patch.object(self.git, '_read', return_value=b'bad\noutput\n'):
            with self.assertRaises(GATE.QualityError):
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

        with patch.object(RUNTIME.os, 'open', side_effect=race_open):
            with self.assertRaises((GATE.QualityError, OSError)):
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

        with patch.object(RUNTIME.os, 'open', side_effect=race_open):
            with self.assertRaises(FileExistsError):
                evidence.write('QUALITY_GATE.json', b'bad')
        self.assertEqual(b'keep', (evidence.path / 'QUALITY_GATE.json').read_bytes())

    def test_reservation_race_rejects_swapped_ancestor(self):
        outside = self.allowed / 'outside'
        outside.mkdir()
        original_open = RUNTIME.os.open
        swapped = False

        def race_open(path, flags, *args, **kwargs):
            nonlocal swapped
            if path == 'race-parent' and not swapped:
                swapped = True
                (self.allowed / path).rename(self.allowed / 'original-parent')
                (self.allowed / path).symlink_to(outside, target_is_directory=True)
            return original_open(path, flags, *args, **kwargs)

        with patch.object(RUNTIME.os, 'open', side_effect=race_open):
            with self.assertRaises(OSError):
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

    def test_ci_runner_temp_and_platform_tmp_alias_work(self):
        runner = self.allowed / 'runner'
        runner.mkdir()
        with patch.dict(os.environ, {'RUNNER_TEMP': str(runner)}):
            evidence = RUNTIME.EvidenceDirectory.reserve(runner / 'ci-quality', self.repo)
            evidence.close()
        alias = self.allowed / 'platform-alias'
        alias.symlink_to(runner, target_is_directory=True)
        evidence = RUNTIME.EvidenceDirectory.reserve(alias / 'local-quality', self.repo, [(alias, runner)])
        evidence.close()

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
        with self.assertRaises(FileExistsError):
            with POLICY.temporary_compose_overlay(root, True):
                self.fail('An existing overlay must not be accepted')
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
        with self.assertRaises(GATE.QualityError):
            with POLICY.temporary_compose_overlay(root, True):
                build.rename(root / 'original-build')
                build.write_text('other work\n')
        self.assertEqual('other work\n', build.read_text())


    def test_candidate_head_and_contents_are_bound_across_phases(self):
        result = {'head': self.git.head(), 'candidate_start_source_sha256': GATE.source_digest(self.repo),
                  'base_ref': self.base, 'changed_paths': sorted(self.git.changed(self.base))}
        GATE.assert_candidate_unchanged(self.git, result)
        with patch.object(self.git, 'head', return_value='0' * 40):
            with self.assertRaises(GATE.QualityError):
                GATE.assert_candidate_unchanged(self.git, result)
        (self.repo / self.source).write_text('class ConcurrentEdit\n')
        with self.assertRaises(GATE.QualityError):
            GATE.assert_candidate_unchanged(self.git, result)


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
        self.assertEqual(2, len(records))
        self.assertEqual({item.path for item in POLICY.TRANSITIONS}, {item['path'] for item in records})

    def test_real_suppression_inventory_never_grants_automatic_approval(self):
        audit = POLICY.audit_suppressions(self.repo, self.repo.changed(self.base), self.base)
        self.assertEqual(103, len(audit))
        self.assertTrue(all(item['approval'] == 'NOT_APPROVED' and item['result'] == 'HOLD' for item in audit))
        self.assertTrue(all(item['inherited_before_r1'] and item['origin_commit'] for item in audit))

    def test_one_byte_change_in_either_actual_transition_is_rejected(self):
        original = POLICY.source_bytes
        for transition in POLICY.TRANSITIONS:
            def altered(repo, filename):
                data = original(repo, filename)
                return data + b'# altered\n' if filename == transition.path else data
            with self.subTest(path=transition.path), patch.object(POLICY, 'source_bytes', side_effect=altered):
                with self.assertRaises(GATE.QualityError):
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
        # A real baseline naming finding cannot grant a waiver to a new composable file.
        naming = next(item for item in old if item['rule'] == 'detekt.FunctionNaming')
        injected = {**naming, 'file': 'app/src/main/java/io/github/aimindseye/tenseven/CompanionWizardActivity.kt', 'line': 61}
        self.assertEqual(1, len(GATE.compare_findings(old, new + [injected], base, repo)[0]))


if __name__ == '__main__':
    unittest.main()
