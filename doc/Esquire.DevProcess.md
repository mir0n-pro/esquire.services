<table style="width: 100%; table-layout: fixed;">
  <tr>
    <td style="width: 12%"><img src="../favicon.ico" alt="Esquire logo" align="right" valign="middle" width="64"></td>
    <td style="width: 88%;">
       <h1>Esquire Application Frameworks(tm) 2.0</h1>
    </td>
  </tr>
</table>

# Esquire — Development Process

How Esquire is built: the integrated pipeline from **analysis & planning →
coding → testing → documenting → releasing**. This is also Esquire's decision-recording approach —
deliberately in place of formal ADRs. Decisions are not filed off to the side as after-the-fact
records; they are captured continuously as a change moves through the pipeline.

Companion to `Esquire.DevSetup.md` (how to stand up the environment). This doc is the *method*;
that one is the *machinery*.

---

## 1. Principles — how a change is approached

- **The framework design is stable and deliberate.** A reported symptom is a QUESTION to diagnose,
  not a work order. Reproduce it, explain what the current design does and why, and CONFIRM it is a
  real defect before touching code. Many "symptoms" are intended behavior.
- **No fast follow-ups, no quick-and-dirty fixes, no temporary half-states.** A change is
  design-affecting until proven otherwise — default to investigate, not patch.
- **Locked decisions.** Once a direction is chosen it is not re-litigated or quietly tweaked.
- **Complete what we start.** Code + config + docs land together; no half-finished extensions.
- **Explicit scope.** Only items named for a sprint belong to it; recent work is not auto-included.
- **Precise, plain wording** — in code, chat, and docs. Say what actually happens.

---

## 2. Analysis & planning — the front of the pipeline (and the ADR alternative)

Each sprint keeps a **sprint planning / triage document** — the `tasks**` file. Its convention:

- **Location & name**: `doc/plans/tasks<version>.md`, where `<version>` is the sprint's version
  digits — `doc/plans/tasks1210.md` for v1.2.**10**, `doc/plans/tasks129.md` for v1.2.**9**.
- **Role**: the living record of the sprint's thinking — the scope list, the per-item triage, and
  every disposition with its rationale and verification. It is created at sprint start and worked
  throughout; at sprint end its durable content settles into its proper home (design docs / Q&A) and
  the file is archived with the sprint.

It is the living record of the sprint's thinking:

- Backlog items, review findings, and design questions are triaged **one by one**, each with a
  **recorded disposition** — accept / reject / postpone — plus the **rationale** and, where acted on,
  the **verification**.
- **This disposition trail is the decision record.** Rationale that outlives the sprint migrates to
  `Esquire.Q&A.md` (the durable "why / why-not" log); the *mechanism* of a decision goes to the
  design docs (`doc/Esquire.*.md`). So a decision is captured across three tightly-linked surfaces —
  planning disposition → Q&A rationale → design-doc mechanism — as a continuous, integrated trail
  rather than an isolated ADR file written after the fact.
- **The design rationale is in the repo, not in someone's head.** The `tasks**.md` discipline
  deliberately moves the reasoning behind a change out of the ephemeral working session and into a
  durable, versioned artifact committed alongside the code. The "why" is reviewable and diff-able
  with the "what".
- **Scope is explicit.** The tasks doc names what is in; nothing is pulled in by proximity.

This is why Esquire does not keep formal ADRs: the decision, its reasoning, its verification, and
its realization are already recorded *along the path that produced them*, never separated from the
work.

---

## 3. The per-phase development cycle

Every intermediate phase runs the SAME full cycle — not only at sprint end:

1. **Develop** — write the code / config.
2. **Test** — unit + integration tests green (`mvn -q -pl <svc> -am test`; JaCoCo coverage at
   `target/site/jacoco`).
3. **Run on docker** — deploy to the local sandbox and VERIFY the change live.
4. **Document changes** — source history headers + per-module `changes.txt` + `release_notes.txt`.
   This is the commit-prep step; it happens HERE, right before the commit — never earlier during dev.
5. **Commit** — to the pending branch (`pending-v1.2.x`).
6. **GHA deploys** — the push triggers GitHub Actions: CI, then deploy to docker + local
   Docker-Desktop k8s (self-hosted runner on `pending-**` push).
7. **Run e2e & smokes** — on docker AND local k8s; confirm the change is in place on both targets.
   The deploy jobs bring up whichever **deployment shape** the machine already runs (classic or compact) and
   remove the other; a change that touches the request path is worth proving on both shapes before release.

**Once per sprint, per target, per profile: build it from nothing.** A stack that is merely *up* hides a
whole class of defect -- a chart value that only a fresh install reads, an image the daemon still has under
an old tag, a seed that never replays. Five such defects survived days of work in v1.2.13 because every
check ran against a stack that had been up for a week. The routine is `k8s-down` (or `docker-compose-down`),
then the rebuild, then up, then the four running-stack suites -- and it belongs in the sprint, not in the
release, so what it finds can still be fixed calmly.

