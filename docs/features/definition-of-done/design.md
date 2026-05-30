# Feature: Definition of Done (concept in progress)

Status: **exploratory** — concept discussion only, no issues created yet. Key design questions still open.

## Motivation

For simple actions ("buy more beer") done is self-evident. For substantial actions the question "am I actually done?" can be fuzzy. A definition of done makes completion criteria explicit and checkable, and connects naturally to the resources concept: doing non-trivial work *produces* evidence — a receipt URI, a confirmation email, a signed document. The DoD describes what evidence you expect to see when the work is complete.

## What is settled

**DoD is an action-level concept only.**
Projects do not get an explicit DoD. A project's readiness is derived — it bubbles up from its actions and recursively from sub-projects. The tree structure already handles this; no new mechanics are needed at the project level.

**DoD is a list of completion criteria on an action.**
Each criterion describes something that must be true for the action to be considered done. Criteria connect naturally to resources: a criterion can be satisfied by the presence of a matching resource (e.g. "a FILE must exist", "a URI to the chosen vendor must exist").

**Relationship to resources (#275–#279).**
Resources are the evidence layer. DoD is the *expectation* layer that sits above it. This feature builds on resources and should not be started before the resources epic is complete.

**Not for every action.**
DoD is opt-in. Simple actions need no criteria. The concept is intended for actions where the completion boundary is genuinely unclear without explicit criteria.

## What is still open

| Question | Options | Notes |
|---|---|---|
| Criteria satisfaction: automatic or manual? | A) Automatic — criterion satisfied when a matching resource is attached. B) Manual — user checks off each criterion. C) Both — automatic for typed resource criteria, manual for named expectations. | Automatic is elegant and low-friction; manual covers criteria that don't map to a resource type. |
| When is DoD defined? | At action creation, or added mid-flight once the action proves non-trivial. | Likely both in practice — need to decide if the UI should nudge toward one. |

## Rough shape (subject to change)

- DoD is an optional list on a `NamNode` (actions only in UI; model may not restrict)
- Each criterion: `type` (resource-type requirement or freeform expectation), `description`, `satisfied` flag
- An action with a DoD shows a readiness indicator in the list view (e.g. "2 / 3 criteria met")
- Clicking Done when unsatisfied criteria exist: warn rather than block (exact behaviour TBD)
- MCP angle: an agent could add resources that satisfy criteria, making the action auto-advance toward done

## Dependencies

- Builds on the resources epic (#275–#279) — do not scope issues before resources ships
