"""Checks for the guards that keep adoption and offline validation safe."""

import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import manage


class AdoptionTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.plan = Path(self.directory.name) / "reviewed.tfplan"
        self.plan.write_bytes(b"saved-plan")
        self.plan_patch = patch.object(manage, "PLAN", self.plan)
        self.plan_patch.start()
        self.addCleanup(self.plan_patch.stop)

    def test_adoption_refuses_every_resource_mutation(self):
        for actions in [["create"], ["update"], ["delete"], ["delete", "create"], ["create", "delete"]]:
            with self.subTest(actions=actions):
                self.plan.write_bytes(b"saved-plan")
                document = {"resource_changes": [{"address": "aws_instance.this", "change": {"actions": actions}}]}
                with patch.object(manage, "plan"), patch.object(manage, "tofu", return_value=json.dumps(document)) as tofu:
                    with self.assertRaisesRegex(SystemExit, "Adoption refused"):
                        manage.adopt()
                    self.assertFalse(any(call.args[0] == "apply" for call in tofu.call_args_list))
                    self.assertFalse(self.plan.exists())

    def test_import_only_plan_can_update_local_state(self):
        document = {"resource_changes": [
            {"address": "aws_instance.this", "change": {"actions": ["no-op"], "importing": {"id": "i-existing"}}},
            {"address": "data.aws_key_pair.existing[0]", "change": {"actions": ["read"]}},
        ]}
        with patch.object(manage, "plan"), patch.object(manage, "tofu", return_value=json.dumps(document)) as tofu:
            manage.adopt()
            tofu.assert_any_call("apply", "-input=false", self.plan)
        self.assertFalse(self.plan.exists())

    def test_failed_plan_cannot_leave_an_old_plan_for_apply(self):
        with patch.object(manage, "tofu", side_effect=subprocess.CalledProcessError(1, "tofu")):
            with self.assertRaises(subprocess.CalledProcessError):
                manage.plan()
        self.assertFalse(self.plan.exists())

    def test_syntax_validation_never_requires_a_running_instance(self):
        executable = Path(self.directory.name) / "ansible-playbook"
        executable.touch()
        with patch.dict(manage.os.environ, {"ANSIBLE_PLAYBOOK_BIN": str(executable)}), \
                patch.object(manage, "LOCAL", Path(self.directory.name)), \
                patch.object(manage, "inventory", side_effect=AssertionError("AWS must not be queried")), \
                patch.object(manage, "run") as run:
            manage.ansible("--syntax-check")
            self.assertIn("--syntax-check", run.call_args.args[0])

    def test_connections_follow_a_recreated_stacks_instance_and_region(self):
        root = Path(self.directory.name)
        (root / "terraform.tfstate").touch()
        outputs = {"aws_context": {"value": {"profile": "another-profile", "region": "ap-southeast-1", "instance_id": "i-new"}}}
        response = {"Reservations": [{"Instances": [{"PublicIpAddress": "203.0.113.15"}]}]}
        with patch.object(manage, "ROOT", root), \
                patch.object(manage, "tofu", return_value=json.dumps(outputs)), \
                patch.dict(manage.os.environ, {key: "" for key in ["AWS_PROFILE", "AWS_REGION", "AWS_INSTANCE_ID", "SSH_HOST"]}), \
                patch.object(manage, "run", return_value=json.dumps(response)) as run:
            host, _ = manage.connection()
            self.assertEqual(host, "203.0.113.15")
            run.assert_called_once_with(["aws", "--profile", "another-profile", "--region", "ap-southeast-1",
                "ec2", "describe-instances", "--instance-ids", "i-new", "--output", "json"], capture=True)


class EC2AddressTests(unittest.TestCase):
    def test_boot_configuration_keeps_the_api_private_and_refreshes_public_addresses(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            curl = root / "curl"
            curl.write_text("""#!/bin/sh
case "$*" in
  */latest/api/token) printf '%s' test-token ;;
  */local-ipv4) printf '%s' 10.0.6.134 ;;
  */public-ipv4) [ "$TEST_PRIVATE_ONLY" != yes ] || exit 22; printf '%s' 203.0.113.10 ;;
  *) exit 1 ;;
esac
""")
            curl.chmod(0o700)
            env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ["PATH"],
                       K3S_CONFIG_DIR=str(root / "config"), K3S_FIXED_EXTERNAL_IP="")
            script = Path(manage.ROOT) / "ansible/files/k3s-aws-address.sh"
            subprocess.run(["sh", str(script)], env=env, check=True)
            config = root / "config/20-aws-address.yaml"
            self.assertIn('advertise-address: "10.0.6.134"', config.read_text())
            self.assertIn('node-external-ip: "203.0.113.10"', config.read_text())
            self.assertEqual(config.stat().st_mode & 0o777, 0o600)
            # A subsequent boot without a public address must remove the stale one.
            subprocess.run(["sh", str(script)], env=dict(env, TEST_PRIVATE_ONLY="yes"), check=True)
            self.assertIn('advertise-address: "10.0.6.134"', config.read_text())
            self.assertNotIn("node-external-ip", config.read_text())


if __name__ == "__main__":
    unittest.main()