**Re-arm observability after a deploy, on the local targets.** A deploy installs the shipped defaults, where
observability is off, so the boards go red on docker and local k8s while the stacks are healthy and serving.
Nothing is wrong: run `o11y-on` (compose) or `o11y-full-on` (k8s) again if the boards are wanted, then drive
traffic before reading them.

The **git boundary is the maintainer's**: steps 1–4 (develop, test, verify, prepare docs) are the
working phase; steps 5–7 are maintainer-gated.

---

## 4. Testing tiers

- **Unit + integration** — `mvn test`. The integration tests (`*IntegrationTest`, Testcontainers +
  `@SpringBootTest`) run the app in-JVM against real Postgres / KeyCloak / broker containers.
  Coverage (unit + in-JVM ITs, combined) via JaCoCo.
- **End-to-end** — Playwright, run on BOTH targets (docker `:4200`, local k8s `esquire.localhost`).
  Mutating specs build and tear down their own working data under the seeded Test House.
- **Load / stress** — the hauberk Gatling harness.
- **Smokes** — targeted resilience checks (e.g. broker-down) on both targets.

---

## 5. Documentation discipline

- **Surfaces**: source `/* History */` headers, per-module `changes.txt`, `doc/release_notes.txt`,
  design docs (`doc/Esquire.*.md`), and `Esquire.Q&A.md` (the decision-rationale log).
- **Document only the FINAL committed state** — never the journey, abandoned approaches, or
  mid-sprint replacements.
- **Docs are prepared at commit-prep, not during dev.** History headers and `changes.txt` stay
  frozen until immediately before the commit.
- **New design-doc placement is DEFERRED to sprint end** (interim notes live in the tasks doc until
  the final structure is settled). New per-task doc files are not created mid-sprint.
- **README = front-door plain prose**; jargon and setup detail live in `doc/`.
- The routine is codified as the `/document-changes-esquire` step (diff each repo against its mirror,
  document every changed source file, skip unit/IT tests, record e2e coverage in release_notes).

---

## 6. Environments & workspace separation

- **Three separated spaces, never mixed**: the dev tree (working copy), the git mirror (the repo),
  and the CI-runner checkout. Promotion dev-tree → mirror is the maintainer's deliberate step,
  reviewed at commit — that review is the "forgot a file?" net.
- **Run targets**: the docker sandbox, local Kubernetes (Docker Desktop), and the cloud (OKE).
  Setup for each is in `Esquire.DevSetup.md`.

---

## 7. Versioning & release cadence

- Version format `vMajor.Minor.Micro-YYMM.DDHH` (DDHH = day + current hour).
- The **micro version bumps at SPRINT START**, not at finalization; within a sprint only the
  datetime advances per entry.
- **One commit = one dated entry** — all of a commit's changes go into one entry. The version lives
  in `release_notes.txt` only.

**Single line, then parallel.** Through v1.2.x, development ran as **one sequential line** — one
sprint, one Micro version, at a time (v1.2.2 → … → v1.2.11). With the active-development horizon
complete, the framework moves into **support / continuous-development mode**: sprints are still
defined against a target and still carry a version, but they no longer march in one strict sequence —
**several sprints can run in parallel**, each on its own pending line (§8), each finalized on its own
schedule. The pool of candidate targets is [Esquire.ContinuingDev.md](Esquire.ContinuingDev.md).

---

## 7a. Pinning the infrastructure images

Three images are Esquire's own, built on an upstream one: **postgres** (the database seed), **keycloak** (the
realm import and the login theme) and **activemq** (the broker configuration and the metrics exporter). Each
declares its **pin** at the top of its Dockerfile, and the build stamps both values onto the image as labels,
so what an image is travels with the artifact rather than being written down beside it:

```dockerfile
ARG BASE=quay.io/keycloak/keycloak:26.4.7   # the upstream image, never a literal in FROM
ARG PIN=v1.2.13-2608.2320                   # the release in which Esquire's content here last changed
FROM ${BASE}
```

**`BASE` is an input, not a constant.** A target that wants a different upstream passes `--build-arg` and says
so where the choice is made: docker runs KeyCloak 26.6.0 while k8s and the cloud stay on 26.4.7. Every
docker-side file that BUILDS the image carries that `args:` block -- the two stack compose files and
`services/keycloak/compose.yaml` -- so whichever one is used, the tag names what is inside it. Without this,
a base moves the moment somebody edits a `FROM`, and the next release build carries it everywhere with
nothing recorded.

**`PIN` is edited by hand, and only when that image's Esquire content changes.** It is the tag: the builders
read it back off the image and tag from it, so nothing is typed twice. An image that gained nothing this
release keeps the tag it had — a release cannot replace infrastructure it did not touch, and a rebuild that
changed nothing rolls nothing.

