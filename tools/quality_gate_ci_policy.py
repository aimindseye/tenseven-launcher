"""Exact CI-completeness provenance. Draft for separate owner approval only."""
from contextlib import ExitStack

from quality_gate_runtime import QualityError, object_id

WORKFLOW = ".github/workflows/android.yml"
POLICY = "tools/quality_gate_policy.py"
HELPER = "tools/quality_gate_ci_policy.py"
PATHS = (WORKFLOW, POLICY, HELPER)

# Fixed code-reviewed delta, not candidate/origin controller bytes. The sole slot
# is the helper's exact blob ID, already checked against the controller pin.
# No full-file hash is stored in a file that contributes to that hash.
APPROVAL_BASE = "7eee5b8d848108e1cbbe21c36ba3869dfb1cbbe3"
_POLICY_BEFORE = "daa42bb5d309396182a97253179bcb46b56ff4f6"
_WORKFLOW_BEFORE = "a7e4863716c9d43c75070f6e7b73dea9d7cd4377"
_WORKFLOW_AFTER = "087a212a14b163f0b6e276864bf1930c865e4df6"
_HELPER_SLOT = b"@REVIEWED_HELPER_BLOB@"
_CONTROLLER_EDITS = (
    (b"""from quality_gate_runtime import DIRECTORY_FLAGS, QualityError, object_id, repository_path
""",
     b"""from quality_gate_runtime import DIRECTORY_FLAGS, QualityError, object_id, repository_path
from quality_gate_ci_policy import verify_ci_completeness
"""),
    (b"""def verify_workflows(repo, changed, base):
    for filename in sorted(p for p in changed if p.startswith(".github/workflows/")):
        if filename != ANDROID_CI:
            raise QualityError("Unexpected CI policy change: " + filename)
        before = repo.blob(base, filename).decode()
        expected = ci_bootstrap(before)
        if CI_STEP in before:
            expected = before.replace(OLD_CI_PATHS, CI_PATHS)
        if source_bytes(repo, filename).decode() != expected:
            raise QualityError("Unexpected CI policy change: " + filename)
""",
     b"""CI_COMPLETENESS_APPROVAL = {
    "base": "7eee5b8d848108e1cbbe21c36ba3869dfb1cbbe3",
    "workflow_before": "a7e4863716c9d43c75070f6e7b73dea9d7cd4377",
    "workflow_after": "087a212a14b163f0b6e276864bf1930c865e4df6",
    "policy_before": "daa42bb5d309396182a97253179bcb46b56ff4f6",
    "helper_after": "@REVIEWED_HELPER_BLOB@"
}


def _expected_ci_baseline(repo, base):
    original = repo.blob(base, ANDROID_CI)
    identifier = blob_id(original)
    if identifier in {CI_COMPLETENESS_APPROVAL["workflow_before"], CI_COMPLETENESS_APPROVAL["workflow_after"]}:
        return identifier
    before = original.decode()
    expected = ci_bootstrap(before)
    if CI_STEP in before:
        expected = before.replace(OLD_CI_PATHS, CI_PATHS)
    return blob_id(expected.encode())


def verify_workflows(repo, changed, base):
    records = verify_ci_completeness(repo, base, CI_COMPLETENESS_APPROVAL,
                                     _source_snapshot, blob_id, _expected_ci_baseline, CI_STEP)
    for filename in sorted(p for p in changed if p.startswith(".github/workflows/")):
        if filename == ANDROID_CI and records:
            continue
        if filename != ANDROID_CI:
            raise QualityError("Unexpected CI policy change: " + filename)
        before = repo.blob(base, filename).decode()
        expected = ci_bootstrap(before)
        if CI_STEP in before:
            expected = before.replace(OLD_CI_PATHS, CI_PATHS)
        if source_bytes(repo, filename).decode() != expected:
            raise QualityError("Unexpected CI policy change: " + filename)
    return records
"""),
    (b"""def review_policy(repo, changed, base, approved_compose=False):
    transitions = verify_protected_policy(repo, changed, base)
    overlay = verify_compose(repo, changed, base, approved_compose)
    verify_workflows(repo, changed, base)
    return {"verified_transitions": transitions, "compose_overlay": overlay,
            "suppression_audit": audit_suppressions(repo, changed, base)}
""",
     b"""def review_policy(repo, changed, base, approved_compose=False):
    transitions = verify_protected_policy(repo, changed, base)
    overlay = verify_compose(repo, changed, base, approved_compose)
    workflow_records = verify_workflows(repo, changed, base)
    return {"verified_transitions": transitions + workflow_records, "compose_overlay": overlay,
            "suppression_audit": audit_suppressions(repo, changed, base)}
"""),
 )


