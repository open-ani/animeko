# GitHub Actions Workflows

`build.yml` 和 `release.yml` 由 `src.main.kts` 生成而来。详情参考 <https://typesafegithub.github.io/github-workflows-kt/>。

`pr-images.yml` 直接维护，拒绝 PR 在资源目录以外新增图片和视频，防止验证截图进入 git 历史。

`datasource-test-mcp.yml` 直接维护，构建自带 JBR 的 `tools/datasource-test-mcp` 发行包，不依赖仓库环境即可运行，用法见该工具的 README。

`codex-agent.yml` 直接维护。新 issue 的分类器会读取组织级 `Priority` 字段的选项，校验模型输出后填写空缺的优先级，保留已有值。P0–P3 labels 已过时，不参与分类。

`OPENANI_BOT_TOKEN` 需要仓库 Issues 写权限和组织 Issue Fields 读权限（classic PAT 对应 `repo` 和 `read:org`）。机器人必须是组织成员，才能读取仅成员可见的 Priority 字段。
字段写入使用 [issue field values API](https://docs.github.com/en/rest/issues/issue-field-values#add-issue-field-values-to-an-issue)，不需要 Projects 权限。

在 PR 的 Reviewers 里请求 `openanibot` 会触发 `qa` job：Codex 以测试工程师的身份构建 PR、运行受影响模块的自动化测试、在 Android 模拟器和打包后的 macOS 桌面应用里按用户操作验证功能，然后把带截图的测试报告作为 PR review 提交（approve / request changes / comment）。它只评价行为，不评价代码写法，也不会推送任何内容。

- 只有仓库 write 权限以上的人发起的请求才会触发。bot 的 review 和普通 reviewer 的一样，maintainer 可以 dismiss。
- 每次请求都是对当前 head 的一次完整重测，使用全新的 Codex 会话，不复用 `iterate` 的对话；测试进行中收到新请求时取消旧测试，避免对过期 commit 出报告。
- PR 作者没有 write 权限时，测试固定在请求时刻的 commit，与 `iterate` job 的策略一致。
- GitHub 不允许把 PR 作者加为 reviewer，所以 openanibot 自己开的 PR 无法用这个方式测试。
- 想让 bot 重点测试某些场景，写在 PR 描述或普通评论里即可。评论里不要 @openanibot，那会触发 `iterate` job。
- Codex 结束时没有提交 review，workflow 会在 PR 上留一条失败评论并标红；review 请求保持待处理，重新请求即可重试。

离线回归测试（执行 workflow 中的 shell，使用模拟 `gh`，不会修改 GitHub）：

```shell
uv run --with pyyaml python .github/workflows/test_issue_classification.py
uv run --with pyyaml python .github/workflows/test_qa_review.py
```
