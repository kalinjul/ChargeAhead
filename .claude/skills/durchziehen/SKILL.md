---
name: durchziehen
description: Takes a feature or fix from request to reviewed commit in one run — a short interview on the decisions that matter, autonomous TDD, quality gates, a subagent code review for SOLID, native components, needless custom workarounds, performance and code quality, fix-and-review until clean, then commit and hand over. Use when asked to build, change or fix something end to end, to "just do it", to "durchziehen", or to implement something and have it reviewed; also when a task is handed over with "do it in TDD" or "review it before I look".
---

# Durchziehen

One run from "build X" to "here's the reviewed commit". No plan documents, no
approval gates in between: one interview up front, then the loop runs on its
own until the reviewer has nothing left to say.

## 1. Interview, once

Before touching code, find the decisions that would change what gets built
and ask them **in a single message**. Not one at a time, not after exploring
for ten minutes. Read what you need first (the files the task names, the
tests around them, the relevant skills: `interactors`, `viewmodels`), then
ask.

- Only decisions whose answer changes the work. A default that a careful
  colleague would pick is stated, not asked ("I'll put this in the drawer
  unless you say otherwise").
- Each question has a recommendation first. Multiple choice where possible.
- Nothing to ask? Say so in one line and start.

The answer is the whole brief. Don't come back with more questions unless the
code proves one of the answers impossible.

## 1b. A branch of its own

After the interview, before the first test:

- `git status --short` must be empty. Uncommitted work belongs to whoever
  left it; stop and ask rather than commit it along.
- `git checkout main && git pull`, then `git checkout -b <branch>`. The name
  is short, lowercase, hyphenated, and says what the task is
  (`charge-now-ac-mode`, `drawer-slider-removal`), not who or when.
- If the user named a branch, or said to continue on the current one, use
  that instead. Otherwise never build on `main`, and never on a branch that
  has an open PR unless the task is a change to that PR.

Linear history: no merges from `main` mid-task. If `main` moved and matters,
rebase.

## 2. Build, in TDD

Red, green, tidy, for each behaviour, smallest step that can fail:

1. Write the test that describes the behaviour. Run it. **See it fail for the
   right reason.** A test that passes before the code exists, or fails to
   compile where it should fail an assertion, is not red yet.
2. Write the least code that makes it green. Run it.
3. Tidy, run the suite of the module you touched.

Rules of the house while building:

- Reach for the native component before writing one: Material 3, Compose
  foundation, AndroidX, Navigation 3. Check the catalogue first; a hand-rolled
  panel that Material ships is a finding later.
- State belongs in a ViewModel, logic in an interactor or observer (the two
  skills say how). A `remember` holding business state is a finding later.
- Every new or changed visual gets a `@PreviewTest` in
  `ui-tests/src/screenshotTest/.../<Feature>Previews.kt`, light and dark, in
  the states the brief names (a mode on, a list with a long tail, an empty
  state). They are the goldens later, and they are what step 2b and the
  reviewer look at. Previews have no Maps SDK; the map screen renders a plain
  backdrop, that's fine.
- Goldens are **not** re-recorded per tweak. Compile and run the unit suites;
  the one docker golden pass comes before the PR, when the user asks.
- Commit as you go, one coherent step per commit, in the repo's commit style
  (`type: subject`, lowercase, under ~70 chars, dry). No AI attribution.

Verification before every claim of "done": the actual gradle command, the
actual exit code. A green you didn't see is a guess.

## 2b. Look at it

Tests prove behaviour, not looks. Before the review, render the previews
this task added or changed and look at the result yourself. Only those: the
task takes a class filter, and the whole suite is a minute you don't need
to spend per round.

    ./gradlew --console=plain --no-scan :ui-tests:validateDebugScreenshotTest --tests '*<Feature>Previews*'

The exit code does not matter here, native renders never match the docker
goldens. What matters is what it wrote:

    ui-tests/build/outputs/screenshotTest-results/preview/debug/rendered/<package path>/<PreviewsKt>/<Preview>_<hash>_0.png

Read every PNG of a preview this task added or changed, light and dark. Ask
the questions a designer would: is it aligned to something, is the spacing
the same as its neighbours', does the colour belong to the theme, is the text
legible on its background, does it look like what the brief described, does
it still look right in dark. Fix what's obviously off before the reviewer
sees it; a reviewer's time is for what you couldn't see yourself.

Never commit these renders as goldens; the docker pass before the PR does that.

## Quality gates

The same gates run twice: before the reviewer is dispatched, and before the
hand-over. Every one has to pass; a red gate is fixed first, it is never
reported as a finding to be weighed.

| Gate | Command | Passes when |
|---|---|---|
| Compiles, every module | `./gradlew :shared:compileKotlinJvm :phone-ui:compileDebugKotlin :androidApp:compileDebugKotlin :ui-tests:compileDebugUnitTestKotlin :ui-tests:compileDebugScreenshotTestKotlin` | exit 0, no `e:` lines |
| Domain tests | `./gradlew :shared:jvmTest` | exit 0 |
| Phone behaviour tests | `./gradlew :ui-tests:testDebugUnitTest` | exit 0 |
| Car tests | `./gradlew :androidApp:testDebugUnitTest` | exit 0 |
| New behaviour has a test | review the diff yourself | every behaviour the brief names is asserted somewhere; a behaviour without a test is a gap, not a style choice |
| New visuals have a preview | review the diff yourself | every composable the brief adds or visibly changes has a `@PreviewTest`, light and dark, in `ui-tests/src/screenshotTest`; its render exists under `rendered/` |
| Warnings | the compile output | no new `w:` lines in files the diff touches |
| Working tree | `git status --short` | empty after the last commit |

Goldens (`tools/screenshots.sh validate`) are not a gate here; they run once
before the PR, when the user asks. `./gradlew testAll` runs the three test
gates in one go, use it when the full tree changed.

Run gates with `--console=plain --no-scan`, the log redirected to the
scratchpad, and read the summary lines; the chat gets the exit code and the
failures, not the log.

## 3. Review, by a subagent

When the task is built and the quality gates pass, dispatch one `general-purpose`
subagent with the prompt in [reviewer.md](reviewer.md). Fill in:

- `{TASK}`: the brief, including the interview answers
- `{BASE_SHA}`/`{HEAD_SHA}`: `git merge-base HEAD main` and `git rev-parse HEAD`
- `{PREVIOUS_FINDINGS}`: empty on the first round; on later rounds the last
  report's findings, each marked fixed or pushed back with the reason
- `{SCREENSHOTS}`: the absolute paths of the rendered PNGs from step 2b for
  the previews this task added or changed, light and dark, one per line, each
  with the preview's name and what state it shows. "none, no visual change"
  when the task touched no UI.

The reviewer sees the diff, the brief and the screenshots, never this
session's history. It does not get to spawn reviewers of its own. It reports
findings with `file:line`, a concrete failure scenario, a severity, and ends
with one verdict line: `VERDICT: CLEAN` or `VERDICT: ISSUES`. A visual
finding names the screenshot and what is off in it; the fix is in the code
behind that preview, so re-render (step 2b) before the next round.

## 4. Loop

- `ISSUES`: fix every Critical and Important finding, in TDD where the finding
  is behaviour. A Minor finding is fixed when it's a one-liner, otherwise
  noted. A finding that is wrong gets pushed back with the code or test that
  proves it, in `{PREVIOUS_FINDINGS}` of the next round, not silently dropped.
  Run the quality gates. Back to step 3 with a fresh subagent.
- `CLEAN`: step 5.
- **Three rounds** without `CLEAN`: stop. Report the findings that keep coming
  back; that's an architecture question for the user, not a fourth attempt.

## 5. Hand over

Quality gates green, everything committed. Then one message to
the user, standing on its own:

- what was built, in the user's terms
- what the review found and how each finding was resolved, pushbacks included
- what was left out or deferred, and why
- the branch name and the commit list

No push, no PR: that's the user's call, asked for explicitly each time.

## Red flags

| Thought | Reality |
|---|---|
| "I'll ask this one as it comes up" | One interview. Collect, then ask once. |
| "The test is obvious, I'll write it after" | Then it tests what you wrote, not what was asked. Red first. |
| "I can review this myself, it's small" | The reviewer's value is a context that doesn't know what you meant. Dispatch it. |
| "That finding is wrong, skip it" | Push back with evidence in the next round. Silence looks like a fix. |
| "Round four will get it" | Three rounds of the same finding is a design problem. Stop and say so. |
| "Let me re-record goldens to be sure" | Not until the PR. Docker is three minutes you spend once. |
| "The tests pass, it must look fine" | Tests don't have eyes. Render it and look, then let the reviewer look. |
| "I'll render all the previews to be safe" | Only the ones this task touched, with `--tests`. The rest have goldens. |
| "A preview for this is overkill" | A preview is the only way anyone sees it before the user does. |
