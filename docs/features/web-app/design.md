# Web App — moved to the NamWeb repo

The web companion app now lives in its own repository:
**[Aha43/NamWeb](https://github.com/Aha43/NamWeb)**.

Its design, product direction, and open questions are maintained there at
`docs/features/web-app/design.md`. The first MVP (capture + triage) shipped in
that repo (NamWeb PR #11).

This pointer is left behind after the web app was split into a separate repo on
2026-06-10. NamWeb consumes the **same Supabase backend** as NamDesktop cloud
sync; the migrations remain here in NamDesktop (`supabase/`) as the single
source of truth for the contract both clients share.
