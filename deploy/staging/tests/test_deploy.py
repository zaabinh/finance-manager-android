"""Run with python -m unittest discover -s deploy/staging/tests -v (Bash required)."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


SHA = "a" * 40
PREVIOUS = "b" * 40
SOURCE = Path(__file__).resolve().parents[1] / "deploy.sh"
BASH = os.environ.get("TEST_BASH") or shutil.which("bash")

# This executable records every Docker operation and models only the deployment contract.
DOCKER = r'''#!/usr/bin/env bash
set -eu
printf '%s\n' "$*" >> "$TEST_STATE/commands"
if [[ "$1" == pull ]]; then
    [[ "${FAIL_PULL:-0}" != 1 ]]; exit
fi
if [[ "$1 $2" == 'image inspect' ]]; then
    if [[ "$*" == *finance-manager-backend:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb* && "${FAIL_PREVIOUS:-0}" == 1 ]]; then exit 1; fi
    if [[ "$*" == *org.opencontainers.image.revision* ]]; then
        printf '%s\n' "${REVISION:-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa}"
    else
        printf '%s\n' sha256:fixture
    fi
    exit
fi
if [[ "$1" == tag ]]; then exit; fi
[[ "$1" == compose ]] || exit 90
shift 5
tag=$(sed -n 's/^BACKEND_TAG=//p' .env)
case "$1" in
    config) [[ "${FAIL_CONFIG:-0}" != 1 ]] ;;
    up)
        printf '%s\n' "$tag" >> "$TEST_STATE/started"
        if [[ "$tag" == aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa ]]; then
            [[ "${FAIL_UP:-0}" != 1 ]]
        else
            [[ "${FAIL_ROLLBACK:-0}" != 1 ]]
        fi ;;
    exec)
        if [[ "$tag" == aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa && "${FAIL_READY:-0}" == 1 ]]; then exit 1; fi
        printf '%s\n' '{"status":"UP"}' ;;
    logs) printf '%s\n' 'fixture backend log' ;;
    *) exit 91 ;;
esac
'''


@unittest.skipUnless(BASH, "Bash is required")
class DeployTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="staging-deploy-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "releases").mkdir()
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.env_file = self.root / ".env"
        self.initial_env = f"BACKEND_TAG={PREVIOUS}\nDATABASE_PASSWORD=untouched\n"
        self.env_file.write_text(self.initial_env, encoding="utf-8")
        (self.root / "compose.yaml").write_text("# fixture\n", encoding="utf-8")
        source = SOURCE.read_text(encoding="utf-8")
        self.assertEqual(source.count("DIR=/srv/dong"), 1)
        # Only relocate the fixed server directory; execute all production control flow.
        self.script = self.root / "deploy.sh"
        self.script.write_text(source.replace("DIR=/srv/dong", f"DIR='{self.root.as_posix()}'"), encoding="utf-8")
        for name, content in {"docker": DOCKER, "sleep": "#!/usr/bin/env bash\nexit 0\n"}.items():
            path = self.bin / name
            path.write_text(content, encoding="utf-8")
            path.chmod(0o755)
        self.env = dict(os.environ, TEST_STATE=self.root.as_posix())
        self.env["PATH"] = os.pathsep.join([str(self.bin), str(Path(BASH).parent), self.env.get("PATH", "")])

    def deploy(self, sha=SHA, **settings):
        result = subprocess.run([BASH, str(self.script), sha], env=dict(self.env, **settings),
                                capture_output=True, text=True, timeout=90)
        self.output = result.stdout + result.stderr
        self.commands = (self.root / "commands").read_text() if (self.root / "commands").exists() else ""
        # No test scenario may touch database containers or volumes.
        for line in self.commands.splitlines():
            self.assertNotIn(" down", line)
            self.assertNotIn("volume", line)
            if " up " in line:
                self.assertTrue(line.endswith("up -d --no-deps --no-build --pull never backend"), line)
        return result.returncode

    def assert_unchanged(self):
        self.assertEqual(self.env_file.read_text(), self.initial_env)

    def test_success_checks_image_and_recreates_only_backend(self):
        self.assertEqual(self.deploy(), 0, self.output)
        self.assertEqual(self.env_file.read_text(), self.initial_env.replace(PREVIOUS, SHA))
        self.assertIn(f"pull -q ghcr.io/zaabinh/finance-manager-backend:{SHA}", self.commands)
        self.assertIn("org.opencontainers.image.revision", self.commands)
        self.assertIn(f"tag ghcr.io/zaabinh/finance-manager-backend:{SHA} finance-manager-backend:{SHA}", self.commands)
        self.assertEqual((self.root / "started").read_text().splitlines(), [SHA])

    def test_invalid_sha_has_no_side_effects(self):
        for value in ["", "main", "A" * 40, "a" * 39, SHA + ";id"]:
            with self.subTest(value=value):
                self.assertNotEqual(self.deploy(value), 0)
                self.assert_unchanged()
                self.assertEqual(self.commands, "")
                self.assertFalse((self.root / "releases" / "deploy.log").exists())

    def test_invalid_backend_tag_configuration_is_rejected_before_pull(self):
        for content in ["DATABASE_PASSWORD=untouched\n", self.initial_env + f"BACKEND_TAG={PREVIOUS}\n",
                        self.initial_env + f"export BACKEND_TAG={PREVIOUS}\n", "BACKEND_TAG=../unsafe\n"]:
            with self.subTest(content=content):
                self.env_file.write_text(content)
                self.assertNotEqual(self.deploy(), 0, self.output)
                self.assertEqual(self.env_file.read_text(), content)
                self.assertNotIn("pull ", self.commands)

    def test_pull_failure_keeps_previous_tag(self):
        self.assertNotEqual(self.deploy(FAIL_PULL="1"), 0)
        self.assert_unchanged()
        self.assertNotIn(" up ", self.commands)

    def test_bare_backend_tag_override_is_rejected_before_docker(self):
        for declaration in ["BACKEND_TAG", "export BACKEND_TAG # unset override"]:
            with self.subTest(declaration=declaration):
                content = self.initial_env + declaration + "\n"
                self.env_file.write_text(content)
                self.assertNotEqual(self.deploy(), 0, self.output)
                self.assertEqual(self.env_file.read_text(), content)
                self.assertEqual(self.commands, "")

    def test_missing_previous_image_is_rejected_before_pull(self):
        self.assertNotEqual(self.deploy(FAIL_PREVIOUS="1"), 0)
        self.assert_unchanged()
        self.assertNotIn("pull ", self.commands)

    def test_revision_mismatch_keeps_previous_tag(self):
        self.assertNotEqual(self.deploy(REVISION="wrong"), 0)
        self.assert_unchanged()
        self.assertNotIn("tag ghcr.io", self.commands)
        self.assertNotIn(" up ", self.commands)

    def test_compose_config_failure_restores_previous_tag(self):
        self.assertNotEqual(self.deploy(FAIL_CONFIG="1"), 0)
        self.assert_unchanged()

    def test_failed_start_restores_previous_tag_and_image(self):
        self.assertNotEqual(self.deploy(FAIL_UP="1"), 0)
        self.assert_unchanged()
        self.assertEqual((self.root / "started").read_text().splitlines(), [SHA, PREVIOUS])
        self.assertIn(f"rollback ok tag={PREVIOUS}", self.output)

    def test_readiness_timeout_restores_previous_tag_and_image(self):
        self.assertNotEqual(self.deploy(FAIL_READY="1"), 0)
        self.assert_unchanged()
        self.assertEqual((self.root / "started").read_text().splitlines(), [SHA, PREVIOUS])
        self.assertIn(f"rollback ok tag={PREVIOUS}", self.output)

    def test_rollback_failure_is_reported(self):
        self.assertNotEqual(self.deploy(FAIL_UP="1", FAIL_ROLLBACK="1"), 0)
        self.assert_unchanged()
        self.assertIn("rollback failed", self.output)
        self.assertIn("manual action needed", self.output)

    def test_existing_lock_rejects_deployment(self):
        lock = (self.root / "releases" / ".deploy.lock").as_posix()
        holder = subprocess.Popen([BASH, "-c", 'exec 9>"$1"; flock 9 || exit; echo locked; read -r release', "lock", lock],
                                  env=self.env, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            self.assertEqual(holder.stdout.readline().strip(), "locked")
            self.assertNotEqual(self.deploy(), 0)
            self.assertIn("another deployment is running", self.output)
            self.assert_unchanged()
            self.assertEqual(self.commands, "")
        finally:
            holder.communicate("release\n", timeout=10)


if __name__ == "__main__":
    unittest.main()
