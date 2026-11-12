# CI Live Monitoring & GitHub Account Safety Guidelines

## 1. Live Monitoring Standard
- Every push to the repository (`main`, `master`, or tags) triggers GitHub Actions CI.
- VANTA / automated workflows must track the build status until completion (`success` / `failure`).
- If a build fails, inspect the `build-log` artifact, isolate compiler/packaging issues, apply fixes, and re-verify until green.

## 2. GitHub Account Protection & Rate-Limit Safeguards
To prevent triggering GitHub's automated Abuse Detection Mechanism (secondary rate-limits) or risking account suspension:
- **No Tight Loops:** Do not poll the GitHub REST API in rapid loops.
- **Polite Polling Interval:** Maintain a minimum delay of 35–45 seconds between status checks.
- **Single-Endpoint Queries:** Query only the specific run ID rather than requesting whole lists.
- **Safe Error Handling:** If HTTP 429 (Too Many Requests) or HTTP 403 (Rate Limit Reached) occurs, back off immediately.
- **Token Security:** Never log, print, or expose personal access tokens in public outputs or commits.
