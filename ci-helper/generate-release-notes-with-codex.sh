#!/usr/bin/env bash
set -euo pipefail

GIT_TAG="${1:-${GITHUB_REF_NAME:-}}"
TAG_VERSION="${2:-${GIT_TAG#v}}"
REPOSITORY="${GITHUB_REPOSITORY:-open-ani/animeko}"
CODEX_MODEL="${CODEX_MODEL:-gpt-5.5}"

if [[ -z "$GIT_TAG" ]]; then
  echo "Usage: $0 <git-tag> [tag-version]" >&2
  exit 2
fi

if ! command -v codex >/dev/null 2>&1; then
  echo "codex CLI is required. Install it before running this script." >&2
  exit 127
fi

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

# Release workflows often start from a shallow checkout. Fetch enough history and tags
# so we can find the previous tag and provide Codex with an accurate changelog context.
if [[ "$(git rev-parse --is-shallow-repository 2>/dev/null || echo false)" == "true" ]]; then
  git fetch --unshallow --tags --force --prune origin '+refs/heads/*:refs/remotes/origin/*' >/dev/null 2>&1 || true
else
  git fetch --tags --force --prune origin '+refs/heads/*:refs/remotes/origin/*' >/dev/null 2>&1 || true
fi

PREVIOUS_TAG="${PREVIOUS_TAG:-}"
if [[ -z "$PREVIOUS_TAG" ]]; then
  tag_commit="$(git rev-list -n 1 "$GIT_TAG" 2>/dev/null || true)"
  if [[ -n "$tag_commit" ]]; then
    PREVIOUS_TAG="$(git describe --tags --abbrev=0 "${tag_commit}^" 2>/dev/null || true)"
  fi
fi

COMPARE_URL="https://github.com/${REPOSITORY}/compare/${PREVIOUS_TAG}...${GIT_TAG}"
if [[ -z "$PREVIOUS_TAG" ]]; then
  COMPARE_URL="https://github.com/${REPOSITORY}/releases/tag/${GIT_TAG}"
fi

if command -v gh >/dev/null 2>&1 && [[ -n "${GH_TOKEN:-${GITHUB_TOKEN:-}}" ]]; then
  if [[ -n "$PREVIOUS_TAG" ]]; then
    gh api -X POST "repos/${REPOSITORY}/releases/generate-notes" \
      -f "tag_name=${GIT_TAG}" \
      -f "previous_tag_name=${PREVIOUS_TAG}" \
      --jq '.body' > "$workdir/github-generated-notes.md" 2>/dev/null || true
  else
    gh api -X POST "repos/${REPOSITORY}/releases/generate-notes" \
      -f "tag_name=${GIT_TAG}" \
      --jq '.body' > "$workdir/github-generated-notes.md" 2>/dev/null || true
  fi
fi
[[ -f "$workdir/github-generated-notes.md" ]] || : > "$workdir/github-generated-notes.md"

if [[ -n "$PREVIOUS_TAG" ]]; then
  GIT_RANGE="$PREVIOUS_TAG..$GIT_TAG"
else
  GIT_RANGE="$GIT_TAG"
fi

git log --no-merges --date=short --pretty=format:'- %h %ad %an: %s%n%b' "$GIT_RANGE" > "$workdir/git-log.md" 2>/dev/null || true
git log --merges --first-parent --date=short --pretty=format:'- %h %ad %an: %s%n%b' "$GIT_RANGE" > "$workdir/merge-log.md" 2>/dev/null || true

{
  cat "$workdir/github-generated-notes.md"
  printf '\n'
  cat "$workdir/git-log.md"
  printf '\n'
  cat "$workdir/merge-log.md"
} | grep -Eo '(pull/[0-9]+|#[0-9]+)' | grep -Eo '[0-9]+' | sort -n | uniq > "$workdir/pr-numbers.txt" || true

: > "$workdir/pull-requests.md"
if command -v gh >/dev/null 2>&1 && [[ -s "$workdir/pr-numbers.txt" ]]; then
  while IFS= read -r pr; do
    [[ -z "$pr" ]] && continue
    gh pr view "$pr" --repo "$REPOSITORY" --json number,title,author,url \
      --jq '"- #\(.number): \(.title) by @\(.author.login) in \(.url)"' \
      >> "$workdir/pull-requests.md" 2>/dev/null \
      || echo "- #${pr}: https://github.com/${REPOSITORY}/pull/${pr}" >> "$workdir/pull-requests.md"
  done < "$workdir/pr-numbers.txt"
fi

cat > "$workdir/prompt.md" <<PROMPT
你是 Animeko 的发布经理。请根据下面的 release context 生成 GitHub Release Notes。

必须严格遵守：
- 只输出 Markdown 正文，不要解释，不要代码块。
- 全部更新说明必须使用中文。
- 每条更新的句末不要写中文句号、英文句号或其他句末标点。
- 符合历史 release notes 格式：
  - 开头列出 1-n 个重要更新，每条用 "- " 开头。
  - 然后一个空行、一行 "----"、再一个空行。
  - 分割线上方展示重要更新，每条用 "- " 开头。
  - 分割线下方展示其他更新，每条用 "- " 开头。
  - 最后保留一行：**Full Changelog**: ${COMPARE_URL}
