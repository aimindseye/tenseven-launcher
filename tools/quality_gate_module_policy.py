"""Exact L0 module transition. REVIEW ONLY until independently approved.

No filename, report-identity or suppression exemptions. Security/ancestry
verification is performed even for fixture-only descendants after activation.
"""
import hashlib

from quality_gate_runtime import QualityError, object_id, GitRepository
import quality_gate_policy as legacy

BASE = '09d331d9f9b278ba1bed1323056cf1504e456629'
ENDPOINTS = {'app/build.gradle.kts': {'before': 'db99f595caca92cb53f482027566a3213c77ded0',
                          'after': 'b8f948739015e056192c72ac2206094b566307da',
                          'sha256': '1368a05cbe3f61273d2c41858cd01eb22f405c7640aa2d4ccab31250663109c1'},
 'design/build.gradle.kts': {'before': '006c56ca9aee276ca4cf313854c486119619dd6a',
                             'after': 'a27790ac93f21e7888f5291e38c6470f01576650',
                             'sha256': 'fb1c30eef9f69b9e429b5ecff52864f8c0d325b79c844be83517095e3d1bd705'},
 'settings.gradle.kts': {'before': '25a51698572271631a2aff287675f712675136ca',
                         'after': '9925159a850d4286544f5197934f9f0f6c474933',
                         'sha256': '35207182e9f05ea9a00531ad5e7644d2bad25b2378e824f5233257b5f9eaf6de'},
 'phone/build.gradle.kts': {'before': None,
                            'after': '6b3bbe077fb2479637b4284dd122596d8e073f57',
                            'sha256': '1cb4b53e999053f18f2431af4192bf9e77b6789b7fc7ff0e643bb96532f39ca3'},
 'calendar/build.gradle.kts': {'before': None,
                               'after': '60876b15bfa147987425452fa754de115667ef79',
                               'sha256': 'eb518346c5bc54c525ab9de829c900d727bb5562e8e331a0f41f3ad096d5d472'},
 '.github/workflows/android.yml': {'before': '087a212a14b163f0b6e276864bf1930c865e4df6',
                                   'after': '33339730ac60aabe9930e8e2188066e787a1de9a',
                                   'sha256': '3e46667f3fa80b0d4d9df92c2182cd191e8fe09e73cf93395c350b19992dcf47'},
 '.github/workflows/codeql.yml': {'before': '3f8f87b29da151abedf84fdff2c6700228b7e6b1',
                                  'after': 'f6a667063e0e1833df4e52356d6416b95551dc5d',
                                  'sha256': 'a4966f44ade190b7336da9fd14dcd5737400dc7bbb3aea0e52110870cd82bcb3'},
 '.github/workflows/frames-ui.yml': {'before': 'c7ed407a80207fc5904a494bd8c2992387a42223',
                                     'after': '9077929d2f37a4df35505fdef85d1a6f1ce8a38f',
                                     'sha256': '9bc6003f5b2866e529dfe36c3a70d5941ee2fe5737d6125a45a78806e6ec7215'},
 '.github/workflows/p2-quality.yml': {'before': 'b5a34516b89a7654d73d2c4f4689142cdf92410f',
                                      'after': '28650de716f37b5a0da9a261f5102bc06bea3e56',
                                      'sha256': 'e025ee124b36cfe8447b2233d7f24c2a3dd3539c0789bbeb6ba607af3e2f8f09'},
 '.github/workflows/security.yml': {'before': '5017e084a6621991f943577b93a6ded367259de2',
                                    'after': 'c36a5da44c0a1ead9b952e96e36543d9087df840',
                                    'sha256': 'ee9d9d5ac59bcb88722957860c169f286a3c78a5686961894234a6783a151993'},
 '.github/workflows/sonar.yml': {'before': '8379ce2767b896db4c5e978c185a22fcaf530c98',
                                 'after': '1ff342b97e34054f045eb8cad2f1a2e061779c89',
                                 'sha256': '69510555123e9838b1844d8beb132f607da186433ac56496dadd0dec2dad8657'},
 'sonar-project.properties': {'before': 'd4fd9e36a739c3d533a2dc972c19f37404bbcdbe',
                              'after': '62f584bb2dbed2d48fc8c8407ed0ee47b98483e7',
                              'sha256': 'fb77376935ab94044de126a8b26a2e2c49de9bd98e01d5c56a7611508c0e414a'},
 'phone/src/main/java/io/github/aimindseye/tenseven/features/phone/TenSevenTelecom.kt': {'before': None,
                                                                                         'after': '8ad78380f3444840f4a2a347fb4bd19225f9c1ec',
                                                                                         'sha256': '260f477d149d3fe3caa4eea8311a9d58b2551dffc9170a4f9821365b326ee24e'}}
