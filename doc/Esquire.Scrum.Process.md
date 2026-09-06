<table style="width: 100%; table-layout: fixed;">
  <tr>
    <td style="width: 12%"><img src="../favicon.ico" alt="Esquire logo" align="right" valign="middle" width="64"></td>
    <td style="width: 88%;">
       <h1>Esquire Application Frameworks(tm) 2.0</h1>
    </td>
  </tr>
</table>

# Esquire -- The Process in Scrum Terms

`Esquire.DevProcess.md` describes how Esquire is built, in its own words. This document says the same
thing in the vocabulary of Scrum, and puts the repositories' own numbers behind every claim. Nothing
here is a second process: it is one process, named twice.

**The product is four repositories, released as one.** Every number below is read from those four.

| Repository | Holds | Commits | Tracked files | Size |
|---|---|---|---|---|
| `esquire.services` | the backend services (19 Maven modules), the deployment charts and scripts, and the project documentation | 197 | 1,554 | 35,003 lines of main Java, 17,456 of test Java |
| `esquire.explorer` | the SPA frontend, the BFF, the Playwright e2e suite and the hauberk load harness | 144 | 368 | 8,457 lines of TypeScript, 6,948 of Java |
| `esquire.ui.lib` | the shared component library the frontend is built from | 52 | 130 | 8,997 lines of TypeScript |
| `esquire.db.seed` | the schema and its seed, in a Postgres and an Oracle branch | 55 | 100 | 7,786 lines of SQL |

**448 commits in total**, first commit 2025-12-26, current release **v1.2.15** tagged 2026-09-07 in all
four. Figures are as of that tag; how they were produced is in the last section.

---

## 1. The team, and the three roles

One maintainer. Product Owner, Scrum Master and Developer are the same person, so nothing is negotiated
between them. What replaces the negotiation is that **each role's decision is written down before it is
acted on**:

- the **Product Owner** decision is the sprint headline and the scope list. Only what the sprint's
  planning document names is in the sprint; nothing is pulled in because it happened to be nearby.
- the **Scrum Master** decision is the process document itself -- the cycle, the gates, the release
  touch-list. It changes deliberately, not per sprint.
- the **Developer** decision is the code, and it carries its reasoning along the same trail.

A written decision can be re-read and contradicted later. That is the guard against one person quietly
moving the goal to meet the work. It is also why the planning documents are committed next to the code
instead of living in a tracker: `doc/plans/tasks12NN.md`, one per sprint, versioned with the sprint.

---

## 2. The mapping

| Scrum | Esquire | Where it lives |
|---|---|---|
| Product backlog | numbered `CD-n` items grouped by area; a number is never reused and a finished item stays as a heading-only stub | `doc/Esquire.ContinuingDev.md` -- **27 open, 2 completed**, in 7 areas |
| Backlog refinement | triage: every item gets a recorded disposition -- accept / reject / postpone -- with its rationale | the sprint plan, then `Esquire.Q&A.md` |
| Sprint | one **Micro** version, `v1.2.N` | 14 sprints, v1.2.2 to v1.2.15 |
| Sprint goal | the sprint headline -- one theme, named when the sprint opens | `doc/v1.2.x.Planning.md` |
| Sprint backlog | the sprint plan: `T1..Tn`, opened by a list of settled decisions | `doc/plans/tasks12NN.md` -- 15 to 39 items |
| Sprint planning | writing that plan | the same file |
| Definition of Done | the seven-step development cycle, section 4 below | `Esquire.DevProcess.md` section 3 |
| Increment | a tag in every repository that had work, and the cloud demo moved onto it | `v1.2.N` in all four repositories |
| Sprint review | release finalization -- the increment is deployed and can be shown running on a public address | `Esquire.DevProcess.md` sections 8, 8a |
| Retrospective | the read-back rounds; what they find becomes backlog items and the next sprint's plan | sprint plan -> `Esquire.ContinuingDev.md` |
| Impediment log | the same backlog -- an obstacle is filed as a numbered item, not carried in the head | `doc/Esquire.ContinuingDev.md` |
| Daily scrum | none -- see section 8 | -- |

---

## 3. The sprint, measured

A sprint is **scope-boxed, not time-boxed**: it ends when its scope is done. What that produced over the
v1.2.x line:

