# F02 review — Public game page + frontend foundation

Date: 2026-11-18. Reviewers: `scope-guardian`, `privacy-reviewer` (read-only agents) on the staged diff of `feature/public-game-page` (F02 + F19 together).

## privacy-reviewer

Verdict: no high or medium findings. Traced and holding: SQL selects only public columns, named parameter; response allowlisted (`gameId` approved, OD-02); malformed codes never reach the DB; GET-only at gateway and service; CSRF/CORS unchanged; no cookies; no new logging; frontend calls only its own public endpoint and stores nothing.

| # | Severity | Finding | Resolution |
|---|---|---|---|
| P1 | low | Gateway write test sent no CSRF token, so it never reached the GET-only route rule | Fixed: `GatewayAccessMatrixIT.writesOnThePublicGamePathAreNeverForwarded` also sends each write with a bootstrapped token → 401, stub never reached (CSRF helper moved to `GatewayTestBase`) |
| P2 | low | No check that the anonymous public GET gets no `Set-Cookie` from the gateway | Fixed: asserted in `publicGamePageIsForwardedToPlaymatchForAnonymousGet` |
| — | later | Access-matrix rows for member, guest and host callers on this route | Added when Account login / Guest check-in exist |

## scope-guardian

Verdict: nothing out of scope, no blocking defect; two items needed the owner.

| # | Severity | Finding | Resolution |
|---|---|---|---|
| S1 | medium | Copy "Page not found" marked approved although added after approval | Owner approved 2026-11-18 ("xài như recommend"); design row says "approved after implementation" |
| S2 | medium | D-1 (one-bound price shown as one price) decided by the implementer | Owner approved 2026-11-18 ("Hiện như 1 giá") |
| S3 | low | Approved design rewritten without marking additions | Design header lists what was added after approval |
| S4 | low | New gateway property `faircourt.services.playmatch-url` not in the design | Added to design §6 |
| S5 | low | Home page wording beyond the rename table | Listed in F19 design §2 (home page copy); owner sees it at the visual check |
| S6 | low | `API Document/frontend.md` "Today only…" stale; system-design §4 still says `play_session` | Fixed both (rename note in system-design §4) |
| S7 | low | `PublicGameIT` not `@IntegrationTest` | Accepted, recorded in implement.md deviations |
| S8 | info | Test results not re-checked by the reviewer | Re-run after fixes, see implement.md |

Owner idea from this review, put in the later backlog (not this feature): L-19 one standard error-code catalogue for every hop (gateway, service to service, third-party APIs).

## Gate

No open high/medium finding. Pipeline re-run after the fixes: see implement.md "Commands executed".
