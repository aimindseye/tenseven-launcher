"""REVIEW ONLY: one exact Robolectric transition; never an exemption.

Installed only after independent approval of the complete generated patch.
This file is a generator template, not imported by the live checker.
"""
import hashlib
from pathlib import Path

from quality_gate_runtime import QualityError, object_id
import quality_gate_policy as legacy
import quality_gate_module_policy as modules
import quality_gate_ci_policy as ci

BASE = 'f54d24fe97623b26ccaf7da72fa104edce1d7c90'
NEW = 'tools/quality_gate_robolectric_policy.py'
CATALOG = 'gradle/libs.versions.toml'
MODULE = 'tools/quality_gate_module_policy.py'
TESTS = 'tools/tests/test_deliverable_quality_gate.py'
APPENDED_TESTS_BLOB = '408bd29be2b6df1fefc8a842056294f180c4fa31'
APPEND_MARKER = b'\n# PR144 exact Robolectric transition regressions.\n'
MAIN_BLOCK = b"if __name__ == '__main__':\n    unittest.main()\n"

DEPENDENCY_ORIGIN = '4e1283cea4fdc8777b44a117defac8861195fcb8'
REVIEW_BASE = '49d08bffcb03e7183ee071e5d1caf86e991b2509'
REVIEW_TESTS_BLOB = '3df77dd5cc1eef755065085275bd95e969adf6f7'
REVIEW_PIN_LINE = b"\n_ROBOLECTRIC_R3_REVIEWED_HELPER_BLOB = '@REVIEWED_HELPER_BLOB@'\n"
BEFORE = {'.editorconfig': 'e02a4ece681bd31785db9618c566295d6b5a332c',
 '.github/workflows/android.yml': '53d88c20accba33a9874145b394b795688fd2e22',
 '.github/workflows/checks.yml': '81b81ec453580c7fc927b03d3e95011f986ad12c',
 '.github/workflows/codeql.yml': '6b5001384e1a3424b8e1704195779d904cec7fff',
 '.github/workflows/frames-ui.yml': '308b4171817cc84aabf61d9f78d54cc260ee87b9',
 '.github/workflows/p2-quality.yml': '8d149ca364be3fb441d2ce3ff4153d1daa4a0244',
 '.github/workflows/security.yml': 'c36a5da44c0a1ead9b952e96e36543d9087df840',
 '.github/workflows/sonar.yml': 'c957711e4297c58f841f79f79747790aef21f163',
 'app/build.gradle.kts': 'b8f948739015e056192c72ac2206094b566307da',
 'app/src/test/java/io/github/aimindseye/tenseven/features/AndroidViewFixtures.kt': '3017786c73ac26fc5ee895cb5108197ecd4afc3c',
 'app/src/test/java/io/github/aimindseye/tenseven/features/calendar/CalendarNavigationBehaviorTest.kt': 'deaf5f36362d3d09132a7ed453cedd37e922f2f6',
 'app/src/test/java/io/github/aimindseye/tenseven/features/calendar/CalendarOptionsBehaviorTest.kt': 'ef9e9271fe2149127f35f2b4bc5772cb362e1a3c',
 'app/src/test/java/io/github/aimindseye/tenseven/features/calendar/CalendarScheduleBehaviorTest.kt': 'c8bbe8ea6907496503f787ad8308739216babb77',
 'app/src/test/java/io/github/aimindseye/tenseven/features/calendar/CalendarViewBehaviorTest.kt': '101338bd018eb742f1d584486b6b11491b675be9',
 'app/src/test/java/io/github/aimindseye/tenseven/features/phone/PhoneBlocklistBehaviorTest.kt': '1ddb379fbe8bb753da6d21899c6a71bba310dac2',
 'app/src/test/java/io/github/aimindseye/tenseven/features/phone/PhoneContactsBehaviorTest.kt': '7e4e2d9b7d2224c3596af754cb133a24c422e24e',
 'app/src/test/java/io/github/aimindseye/tenseven/features/phone/PhoneDialBehaviorTest.kt': 'd40b66d62db4124ee6438f127753d3027981c0e0',
 'app/src/test/java/io/github/aimindseye/tenseven/features/phone/PhoneFixtureRecords.kt': '02774c30ace01078f7d16d30e6d25aaf42198aaa',
 'app/src/test/java/io/github/aimindseye/tenseven/features/phone/PhonePermissionBehaviorTest.kt': 'a0bb2324d9476fe309b0b2aef8ea6fd32343d0f3',
 'app/src/test/java/io/github/aimindseye/tenseven/features/phone/PhoneRecentsBehaviorTest.kt': '324154e7ec53d5322891964bf097c7b19a1b8700',
 'build.gradle.kts': 'b10fa8e2e47f7dc42e77813bd3679ab44bced292',
 'calendar/build.gradle.kts': '60876b15bfa147987425452fa754de115667ef79',
 'calendar/src/test/java/io/github/aimindseye/tenseven/features/AndroidViewFixtures.kt': '3017786c73ac26fc5ee895cb5108197ecd4afc3c',
 'config/DETEKT_PROVENANCE.env': '1953f85ea317eec38da2cb51dac037832e878417',
 'config/detekt-tenseven.yml': '350cdacb05c194df6ed294ed20d9d017c69076d2',
 'config/detekt.yml': '46ec82af4af1b79a4357bf0a8608538a549b1aaf',
 'design/build.gradle.kts': 'a27790ac93f21e7888f5291e38c6470f01576650',
 'gradle.properties': '57bb06febd33fe1c780dc5108a0c7d7f9b3e312e',
 'gradle/libs.versions.toml': 'cef0f4dde1afc5d33114b5701ed13752f425a783',
 'gradle/wrapper/gradle-wrapper.jar': '9fa7f288283f81eec4952784c271abd7e0938dee',
 'gradle/wrapper/gradle-wrapper.properties': 'fad1ca746fdabbc0bf94fb9e2cff33e0dfb6d8bd',
 'phone/build.gradle.kts': '6b3bbe077fb2479637b4284dd122596d8e073f57',
 'phone/src/main/java/io/github/aimindseye/tenseven/features/phone/TenSevenTelecom.kt': '8ad78380f3444840f4a2a347fb4bd19225f9c1ec',
 'phone/src/test/java/io/github/aimindseye/tenseven/features/AndroidViewFixtures.kt': '3017786c73ac26fc5ee895cb5108197ecd4afc3c',
 'settings.gradle.kts': '9925159a850d4286544f5197934f9f0f6c474933',
 'sonar-project.properties': '62f584bb2dbed2d48fc8c8407ed0ee47b98483e7',
 'tools/deliverable_quality_gate.py': '93bd71b86410b21f146952a0ec7bb3e750c2f6b6',
 'tools/quality_gate_ci_policy.py': 'f4b6ab57f05423e671aeb1a4e9ad7934d923edee',
 'tools/quality_gate_module_policy.py': 'f1cfb5f485feb6ecf49bb550dde7eaf6d3a2678e',
 'tools/quality_gate_policy.py': '200f4b645d23f1a3850391b664822a31738f8d79',
 'tools/quality_gate_runtime.py': '1609e7c9896b0c38e953878a0ee97a9af7a7d832',
 'tools/tests/test_deliverable_quality_gate.py': 'c67139124f1a56287704fab08c8bbf50457e65af'}
