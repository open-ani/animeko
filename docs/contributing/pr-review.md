# PR 审查

## 代码审查

我们将代码审查看做是一个双向知识交换的机会。
代码审查不仅仅是为了找出错误，更是为了提高代码质量，确保代码长期开发的效率。开发者也有机会学习到新的技术和最佳实践。

- 有关代码审查的最佳实践，可参考
  Google [The Standard of Code Review](https://google.github.io/eng-practices/review/reviewer/standard.html)

### 如何应对代码审查的评论

审核者会尽其所能提供反馈。一个 PR 可能会收到几个到数十个评论，这些评论可能是必须修改的问题，也可能是一些轻微的建议。

- "nit: " 表示一个轻微问题，这表示当前的代码是可以接受的，不过有一个更好的写法，下次可以这么做。开发者可以直接忽略这些评论 (
  点击 "
  Resolve Conversation") 以节约时间。

对于其他评论，通常的解决规则是：

- 按照评论内容修改代码，commit，然后 push。如果你比较确定这个修改是正确的，直接点击 "Resolve Conversation"
  。如果不确定，可以在评论中回复 "done"，提醒审核者仍需关注这个评论。
- 在任何时候回复评论后都不要点击 "Resolve Conversation"，否则会导致审核者错过回复。