- 所有更新都要按重要性从上往下排序；优先说新特性、功能增强和体验改进，再说问题修复。
- 分割线上方只放重要更新，要面向用户，避免内部实现术语；不要把所有 commit 都塞进重要更新。
- 分割线下方需要包含所有其他对用户使用有影响的更新；文档更新、纯构建/CI/依赖/内部重构、测试调整、代码清理等对用户使用没有直接影响的更新不要包含。
- 如果某个更新已经在分割线上方提及，分割线下方不要重复写。
- PR 可以放在重要更新或次要更新里；若有作者和 PR 链接，沿用类似历史格式："... by @author in https://github.com/open-ani/animeko/pull/123"。
- 但 @Him188 和 @StageGuard 的 PR 不需要写 by 和 in，也不需要附 PR 链接；只要正文提到对应改动即可。
- 不要用“关闭 PR”“关闭链接”“关闭 https://...”这类说法来表示 PR；要直接描述用户可感知的改动。
- 不要重复提及同一个 PR；如果某个 PR 已经在分割线上面提及，分割线下面不要再提及它。
- 分割线下面只写其他对用户使用有影响且未在上方提及的更新；不要写文档、纯构建/CI/依赖/内部重构、测试调整、代码清理。
- 每一个对用户使用有影响的 PR 都必须在全文至少提及一次；如果 PR 只有文档、纯构建/CI/依赖/内部重构、测试调整、代码清理等内容，可以不提。
- 不要包含下载链接、二维码、ANI-SERVER-MAGIC-SEPARATOR 或 release-template 内容。

# Release
- Git tag: ${GIT_TAG}
- Version: ${TAG_VERSION}
- Previous tag: ${PREVIOUS_TAG:-无}
- Full changelog: ${COMPARE_URL}

# PRs to consider; mention user-facing ones somewhere exactly once
$(cat "$workdir/pull-requests.md")

# GitHub generated notes for reference
$(cat "$workdir/github-generated-notes.md")

# Merge commits
$(cat "$workdir/merge-log.md")

# Commits
$(cat "$workdir/git-log.md")
PROMPT

codex exec \
  --model "$CODEX_MODEL" \
  --sandbox read-only \
  --ephemeral \
  --output-last-message "$workdir/codex-notes.md" \
  - < "$workdir/prompt.md" > "$workdir/codex.log" 2>&1

python3 - "$workdir/codex-notes.md" "$workdir/pull-requests.md" "$COMPARE_URL" <<'PY'
import re
import sys
from pathlib import Path

notes_path = Path(sys.argv[1])
prs_path = Path(sys.argv[2])
compare_url = sys.argv[3]
text = notes_path.read_text().strip()

# Strip accidental Markdown fences.
text = re.sub(r"^```(?:markdown)?\s*", "", text, flags=re.I).strip()
text = re.sub(r"\s*```$", "", text).strip()

if not re.search(r"(?m)^-\s+", text):
    text = "- 本次更新包含多项体验优化与问题修复\n\n----\n\n" + text

if not re.search(r"(?m)^----\s*$", text):
    lines = text.splitlines()
    insert_at = 0
    for i, line in enumerate(lines):
        if line.startswith("- "):
            insert_at = i + 1
        elif insert_at:
            break
    lines[insert_at:insert_at] = ["", "----", ""]
    text = "\n".join(lines).strip()

if "Full Changelog" not in text:
    text = text.rstrip() + f"\n\n**Full Changelog**: {compare_url}"

pr_lines = [line.strip() for line in prs_path.read_text().splitlines() if line.strip()]
missing = []
for line in pr_lines:
    m = re.search(r"#(\d+).*?(https://\S+/pull/\d+)?", line)
    if not m:
        continue
    number = m.group(1)
    url_match = re.search(r"https://\S+/pull/\d+", line)
    url = url_match.group(0) if url_match else ""
    author_match = re.search(r"by @([^\s]+)", line)
    author_name = author_match.group(1) if author_match else ""
    if not author_name or author_name in {"Him188", "StageGuard"}:
        # Maintainer/unknown PRs do not need explicit attribution or links; avoid
        # adding generic fallback bullets that would duplicate user-facing summaries.
        continue
    mentioned = (url and url in text) or re.search(rf"(?<!\d)#?{re.escape(number)}(?!\d)", text)
    if not mentioned:
        author = f" by @{author_name}"
        link = f" in {url}" if url else ""
        missing.append(f"- 包含 PR #{number} 的改动{author}{link}")

if missing:
    full_re = re.compile(r"\n?\*\*?Full Changelog\*\*?:.*$", re.I | re.S)
    full_match = full_re.search(text)
    if full_match:
        before = text[:full_match.start()].rstrip()
        full = text[full_match.start():].strip()
        text = before + "\n" + "\n".join(missing) + "\n\n" + full
    else:
        text = text.rstrip() + "\n" + "\n".join(missing)

print(text.strip())
PY