MODULE_EDITS = (
    (b'    """Validate exact current bytes and every before/after ancestry state."""\n',
     b'    """Validate exact current bytes and every before/after ancestry state."""\n'
     b'    from quality_gate_robolectric_policy import active as dependency_active, verify as verify_dependency\n'
     b'    if dependency_active(repo):\n'
     b'        return verify_dependency(repo, base)\n'),
    (b'        previous = repo.blob_id(base, name)\n',
     b'        current = verified.get("dependency_endpoints", {}).get(name, current)\n'
     b'        previous = repo.blob_id(base, name)\n'),
    (b'            module_origin=verified["origin"], approval="Original R2.1 plus exact L0 source-set transition"))\n',
     b'            module_origin=verified["origin"], approval="Original R2.1 plus exact L0 source-set transition"))\n'
     b'        if name in verified.get("dependency_endpoints", {}):\n'
     b'            records[-1].update(\n'
     b'                dependency_origin=verified["dependency_origin"],\n'
     b'                dependency_parent=verified["dependency_parent"],\n'
     b'                dependency_verification_mode=verified["dependency_verification_mode"],\n'
     b'                approval="Exact reviewed Robolectric 4.18.1 extension; original R2.1 and L0 origins retained",\n'
     b'                analysis_effect="Test-only 4.18 -> 4.18.1; no analyzer overlay")\n'),
)
CI_EDITS = (
    (b'    """Independently derive controller bytes; never trust its proposed after-ID."""\n',
     b'    """Independently derive controller bytes; never trust its proposed after-ID."""\n'
     b'    from quality_gate_robolectric_policy import active as dependency_active, verify as verify_dependency\n'
     b'    if dependency_active(repo):\n'
     b'        return verify_dependency(repo, base)["controller"]\n'),
)
PIN_LINE = b"\n_ROBOLECTRIC_REVIEWED_HELPER_BLOB = '@REVIEWED_HELPER_BLOB@'\n"
TEST_EDITS = ((b'        helper = Path(POSTCOMMIT_CI.__file__).read_bytes()\n',
  b'        helper = real.blob("6b3918755f7ac4945b59e7278c4dbb2da7a2e78a", POSTCOMMIT_CI.HELPER)\n'),
 (b'        self.ancestor_cache = {}\n\n    def head(self):\n',
  b'        self.ancestor_cache = {}\n\n    def files(self):\n        if self.current not in self.states:\n '
  b'           return self.real.tree_files(self.current)\n        original = self.real.tree_files(POSTCOMMIT_'
  b'CI._L0_CORRECTION_BASE)\n        changed = self.states[self.current]\n        return (original - set(c'
  b'hanged)) | {name for name, oid in changed.items() if oid is not None}\n\n    def resolve(self, value):'
  b'\n        return RUNTIME.object_id(value) if value in self.parents else self.real.resolve(value)\n\n   '
  b' def head(self):\n'),
 (b"    def _read(self, args):\n        if args[0] == 'rev-list' and '--parents' in args:\n",
  b"    def _read(self, args):\n        if args[0] == 'log' and args[2] in self.parents:\n            head"
  b" = args[2]\n            paths = args[args.index('--') + 1:]\n            changed, real_roots = set(), "
  b'set()\n            for commit in self.ancestors(head):\n                if commit not in self.states:\n'
  b'                    continue\n                for parent in self.parents[commit]:\n                   '
  b' if parent not in self.states:\n                        real_roots.add(parent)\n                    fo'
  b'r name in paths:\n                        previous = self.states[parent].get(name) if parent in self.stat'
  b'es else (self.real.blob_id(parent, name) if name in self.real.tree_files(parent) else None)\n            '
  b'            if self.states[commit].get(name) != previous:\n                            changed.add(commit'
  b")\n            for root in real_roots:\n                changed.update(self.real._read(['log', '--form"
  b"at=%H', root, '--', *paths]).decode().splitlines())\n            return ('\\n'.join(sorted(changed)) +"
  b" ('\\n' if changed else '')).encode()\n        if args[0] == 'rev-list' and '--parents' in args:\n"))