ANCHORS = {'.editorconfig': 'e02a4ece681bd31785db9618c566295d6b5a332c',
 'gradle/wrapper/gradle-wrapper.properties': 'fad1ca746fdabbc0bf94fb9e2cff33e0dfb6d8bd',
 'gradle.properties': '57bb06febd33fe1c780dc5108a0c7d7f9b3e312e',
 'gradle/libs.versions.toml': 'cef0f4dde1afc5d33114b5701ed13752f425a783',
 'config/detekt.yml': '46ec82af4af1b79a4357bf0a8608538a549b1aaf',
 'gradle/wrapper/gradle-wrapper.jar': '9fa7f288283f81eec4952784c271abd7e0938dee',
 'config/DETEKT_PROVENANCE.env': '1953f85ea317eec38da2cb51dac037832e878417',
 'build.gradle.kts': 'b10fa8e2e47f7dc42e77813bd3679ab44bced292',
 'config/detekt-tenseven.yml': '350cdacb05c194df6ed294ed20d9d017c69076d2',
 '.github/workflows/checks.yml': '81b81ec453580c7fc927b03d3e95011f986ad12c'}
FIXTURES = {'app/src/test/java/io/github/aimindseye/tenseven/features/AndroidViewFixtures.kt': '6ce24b549dee98604198d0e829c73e0639257c15cfb66be33b86416af971fd28',
 'app/src/test/java/io/github/aimindseye/tenseven/features/calendar/CalendarNavigationBehaviorTest.kt': '90216fd399319fd465667c33eecce4957d78c0f0d6835fe68d4cc30ef7ef6577',
 'app/src/test/java/io/github/aimindseye/tenseven/features/calendar/CalendarOptionsBehaviorTest.kt': '61f36315c6a7f0615276f4194489157c8361230f02c7b8d760bcc9978904180f',
 'app/src/test/java/io/github/aimindseye/tenseven/features/calendar/CalendarScheduleBehaviorTest.kt': '3c418ae7043dc917271e3a7d68935199d8997e6cc78b3b4df07a40d77dd9ce63',
 'app/src/test/java/io/github/aimindseye/tenseven/features/calendar/CalendarViewBehaviorTest.kt': 'e5545c4ef16d66d22e05cf96c18b8ee004661e6bbdb227b019f47bcf10a5522c',
 'app/src/test/java/io/github/aimindseye/tenseven/features/phone/PhoneBlocklistBehaviorTest.kt': '5a92fe700f293063ab02ba87f5833103584d6b264ca11bc1f7adb2cf533d8355',
 'app/src/test/java/io/github/aimindseye/tenseven/features/phone/PhoneContactsBehaviorTest.kt': '1bd8cc7e107a578aefca002501059ac18a07a9d75217fdf6e46ee819f7886726',
 'app/src/test/java/io/github/aimindseye/tenseven/features/phone/PhoneDialBehaviorTest.kt': 'a4a12691e6aa6debda08acb210452c3e101efc9fe2bd7284377c56bda0bea46b',
 'app/src/test/java/io/github/aimindseye/tenseven/features/phone/PhoneFixtureRecords.kt': '82987db9c73491d42dc04f90264b647b1ebc25d3a664f7a06792902706db5f97',
 'app/src/test/java/io/github/aimindseye/tenseven/features/phone/PhonePermissionBehaviorTest.kt': '621df2ece316aaf72c0108fafab74d8f733a10721195676e36771213a2470f4d',
 'app/src/test/java/io/github/aimindseye/tenseven/features/phone/PhoneRecentsBehaviorTest.kt': '76913543cc9e77691ea0aeef3bef2f81b18c8bbeb3cfe0d8f627fb10c1c374c4'}