def _approved_state(repo, record, data, blob_id):
    """Derive the full controller endpoint independently of its current/history bytes."""
    helper_blob = blob_id(data[HELPER])
    fixed = {"base": APPROVAL_BASE, "workflow_before": _WORKFLOW_BEFORE,
             "workflow_after": _WORKFLOW_AFTER, "policy_before": _POLICY_BEFORE,
             "helper_after": helper_blob}
    if record != fixed:
        raise QualityError("CI approval constants differ from the reviewed joint transition")
    controller = repo.blob(APPROVAL_BASE, POLICY)
    if blob_id(controller) != _POLICY_BEFORE or _HELPER_SLOT in controller:
        raise QualityError("CI immutable controller template base differs")
    for original, replacement in _CONTROLLER_EDITS:
        if controller.count(original) != 1:
            raise QualityError("CI controller template has ambiguous source edit")
        controller = controller.replace(original, replacement, 1)
    if controller.count(_HELPER_SLOT) != 1:
        raise QualityError("CI controller template requires one exact helper slot")
    controller = controller.replace(_HELPER_SLOT, helper_blob.encode(), 1)
    return {WORKFLOW: _WORKFLOW_AFTER, POLICY: blob_id(controller), HELPER: helper_blob}


def _state(repo, commit):
    raw = repo._read(["ls-tree", "-z", object_id(commit), "--", *PATHS])
    state = dict.fromkeys(PATHS)
    for entry in raw.split(b"\0"):
        if entry:
            metadata, path = entry.decode().split("\t", 1)
            mode, kind, identifier = metadata.split()
            if mode != "100644" or kind != "blob" or path not in state:
                raise QualityError("Unexpected CI transition source type or mode")
            state[path] = object_id(identifier)
    return state


def _ancestry(repo, head, root):
    ancestors = {object_id(value) for value in repo._read(["rev-list", object_id(head), "--"]).decode().splitlines()}
    if root not in ancestors:
        return []
    revision = object_id(root) + ".." + object_id(head)
    values = repo._read(["rev-list", "--ancestry-path", "--reverse", revision, "--"]).decode().splitlines()
    return [root, *(object_id(value) for value in values)]


def _single_parent(repo, commit, root):
    values = repo._read(["rev-list", "--parents", "-n", "1", object_id(commit), "--"]).decode().split()
    return [object_id(value) for value in values] == [commit, root]


def _verify_history(repo, head, record, states, approved):
    root = record["base"]
    before = {WORKFLOW: record["workflow_before"], POLICY: record["policy_before"], HELPER: None}
    if states.get(root) != before:
        raise QualityError("CI approval base source differs")
    origins = [commit for commit, state in states.items()
               if commit != root and _single_parent(repo, commit, root) and state == approved]
    if len(origins) != 1:
        raise QualityError("CI transition requires one single-parent origin directly after approval base")
    origin = origins[0]
    for commit, state in states.items():
        expected = approved if repo.ancestor(origin, commit) else before
        if state != expected:
            raise QualityError("CI workflow/policy/helper history was modified or restored")
    if not repo.ancestor(origin, head):
        raise QualityError("CI origin is outside candidate ancestry")
    return origin


