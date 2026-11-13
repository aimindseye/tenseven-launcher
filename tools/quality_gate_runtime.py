"""Runtime boundaries for the quality gate; CLI data is never a command schema.

Repository contents and the runtime environment are trusted build inputs. CLI
refs and output destinations are untrusted. Evidence writes stay on reserved
inodes, even when an attacker replaces a pathname between validation and use.
"""

import hashlib
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import tempfile


class QualityError(ValueError):
    """Input, policy or evidence cannot be qualified."""


OID = re.compile(r"[0-9a-f]{40}")
REF = re.compile(r"[A-Za-z0-9][A-Za-z0-9._/-]{0,254}")
ENTRY = re.compile(r"[A-Za-z0-9_][A-Za-z0-9_.-]{0,254}")
GIT = shutil.which("git")
DIRECTORY_FLAGS = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW


def object_id(value):
    if not OID.fullmatch(value):
        raise QualityError("Expected a full lowercase Git object ID")
    return value


def reference(value):
    if not REF.fullmatch(value) or any(part in value for part in ("..", "//")):
        raise QualityError("Invalid Git reference")
    if any(part.startswith(".") or part.endswith((".", ".lock")) for part in value.split("/")):
        raise QualityError("Invalid Git reference component")
    if value.endswith("/"):
        raise QualityError("Invalid Git reference terminator")
    return value


def repository_path(value):
    path = Path(value)
    if path.is_absolute() or not path.parts or any(part in {".", "..", ".git"} for part in path.parts):
        raise QualityError("Invalid repository-relative path")
    if any(ord(char) < 32 for char in value):
        raise QualityError("Control character in repository path")
    return path.as_posix()


class GitRepository:
    """Fixed Git operations; only resolve() accepts a CLI revision."""

    def __init__(self, path):
        self.path = Path(path).expanduser().resolve(strict=True)
        if not self.path.is_dir() or not GIT:
            raise QualityError("Expected repository directory and installed Git")
        root = self._read(["rev-parse", "--show-toplevel"]).decode().strip()
        if Path(root).resolve() != self.path:
            raise QualityError("--repo must name the repository root")

    def _run(self, arguments):
        return subprocess.run(
            [GIT, "--literal-pathspecs", "-C", str(self.path), *arguments],
            capture_output=True, check=False,
        )

    def _read(self, arguments):
        result = self._run(arguments)
        if result.returncode:
            raise QualityError("Git operation failed: " + result.stderr.decode(errors="replace"))
        return result.stdout

    def resolve(self, value):
        safe_ref = reference(value)
        output = self._read(["rev-parse", "--verify", "--end-of-options", safe_ref + "^{commit}"])
        return object_id(output.decode().strip())

    def head(self):
        return object_id(self._read(["rev-parse", "--verify", "HEAD^{commit}"]).decode().strip())

    def ancestor(self, older, newer):
        result = self._run(["merge-base", "--is-ancestor", object_id(older), object_id(newer)])
        if result.returncode not in {0, 1}:
            raise QualityError("Cannot establish commit ancestry")
        return result.returncode == 0

    def parent(self, commit):
        value = self._read(["rev-parse", "--verify", "--end-of-options", object_id(commit) + "^1"])
        return object_id(value.decode().strip())

    def blob(self, commit, filename):
        spec = object_id(commit) + ":" + repository_path(filename)
        return self._read(["show", "--no-ext-diff", "--no-textconv", spec, "--"])

    def blob_id(self, commit, filename):
        data = self.blob(commit, filename)
        return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data, usedforsecurity=False).hexdigest()

    def changed(self, base):
        tracked = self._read(["diff", "--no-ext-diff", "--no-textconv", "--no-renames",
                              "--name-only", "--diff-filter=ACMD", "-z", object_id(base), "--"])
        untracked = self._read(["ls-files", "--others", "--exclude-standard", "-z"])
        return {repository_path(p) for p in (tracked + b"\0" + untracked).decode().split("\0") if p}

    def files(self):
        raw = self._read(["ls-files", "--cached", "--others", "--exclude-standard", "-z"])
        return sorted({repository_path(p) for p in raw.decode().split("\0") if p})

    def tree_files(self, commit):
        raw = self._read(["ls-tree", "-r", "--name-only", "-z", object_id(commit)])
        return {repository_path(p) for p in raw.decode().split("\0") if p}

    def diff(self, base, filename):
        return self._read(["diff", "--no-ext-diff", "--no-textconv", "--unified=0",
                           object_id(base), "--", repository_path(filename)]).decode()

    def status(self):
        return self._read(["status", "--porcelain"]).decode()

    def branch(self):
        return self._read(["branch", "--show-current"]).decode().strip()

    def detached_worktree(self, destination, commit):
        # destination is generated inside a reserved evidence directory, never a CLI ref.
        path = Path(destination)
        if not path.is_absolute():
            raise QualityError("Worktree destination must be absolute")
        self._read(["worktree", "add", "--detach", "--", str(path), object_id(commit)])

    def line_origin(self, commit, filename, line):
        raw = self._read(["blame", "--line-porcelain", "-L", f"{int(line)},{int(line)}",
                          object_id(commit), "--", repository_path(filename)])
        return object_id(raw.decode().split()[0])


