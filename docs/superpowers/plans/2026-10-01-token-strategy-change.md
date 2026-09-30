# Token Strategy Change Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans for backend and a parallel frontend implementer. The user explicitly requested execution on a new branch.

**Goal:** prevent token substitution, refresh replay, and client authentication races.

**Architecture:** retain stateless access JWTs and persist one current refresh hash per login. Rotate refresh tokens atomically; synchronize frontend cookie mutations and distinguish authentication termination from temporary failures.

**Tech Stack:** existing Java 21/Spring/JPA/MySQL/JJWT and React/TypeScript/Axios/Vitest; no dependencies added.

**Spec:** `docs/superpowers/specs/2026-10-01-token-strategy-change.md`

## Global Constraints

- Branch: `codex/token-strategy-change` (Git does not allow spaces).
- Access lifetime defaults to 600 seconds; refresh maximum is seven days from login.
- No per-request access DB checks or Redis; logout does not invalidate already-issued access JWTs.
- Preserve unrelated untracked user documents and README.

## Review Focus

- A failed/reused refresh must leave its family revoked despite transaction rollback.
- Password change racing with refresh must not leave an active refresh family.
- Simultaneous refresh requests and late Set-Cookie responses must not resurrect a logout.
- Transient restoration failure must remain visible and must not overwrite a newer login.
- Cookie path migration and deployment schema must work with existing installations.

### Task 1: Backend token lifecycle

Files: JwtProvider, JwtAuthenticationFilter, UserService, UserController, token result records; new RefreshToken entity/repository/store and TokenStrategyTest; existing UserServiceTest.

Produces: unchanged login/refresh JSON; strict-purpose access verification; explicit refresh 401 codes and atomic persisted rotation/revocation.

- [x] Write tests for substitution, same-second rotation, replay revocation, logout, password changes, fixed deadlines, and concurrent refresh.
- [x] Run `gradlew.bat test --tests '*TokenStrategyTest'` and observe missing behavior.
- [x] Implement the smallest JWT/JPA change and update existing unit tests for the new collaborator.
- [x] Run targeted tests, then the complete backend suite.

### Task 2: Frontend authentication lifecycle (parallel)

Files: existing API/token/auth/Axios, AuthContext, ProtectedRoute, Mypage as necessary; targeted Vitest tests.

Consumes: current success JSON and explicit refresh termination error codes from Task 1.

- [x] Capture baseline and write failing real authentication flow tests.
- [x] Share refresh results, serialize cookie mutation, ignore stale state writes, and expose temporary restore errors.
- [x] Run authentication tests, complete frontend suite, build, and lint.

### Task 3: Deployment and final review

Files: existing/fresh SQL schema, JWT environment examples, token strategy deployment notes.

- [x] Add existing-database migration and fresh-database schema; default access configuration and examples agree.
- [x] Review all changes independently, fix material findings with regression tests.
- [x] Run complete backend/frontend suites and build; record actual outcomes below.

## Progress

- Branch created; unrelated untracked files preserved. Implementation authorized by the user.
- Initial sandbox Gradle run could not use its default C:/.gradle cache. Use the existing C:/Users/c/.gradle cache with the approved test runner.
- Backend original nine regressions observed RED, then GREEN with existing UserService tests after implementation.
- Review found a credential-check/new-login race and nested refresh transaction connection exhaustion. Both reproduced RED; login now locks the user row, and refresh suspends the enclosing transaction before its committed rotation transaction.
- Full backend rerun: 176 passed, 1 existing opt-in MySQL order concurrency test skipped, no failures/errors. Auth lifecycle tests use H2; no migration applied to a running MySQL database.
- Frontend baseline: 39 tests and build passed; lint already contained 8 errors. Initial implementation: 70 tests and build passed, same 8 lint errors.
- Final independent review reproduced a cross-tab/discarded-login cookie identity race and hidden unsupported-browser guidance. Five regressions observed RED then GREEN: refresh compares token subjects before retry, stale successful login invalidates previous auth, and Loginpage preserves browser guidance.
- Final frontend rerun: 75 tests passed; TypeScript and production build passed; lint retains the same eight baseline errors. Browser API doubles cover tab coordination; no manual two-tab browser check performed.
- Final diff whitespace check passed. All implementation and review tasks complete; deployment migration remains an operator action.