def _verify_current(repo, base, head, record, states, data, blob_id, expected_workflow, mandatory):
    if not repo.ancestor(base, head) or record["base"] not in states:
        raise QualityError("CI comparison/base ancestry is invalid")
    current = {path: blob_id(data[path]) for path in PATHS}
    approved = _approved_state(repo, record, data, blob_id)
    if current != approved:
        raise QualityError("CI joint source differs from exact endpoints")
    if mandatory not in data[WORKFLOW].decode():
        raise QualityError("Original main-relative CI gate was removed or changed")
    if expected_workflow(repo, base) not in {record["workflow_before"], record["workflow_after"]}:
        raise QualityError("Unknown main/base workflow bootstrap")
    if head == record["base"]:
        expected = {WORKFLOW: record["workflow_before"], POLICY: record["policy_before"], HELPER: None}
        if states[head] != expected:
            raise QualityError("Pre-commit CI source has unexpected base")
        return None, current
    return _verify_history(repo, head, record, states, approved), current


def verify_ci_completeness(repo, base, record, snapshot, blob_id, expected_workflow, mandatory):
    """record/callbacks are reviewed code constants, never CLI or approval-file input."""
    head = repo.head()
    base = repo.resolve(base)
    commits = _ancestry(repo, head, object_id(record["base"]))
    states = {commit: _state(repo, commit) for commit in commits}
    history_active = any(state[WORKFLOW] == record["workflow_after"] or state[HELPER] is not None
                         or state[POLICY] != record["policy_before"] for state in states.values())
    known_files = repo.files()
    if WORKFLOW not in known_files:
        if history_active:
            raise QualityError("Activated CI workflow is missing")
        return []
    with ExitStack() as stack:
        initial = stack.enter_context(snapshot(repo, {WORKFLOW, POLICY}))
        active = (history_active or blob_id(initial[WORKFLOW]) == record["workflow_after"]
                  or HELPER in known_files or (commits and blob_id(initial[POLICY]) != record["policy_before"]))
        if not active:
            if repo.head() != head:
                raise QualityError("CI candidate HEAD changed")
            return []
        data = stack.enter_context(snapshot(repo, set(PATHS)))
        origin, current = _verify_current(repo, base, head, record, states, data, blob_id, expected_workflow, mandatory)
        if repo.head() != head:
            raise QualityError("CI candidate HEAD changed")
        result = {"path": WORKFLOW, "before": record["workflow_before"], "after": record["workflow_after"],
                  "origin": origin, "origin_parent": record["base"], "comparison_base": base,
                  "helper_blob": current[HELPER], "policy_blob": current[POLICY],
                  "verification_mode": "pre_commit" if origin is None else "immutable_origin",
                  "approval": "Exact CI-completeness workflow/policy/helper transition"}
    return [result]