| Sprint | Headline | Tagged | Days | services | explorer | ui.lib | db.seed | Commits |
|---|---|---|---|---|---|---|---|---|
| v1.2.2 | The Establishment -- first complete vertical slice | 2026-05-03 | 90 | 49 | 46 | 41 | 16 | 152 |
| v1.2.3 | Backend-for-Frontend | 2026-05-08 | 5 | 7 | 5 | 3 | 1 | 16 |
| v1.2.4 | Hauberk load harness + non-browser auth patterns | 2026-05-18 | 10 | 5 | 1 | 0 | 1 | 7 |
| v1.2.5 | bizTree "Taijitu" cache refactor | 2026-05-24 | 6 | 9 | 4 | 0 | 2 | 15 |
| v1.2.6 | enyMan redundancy, entity-id minting | 2026-06-02 | 9 | 8 | 5 | 0 | 0 | 13 |
| v1.2.7 | Audit logging + CI/CD pipeline | 2026-06-10 | 8 | 15 | 9 | 0 | 5 | 29 |
| v1.2.8 | The Messaging Bus | 2026-06-20 | 10 | 12 | 7 | 0 | 4 | 23 |
| v1.2.9 | Hardening | 2026-06-25 | 5 | 14 | 4 | 0 | 4 | 22 |
| v1.2.10 | Resilience / durability, high availability on the cloud | 2026-07-05 | 10 | 13 | 9 | 0 | 4 | 26 |
| v1.2.11 | Observability | 2026-07-27 | 22 | 20 | 18 | 3 | 4 | 45 |
| v1.2.12 | Per-entity change number | 2026-08-11 | 15 | 6 | 4 | 0 | 4 | 14 |
| v1.2.13 | Compact topology + hardening | 2026-08-28 | 17 | 12 | 6 | 0 | 0 | 18 |
| v1.2.14 | AWS | 2026-09-02 | 5 | 10 | 7 | 0 | 0 | 17 |
| v1.2.15 | User activation | 2026-09-07 | 5 | 6 | 4 | 1 | 1 | 12 |

**Reading the table.**

- **v1.2.2 is the establishment sprint** -- 90 days, the first complete vertical slice built from nothing.
  It is not a cadence figure and is left out of the averages below.
- **The thirteen sprints after it: 5 to 22 days, median 9.** 257 commits, median 17 per sprint.
- **The long ones are the ones that added a stack, not the ones that ran late.** v1.2.11 (22 days) added
  the whole observability stack; v1.2.13 (17 days) added a second deployment shape and then read the
  solution back item by item.
- **A repository moves only when the sprint needs it.** `ui.lib` took part in 4 sprints of 14, `db.seed`
  in 11. A repository with nothing to say releases nothing -- a normal outcome, not a gap.
- **Commit counts are a weak measure of work** and are given as scale, not as velocity. The sprint plan is
  the record of what was decided and done.

---

## 4. Definition of Done

An item is done when, **in every repository it touched**:

1. the code and its configuration are in;
2. unit and integration tests are green (`mvn -q -pl <svc> -am test`);
3. the change has been verified live on the docker stack;
4. the e2e suite and the smokes are green on docker **and** on local Kubernetes;
5. the source history header, the module's `changes.txt` and the `release_notes.txt` entry are written;
6. it is committed on the sprint branch.

Then the pipeline deploys it, and it is verified again on the deployed target. "It compiles and the tests
passed" is step 2 of six.

**Once per sprint, per target, the stack is built from nothing.** A stack that is merely up hides a whole
class of defect -- a chart value only a fresh install reads, an image the daemon still holds under an old
tag, a seed that never replays. Five such defects survived days of work in v1.2.13 because every check ran
against a stack that had been up for a week.

The gates are automated wherever a machine can hold them: **3 GitHub Actions workflows** -- CI on every
push, a local-Kubernetes deploy on every push to a sprint branch, and a cloud deploy when the sprint merges.

---

## 5. One sprint, four repositories

A sprint is defined against the **product**, not against a repository. A single item often touches three of
the four -- a new field is a `db.seed` change, a service change and an explorer change -- and what Scrum
would solve with a shared sprint goal across teams is solved here by one version number:

- all four repositories run the **same branch flow under the same sprint name**: work on `pending-v1.2.N`,
  PR into `develop`, tag `v1.2.N`, archive `release/v1.2.N`;
