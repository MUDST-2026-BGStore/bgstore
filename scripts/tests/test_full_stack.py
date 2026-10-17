"""Exercise the real harness with disposable command doubles, without Docker."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

REPO = Path(__file__).resolve().parents[2]


class FullStackTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="bgstore scripts ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        shutil.copytree(REPO / "scripts", self.root / "scripts",
                        ignore=shutil.ignore_patterns("__pycache__"))
        shutil.copytree(REPO / ".agents/bin", self.root / ".agents/bin")
        realm = self.root / "infra/local/keycloak/bgstore-realm.json"
        realm.parent.mkdir(parents=True)
        shutil.copyfile(REPO / "infra/local/keycloak/bgstore-realm.json", realm)
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        (bin_dir / "bash").symlink_to("/bin/bash")
        stub = bin_dir / "stub"
        stub.write_text('''#!/usr/bin/env python3
import json, os, pathlib, sys
command = pathlib.Path(sys.argv[0]).name
args = sys.argv[1:]
with open(os.environ["CALLS"], "a") as log:
    log.write(json.dumps([command, *args]) + "\\n")
if command in ("timeout", "realpath"):
    raise AssertionError("GNU tools must not be required")
if command == "docker":
    if "up" in args:
        realm = pathlib.Path(os.environ["BGSTORE_E2E_REALM_FILE"])
        assert realm.is_relative_to(pathlib.Path.cwd()), realm
        data = json.loads(realm.read_text())
        client = next(c for c in data["clients"] if c["clientId"] == "bgstore-web")
        assert client["baseUrl"] == os.environ["BASE_URL"]
        assert data["attributes"]["bgstoreAppUrl"] == os.environ["BASE_URL"]
        assert client["redirectUris"] == [os.environ["BASE_URL"] + "/auth/callback/bgstore"]
        assert client["attributes"]["post.logout.redirect.uris"] == os.environ["BASE_URL"] + "/"
        sys.exit(int(os.environ.get("UP_STATUS", "0")))
    if "ps" in args and "-q" in args:
        print("api-container")
    if "cp" in args and any(a.startswith("api:") for a in args):
        pathlib.Path(args[-1]).write_text("coverage fixture")
if command == "pnpm":
    sys.exit(int(os.environ.get("PLAYWRIGHT_STATUS", "0")))
''')
        stub.chmod(0o755)
        for name in ("docker", "pnpm", "timeout", "realpath"):
            (bin_dir / name).symlink_to(stub)
        gradlew = self.root / "apps/api/gradlew"
        gradlew.parent.mkdir(parents=True)
        gradlew.symlink_to(stub)
        self.calls_file = self.root / "calls.jsonl"
        self.env = dict(os.environ, PATH=f"{bin_dir}:{os.environ['PATH']}",
                        CALLS=str(self.calls_file), TMPDIR="/unshared-macos-temp",
                        XDG_CACHE_HOME=str(self.root / "cache"))

    def run_harness(self, status=0, entry="scripts/full-stack-e2e", up_status=0):
        self.env.update(PLAYWRIGHT_STATUS=str(status), UP_STATUS=str(up_status))
        # /bin/bash is Apple's stock 3.2 on macOS, and also works on Linux.
        result = subprocess.run(["/bin/bash", str(self.root / entry), "--grep=staff checkout"],
                                cwd="/", env=self.env, capture_output=True, text=True, timeout=20)
        self.assertEqual(result.returncode, up_status or status, result.stdout + result.stderr)
        calls = [json.loads(line) for line in self.calls_file.read_text().splitlines()]
        self.assertTrue(any(c[0] == "docker" and "down" in c and "--volumes" in c for c in calls))
        runtime = self.root / "apps/web-e2e/test-output/runtime"
        self.assertFalse(runtime.exists() and any(runtime.iterdir()))
        return calls

    def test_success_with_unshared_tmpdir_and_stock_bash(self):
        calls = self.run_harness()
        self.assertIn(["pnpm", "e2e", "--grep=staff checkout"], calls)
        self.assertTrue(any(c[0] == "gradlew" and "jacocoE2eReport" in c for c in calls))

    def test_playwright_failure_still_cleans_up(self):
        self.run_harness(status=17)

    def test_startup_failure_still_cleans_up(self):
        calls = self.run_harness(up_status=23)
        self.assertFalse(any(c[0] == "pnpm" for c in calls))

    def test_legacy_full_stack_entry(self):
        self.run_harness(entry=".agents/bin/full-stack-e2e")

    def test_legacy_verify_forwards_arguments_and_exit_status(self):
        result = subprocess.run(["/bin/bash", str(self.root / ".agents/bin/verify"),
                                 "full-stack", "--grep=staff checkout"],
                                env=dict(self.env, PLAYWRIGHT_STATUS="17"),
                                cwd="/", capture_output=True, text=True, timeout=20)
        self.assertEqual(result.returncode, 17, result.stdout + result.stderr)
        calls = [json.loads(line) for line in self.calls_file.read_text().splitlines()]
        self.assertIn(["pnpm", "e2e", "--grep=staff checkout"], calls)


if __name__ == "__main__":
    unittest.main()