def _entry(value):
    if not ENTRY.fullmatch(value) or value in {".", ".."}:
        raise QualityError("Unsafe evidence entry name")
    return value


def trusted_roots():
    # Environment is supplied by the operator/CI runner, never by CLI flags.
    roots = [Path(tempfile.gettempdir()), Path("/tmp"), Path.home() / "Downloads"]
    if os.environ.get("RUNNER_TEMP"):
        roots.append(Path(os.environ["RUNNER_TEMP"]))
    return [(p.absolute(), p.resolve()) for p in roots if p.is_dir()]


def _relative_destination(requested, alias, canonical):
    for prefix in (alias, canonical):
        try:
            parts = requested.relative_to(prefix).parts
        except ValueError:
            continue
        if not parts:
            raise QualityError("Cannot use a trusted root itself as evidence")
        return tuple(_entry(part) for part in parts)
    return None


def evidence_destination(value, repo, roots=None):
    raw = os.fspath(value)
    if any(part in {".", ".."} for part in raw.split("/")) or "\0" in raw:
        raise QualityError("Traversal in evidence destination")
    requested = Path(raw).expanduser()
    if not requested.is_absolute():
        raise QualityError("Evidence destination must be absolute")
    for alias, canonical in roots if roots is not None else trusted_roots():
        parts = _relative_destination(requested, alias, canonical)
        if parts is None:
            continue
        destination = canonical.joinpath(*parts)
        if destination == repo or repo in destination.parents or destination in repo.parents:
            raise QualityError("Evidence must be disjoint from repository")
        return canonical, parts
    raise QualityError("Evidence must be under runtime temp, RUNNER_TEMP or Downloads")


def _open_chain(root, components):
    descriptor = os.open(root, DIRECTORY_FLAGS)
    try:
        for part in components:
            next_fd = os.open(part, DIRECTORY_FLAGS, dir_fd=descriptor)
            os.close(descriptor)
            descriptor = next_fd
        return descriptor
    except BaseException:
        os.close(descriptor)
        raise


def _make_directory(parent, name, exclusive):
    try:
        os.mkdir(name, mode=0o700, dir_fd=parent)
    except FileExistsError:
        if exclusive:
            raise QualityError("Evidence destination already exists") from None
    descriptor = os.open(name, DIRECTORY_FLAGS, dir_fd=parent)
    metadata = os.fstat(descriptor)
    if metadata.st_uid != os.geteuid() or stat.S_IMODE(metadata.st_mode) & 0o022:
        os.close(descriptor)
        raise QualityError("Evidence parent is not privately controlled by the current user")
    return descriptor


class EvidenceDirectory:
    """Private, descriptor-relative evidence storage, with no pathname writes."""

    def __init__(self, root, components, descriptor):
        self.root, self.components, self.fd = root, components, descriptor
        self.path = root.joinpath(*components)

    @classmethod
    def reserve(cls, value, repo, roots=None):
        root, parts = evidence_destination(value, repo, roots)
        parent = os.open(root, DIRECTORY_FLAGS)
        try:
            for index, part in enumerate(parts):
                child = _make_directory(parent, part, exclusive=index == len(parts) - 1)
                os.close(parent)
                parent = child
            evidence = cls(root, parts, parent)
            evidence.assert_linked()
            return evidence
        except BaseException:
            os.close(parent)
            raise

    def assert_linked(self):
        current = _open_chain(self.root, self.components)
        try:
            expected, actual = os.fstat(self.fd), os.fstat(current)
            if (expected.st_dev, expected.st_ino) != (actual.st_dev, actual.st_ino):
                raise QualityError("Evidence directory was replaced")
        finally:
            os.close(current)

    def child(self, name):
        self.assert_linked()
        child = _make_directory(self.fd, _entry(name), exclusive=True)
        return EvidenceDirectory(self.root, (*self.components, name), child)

    def open_file(self, name):
        self.assert_linked()
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW
        descriptor = os.open(_entry(name), flags, mode=0o600, dir_fd=self.fd)
        try:
            self.assert_linked()
        except BaseException:
            os.close(descriptor)
            raise
        return os.fdopen(descriptor, "wb")

    def write(self, name, data):
        with self.open_file(name) as stream:
            stream.write(data)
        self.assert_linked()

    def archive(self, filename, data):
        parts = Path(repository_path(filename)).parts
        directory = self
        opened = []
        try:
            for part in parts[:-1]:
                child = _make_directory(directory.fd, _entry(part), exclusive=False)
                directory = EvidenceDirectory(self.root, (*directory.components, part), child)
                opened.append(directory)
            directory.write(parts[-1], data)
        finally:
            for child in opened:
                child.close()

    def close(self):
        if self.fd is not None:
            os.close(self.fd)
            self.fd = None
