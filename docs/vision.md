# NAM Desktop Vision

NAM Desktop is a Java/Swing desktop application for exploring and implementing a personal GTD-inspired productivity system.

This project is not a direct port of earlier NAM implementations. It is a new iteration based on lessons learned from previous web/.NET work and later Java desktop projects. The purpose is to build a small, useful, local-first application while refining the underlying ideas through small implementation steps.

## Development Model

Development is issue-driven.

Every implementation change should start from a small GitHub issue. Each issue should have a clear goal, explicit scope, and explicit out-of-scope items.

The project is developed with AI assistance:

- ChatGPT is used for high-level design, architecture, naming, and conceptual discussion.
- Claude Code is used for concrete implementation work.
- GitHub issues are the unit of work.
- Small iterations are preferred over large speculative implementations.

The goal is disciplined AI-assisted development, not uncontrolled “vibe coding.”

## Core Model: One Tree

NAM Desktop is centered around one persistent tree of `NamNode` objects.

A `NamNode` is the basic storage primitive. Most user data should be represented as nodes rather than separate persistent entity types such as `Project`, `Action`, or `InboxItem`.

A node may be interpreted differently depending on:

- where it is in the tree,
- whether it has children,
- its status,
- its user tags,
- its system markers,
- which lens/view is currently being used.

The model should keep storage simple while allowing rich interpretation.

## Node Meaning

A node does not have only one absolute meaning.

For example:

- A node under the inbox area may be perceived as an inbox item.
- A node with children may be perceived as a project-like node.
- A leaf node under a project may be perceived as an action.
- A node may act as a readiness station in a Mission Control view.
- A node may appear in a context view because it has a matching tag or facet.

Meaning should not be scattered throughout the UI. Classification and interpretation rules should be centralized and tested.

## Tags, Markers, and Status

User tags and system semantics should be kept conceptually separate.

User tags are user-facing organizational labels, such as:

- `@home`
- `@computer`
- `@errands`
- `astronomy`
- `family`

System markers are application-facing semantics, such as:

- `waiting`
- `blocked`
- `optional`
- `lens:mission-control`

Node status should be explicit. Likely statuses include:

- active
- done
- cancelled
- archived

Avoid relying only on arbitrary strings where stronger concepts are needed.

## Commands, Not Random Mutation

Important changes to the tree should go through command/service methods rather than direct UI mutation.

Examples:

- convert inbox item to action,
- convert inbox item to project,
- convert action to project,
- convert project to action,
- move node,
- mark node done,
- archive node,
- add or remove tag,
- add node to mission control view.

This keeps domain behavior explicit and testable.

## Lenses / Views

NAM Desktop should support lenses: operational views over the same underlying node tree.

A lens does not own the data. It interprets existing nodes for a particular purpose.

Possible lenses include:

- Inbox view
- Project view
- Context view
- Mission Control
- GNAT / next-action extraction
- Blocker Radar
- Review Lens
- Launchpad
- Sweep View

Not all lenses need to exist early. The architecture should allow them to emerge naturally.

## Mission Control

Mission Control is a readiness lens.

It answers:

> What prevents this mission from being GO?

Applied to a node, Mission Control presents a set of readiness stations. A station may be a direct child of the mission node or a selected node from elsewhere in the tree.

A station is GO when its relevant child nodes are done. The mission is GO when all stations are GO.

Mission Control is not the primary daily work view. Most work still happens through ordinary next actions, contexts, and project work. Mission Control becomes useful when global readiness starts to matter.

The intended experience is that the user may know the mission is currently NO GO, continue working ordinary next actions, and later open Mission Control to discover that accumulated work has brought the mission closer to GO than expected.

## Structural Ownership vs View Participation

A node has one structural place in the tree.

However, a node may participate in many views.

For example, an action may belong structurally under a project, appear in the `@home` context view, be part of a Mission Control station, and appear in a review view.

Do not solve this by duplicating nodes. Prefer references, tags, markers, and view definitions.

## Ordering

Ordering is contextual.

A node should not have one global sort value. The same node may need different ordering in different views.

There are at least three kinds of order:

1. Structural order  
   The order of real children under a parent node.

2. View-specific manual order  
   The order a user has chosen inside a particular context, mission, launchpad, or other view.

3. Computed fallback order  
   The default order for items not yet manually sorted.

Structural order belongs to the tree. View-specific order belongs to the view/list and must be persisted separately.

When opening a view, saved order should be reconciled with current membership:

- items still present keep their saved relative order,
- completed/deleted/non-matching items are ignored,
- new unsorted items are appended or inserted using default sort.

This is important because users naturally drag items around in views, and the application should remember that human-imposed order.

## Persistence

The first persistence model should be simple and local-first.

JSON is a strong candidate for early development because it is inspectable, easy to debug, and flexible while the model evolves.

The persisted workspace should include:

- nodes,
- tree structure,
- tags,
- markers,
- node status,
- view definitions,
- persisted view orders.

Avoid premature database complexity unless later requirements justify it.

## UI Direction

The first UI should be simple Java/Swing.

A likely shape:

- main frame,
- left navigation/tree,
- central work area,
- detail/editor area,
- menu/toolbar actions,
- dialogs for focused editing.

The Swing UI should invoke domain commands and render lens models. It should not contain hidden domain logic.

## Early Scope

The first implementation should stay small.

A good initial target:

- create/load/save a local workspace,
- create basic `NamNode`s,
- display the node tree,
- add/edit/delete nodes,
- mark nodes done,
- preserve structural order,
- begin separating domain model from Swing UI.

More advanced concepts such as Mission Control, context views, and view-specific ordering should be added through later issues.

## Guiding Principle

NAM Desktop should evolve through small, working increments.

Prefer a modest feature that works and teaches something over a broad architecture that is not yet exercised.

The architecture should remain open to the larger vision, but each implementation step should be concrete, testable, and useful.
