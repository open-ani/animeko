"""Exercise the qa job's deterministic shell steps with a local fake gh, without GitHub writes.
Also checks that the other agent jobs' prompts carry the test account.

Run: uv run --with pyyaml python .github/workflows/test_qa_review.py
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
MARKER = "<!-- openanibot:qa-report run=777.1 -->"
RUN_URL = "https://github.com/example/app/actions/runs/777"


def review(body, login="openanibot", state="APPROVED"):
    return {"user": {"login": login}, "state": state, "body": body}


def fake_gh():
    root = Path(os.environ["FAKE_ROOT"])
    state = json.loads((root / "fake-state.json").read_text())
    args = sys.argv[2:]
    body = sys.stdin.read() if "--body-file" in args else None
    with (root / "calls.jsonl").open("a") as log:
        log.write(json.dumps({"args": args, "body": body}) + "\n")

    if args[0] == "api":
        assert "--paginate" in args
        assert "repos/example/app/pulls/42/reviews?per_page=100" in args
        # `gh api --paginate` prints one JSON document per page, back to back.
        for page in state["pages"]:
            print(json.dumps(page))
    elif args[:3] == ["pr", "comment", "42"]:
        assert args[args.index("--repo") + 1] == "example/app"
        assert args[args.index("--body-file") + 1] == "-"
    else:
        raise AssertionError(f"Unexpected gh command: {args}")


class QaReviewTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = yaml.safe_load(WORKFLOW.read_text())
        cls.steps = {
            step["name"]: step["run"]
            for step in cls.workflow["jobs"]["qa"]["steps"]
            if "name" in step
        }

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.env = {
            **os.environ,
            "FAKE_ROOT": str(self.root),
            "RUNNER_TEMP": str(self.root),
            "REPO": "example/app",
            "PR_NUMBER": "42",
            "PR_URL": "https://github.com/example/app/pull/42",
            "REQUESTER": "maintainer",
            "GIT_NAME": "openanibot",
            "BASE_BRANCH": "main",
            "QA_MARKER": MARKER,
            "RUN_URL": RUN_URL,
            "GITHUB_RUN_ID": "777",
            "GITHUB_RUN_ATTEMPT": "1",
            "HEAD_REF": "feature/x",
            "BASE_REF": "main",
            "HEAD_SHA": "a" * 40,
            "LIVE_HEAD_SHA": "b" * 40,
            "PR_AUTHOR": "contributor",
            "AUTHOR_PERMISSION": "none",
            "CHECKOUT_POLICY": "pinned",
            # Explicitly empty: the developer's shell may carry a real test account.
            "ANI_TEST_ACCOUNT_EMAIL": "",
            "ANI_TEST_ACCOUNT_OTP": "", "ANI_SERVER_PROD_MONGODB_CONNECTION_STRING": "", "ANI_SERVER_PROD_ANIMEDB_CONNECTION_URL": "",
            "PATH": f"{self.root}{os.pathsep}{os.environ['PATH']}",
        }
        gh = self.root / "gh"
        gh.write_text(
            f"#!/bin/bash\nexec {shlex.quote(sys.executable)} "
            f"{shlex.quote(str(Path(__file__).resolve()))} --fake-gh \"$@\"\n"
        )
        gh.chmod(0o755)
        self.state = {"pages": [[]]}
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

    def comments(self):
        calls_path = self.root / "calls.jsonl"
        if not calls_path.exists():
            return []
        calls = [json.loads(line) for line in calls_path.read_text().splitlines()]
        return [call["body"] for call in calls if call["args"][:2] == ["pr", "comment"]]

    def ensure(self, pages, success):
        self.state["pages"] = pages
        self.save_state()
        return self.run_step("Ensure the review was submitted", success)

    # --- Workflow wiring -------------------------------------------------------

    def test_review_request_is_wired_to_an_authorized_qa_job(self):
        on = self.workflow.get("on", self.workflow.get(True))
        self.assertEqual(on["pull_request_target"], {"types": ["review_requested"]})
        authorize_if = self.workflow["jobs"]["authorize"]["if"]
        self.assertIn("github.event.requested_reviewer.login == 'openanibot'", authorize_if)
        qa = self.workflow["jobs"]["qa"]
        self.assertEqual(qa["needs"], "authorize")
        self.assertIn("needs.authorize.outputs.authorized == 'true'", qa["if"])
        self.assertIn("github.event.action == 'review_requested'", qa["if"])
        self.assertTrue(qa["concurrency"]["cancel-in-progress"])
        self.assertEqual(qa["permissions"]["contents"], "read")
        ensure = next(s for s in qa["steps"] if s.get("name") == "Ensure the review was submitted")
        self.assertEqual(ensure["if"], "${{ !cancelled() }}")

    # --- Build prompt -----------------------------------------------------------

    def test_prompt_carries_the_marker_and_review_command(self):
        self.run_step("Build prompt")
        prompt = (self.root / "prompt.txt").read_text()
        self.assertIn(f"\n   {MARKER}\n", prompt)
        self.assertIn("gh pr review 42 --repo example/app", prompt)
        self.assertIn("https://github.com/example/app/pull/42", prompt)
        self.assertIn("@maintainer requested your review", prompt)
        self.assertIn("pinned to the commit", prompt)
        self.assertIn("b" * 40, prompt)

    def test_prompt_for_write_author_uses_latest_head(self):
        self.env.update({"CHECKOUT_POLICY": "latest", "AUTHOR_PERMISSION": "write"})
        self.run_step("Build prompt")
        prompt = (self.root / "prompt.txt").read_text()
        self.assertIn("has write access to example/app", prompt)
        self.assertNotIn("pinned to the commit", prompt)

    def test_prompt_without_test_account_says_login_is_impossible(self):
        self.run_step("Build prompt")
        prompt = (self.root / "prompt.txt").read_text()
        self.assertIn("No test account is configured", prompt)
        self.assertNotIn("Test account: sign in", prompt)
        self.assertNotIn("Production database:", prompt)
        self.assertIn("test-sandbox/local-test-account.json", prompt)

    def test_prompt_carries_the_production_database_hint_but_not_the_secret(self):
        uri = "mongodb://agent:pw@db.example.com:1234/?authSource=admin"
        self.env.update({"ANI_SERVER_PROD_MONGODB_CONNECTION_STRING": uri, "ANI_SERVER_PROD_ANIMEDB_CONNECTION_URL": uri})
        self.run_step("Build prompt")
        prompt = (self.root / "prompt.txt").read_text()
        self.assertIn("Production database:", prompt)
        self.assertIn("--server-db production --animedb production", prompt)
        self.assertNotIn("db.example.com", prompt)
        self.assertNotIn("agent:pw", prompt)

    def test_prompt_carries_the_test_account(self):
        self.env.update({"ANI_TEST_ACCOUNT_EMAIL": "qa-bot@example.com", "ANI_TEST_ACCOUNT_OTP": "246810"})
        self.run_step("Build prompt")
        prompt = (self.root / "prompt.txt").read_text()
        self.assertIn("Test account: sign in to the app with email login as qa-bot@example.com", prompt)
        self.assertIn("the verification code is always\n246810", prompt)
        self.assertIn("never write them into the report", prompt)
        self.assertNotIn("No test account is configured", prompt)

    def test_half_configured_test_account_counts_as_none(self):
        for env in [{"ANI_TEST_ACCOUNT_EMAIL": "qa-bot@example.com"}, {"ANI_TEST_ACCOUNT_OTP": "246810"}]:
            with self.subTest(env=env):
                self.env.update({"ANI_TEST_ACCOUNT_EMAIL": "", "ANI_TEST_ACCOUNT_OTP": "", **env})
                self.run_step("Build prompt")
                prompt = (self.root / "prompt.txt").read_text()
                self.assertIn("No test account is configured", prompt)
                self.assertNotIn("qa-bot@example.com", prompt)
                self.assertNotIn("246810", prompt)

    # --- Test account in the other agent jobs' prompts -------------------------

    OTHER_JOB_ENVS = {
        "implement": {"ISSUE_NUMBER": "7", "ISSUE_URL": "https://github.com/example/app/issues/7"},
        "iterate": {
            "CROSS": "false", "PUSH_REMOTE": "origin", "HEAD_REF": "feature/x", "HEAD_REPO": "example/app",
            "TRIGGER_URL": "https://github.com/example/app/pull/42#issuecomment-1",
        },
        "respond": {
            "ISSUE_NUMBER": "7", "ISSUE_URL": "https://github.com/example/app/issues/7",
            "COMMENT_URL": "https://github.com/example/app/issues/7#issuecomment-1",
        },
    }

    def build_prompt_of(self, job, extra_env):
        step = next(s for s in self.workflow["jobs"][job]["steps"] if s.get("name") == "Build prompt")
        result = subprocess.run(
            ["bash", "-c", step["run"]], env={**self.env, **extra_env}, cwd=self.root,
            capture_output=True, text=True,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return (self.root / "prompt.txt").read_text()

    def test_other_agent_jobs_carry_the_test_account(self):
        account = {"ANI_TEST_ACCOUNT_EMAIL": "qa-bot@example.com", "ANI_TEST_ACCOUNT_OTP": "246810"}
        for job, env in self.OTHER_JOB_ENVS.items():
            with self.subTest(job=job):
                prompt = self.build_prompt_of(job, env)
                self.assertIn("No test account is configured", prompt)
                self.assertNotIn("246810", prompt)
                prompt = self.build_prompt_of(job, {**env, **account})
                self.assertIn("email login as qa-bot@example.com", prompt)
                self.assertIn("code is always 246810", prompt)
                self.assertIn("The email and the code are secrets", prompt)
                self.assertNotIn("No test account is configured", prompt)
                self.assertIn("test-sandbox/local-test-account.json", prompt)
                self.assertNotIn("Production database:", prompt)
                uri = "mongodb://agent:pw@db.example.com:1234/?authSource=admin"
                prompt = self.build_prompt_of(job, {**env, "ANI_SERVER_PROD_MONGODB_CONNECTION_STRING": uri})
                self.assertIn("Production database:", prompt)
                self.assertNotIn("db.example.com", prompt)

    def test_other_agent_jobs_receive_the_test_account_env(self):
        for job in ["implement", "iterate", "qa", "respond"]:
            with self.subTest(job=job):
                env = self.workflow["jobs"][job]["env"]
                self.assertEqual(env["ANI_TEST_ACCOUNT_EMAIL"], "${{ secrets.ANI_TEST_ACCOUNT_EMAIL }}")
                self.assertEqual(env["ANI_TEST_ACCOUNT_OTP"], "${{ secrets.ANI_TEST_ACCOUNT_OTP }}")
                for name in ["ANI_SERVER_PROD_MONGODB_CONNECTION_STRING", "ANI_SERVER_PROD_ANIMEDB_CONNECTION_URL"]:
                    self.assertEqual(env[name], "${{ secrets.ANI_SERVER_PROD_MONGODB_CONNECTION_STRING }}")
        for job in ["classify", "authorize"]:
            with self.subTest(job=job):
                self.assertNotIn("ANI_TEST_ACCOUNT_EMAIL", self.workflow["jobs"][job].get("env", {}))

    # --- Ensure the review was submitted ---------------------------------------

    def test_submitted_review_with_marker_passes_without_comment(self):
        self.ensure([[review(f"Report body\n\n{MARKER}")]], success=True)
        self.assertEqual(self.comments(), [])

    def test_bot_login_matches_case_insensitively(self):
        self.ensure([[review(MARKER, login="OpenAniBot", state="CHANGES_REQUESTED")]], success=True)
        self.assertEqual(self.comments(), [])

    def test_marker_on_a_later_page_is_found(self):
        self.ensure([[review("older report <!-- openanibot:qa-report run=700.1 -->")], [review(MARKER)]], success=True)
        self.assertEqual(self.comments(), [])

    def test_missing_review_posts_failure_comment_and_fails(self):
        for pages in [[], [[]], [[review("unrelated", login="maintainer")]]]:
            with self.subTest(pages=pages):
                (self.root / "calls.jsonl").unlink(missing_ok=True)
                self.ensure(pages, success=False)
                comments = self.comments()
                self.assertEqual(len(comments), 1)
                self.assertIn(RUN_URL, comments[0])
                self.assertIn("<!-- openanibot:qa-failed run=777.1 -->", comments[0])

    def test_reviews_that_do_not_prove_this_run_do_not_count(self):
        decoys = [
            review(MARKER, login="maintainer"),                       # someone else quoting the marker
            review("<!-- openanibot:qa-report run=776.1 -->"),       # an earlier run
            review(MARKER, state="PENDING"),                         # drafted, never submitted
            review(None),                                            # review without a body
        ]
        self.ensure([decoys], success=False)
        self.assertEqual(len(self.comments()), 1)


if __name__ == "__main__":
    if sys.argv[1:] and sys.argv[1] == "--fake-gh":
        fake_gh()
    else:
        unittest.main()
