# Feature: Library (concept in progress)

Status: **exploratory** — concept discussion only, no issues created yet. Several design questions still open.

## The idea

When an action or project closes, some of what it produced is worth keeping — not the task, but the *output*. A photo shoot produces photos. A research action produces a conclusion. A failed project produces a lesson. Today that output has nowhere to live in NAM; it disappears with the task or lives entirely outside the system.

The Library is where that output goes. It is a separate, persistent storage area that outlives the nodes that produced it. Deleting a project does not touch its Library entries.

The act of archiving is not a chore. It is a deliberate closing ritual — the private eye slipping the file into the cabinet, the metallic click, case closed. The moment has weight. The UI should honor that.

## Naming

- Nav entry: **Library** — elevates what lives there
- The act: **archiving** — honest verb
- An item in the Library: **archive entry**

## What is settled

**The Library is separate storage, not a lens.**
Archive entries must outlive their source nodes. A project can be deleted after archiving without losing what it produced. Source context (title, path, date) is frozen at archive time.

**Archiving is triggered at close time.**
When an action or project is marked Done — or closed as partial or failed — the archival flow is offered. It is never automatic; the user always chooses to archive or skip.

**Closure type.**
Every archive entry carries a closure type:
- `DONE` — completed as intended
- `PARTIAL` — project closed with some things unfinished (projects only; not meaningful for single actions)
- `FAILED` — did not achieve what was intended; crash report territory

Cancelled/abandoned nodes are offered to the archival flow but default to **skip**. The user can override. Archiving a failed project is meaningful; archiving an abandoned "buy milk" is noise.

**The archival dialog is a moment.**
Not a yes/no prompt. A focused, intentional experience:
- Shows the node title, closure type selector, source path
- Resources from the node are pre-loaded — user can remove any that don't belong
- User can add new resources at this moment (things produced during the work)
- Summary field: free text — conclusion, reflection, crash report; the closing line
- "Archive it" commits; "Skip" dismisses with no trace

The dialog should feel like closing something, not filling out a form.

**Archive entry shape (draft):**
- Source title and path — frozen at archive time
- Source node ID — nullable (node may be deleted later)
- Date archived
- Closure type
- Tags — carried over from source node (enables Library filtering)
- Resources — carried over plus anything added at archive time
- Summary — written at archive time; the human reflection

**TEXT resources as inline content.**
A resource with type `TEXT` holds its value directly — the one-liner proof, the key conclusion, the lesson learned. The Library can contain knowledge, not just references to things elsewhere.

**Re-opened actions.**
If an action is re-opened (not done after all), its archive entry survives. When the action closes again later, the new closure adds to the same entry rather than creating a duplicate.

**The Library as a place.**
A dedicated nav entry. Browse by: source project, tag, closure type, date. Full-text search over summaries and text-type resource values. Failure entries may warrant distinct visual treatment (still open).

**Relationship to other features.**
- Builds on **resources** (#275–#279) — archive entries use the same resource type system
- Connects to **definition of done** — DoD criteria describe expected outputs; the Library is where those outputs land
- Should not be scoped until resources ships

## What is still open

| Question | Notes |
|---|---|
| Does the archival dialog need a distinct visual treatment (full-screen, modal overlay, themed differently)? | The "metallic click" feeling may require more than a standard dialog |
| Failure entries — separate visual treatment in Library browser? | Color, icon, or badge to distinguish FAILED from DONE at a glance |
| Project archival vs. action archival — are these independent Library entries or linked? | A project's actions may each have their own entries; does the project also get one? |
| `PARTIAL` on actions | Currently only meaningful for projects — confirm actions are DONE or FAILED only |
| Library organisation beyond tags + date | Whether source project grouping, timeline view, or other navigation modes are needed — best answered through use |

## Dependencies

- Builds on resources epic (#275–#279)
- Informed by definition of done (docs/features/definition-of-done/design.md)
- Do not create implementation issues before resources ships and open questions above are resolved
