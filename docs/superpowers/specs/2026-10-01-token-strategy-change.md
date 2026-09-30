# Token strategy change

Approved scope: keep stateless access JWTs, validate token purpose, and store/rotate/revoke refresh tokens in the existing MySQL database. Do not add HTTP sessions, access-token DB checks, Redis, or dependencies.

- Access JWTs carry `token_use=access` and the user's role; default lifetime is 600 seconds. Responses derive `expiresIn` from the configured lifetime.
- Refresh JWTs carry `token_use=refresh`, a random `jti`, and a stable random `family_id` per login. The original login's seven-day deadline never extends on refresh.
- Store one current SHA-256 token hash, user ID, family ID, and expiration per login. Compare and rotate under a database row lock. Reuse of a signed old refresh token revokes its family; persist revocation before returning 401.
- Logout revokes the current family; password changes and account deletion revoke every refresh family for that user. Existing access tokens remain valid until expiration.
- Refresh cookies remain HttpOnly/Secure/SameSite=Lax and use `/api/auth` so logout receives them. Remove the previous `/api/auth/refresh` cookie on login, refresh, and logout.
- Login/refresh success JSON shapes stay unchanged. Missing, expired, invalid, revoked, and inactive-account refresh failures return explicit 401 codes.
- Frontend shares a refresh Promise, serializes cookie mutations across tabs using native Web Locks, retries old-token 401s with a newer token, and ignores outdated authentication results. Browsers without Web Locks show an actionable error rather than rotate unsafely.
- Network and 5xx errors do not imply logout; initial restore errors are visible and retryable. Logout server failure must not be reported as success.
- Existing JWTs without purpose/family metadata are rejected after deployment; users log in again. Schema changes must be provided for both existing and fresh MySQL databases.

Verification: real-JWT/API and H2 lifecycle/concurrency tests; frontend authentication race tests; complete backend/frontend suites and frontend build.