**The rule that keeps the pin honest:** if the pin already names a **different** image, the content changed and
nobody moved the pin, and the build refuses. A pin that can mean two images is worth less than no pin.

**At sprint finalization**, for each of the three: if its content changed during the sprint, move `ARG PIN` to
the release stamp, rebuild, and let the values be re-pinned. If it did not change, leave it — that is the point.
The postgres pin and the seed's `DB_VERSION` move together, since the seed is what that image carries.

The cloud is the one exception to the tag, on purpose: `oke-up.bat` sets a **single release tag for the whole
stack**, infrastructure included, so every image in a deployment is named by one release. The labels still say
what is inside each of them.


---

## 7b. What each pipeline builds from

Esquire is three repositories -- `services`, `explorer`, `db.seed` -- and a deployment is built from all
three. Which branch of the other two a pipeline reads is decided by **when that pipeline runs**, and the two
answers differ on purpose.

| pipeline | runs when | reads explorer + db.seed from |
|---|---|---|
| **CI** (`ci.yml`) | every push | this repository only -- the reactor build and the unit tests |
| **local deploy** (`deploy-local.yml`) | a push to `pending-**` | the **sprint branch** when it exists, falling back to `develop` |
| **cloud deploy** (`deploy-oke.yml`) | a `pending-*` PR **merged** into `develop` | **`develop`** |

**The local pipeline validates the sprint while it is still in flight**, so it has to read the sprint's own
work in the other repositories -- an e2e spec added during a sprint lives in `explorer` and is not on
`develop` yet. **The cloud pipeline runs after the merge**, at the moment `develop` IS the release, so
`develop` is what it reads. Neither is a fallback for the other.

What holds the two together is that **the three repositories are promoted together**: a services release that
needs a seed or an explorer change reaches `develop` alongside it. That is the condition the cloud pipeline's
choice rests on -- part of the release, not an assumption about it.

## 8. Sprint finalization

At sprint end, beyond the per-commit code-change docs:

- Move the **infrastructure pins** that need moving (§7a) — one check per Esquire-built infra image.
- Settle the **deferred design-doc placement** — interim `doc/<sprint>.tasks` notes and any saga docs
  land in their proper, visible home now that the final structure is known.
- Refresh the **README(s)**, the landing page, and other release-facing material. The per-version README
  notes — "**do the versioning**", i.e. describing what the sprint delivered, NOT bumping build-file
  versions — follow a strict **roll-down**:
  - **Add** the finished sprint as the newest version in FULL detail (intro line naming the sprint theme +
    bullets / per-subproject table), heading dated the **finalization date** (not the change date), and
    **no** "More Details" link (its release branch is not archived yet).
  - **Collapse** the previously-newest version down to a **single sentence + its release-branch
    `[More Details: vX.Y.Z README](…/tree/release/vX.Y.Z?tab=readme-ov-file)` link** — it just lost "newest".
  - Per-repo shape: `db.seed` and `explorer` use `## vX — complete (date)` sections (collapse the prior one
    in place). `services` uses BOTH a top **blockquote callout** — current sprint only + a horizon line, with
    the prior sprint note **removed** — AND a `## Release History` section that rolls down like the others.
  - **Front-door prose only**: omit mechanical plumbing (e.g. the `DB_VERSION` / package-version bump); the
    horizon line names only the current release line's remaining sprints — no next-major / future-product promo.
  - **Milestone-report placeholder**: in the Release History table, reserve the new version as a real linked
    reference in the existing convention (`[vX.Y.Z Milestone Report](…/report_vX.Y.Z.md)`) — a deliberately
    dead link that resolves once the report lands after release — in every repo row that had work this sprint.
- The finalization refresh itself (the README version notes, the landing-page / component-model /
  diagram refresh, other release-facing copy) IS the sprint's release-facing documentation &mdash; it
  does NOT get its own dated `release_notes` change entry. Per-commit `release_notes` entries are for the
  sprint's CODE changes, recorded when each landed; the finalization refresh only re-states them for the
  front door.
- Put the **standing cloud demos** on the released tag. OKE is deployed by GitHub Actions and needs
  nothing here. **AWS runs the same chain without a workflow** (`Esquire.GitHubActions.md` section 4.3a),
  and THIS is where its trigger lives: while the demo is up, `aws-release.bat <tag>` from
  `services/k8s-aws-compact/`, then `aws-e2e-public.bat`. Nothing turns red if this is skipped; the demo
  simply serves the previous sprint, which is how it was found.