COPIES = {'phone/src/test/java/io/github/aimindseye/tenseven/features/AndroidViewFixtures.kt': '6ce24b549dee98604198d0e829c73e0639257c15cfb66be33b86416af971fd28',
 'calendar/src/test/java/io/github/aimindseye/tenseven/features/AndroidViewFixtures.kt': '6ce24b549dee98604198d0e829c73e0639257c15cfb66be33b86416af971fd28'}
MODULES = {'phone', 'design', 'calendar', 'app'}
PHONE_CONTRACT = {'before_path': 'app/src/main/java/io/github/aimindseye/tenseven/features/phone/TenSevenTelecom.kt',
 'after_path': 'phone/src/main/java/io/github/aimindseye/tenseven/features/phone/TenSevenTelecom.kt',
 'before_blob': '60a113f13c46c9088c2ff7fdf7c077b526b15be3',
 'method_sha256': '7a84890369ed0850da01c102bbe0f72a641d9c18c1d271c07e0aeafae7cd05b8',
 'method': '    @SuppressLint("MissingPermission")\n'
           '    private fun silenceIncomingRinger() {\n'
           '        val role = getSystemService(RoleManager::class.java)\n'
           '        if (role?.isRoleHeld(RoleManager.ROLE_DIALER) != true) {\n'
           '            Toast.makeText(this, "Default Phone role required", '
           'Toast.LENGTH_SHORT).show()\n'
           '            return\n'
           '        }\n'
           '        try {\n'
           '            '
           'getSystemService(android.telecom.TelecomManager::class.java)?.silenceRinger()\n'
           '        } catch (_: SecurityException) {\n'
           '            Toast.makeText(this, "Default Phone role required", '
           'Toast.LENGTH_SHORT).show()\n'
           '        }\n'
           '    }\n',
 'source_bytes_identical': True,
 'risk': 'Phone/Telecom permission contract; default-role guard and SecurityException handling '
         'retained; no privileged permission added',
 'actual_original_annotation_origin': 'c42fb4b221e2ede2dce35315eca24de413b75077',
 'before_line': 498}
MAINLINE_ORIGIN = '2ff9bfe500a131ebd0c1d832790520041f119084'
MAINLINE_PARENT = 'b27162819a1611fc0abae8c389deb54cb9352b44'
MAINLINE_PROOF = {'.github/workflows/android.yml': ('0b33ee891bb312738e5691707777edffcc45d212',
                                   '8992682ff04b44a8fb5703acb699eab6f62d5b97'),
 '.github/workflows/codeql.yml': ('3f8f87b29da151abedf84fdff2c6700228b7e6b1',
                                  '1774f5e617f1475925cc6abfbd30ad30770318c3'),
 '.github/workflows/frames-ui.yml': ('c7ed407a80207fc5904a494bd8c2992387a42223',
                                     'ded393cb98cdb0f1322d887eb66a75d6a317eb8b'),
 '.github/workflows/p2-quality.yml': ('b5a34516b89a7654d73d2c4f4689142cdf92410f',
                                      '3c28cfd2e081875bc315a9843c30d36b764329e6'),
 '.github/workflows/sonar.yml': ('8379ce2767b896db4c5e978c185a22fcaf530c98',
                                 '218b23c3ab3bae1b952ca3061774ff9f9c3a190b')}
MAINLINE_VARIANTS = {'.github/workflows/android.yml': '53d88c20accba33a9874145b394b795688fd2e22',
 '.github/workflows/codeql.yml': '6b5001384e1a3424b8e1704195779d904cec7fff',
 '.github/workflows/frames-ui.yml': '308b4171817cc84aabf61d9f78d54cc260ee87b9',
 '.github/workflows/p2-quality.yml': '8d149ca364be3fb441d2ce3ff4153d1daa4a0244',
 '.github/workflows/sonar.yml': 'c957711e4297c58f841f79f79747790aef21f163'}


