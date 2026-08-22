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

## 8. Sprint finalization

At sprint end, beyond the per-commit code-change docs:

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
- Run the **release finalization** proper: version finalize, then the branch flow
  `pending → PR → develop → tag → archive release/ → new pending` (the maintainer's git step). Each
  sprint runs this flow on **its own pending line** off `develop`; in continuous-development mode more
  than one such line can be open at once, each finalizing independently.

---

## 9. Why this instead of ADRs

A formal ADR is an isolated, after-the-fact record of one decision. Esquire's pipeline records the
same information — *what* was decided, *why*, how it was *verified*, and *where* it was realized —
but does so **inline with the work**: the planning/triage disposition, the durable Q&A rationale, and
the design-doc mechanism together form a living decision trail that is never detached from the code,
tests, and docs that carried the decision out. The process *is* the record.