- Run the **release finalization** proper: version finalize, then the branch flow
  `pending → PR → develop → tag → archive release/ → new pending` (the maintainer's git step). Each
  sprint runs this flow on **its own pending line** off `develop`; in continuous-development mode more
  than one such line can be open at once, each finalizing independently.

---

## 8a. The release touch-list -- what gets edited every time

Every one of these has been missed at least once. They are listed together because nothing in the build
points at them: no test fails, no pipeline turns red, and a stale one is only found by reading it.

**At SPRINT START (not at release):**

| Place | Care |
|---|---|
| `services/pom.xml` | the Micro version. Bumped here so the build tag and the image tag do not lag. |
| `explorer/frontend` package version | frontend only. |
| `db.seed` `DB_VERSION` | BOTH vendors, and only when the sprint has schema work. |

**At RELEASE (finalization):**

| Place | Care |
|---|---|
| `services/README.md` | the top callout carries the CURRENT sprint only -- the previous sprint's note is REMOVED, not collapsed. It says what the sprint delivered, so re-read it: it was written when the sprint opened and usually describes only half of what shipped. |
| `services/Releases.md` | the new version added in FULL, with NO "More Details" link (its release branch is not archived yet); the previously-newest collapsed to one paragraph PLUS its `release/vX.Y.Z` link; a milestone-report placeholder row for every repo that had work -- a deliberately dead link that resolves when the report lands. |
| `explorer/README.md` | same roll-down in its own shape: `## vX -- complete (date)` in full, the prior one collapsed to a sentence plus its link. |
| `explorer/frontend/src/index.html` | the JSON-LD `softwareVersion`. It names the RELEASED version -- what a visitor can actually get -- so it is set to the version being tagged. Nothing points at it: it read four releases behind for months. |
| `doc/v1.2.x.Planning.md` | the roadmap row gets its DATE and what actually shipped. The forecast is usually wrong -- v1.2.13 was planned as "fresh-mind hardening" and shipped as compact topology and hardening. Delivered rows carry a date; do not leave a `*planned*` placeholder row in a table that records deliveries. |
| `doc/v1.2.x.Goal.md` | only when the sprint changed what Esquire IS. A sprint that adds a deployment shape or a stack part changes statements written as absolutes elsewhere in the file; find them, do not only add. |
| `doc/Esquire.ContinuingDev.md` | CD items the sprint discharged move to `## Completed` as heading-only stubs. The numbering is a stable reference and a number is never reused, so an item is never deleted outright. Check the whole file: an item that shipped often still reads as a proposal. |
| `db.seed/README.md` | the same roll-down, in its own `## vX -- complete (date)` shape. Touched even in a sprint with no schema work -- the section says what the sprint meant for the seed, which may be "nothing". |
| `esquire.ui.lib/README.md` | the same, if the library moved. Easy to forget: it is the repo that changes least. |
| `db.seed` / `ui.lib` `doc/release_notes.txt` | each repo keeps its own; a commit spanning repos is documented in EVERY repo it touched, not only the one edited last. |
| `explorer/frontend/public/landing/*.html` | the six landing tabs -- what-is-it, who-needs-it, why-it-matters, vs-competition, architecture, vision. A sprint that changes what the framework IS makes a claim on these pages stale or overstated. |
| `explorer/frontend/public/img/ComponentModel*.png` | the component-model drawings, one per shape. A sprint that adds, removes or composes a service leaves them wrong, and nothing checks a picture. |
| `explorer/frontend/public/img/og-banner.png` | the social banner, plus its `?v=` cache-buster in `index.html` -- a changed banner served under an unchanged query string is not seen. |
| `<repo>/doc/reports/report_vX.Y.Z.md` | the milestone report -- GENERATED, not written: run `git_gen_rep [from [till]]` from the repo (`git-utilities/git-gen-rep`), which builds it from that repo's `release_notes.txt` and its `changes.txt` files over the given tag range. Run it AFTER the tag, in every repo that had work -- it is what the `Releases.md` placeholder links resolve to. What it produces is only as good as the entries made per commit, which is why those are written when each change lands. |
| `doc/Esquire.TestingStack.md` | recount every figure against reality. They live in TWO places -- the summary row and the per-module table -- and updating one leaves the file contradicting itself. Count with the tools, not with grep: `playwright test --list` and `hauberk.cmd list` are authoritative, because tests declared through an alias and abstract simulation bases are invisible to a pattern. |

**None of this gets its own `release_notes` entry.** The finalization refresh IS the release-facing
documentation; per-commit entries record the sprint's CODE changes, made when each landed (see 8).

---

## 9. Why this instead of ADRs

A formal ADR is an isolated, after-the-fact record of one decision. Esquire's pipeline records the
same information — *what* was decided, *why*, how it was *verified*, and *where* it was realized —
but does so **inline with the work**: the planning/triage disposition, the durable Q&A rationale, and
the design-doc mechanism together form a living decision trail that is never detached from the code,
tests, and docs that carried the decision out. The process *is* the record.