def edits(data, replacements):
    for before, after in replacements:
        if data.count(before) != 1:
            raise QualityError('Ambiguous frozen Robolectric source edit')
        data = data.replace(before, after, 1)
    return data


def active(repo):
    # Source inventory belongs to the repository abstraction. A historical model
    # must not activate merely because its backing directory has a newer helper.
    # Real GitRepository.files() includes tracked and non-ignored untracked input.
    if NEW in repo.files():
        return True
    raw = repo._read(['log', '--format=%H', object_id(repo.head()), '--', NEW])
    return bool(raw.strip())


def state(repo, commit, names):
    result = dict.fromkeys(names)
    raw = repo._read(['ls-tree', '-r', '-z', object_id(commit), '--', *sorted(names)])
    for entry in raw.split(b'\0'):
        if not entry:
            continue
        metadata, name = entry.decode().split('\t', 1)
        mode, kind, identifier = metadata.split()
        if mode != '100644' or kind != 'blob' or name not in result:
            raise QualityError('Unexpected Robolectric protected source type or mode')
        result[name] = object_id(identifier)
    return result


def frozen_provenance(repo):
    """Replay the unchanged original provenance with frozen immutable inputs."""
    if state(repo, BASE, BEFORE) != BEFORE:
        raise QualityError('Frozen Robolectric parent source differs')
    before, after = modules.expected(repo, False), modules.expected(repo, True)
    harness = legacy._harness_origin(repo, modules.BASE)
    origin = modules.history(repo, BASE, before, after)
    ci._verify_original_ci(repo, legacy.blob_id)
    original = ci._l0_correction_before(repo, legacy.blob_id)
    source = {name: repo.blob(BASE, name) for name in ci._L0_PATHS}
    corrected = ci._l0_approved(repo, source, legacy.blob_id)
    states = {commit: ci._l0_state(repo, commit) for commit in ci._ancestry(repo, BASE, ci._L0_BASE)}
    old_states = {commit: states[commit] for commit in ci._ancestry(repo, ci._L0_CORRECTION_BASE, ci._L0_BASE)}
    old_origin = ci._l0_origin(repo, ci._L0_CORRECTION_BASE, old_states, states[ci._L0_BASE], original)
    correction = ci._l0_correction_origin(repo, BASE, states, states[ci._L0_BASE], original, corrected)
    if origin != old_origin or old_origin != ci._L0_CORRECTION_BASE:
        raise QualityError('Original module/controller origins differ')
    if correction != '6b3918755f7ac4945b59e7278c4dbb2da7a2e78a':
        raise QualityError('Original correction origin differs')
    return harness, origin, correction


