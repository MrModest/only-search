# Commit and PR titles
Use type(scope): summary (scope is optional), with type fix, feat, or chore.
Examples: fix(search): …, feat(widgets): …, chore(ci): …, chore(tests): …, chore(docs): ….

PRs are squash-merged: the PR title becomes the main commit subject and its description becomes the body. The title decides the release: fix publishes a patch, feat publishes a minor, chore publishes nothing. Pick honestly: user-visible app behaviour changes are fix or feat. Use chore for CI, tests, docs, and tooling that do not affect the shipped app.

Never edit version.txt or create v* tags or releases by hand; the workflow owns them.
Keep one topic per PR. Describe the final change in the PR description.
