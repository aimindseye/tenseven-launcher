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
    state = {path: None for path in PATHS}
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