_L0_BASE = '09d331d9f9b278ba1bed1323056cf1504e456629'
_L0_MODULE_BLOB = 'f1cfb5f485feb6ecf49bb550dde7eaf6d3a2678e'
_L0_GATE_BLOB = '93bd71b86410b21f146952a0ec7bb3e750c2f6b6'
_L0_WORKFLOW_BLOB = '33339730ac60aabe9930e8e2188066e787a1de9a'
_L0_JAVA_WORKFLOW_BLOB = '53d88c20accba33a9874145b394b795688fd2e22'
_L0_MAIN_BOOTSTRAP = '63f0c4efa0db5a027db9af8ffca196f21e75fa87'
_L0_CONTROLLER_EDITS = ((b'    protected = changed & QUALITY_CONFIG_FILES\n',
  b'    from quality_gate_module_policy import active, historical_records\n    if active(repo):\n '
  b'       return historical_records(repo, base, changed)\n    protected = changed & QUALITY_CONF'
  b'IG_FILES\n'),
 (b'def verify_test_harness(repo, protected, base):\n',
  b'def verify_test_harness(repo, protected, base):\n    from quality_gate_module_policy import a'
  b'ctive, harness_records\n    if active(repo):\n        return harness_records(repo, base)\n'),
 (b'def verify_test_harness_analysis(repo, parent):\n',
  b'def verify_test_harness_analysis(repo, parent):\n    from quality_gate_module_policy import a'
  b'ctive, analysis\n    if active(repo):\n        return analysis(repo, parent)\n'),
 (b'def verify_workflows(repo, changed, base):\n',
  b'def verify_workflows(repo, changed, base):\n    from quality_gate_module_policy import active'
  b', verify\n    if active(repo):\n        return [verify(repo, base)]\n'),
 (b'    return {"verified_transitions": transitions + workflow_records, "compose_overlay": overl'
  b'ay,\n            "suppression_audit": audit_suppressions(repo, changed, base)}',
  b'    audit = audit_suppressions(repo, changed, base)\n    contracts = []\n    from quality_gate'
  b'_module_policy import active, review_permission_contract\n    if active(repo):\n        audit,'
  b' contracts = review_permission_contract(repo, base, audit)\n    return {"verified_transitions'
  b'": transitions + workflow_records, "compose_overlay": overlay,\n            "suppression_audi'
  b't": audit, "reviewed_suppression_contracts": contracts}'),
 (b'"helper_after": "8cdd8d71f28d410c09b57a64fbd7f6492aa8bed1"',
  b'"helper_after": "@L0_CI_HELPER_BLOB@"'))

_L0_PATHS = PATHS + ("tools/quality_gate_module_policy.py", "tools/deliverable_quality_gate.py")


def _l0_state(repo, commit):
    raw = repo._read(["ls-tree", "-r", "-z", object_id(commit), "--", *_L0_PATHS])
    result = dict.fromkeys(_L0_PATHS)
    for entry in raw.split(b"\0"):
        if entry:
            metadata, name = entry.decode().split("\t", 1)
            mode, kind, identifier = metadata.split()
            if mode != "100644" or kind != "blob" or name not in result:
                raise QualityError("Invalid L0 controller source type")
            result[name] = object_id(identifier)
    return result


def _l0_approved(repo, data, blob_id):
    helper = blob_id(data[HELPER])
    controller = repo.blob(_L0_BASE, POLICY)
    if blob_id(controller) != "e105c759791888f212cf980c7336376fb2310dcc":
        raise QualityError("Frozen reviewed controller differs")
    for old, new in _L0_CONTROLLER_EDITS:
        if controller.count(old) != 1:
            raise QualityError("Ambiguous L0 controller template")
        controller = controller.replace(old, new, 1)
    token = b"@L0_CI_HELPER_BLOB@"
    if controller.count(token) != 1:
        raise QualityError("L0 controller helper pin differs")
    controller = controller.replace(token, helper.encode(), 1)
    return {WORKFLOW: _L0_WORKFLOW_BLOB, POLICY: blob_id(controller), HELPER: helper,
            "tools/quality_gate_module_policy.py": _L0_MODULE_BLOB,
            "tools/deliverable_quality_gate.py": _L0_GATE_BLOB}


def _verify_original_ci(repo, blob_id):
    data = {name: repo.blob(_L0_BASE, name) for name in PATHS}
    record = dict(base=APPROVAL_BASE, workflow_before=_WORKFLOW_BEFORE,
                  workflow_after=_WORKFLOW_AFTER, policy_before=_POLICY_BEFORE,
                  helper_after="8cdd8d71f28d410c09b57a64fbd7f6492aa8bed1")
    approved = _approved_state(repo, record, data, blob_id)
    commits = _ancestry(repo, _L0_BASE, APPROVAL_BASE)
    states = {commit: _state(repo, commit) for commit in commits}
    origin = _verify_history(repo, _L0_BASE, record, states, approved)
    if origin != _L0_BASE:
        raise QualityError("Original CI origin differs from reviewed implementation")