def state(repo, commit):
    names = set(ENDPOINTS) | set(ANCHORS) | set(FIXTURES) | set(COPIES)
    raw = repo._read(["ls-tree", "-r", "-z", object_id(commit), "--", *sorted(names)])
    result = dict.fromkeys(names)
    for item in raw.split(b"\0"):
        if not item:
            continue
        metadata, name = item.decode().split("\t", 1)
        mode, kind, identifier = metadata.split()
        if mode != "100644" or kind != "blob" or name not in result:
            raise QualityError("Unexpected approved policy/fixture mode or type")
        result[name] = object_id(identifier)
    return result


def expected(repo, after):
    result = {name: pair["after" if after else "before"] for name, pair in ENDPOINTS.items()}
    result.update(ANCHORS)
    for name, digest in FIXTURES.items():
        original = repo.blob(BASE, name)
        if hashlib.sha256(original).hexdigest() != digest:
            raise QualityError("Frozen fixture endpoint differs")
        result[name] = legacy.blob_id(original)
    for name, digest in COPIES.items():
        raw = repo.blob(BASE, next(iter(FIXTURES)))
        if hashlib.sha256(raw).hexdigest() != digest:
            raise QualityError("Effective fixture source differs")
        result[name] = legacy.blob_id(raw) if after else None
    return result


def commits(repo, head):
    if not repo.ancestor(BASE, head):
        raise QualityError("L0 base is outside candidate ancestry")
    revision = BASE + ".." + object_id(head)
    values = repo._read(["rev-list", "--ancestry-path", "--reverse", revision, "--"]).decode().splitlines()
    return [BASE, *(object_id(value) for value in values)]


def direct(repo, commit):
    values = repo._read(["rev-list", "--parents", "-n", "1", object_id(commit), "--"]).decode().split()
    return values == [commit, BASE]


def history(repo, head, before, after):
    states = {commit: state(repo, commit) for commit in commits(repo, head)}
    if states[BASE] != before:
        raise QualityError("Immutable L0 base differs")
    if head == BASE:
        return None
    origins = [commit for commit, value in states.items() if direct(repo, commit) and value == after]
    if len(origins) != 1:
        raise QualityError("Need one single-parent joint origin directly after L0 base")
    origin = origins[0]
    if not repo.ancestor(origin, head):
        raise QualityError("Origin is outside candidate history")
    for commit, value in states.items():
        expected_state = java_variant(repo, commit, after) if repo.ancestor(origin, commit) else before
        if value != expected_state:
            raise QualityError("Policy or fixture history modified, partial or restored")
    return origin


def verify(repo, base):
    """Validate exact current bytes and every before/after ancestry state."""
    from quality_gate_robolectric_policy import active as dependency_active, verify as verify_dependency
    if dependency_active(repo):
        return verify_dependency(repo, base)
    head = repo.head()
    base = repo.resolve(base)
    if not repo.ancestor(base, head):
        raise QualityError("Comparison base is outside ancestry")
    before, after = expected(repo, False), expected(repo, True)
    # Verify approved original R2.1 history all the way to the frozen L0 parent.
    harness_origin = legacy._harness_origin(repo, BASE)
    from quality_gate_ci_policy import verify_l0_controller, _L0_PATHS
    verify_workflow_inventory(repo)
    names = set(after) | set(_L0_PATHS)
    with legacy._source_snapshot(repo, names) as data:
        controller = verify_l0_controller(repo, base, legacy._source_snapshot, legacy.blob_id)
        current = {name: legacy.blob_id(data[name]) for name in after}
        if current != java_variant(repo, head, after):
            raise QualityError("Unknown, partial or modified joint endpoint/fixture")
        modules = {item.parent.name for item in repo.path.glob("*/build.gradle.kts")}
        if modules != MODULES:
            raise QualityError("Unapproved or missing Android module")
        origin = history(repo, head, before, after)
    if controller["origin"] != origin:
        raise QualityError("Module/controller origins differ")
    verify_workflow_inventory(repo)
    if {item.parent.name for item in repo.path.glob("*/build.gradle.kts")} != MODULES:
        raise QualityError("Module inventory changed during verification")
    if repo.head() != head:
        raise QualityError("Candidate HEAD changed")
    return {"status": "VERIFIED_EXACT_MODULE_TRANSITION", "base": BASE,
            "origin": origin, "verification_mode": "pre_commit" if origin is None else "immutable_origin",
            "r21_origin": harness_origin, "modules": sorted(MODULES)}