def _dependency_after_state(repo, data):
    helper = legacy.blob_id(data[NEW])
    if helper != ci._ROBOLECTRIC_REVIEWED_HELPER_BLOB:
        raise QualityError('Robolectric executable helper pin differs')
    result = dict(BEFORE, **{NEW: helper})
    if data[TESTS].count(APPEND_MARKER) != 1 or not data[TESTS].endswith(MAIN_BLOCK):
        raise QualityError('Missing exact appended dependency regressions')
    appended = data[TESTS].split(APPEND_MARKER, 1)[1][:-len(MAIN_BLOCK)]
    if legacy.blob_id(appended) != APPENDED_TESTS_BLOB:
        raise QualityError('Dependency regression tests differ from review')
    proposals = {
        CATALOG: edits(repo.blob(BASE, CATALOG), ((b'robolectric = "4.18"\n', b'robolectric = "4.18.1"\n'),)),
        MODULE: edits(repo.blob(BASE, MODULE), MODULE_EDITS),
        ci.HELPER: edits(repo.blob(BASE, ci.HELPER), CI_EDITS) + PIN_LINE.replace(b'@REVIEWED_HELPER_BLOB@', helper.encode()),
        TESTS: edits(edits(repo.blob(BASE, TESTS), TEST_EDITS), ((MAIN_BLOCK, APPEND_MARKER + appended + MAIN_BLOCK),)),
    }
    result.update({name: legacy.blob_id(raw) for name, raw in proposals.items()})
    return result


def _parents(repo, commit):
    values = repo._read(['rev-list', '--parents', '-n', '1', object_id(commit), '--']).decode().split()
    if not values or values[0] != commit:
        raise QualityError('Invalid immutable parent record')
    return [object_id(value) for value in values[1:]]


def _side_parent_state(repo, commit, states, frozen, names):
    if commit in states:
        return states[commit]
    if commit not in frozen:
        raise QualityError('Incomplete or unanchored side history')
    return state(repo, commit, names)


def _verify_side_commit(repo, commit, states, frozen, names):
    parents = _parents(repo, commit)
    if not parents:
        raise QualityError('Unanchored new side ancestry')
    for parent in parents:
        if states[commit] != _side_parent_state(repo, parent, states, frozen, names):
            raise QualityError('Unreviewed protected change on side ancestry')


def after_states(repo):
    """Derive v5 independently from its frozen inputs, then the exact R3 extension."""
    original = {name: repo.blob(DEPENDENCY_ORIGIN, name) for name in set(BEFORE) | {NEW}}
    after = _dependency_after_state(repo, original)
    if state(repo, DEPENDENCY_ORIGIN, after) != after or not ci._single_parent(repo, DEPENDENCY_ORIGIN, BASE):
        raise QualityError('Original immutable dependency endpoint/origin differs')
    helper = legacy.blob_id(Path(__file__).read_bytes())
    if helper != ci._ROBOLECTRIC_R3_REVIEWED_HELPER_BLOB:
        raise QualityError('Loaded R3 helper differs from independent controller pin')
    controller = original[ci.HELPER] + REVIEW_PIN_LINE.replace(b'@REVIEWED_HELPER_BLOB@', helper.encode())
    if Path(ci.__file__).read_bytes() != controller:
        raise QualityError('Loaded R3 controller differs from independent derivation')
    reviewed = {**after, NEW: helper, TESTS: REVIEW_TESTS_BLOB, ci.HELPER: legacy.blob_id(controller)}
    return after, reviewed


