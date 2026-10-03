"""Exercise the workflow's actual shell steps with a local fake gh, without GitHub writes.

Run: uv run --with pyyaml python .github/workflows/test_issue_classification.py
"""

import json
import os
from pathlib import Path
import shlex
import subprocess
import sys
import tempfile
import unittest

import yaml


WORKFLOW = Path(__file__).with_name("codex-agent.yml")
PRIORITY_ID = 1234
PRIORITIES = ["Urgent", "High", "Medium", "Low"]
CLASSIFICATION = {
    "title": "Playback stalls when switching episodes",
    "issue_type": "Bug",
    "priority": "Medium",
    "labels": ["android", "s: player"],
    "request_logs": False,
}


def fake_gh():
    root = Path(os.environ["CLASSIFIER_DIR"])
    state_path = root / "fake-state.json"
    state = json.loads(state_path.read_text())
    args = sys.argv[2:]
    payload = json.load(sys.stdin) if "--input" in args else None
    with (root / "calls.jsonl").open("a") as log:
        log.write(json.dumps({"args": args, "payload": payload}) + "\n")

    if args[:2] == ["issue", "view"]:
        result = state["issue"]
    elif args[:2] == ["label", "list"]:
        result = state["labels"]
    elif args[:2] == ["issue", "edit"]:
        result = None
    elif args[0] == "api":
        assert args[args.index("-H") + 1] == "X-GitHub-Api-Version: 2026-03-10"
        endpoint = next(arg for arg in args if arg.startswith(("orgs/", "repos/")))
        if endpoint == "orgs/example/issue-fields":
            if state.get("deny_fields"):
                raise SystemExit(1)
            result = state["fields"]
        elif endpoint == "repos/example/app/issues/42/issue-field-values?per_page=100":
            assert "--method" not in args
            assert "--paginate" in args
            result = state["values"]
        elif endpoint == "repos/example/app/issues/42/issue-field-values":
            assert args[args.index("--method") + 1] == "POST"
            if state.get("deny_write"):
                raise SystemExit(1)
            assert set(payload) == {"issue_field_values"}
            assert len(payload["issue_field_values"]) == 1
            field = payload["issue_field_values"][0]
            assert field["field_id"] == PRIORITY_ID
            assert field["value"] in PRIORITIES
            if not state.get("ignore_write"):
                state["values"] = [v for v in state["values"] if v["issue_field_id"] != PRIORITY_ID]
                state["values"].append({
                    "issue_field_id": PRIORITY_ID,
                    "value": 99,
                    "single_select_option": {"name": field["value"]},
                })
                state_path.write_text(json.dumps(state))
            result = state["values"]
        else:
            raise AssertionError(f"Unexpected API endpoint: {endpoint}")
    else:
        raise AssertionError(f"Unexpected gh command: {args}")
    print(json.dumps(result))


class IssueClassificationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.steps = {
            step["name"]: step["run"]
            for step in yaml.safe_load(WORKFLOW.read_text())["jobs"]["classify"]["steps"]
        }

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.env = {
            **os.environ,
            "CLASSIFIER_DIR": str(self.root),
            "REPO": "example/app",
            "ISSUE_NUMBER": "42",
            "READY_LABEL": "ready-for-dev",
            "GIT_NAME": "openanibot",
            "PATH": f"{self.root}{os.pathsep}{os.environ['PATH']}",
        }
        gh = self.root / "gh"
        gh.write_text(
            f"#!/bin/bash\nexec {shlex.quote(sys.executable)} "
            f"{shlex.quote(str(Path(__file__).resolve()))} --fake-gh \"$@\"\n"
        )
        gh.chmod(0o755)
        self.state = {
            "issue": {
                "title": CLASSIFICATION["title"], "body": "Android playback stalls.",
                "issueType": None, "labels": [{"name": "P1"}, {"name": "android"}],
            },
            "labels": [{"name": name, "description": ""} for name in [
                "android", "s: player", "P0", "P1", "P2", "P3", "p2", "P10",
                "ready-for-dev", "codex", "help wanted", "waiting-for-reply", "z: invalid",
            ]],
            "fields": [{
                "id": PRIORITY_ID, "name": "Priority", "data_type": "single_select",
                "description": "Current importance",
                "options": [{"name": name, "priority": index} for index, name in enumerate(PRIORITIES)],
            }],
            "values": [{"issue_field_id": 5678, "value": 10}],
        }
        self.save_state()

    def save_state(self):
        (self.root / "fake-state.json").write_text(json.dumps(self.state))

    def run_step(self, name, success=True):
        result = subprocess.run(
            ["bash", "-c", self.steps[name]], env=self.env, cwd=self.root,
            capture_output=True, text=True,
        )
        if success:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        return result

    def prepare(self):
        self.run_step("Fetch issue, label taxonomy, and Priority field")
        self.run_step("Build constrained classification prompt")

    def apply(self, classification=None, success=True):
        (self.root / "classification.json").write_text(json.dumps(
            CLASSIFICATION if classification is None else classification
        ))
        return self.run_step("Validate and apply classification", success)

    def writes(self):
        calls = [json.loads(line) for line in (self.root / "calls.jsonl").read_text().splitlines()]
        return [call for call in calls if call["args"][:2] == ["issue", "edit"] or "--method" in call["args"]]

    def test_taxonomy_and_schema_exclude_legacy_labels(self):
        self.prepare()
        schema = json.loads((self.root / "schema.json").read_text())
        self.assertEqual(schema["properties"]["priority"]["enum"], PRIORITIES)
        self.assertIn("priority", schema["required"])
        self.assertEqual(schema["properties"]["labels"]["items"]["enum"], ["android", "s: player"])
        issue = json.loads((self.root / "issue.json").read_text())
        self.assertEqual(issue["labels"], [{"name": "android"}])

    def test_each_priority_uses_discovered_field_id_and_preserves_other_fields(self):
        self.prepare()
        for priority in PRIORITIES:
            with self.subTest(priority=priority):
                self.save_state()
                self.apply({**CLASSIFICATION, "priority": priority})
                values = json.loads((self.root / "fake-state.json").read_text())["values"]
                self.assertEqual(values[0], {"issue_field_id": 5678, "value": 10})
                self.assertEqual(values[1]["single_select_option"]["name"], priority)
                self.assertEqual(self.writes()[-1]["payload"], {
                    "issue_field_values": [{"field_id": PRIORITY_ID, "value": priority}],
                })

    def test_priority_set_during_classification_is_preserved(self):
        self.prepare()
        self.state["values"].append({
            "issue_field_id": PRIORITY_ID, "value": 7,
            "single_select_option": {"name": "High"},
        })
        self.save_state()
        result = self.apply()
        self.assertIn("Preserving existing Priority: High", result.stdout)
        self.assertEqual(len(self.writes()), 1)  # Title/type/labels only.
        self.assertEqual(self.writes()[0]["args"][:2], ["issue", "edit"])

    def test_rerun_preserves_priority(self):
        self.prepare()
        self.apply()
        result = self.apply({**CLASSIFICATION, "priority": "Urgent"})
        self.assertIn("Preserving existing Priority: Medium", result.stdout)
        self.assertEqual(len([call for call in self.writes() if "--method" in call["args"]]), 1)

    def test_invalid_priority_causes_no_writes(self):
        self.prepare()
        for priority in [None, "", "P1", "Critical", 1, ["High"], "$(touch injected)"]:
            with self.subTest(priority=priority):
                self.apply({**CLASSIFICATION, "priority": priority}, success=False)
                self.assertEqual(self.writes(), [])
        without_priority = {key: value for key, value in CLASSIFICATION.items() if key != "priority"}
        self.apply(without_priority, success=False)
        self.assertEqual(self.writes(), [])
        self.assertFalse((self.root / "injected").exists())

    def test_forbidden_labels_cause_no_writes_even_if_in_allowlist(self):
        self.prepare()
        (self.root / "allowed-labels.json").write_text(json.dumps(self.state["labels"]))
        for label in ["P0", "P1", "P2", "P3", "p2", "P10", "ready-for-dev", "unknown"]:
            with self.subTest(label=label):
                self.apply({**CLASSIFICATION, "labels": ["android", label]}, success=False)
                self.assertEqual(self.writes(), [])

    def test_missing_or_invalid_field_fails_before_classification(self):
        field = self.state["fields"][0]
        for fields in [[], [field, field], [{**field, "options": []}], [{**field, "data_type": "text"}]]:
            with self.subTest(fields=fields):
                self.state["fields"] = fields
                self.save_state()
                self.run_step("Fetch issue, label taxonomy, and Priority field", success=False)
                self.assertEqual(self.writes(), [])

    def test_field_permission_failure_is_not_silently_ignored(self):
        self.state["deny_fields"] = True
        self.save_state()
        self.run_step("Fetch issue, label taxonomy, and Priority field", success=False)
        self.assertEqual(self.writes(), [])

    def test_failed_or_unconfirmed_write_fails_the_step(self):
        self.prepare()
        for flag in ["deny_write", "ignore_write"]:
            with self.subTest(flag=flag):
                self.state[flag] = True
                self.save_state()
                self.apply(success=False)
                del self.state[flag]


if __name__ == "__main__":
    if sys.argv[1:] and sys.argv[1] == "--fake-gh":
        fake_gh()
    else:
        unittest.main()