def mainline_active(repo, head):
    ancestors = set(repo._read(["rev-list", object_id(head), "--"]).decode().splitlines())
    return MAINLINE_ORIGIN in ancestors



def verify_mainline_java(repo, head):
    if not mainline_active(repo, head):
        return False
    values = repo._read(["rev-list", "--parents", "-n", "1", MAINLINE_ORIGIN, "--"]).decode().split()
    if values != [MAINLINE_ORIGIN, MAINLINE_PARENT]:
        raise QualityError("Mainline action update parent differs")
    for name, pair in MAINLINE_PROOF.items():
        if (repo.blob_id(MAINLINE_PARENT, name), repo.blob_id(MAINLINE_ORIGIN, name)) != pair:
            raise QualityError("Mainline action update endpoint differs")
    return True



def java_variant(repo, head, after):
    if not verify_mainline_java(repo, head):
        return after
    result = dict(after)
    result.update(MAINLINE_VARIANTS)
    return result


def verify_workflow_inventory(repo):
    expected = {name for name in ENDPOINTS.keys() | ANCHORS.keys() if name.startswith(".github/workflows/")}
    current = {name for name in repo.files() if name.startswith(".github/workflows/")}
    if current != expected:
        raise QualityError("Workflow inventory changed without an exact approval")


def active(repo):
    head = repo.head()
    known = repo.files()
    current = "tools/quality_gate_module_policy.py" in known
    ancestors = set(repo._read(["rev-list", object_id(head), "--"]).decode().splitlines())
    historical = False
    if BASE in ancestors:
        for commit in commits(repo, head):
            value = repo._read(["ls-tree", object_id(commit), "--", "tools/quality_gate_module_policy.py"])
            historical = historical or bool(value)
    if repo.head() != head:
        raise QualityError("HEAD changed while checking module activation")
    return current or historical



def modules(root):
    repo = GitRepository(root)
    names = {item.parent.name for item in root.glob("*/build.gradle.kts")}
    if active(repo):
        verify(repo, repo.head())
        return sorted(MODULES)
    if names != {"app", "design"}:
        raise QualityError("Module inventory changed without the exact L0 transition")
    return ["app", "design"]



def historical_records(repo, base, changed):
    verification = verify(repo, base)
    records = []
    for transition in legacy.TRANSITIONS:
        if transition.path not in changed:
            continue
        if (repo.blob_id(repo.parent(transition.origin), transition.path),
                repo.blob_id(transition.origin, transition.path)) != (transition.before, transition.after):
            raise QualityError("Historical transition endpoints differ")
        if not repo.ancestor(transition.origin, BASE):
            raise QualityError("Historical origin is outside frozen L0 parent")
        if repo.blob_id(base, transition.path) not in {
                transition.before, transition.after,
                ENDPOINTS.get(transition.path, {}).get("after")}:
            raise QualityError("Unknown historical comparison endpoint")
        records.append(legacy.asdict(transition))
    records.extend(harness_records(repo, base, verification))
    allowed = set(ENDPOINTS) | set(legacy.TEST_HARNESS_ENDPOINTS) | {t.path for t in legacy.TRANSITIONS}
    protected = changed & (legacy.QUALITY_CONFIG_FILES | set(ENDPOINTS))
    if protected - allowed:
        raise QualityError("Unapproved protected L0 configuration")
    workflows = {name for name in changed if name.startswith(".github/workflows/")}
    if workflows - set(ENDPOINTS):
        raise QualityError("Unapproved workflow transition")
    return records