- an item is **not done until every repository it touched is documented and promoted**. The commit-prep
  step diffs each repository against its own mirror, so "documented the one I edited last" does not pass;
- the repositories are **promoted together**, which is the condition the cloud pipeline rests on when it
  builds a deployment from `develop` in all of them;
- the local pipeline reads the **sprint branch** of the other repositories, because it validates the sprint
  while it is still in flight; the cloud pipeline reads `develop`, because by then `develop` is the release.

---

## 6. The increment, and what "review" means here

The increment is not a demo build. Every sprint ends with **the framework running on a public address**:
the standing cloud demo is moved onto the released tag, and the browser end-to-end suite is run against it
there.

Release finalization is the review, and it has a written touch-list because none of it is caught by a build.
The README version notes roll down, the roadmap row gets its date and what actually shipped, the landing
pages and the component-model drawings are re-checked against what the sprint changed, and the milestone
report is generated per repository out of that repository's own release notes and `changes.txt` files.

---

## 7. Quality gates, by the numbers

| Tier | What it drives | Count |
|---|---|---|
| Unit tests | mocked collaborators, in-JVM | 125 test classes |
| Integration tests | the app in-JVM against real Postgres / KeyCloak / broker containers | 4 |
| Frontend unit specs | the component library and the SPA | 25 specs in 4 files |
| Browser end-to-end | Playwright, run on docker AND local Kubernetes, then against the cloud | 50 tests in 20 spec files |
| Load / stress / race repro | the hauberk harness on Gatling | 22 Simulations over 32 reusable Chains |
| Configuration matrices | one workload pushed through a grid of configurations, asserted in the database | ~27 cells |

---

## 8. Where this is deliberately not Scrum

- **No time-box.** A sprint ends when its scope is done. The observed range is 5 to 22 days. A fixed box
  would be paid for out of the verification, which is the part worth keeping.
- **No estimates, no velocity, no points.** With one developer the number would measure nothing the finished
  sprint plan does not already say. The commit counts in section 3 are scale, not velocity.
- **No daily scrum.** Its job -- knowing what changed and what is blocked -- is done by the sprint plan, and
  by the rule that the state of the work is read from a diff against the repository, never from memory.
- **Sprints can run in parallel.** Since the v1.2.x horizon closed, the framework is in
  continuous-development mode: more than one sprint line can be open at once, each against its own target and
  finalized on its own schedule. That is closer to Kanban than to Scrum, and it is the shape support work
  actually has.
- **The backlog is not ranked.** Items are grouped by area and picked when a sprint opens; there is no
  maintained priority order, because the person picking is the person who wrote them.
- **No separate decision records.** Scrum says nothing about them, but most teams that run it keep ADRs.
  Esquire records a decision along the path that produced it -- the planning disposition, the durable
  rationale in `Esquire.Q&A.md`, the mechanism in the design doc -- so the "why" is versioned with the "what".

---

## 9. Documentation is a sprint deliverable, not a phase

Every commit that changes code writes three things: the source file's history header, the module's
`changes.txt`, and a dated `release_notes.txt` entry. That is what makes the milestone reports generatable
instead of written.

| Surface | services | explorer | ui.lib | db.seed |
|---|---|---|---|---|
| dated release-note entries | 133 | 85 | 44 | 27 |
| per-module `changes.txt` files | 21 | 3 | 1 | 2 |

Alongside them, **28 design documents** in `services/doc` (14,734 lines), plus the per-sprint plans and the
milestone reports.

---

## 10. How these numbers were produced

All of it is read from the four repositories, so any figure can be re-checked:

- **Commits per sprint**: the sprint window is the interval between two consecutive `v1.2.N` tags in
  `esquire.services`; within that window each repository is counted with
  `git rev-list --count --since=<from> --until=<to> develop`. One repository sets the window because all
  four are tagged with the same sprint name on the same release.
- **Tag dates**: `git for-each-ref --sort=creatordate refs/tags`.
- **Sizes**: `git ls-files` filtered by extension, lines counted with `wc -l`.
- **Test counts**: from the tools, not from a file pattern -- `playwright test --list` and `hauberk list`
  are the authority, because a test declared through an alias and a simulation declared on an abstract base
  are both invisible to a grep. The counts above match `Esquire.TestingStack.md`.
- **Backlog counts**: the item headings in `Esquire.ContinuingDev.md`, split at its `## Completed` section.
