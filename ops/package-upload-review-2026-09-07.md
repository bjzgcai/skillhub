# Package Upload Change Inventory and Review

Date: 2026-09-07 (UTC)
Branch: `feature/package-upload-limits`, HEAD `ccb242a`.

## Status

This is a working-tree cleanup and diagnostic review, not merge approval.
Full quality gates are blocked. No commit, push, deployment, runtime script
sync, or production database change was performed during this cleanup.
The fixes described below exist locally only.

## Remaining Findings

1. **P1: rollback can select stale images after a component-only release.**
   `release-lib.sh:38` records shared-env image tags in `release.json` even
   for the component that is not being released. `deploy-release.sh:76`
   writes the actual server image to `manifest.json`, but
   `rollback-release.sh:55` reads only `release.json`. A later server/all
   rollback to a web release can therefore select an unrelated server image.
   The production web release `20260902T113316Z` demonstrates the drift:
   its release JSON lists `prod-latest-abdb516`, while the server runs
   `prod-local-20260826T0902-validation-fix`. Reconcile the snapshot format
   and rollback reader, with component-only deployment regression tests.

2. **P1: storage preflight failure leaves the server removed.**
   `deploy-release.sh:106` removes the server before the storage precheck.
   The failure branch at line 110 exits without restoring it. Run all
   non-destructive prechecks before changing server/scanner containers.
   Also cover container-start failure and post-storage-check recovery.

3. **P2: all-component deployment loses its original manifest.**
   `deploy-release.sh:164` writes an all-component manifest, then
   `apply_server` and `apply_web` overwrite the same file. Its final
   component becomes `web`, and the previous server/scanner may already be
   the newly deployed versions. A web failure also does not restore the
   already-updated server. This is existing deployment orchestration debt;
   do not treat an all-component release as an atomic transaction.

These broader existing release issues were left for a dedicated, mocked
deployment/rollback test harness rather than silently changing production
recovery semantics in a small cleanup.

## Fixed Locally

- Scanner rollback now passes the selected recorded image to the ensure
  helper instead of checking one image and starting the shared-env image.
- The scanner ensure helper checks image availability before removing the
  running container. Tests cover explicit rollback tags and missing images.
- The release helper's follow-up command retains the scanner tag and SSH host.
- Replaced the pnpm esbuild build-approval placeholder with `true`; included
  the workspace configuration in the Docker dependency-install layer.
- Fixed request aborts being reported as network failures instead of HTTP
  408. Browser verification reproduced the error before the fix and the
  localized publish-timeout message after it.
- Added gzip expansion-limit/truncation tests and frontend publish/preview
  timeout, success, failure, and timer-cleanup coverage.

## Suggested Commit Groups

Keep the existing upload-limits commit `ccb242a`. Do not mix all remaining
files into a single commit. No staging or history rewriting was performed.

1. `fix(db): restore deployed download event migration`
   - `server/skillhub-app/src/main/resources/db/migration/V45__skill_download_event.sql`
   - This is restoration of an already-executed migration, not a new schema
     feature. Keep its filename and bytes unchanged despite V46/V47 existing.
   - Production JAR/local SHA256: `05015b6e55e48321e37d9eb261775b783e9dc6a16be8d29e256b796af37370d4`.
   - No download-event producer was found in the current Java sources;
     restoring this table must not be described as complete download analytics.
2. `fix(package): accept namespaced svg and compressed json payloads`
   - `SkillPublishProperties.java`, `application.yml`, `SkillPackagePolicy.java`,
     `SkillPackageValidatorTest.java`.
   - Gzipped JSON currently receives bounded gzip/UTF-8 validation, not JSON
     syntax validation, consistent with plain JSON's existing text checks.
3. `fix(web): extend publish timeout and classify aborts correctly`
   - `web/nginx.conf.template`, `web/src/shared/hooks/use-skill-queries.ts`,
     `web/src/api/client.ts` and their new tests.
4. `chore(web): pin nginx and complete build approval configuration`
   - `web/Dockerfile`, `web/pnpm-workspace.yaml`.
   - Local checks use pnpm 11.10.0; Docker remains on pnpm 9.15.4.
     The image build itself has not been validated in this review.
5. `fix(ops): track scanner release tags and honor rollback images`
   - Four modified ops scripts, `scripts/tests/release-lib-test.sh`.
   - Resolve remaining release findings before treating this group as
     production-ready. Keep review notes and browser evidence identifiable.

## Verification

- PASS: `make typecheck-web`, `make lint-web`, `make build-frontend`.
- PASS: `bash scripts/tests/release-lib-test.sh`; shell syntax and diff checks.
- PASS: 55 targeted backend tests: `SkillPackageValidatorTest` (29),
  `MultipartPackageExtractorTest` (6), `ZipPackageExtractorTest` (3),
  `SkillPackageArchiveExtractorTest` (12), `UnifiedSecurityPrePublishValidatorTest` (5).
- PASS: 7 new frontend tests in `client-timeout.test.ts` and `publish-request.test.ts`.
- BLOCKED: full frontend tests: 536 passed, 4 failed (180 files).
  Existing mock drift in `landing.test.tsx`
  (`useCurrentWeeklySkill`) and `dashboard/review-detail.test.tsx`
  (`useReviewBadgeOptions`). These tests were not weakened or changed.
- BLOCKED: `make test-backend-app` reaches skillhub-app but reports 187 errors
  out of 446 app tests. Startup logs require DingTalk SSO credentials that the
  test profile does not provide. Preceding reactor modules passed.
- NOT RUN: security-scanner tests could not start because this interpreter
  has no pytest installed. No global Python dependencies were installed.
- Build warnings: existing large frontend chunks and outdated Browserslist data.
- No Controller contract changed; generated OpenAPI types were not edited.

## Browser Evidence

Playwright MCP used the local Vite page with intercepted API responses and
a virtual clock. No production upload was made. This verifies frontend
behavior, not a real five-minute backend scan or nginx integration.
The generated ZIP is a UI fixture, not a validated publishable skill bundle.

Screenshots under `web/test/screenshots/package-upload-review/`:

- `step-1-publish-page.png`: initial form.
- `step-2-file-selected.png`: selected ZIP and completed preview.
- `step-3-pending-after-61s.png`: upload survives the previous 60-second limit.
- `step-4-publish-success.png`: successful response navigates to skills.
- `step-5-timeout.png`: pre-fix timeout incorrectly shown as network failure.
- `step-6-timeout-fixed.png`: corrected timeout branch.
- `step-7-timeout-desktop.png`: desktop verification (1440 x 1000).
- `step-8-mobile-publish.png`: mobile form (390 x 844); no horizontal overflow,
  submit disabled until a file is selected.

Visual inspection of the mobile screenshot shows existing clipped guidance
text in `web/src/pages/dashboard/publish.tsx` (the flex child around line 367
lacks a shrinking constraint). No horizontal document overflow does not imply
that all text fits. Mobile visual acceptance remains incomplete; layout was
not changed as part of the request-timeout cleanup.

Console output includes mocked/unsupported auxiliary API traffic and the
existing meta-CSP frame-ancestors warning; this is not a clean-console signoff.
The final merge-review stage remains blocked by the full quality gates above.