def harness_records(repo, base, verified=None):
    verified = verify(repo, base) if verified is None else verified
    records = []
    for name, pair in legacy.TEST_HARNESS_ENDPOINTS.items():
        current = ENDPOINTS.get(name, {}).get("after", pair[1])
        current = verified.get("dependency_endpoints", {}).get(name, current)
        previous = repo.blob_id(base, name)
        if previous not in {pair[0], pair[1], current}:
            raise QualityError("Unknown R2.1 comparison endpoint")
        records.append(dict(path=name, before=pair[0], after=current,
            original_after=pair[1], origin=verified["r21_origin"],
            origin_parent=legacy.TEST_HARNESS_BASE, comparison_base=base,
            comparison_base_blob=previous, verification_mode="immutable_origin",
            module_origin=verified["origin"], approval="Original R2.1 plus exact L0 source-set transition"))
        if name in verified.get("dependency_endpoints", {}):
            records[-1].update(
                dependency_origin=verified["dependency_origin"],
                dependency_parent=verified["dependency_parent"],
                dependency_verification_mode=verified["dependency_verification_mode"],
                approval="Exact reviewed Robolectric 4.18.1 extension; original R2.1 and L0 origins retained",
                analysis_effect="Test-only 4.18 -> 4.18.1; no analyzer overlay")
    return records



def analysis(repo, parent):
    verified = verify(repo, parent.head())
    records = {item["path"]: item for item in harness_records(repo, parent.head(), verified)}
    for name in ("app/build.gradle.kts", "design/build.gradle.kts", "settings.gradle.kts"):
        previous = parent.blob_id(parent.head(), name)
        if name not in records:
            pair = ENDPOINTS[name]
            if previous not in {pair["before"], pair["after"]}:
                raise QualityError("Unknown L0 analysis input")
            records[name] = dict(path=name, comparison_base_blob=previous,
                                 after=pair["after"], origin=verified["origin"])
    result = {}
    for name, record in records.items():
        result[name] = legacy._harness_analysis_record(
            record, legacy.source_bytes(parent, name), legacy.source_bytes(repo, name))
    return result



def review_permission_contract(repo, base, audit):
    verified = verify(repo, base)
    contract = PHONE_CONTRACT
    original = repo.blob(BASE, contract["before_path"])
    method = contract["method"].encode()
    if (legacy.blob_id(original) != contract["before_blob"]
            or hashlib.sha256(method).hexdigest() != contract["method_sha256"]
            or original.count(method) != 1):
        raise QualityError("Frozen Phone role contract differs")
    if repo.line_origin(BASE, contract["before_path"], contract["before_line"]) != contract["actual_original_annotation_origin"]:
        raise QualityError("Original Phone annotation provenance differs")
    path = contract["after_path"]
    with legacy._source_snapshot(repo, {path}) as data:
        if (legacy.blob_id(data[path]) != ENDPOINTS[path]["after"]
                or data[path].count(method) != 1):
            raise QualityError("Reviewed Phone source/role contract differs")
    marker = '    @SuppressLint("MissingPermission")'
    approved, pending = [], []
    for item in audit:
        if (item["file"] == path and item["line"] == contract["before_line"]
                and item["text"] == marker
                and item["classification"] == "permission-contract-review-required"):
            approved.append({**item, "approval": "L0 exact Phone role-contract relocation",
                "result": "EXACT_REVIEWED_CONTRACT", "original_source_commit": BASE,
                "original_annotation_commit": contract["actual_original_annotation_origin"],
                "original_blob": contract["before_blob"], "candidate_blob": ENDPOINTS[path]["after"],
                "method_sha256": contract["method_sha256"], "module_origin": verified["origin"]})
        else:
            pending.append(item)
    if len(approved) > 1:
        raise QualityError("Duplicate reviewed Phone marker")
    return pending, approved