def verify_l0_controller(repo, base, snapshot, blob_id):
    """Independently derive controller bytes; never trust its proposed after-ID."""
    head = repo.head()
    if not repo.ancestor(base, head):
        raise QualityError("L0 comparison ancestry differs")
    _verify_l0_comparison_workflow(repo, base)
    _verify_original_ci(repo, blob_id)
    before = _l0_state(repo, _L0_BASE)
    commits = _ancestry(repo, head, _L0_BASE)
    if not commits:
        raise QualityError("L0 controller base is outside ancestry")
    states = {commit: _l0_state(repo, commit) for commit in commits}
    with snapshot(repo, set(_L0_PATHS)) as data:
        after, origin, correction = _l0_reviewed_current(repo, head, states, before, data, blob_id)
        current_after = _l0_java_variant(repo, head, after)
        if {name: blob_id(data[name]) for name in _L0_PATHS} != current_after:
            raise QualityError("L0 controller/helper/gate endpoint differs")
        if states[_L0_BASE] != before:
            raise QualityError("L0 immutable base differs")
        if repo.head() != head:
            raise QualityError("L0 controller HEAD changed")
    return dict(origin=origin, parent=_L0_BASE, endpoints=after,
                verification_mode="pre_commit" if origin is None else "immutable_origin",
                correction_origin=correction,
                correction_parent=_L0_CORRECTION_BASE if after[HELPER] != _L0_CORRECTION_OLD_HELPER else None,
                correction_verification_mode=("pre_commit" if correction is None else "immutable_origin")
                if after[HELPER] != _L0_CORRECTION_OLD_HELPER else None)


def _verify_l0_comparison_workflow(repo, base):
    from quality_gate_policy import _expected_ci_baseline
    from quality_gate_module_policy import MAINLINE_PROOF, verify_mainline_java
    valid_mainline = verify_mainline_java(repo, repo.head())
    endpoints = {_WORKFLOW_BEFORE, _WORKFLOW_AFTER, _L0_WORKFLOW_BLOB}
    previous, mainline = MAINLINE_PROOF[WORKFLOW]
    legacy = {previous: _WORKFLOW_BEFORE}
    if valid_mainline:
        endpoints.add(_L0_JAVA_WORKFLOW_BLOB)
        legacy[mainline] = _L0_MAIN_BOOTSTRAP
    identifier = repo.blob_id(base, WORKFLOW)
    if identifier in endpoints:
        return
    # Only these exact existing historical inputs may use the older bootstrap.
    # A partial/unknown workflow cannot recover authorization by normalization.
    expected = legacy.get(identifier)
    if expected is None or _expected_ci_baseline(repo, base) != expected:
        raise QualityError("Unknown main/comparison workflow bootstrap")


# This correction is a separate joint endpoint directly after the real, frozen L0 origin.
# Its future origin is discovered from immutable history; no future SHA is authorized here.
_L0_CORRECTION_BASE = '925b08da25802d8acb5b9e770d42297b20f64a8f'
_L0_CORRECTION_OLD_HELPER = 'f560fec57d8ea431f14aaa3bd2cec2822a2660dd'
_L0_CORRECTION_OLD_POLICY = '3d38ad7435b5f3393bef901bddef68b8c475db91'


def _l0_correction_before(repo, blob_id):
    if not _single_parent(repo, _L0_CORRECTION_BASE, _L0_BASE):
        raise QualityError("Frozen L0 correction parent differs")
    data = {name: repo.blob(_L0_CORRECTION_BASE, name) for name in _L0_PATHS}
    if (blob_id(data[HELPER]) != _L0_CORRECTION_OLD_HELPER
            or blob_id(data[POLICY]) != _L0_CORRECTION_OLD_POLICY):
        raise QualityError("Frozen L0 correction template differs")
    approved = _l0_approved(repo, data, blob_id)
    if _l0_state(repo, _L0_CORRECTION_BASE) != approved:
        raise QualityError("Frozen L0 correction joint endpoint differs")
    return approved


