# GitHub Actions Workflows

`build.yml` 和 `release.yml` 由 `src.main.kts` 生成而来。详情参考 <https://typesafegithub.github.io/github-workflows-kt/>。

`pr-images.yml` 直接维护，拒绝 PR 在资源目录以外新增图片和视频，防止验证截图进入 git 历史。

`datasource-test-mcp.yml` 直接维护，构建自带 JBR 的 `tools/datasource-test-mcp` Windows 发行包，不依赖仓库环境即可运行，用法见该工具的 README。

`codex-agent.yml` 直接维护。新 issue 的分类器会读取组织级 `Priority` 字段的选项，校验模型输出后填写空缺的优先级，保留已有值。P0–P3 labels 已过时，不参与分类。

`OPENANI_BOT_TOKEN` 需要仓库 Issues 写权限和组织 Issue Fields 读权限（classic PAT 对应 `repo` 和 `read:org`）。机器人必须是组织成员，才能读取仅成员可见的 Priority 字段。
字段写入使用 [issue field values API](https://docs.github.com/en/rest/issues/issue-field-values#add-issue-field-values-to-an-issue)，不需要 Projects 权限。

在 PR 的 Reviewers 里请求 `openanibot` 会触发 `qa` job：Codex 以测试工程师的身份构建 PR、运行受影响模块的自动化测试、在 Android 模拟器和打包后的 macOS 桌面应用里按用户操作验证功能，然后把带截图的测试报告作为 PR review 提交（approve / request changes / comment）。它只评价行为，不评价代码写法，也不会推送任何内容。

- 只有仓库 write 权限以上的人发起的请求才会触发。bot 的 review 和普通 reviewer 的一样，maintainer 可以 dismiss。
- 每次请求都是对当前 head 的一次完整重测，使用全新的 Codex 会话，不复用 `iterate` 的对话；测试进行中收到新请求时取消旧测试，避免对过期 commit 出报告。
- PR 作者没有 write 权限时，测试固定在请求时刻的 commit，与 `iterate` job 的策略一致。
- GitHub 不允许把 PR 作者加为 reviewer，所以 openanibot 自己开的 PR 无法用这个方式测试。
- 想让 bot 重点测试某些场景，写在 PR 描述或普通评论里即可。评论里不要 @openanibot，那会触发 `iterate` job。
- 登录用的测试帐号来自仓库 secrets `ANI_TEST_ACCOUNT_EMAIL` 和 `ANI_TEST_ACCOUNT_OTP`：在 ani-api-server 的管理后台把某个帐号设为测试帐号（权限 `users.test-account`，固定 6 位验证码登录、不发邮件，验证码在用户详情的"绑定"分区查看），再把邮箱和验证码填进这两个 secret，两者都要设置。`implement`、`iterate`、`qa`、`respond` 四个 job 都把它们放进环境并写进 prompt。标记保存在服务端用户库里：生产服务器和用 `--server-db production` 启动的本地服务器都能用这个帐号。没有配置时 bot 无法登录生产服务器，需要登录的场景会报告为未测试。
- 生产库连接串来自 secret `ANI_SERVER_PROD_MONGODB_CONNECTION_STRING`：Mongo 用户 `ani-agent`（对 `ani-production` 读写、对 `animedb` 只读）经 Railway 的 TCP 代理连 mongo-1。四个 job 把它同时放进 `ANI_SERVER_PROD_MONGODB_CONNECTION_STRING` 和 `ANI_SERVER_PROD_ANIMEDB_CONNECTION_URL`，ani-api-server 的 animeko-server-local-test-server skill 用 `--server-db production --animedb production` 启动时自动读取，agent 就能在本地起一个连着生产数据的服务器用测试帐号登录。prompt 只告诉 agent 连接串在环境里，不写出内容。
- 本地库模式（skill 默认的 `--server-db local`）没有生产帐号：启动脚本会自动在本地库建一个测试帐号，邮箱和验证码写到服务端仓库的 `test-sandbox/local-test-account.json`，agent 用它登录。
- Codex 结束时没有提交 review，workflow 会在 PR 上留一条失败评论并标红；review 请求保持待处理，重新请求即可重试。

离线回归测试（执行 workflow 中的 shell，使用模拟 `gh`，不会修改 GitHub）：

```shell
uv run --with pyyaml python .github/workflows/test_issue_classification.py
uv run --with pyyaml python .github/workflows/test_qa_review.py
```