def _review_origin(repo, states, reviewed):
    changed = [commit for commit, value in states.items() if value == reviewed]
    if not changed:
        return None
    origins = [commit for commit in changed if ci._single_parent(repo, commit, REVIEW_BASE)]
    if len(origins) != 1:
        raise QualityError('Need one exact R3 origin directly after qualified TLS parent')
    return origins[0]


def _expected_transition_state(repo, commit, origins, endpoints):
    dependency, review = origins
    before, after, reviewed = endpoints
    if review is not None and repo.ancestor(review, commit):
        return reviewed
    if repo.ancestor(dependency, commit):
        return after
    if repo.ancestor(BASE, commit):
        return before
    return None


def _verify_transition_states(repo, states, origins, endpoints, frozen):
    for commit, value in states.items():
        expected = _expected_transition_state(repo, commit, origins, endpoints)
        if expected is None:
            _verify_side_commit(repo, commit, states, frozen, endpoints[1])
        elif value != expected:
            raise QualityError('Dependency/R3 protected history modified, partial or restored')


def transition_history(repo, head, before, after, reviewed):
    if not repo.ancestor(BASE, head):
        raise QualityError('Frozen Robolectric base outside candidate ancestry')
    revision = BASE + '..' + object_id(head)
    commits = [object_id(c) for c in repo._read(['rev-list', '--reverse', revision, '--']).decode().splitlines()]
    if not commits:
        return None, None
    frozen = {object_id(c) for c in repo._read(['rev-list', BASE, '--']).decode().splitlines()}
    states = {commit: state(repo, commit, after) for commit in commits}
    origins = [commit for commit, value in states.items()
               if ci._single_parent(repo, commit, BASE) and value == after]
    if len(origins) != 1:
        raise QualityError('Need one joint Robolectric origin directly after frozen parent')
    origin = origins[0]
    review = _review_origin(repo, states, reviewed)
    _verify_transition_states(repo, states, (origin, review), (before, after, reviewed), frozen)
    return origin, review


def _verify_current_family(head, current, after, reviewed, review_origin):
    if current == after and review_origin is None:
        return
    if current != reviewed:
        raise QualityError('Unknown, partial or reverted R3 protected endpoint')
    if review_origin is None and head != REVIEW_BASE:
        raise QualityError('R3 precommit must be at exact qualified TLS parent')


def verify(repo, base):
    head = repo.head()
    base = repo.resolve(base)
    if not repo.ancestor(base, head):
        raise QualityError('Robolectric comparison base outside ancestry')
    harness, module_origin, correction = frozen_provenance(repo)
    modules.verify_workflow_inventory(repo)
    ci._verify_l0_comparison_workflow(repo, base)
    names = set(BEFORE) | {NEW}
    with legacy._source_snapshot(repo, names) as data:
        after, reviewed = after_states(repo)
        current = {name: legacy.blob_id(raw) for name, raw in data.items()}
        before = dict(BEFORE, **{NEW: None})
        if state(repo, BASE, names) != before:
            raise QualityError('Frozen Robolectric joint parent differs')
        origin, review_origin = transition_history(repo, head, before, after, reviewed)
        _verify_current_family(head, current, after, reviewed, review_origin)
    if {item.parent.name for item in repo.path.glob('*/build.gradle.kts')} != modules.MODULES:
        raise QualityError('Android module inventory differs')
    modules.verify_workflow_inventory(repo)
    if repo.head() != head:
        raise QualityError('Robolectric candidate HEAD changed')
    controller = {
        'origin': module_origin, 'parent': modules.BASE,
        'endpoints': {name: current[name] for name in ci._L0_PATHS},
        'verification_mode': 'immutable_origin', 'correction_origin': correction,
        'correction_parent': ci._L0_CORRECTION_BASE, 'correction_verification_mode': 'immutable_origin',
        'dependency_origin': origin, 'dependency_parent': BASE,
        'maintainability_origin': review_origin, 'maintainability_parent': REVIEW_BASE,
    }
    return {
        'status': 'VERIFIED_EXACT_MODULE_TRANSITION', 'base': modules.BASE,
        'origin': module_origin, 'verification_mode': 'immutable_origin', 'r21_origin': harness,
        'modules': sorted(modules.MODULES), 'dependency_origin': origin, 'dependency_parent': BASE,
        'dependency_verification_mode': 'pre_commit' if origin is None else 'immutable_origin',
        'dependency_endpoints': {CATALOG: current[CATALOG]}, 'controller': controller,
        'maintainability_origin': review_origin, 'maintainability_parent': REVIEW_BASE,
    }
