# Contributing

Hedera strives for decentralization and that includes in its development process. As such, outside contributions are
welcomed and encouraged. For general guidelines on how to contribute to Hedera projects, please read the organization's
[contributing guide](https://github.com/hashgraph/.github/blob/main/CONTRIBUTING.md). This document outlines additional
processes that the mirror node uses for its development.

## Issues

Issues should be created using one of the issue templates. GitHub allows these templates to be bypassed in some areas
of its user interface, but users should refrain from doing so and ensure all fields of the template are filled.
Any issues that have been addressed on main or a release branch are required to have a milestone associated with them.

### Labels

Labels are used to group related issues together and for various release process tasks. Only maintainers have permission
to add or remove labels to issues. For other contributors, project maintainers will adjust the labels as necessary to
meet the project's requirements. Every issue should have one of these mandatory `Type` labels:

- `bug`
- `dependencies`
- `documentation`
- `enhancement`

This label will be used for grouping items in the release notes. Additionally, issues are also recommended to have a
green `Area` label to indicate which component is impacted and to route it to the appropriate team member. Any other
label can be added on an as-needed basis.

## Pull Requests

When opening a pull request (PR), take care that its body is filled out using the template. The description should
contain a short description of the change, followed by a bulleted list of changes in imperative present tense (i.e. use
terms like `add`, `remove`, `change`, etc.). This description should be copied to the commit message once the PR is
approved and squash-merged to the target branch. Below is a good example of a PR description:

```
Increase the overall performance of `/api/v1/contracts/call` by 1-2x due to the combined switch from E2 to N2 machine
class and the switch to virtual threads.

* Add request and response metrics to replace reactor netty metrics
* Enable virtual threads
* Change from reactive Spring WebFlux to synchronous Spring MVC
* Fix crashing under heavy load due to lack of non-heap memory
* Increase max replicas from 8 to 12
```

Every PR should have a corresponding issue associated with it. The pull request should indicate it fixes its
corresponding issue(s) by using the keyword `Fixes #1234` in the `Related issue(s)` section of the PR template. The
labels from the issue should be copied over from the issue so that it appears properly in the release notes. All pull
requests targeting main or a release branch should have a milestone set. Leave the project field empty since only an
issue should have a project assigned to ensure work is not tracked twice on the board.

Before opening a PR, the submitter should ensure all tests pass locally and any manual testing has been conducted to
verify the new functionality. The PR should stay as draft and without requesting reviewers until all requirements of the
ticket are complete, all checks pass, and a self-review of the changes has been conducted. Once that criteria has been
met, the PR can be marked as ready for review and the `@hiero-ledger/github-committers` team requested for a review. PRs
require at least one approval to merge, but in practice we encourage at least two approvals before
merging most PRs.