def _l0_correction_pin(data, blob_id):
    # The helper pin is reviewed executable controller code, not an approval-file/CLI input.
    # Derive the complete controller below; never trust a controller-declared after-ID.
    from quality_gate_policy import CI_COMPLETENESS_APPROVAL
    pin = object_id(CI_COMPLETENESS_APPROVAL["helper_after"])
    expected = dict(base=APPROVAL_BASE, workflow_before=_WORKFLOW_BEFORE,
                    workflow_after=_WORKFLOW_AFTER, policy_before=_POLICY_BEFORE,
                    helper_after=pin)
    if (CI_COMPLETENESS_APPROVAL != expected or pin == _L0_CORRECTION_OLD_HELPER
            or blob_id(data[HELPER]) != pin):
        raise QualityError("Correction controller/helper pin differs")
    return pin


def _l0_correction_origin(repo, head, states, before, original, corrected):
    root = _L0_CORRECTION_BASE
    if root not in states or not repo.ancestor(root, head) or states[root] != original:
        raise QualityError("Correction requires the exact immutable L0 origin")
    if head == root:
        return None
    origins = [commit for commit, state in states.items()
               if commit != root and _single_parent(repo, commit, root) and state == corrected]
    if len(origins) != 1:
        raise QualityError("Need one single-parent correction directly after frozen L0 origin")
    origin = origins[0]
    if not repo.ancestor(origin, head):
        raise QualityError("Correction origin is outside candidate ancestry")
    for commit, state in states.items():
        if repo.ancestor(origin, commit):
            expected = _l0_java_variant(repo, commit, corrected)
        elif repo.ancestor(root, commit):
            expected = _l0_java_variant(repo, commit, original)
        else:
            expected = before
        if state != expected:
            raise QualityError("Correction history modified, partial or restored")
    return origin


def _l0_reviewed_current(repo, head, states, before, data, blob_id):
    after = _l0_approved(repo, data, blob_id)
    if blob_id(data[HELPER]) == _L0_CORRECTION_OLD_HELPER:
        # Unchanged original pre-commit/committed source remains valid. A later restoration
        # cannot erase activation: the original full-history check rejects corrected states.
        return after, _l0_origin(repo, head, states, before, after), None
    _l0_correction_pin(data, blob_id)
    if _L0_CORRECTION_BASE not in states or not repo.ancestor(_L0_CORRECTION_BASE, head):
        raise QualityError("Correction activation requires the exact frozen L0 origin")
    original = _l0_correction_before(repo, blob_id)
    history = {commit: states[commit] for commit in _ancestry(repo, _L0_CORRECTION_BASE, _L0_BASE)}
    origin = _l0_origin(repo, _L0_CORRECTION_BASE, history, before, original)
    if origin != _L0_CORRECTION_BASE:
        raise QualityError("Original L0 origin differs from correction approval")
    correction = _l0_correction_origin(repo, head, states, before, original, after)
    return after, origin, correction


def _l0_origin(repo, head, states, before, after):
    if head == _L0_BASE:
        return None
    origins = [commit for commit, state in states.items()
               if _single_parent(repo, commit, _L0_BASE) and state == after]
    if len(origins) != 1:
        raise QualityError("Need one L0 controller origin directly after frozen09")
    origin = origins[0]
    for commit, state in states.items():
        expected = _l0_java_variant(repo, commit, after) if repo.ancestor(origin, commit) else before
        if state != expected:
            raise QualityError("L0 controller history modified, partial or restored")
    return origin


def _l0_java_variant(repo, head, after):
    from quality_gate_module_policy import verify_mainline_java
    if not verify_mainline_java(repo, head):
        return after
    result = dict(after)
    result[WORKFLOW] = _L0_JAVA_WORKFLOW_BLOB
    return result
