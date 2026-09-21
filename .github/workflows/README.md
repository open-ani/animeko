# GitHub Actions Workflows

`build.yml` 和 `release.yml` 由 `src.main.kts` 生成而来。详情参考 <https://typesafegithub.github.io/github-workflows-kt/>。

`codex-agent.yml` 直接维护。新 issue 的分类器会读取组织级 `Priority` 字段的选项，校验模型输出后填写空缺的优先级，保留已有值。P0–P3 labels 已过时，不参与分类。

`OPENANI_BOT_TOKEN` 需要仓库 Issues 写权限和组织 Issue Fields 读权限（classic PAT 对应 `repo` 和 `read:org`）。机器人必须是组织成员，才能读取仅成员可见的 Priority 字段。
字段写入使用 [issue field values API](https://docs.github.com/en/rest/issues/issue-field-values#add-issue-field-values-to-an-issue)，不需要 Projects 权限。

分类流程的离线回归测试（执行 workflow 中的 shell，使用模拟 `gh`，不会修改 GitHub）：

```shell
uv run --with pyyaml python .github/workflows/test_issue_classification.py
```
