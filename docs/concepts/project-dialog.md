# Project Workbench

## Status

Concept note / near-term design direction.

This document describes a proposed project-working view for NAM Desktop. It is intended to guide future implementation issues.

The purpose is to make project work practical without forcing the user to work directly in a recursive tree widget.

## Core Idea

NAM uses a tree-shaped model because project trees have strong expressive power.

A project may contain actions. It may also contain child projects. Those child projects may themselves contain actions and further child projects.

This structure is useful as a model.

However, a tree widget is often a poor surface for actually doing work. It exposes the structural complexity of the model and makes the user hunt for actionable items.

The Project Workbench should be:

> action-forward and structure-aware.

It should put actions in the front seat and the project tree in the back seat.

The tree still exists. It still gives meaning, containment, and navigation. But the main visible content is the work that can be done.

## Problem

A normal recursive project tree view answers:

> What is the structure?

But when engaging with a project, the more useful question is:

> What actions are available at this project level?

A deeply expanded tree can show everything, but it also shows too much structure at once.

Example:

```text
Project
├── Subproject A
│   ├── Action 1
│   ├── Action 2
│   └── Subproject A.1
│       ├── Action 3
│       └── Action 4
└── Subproject B
    ├── Action 5
    └── Subproject B.1
        └── Action 6
```

This is expressive, but not necessarily pleasant to work from.

The user has to visually parse the tree before seeing the work.

Proposed View

For a selected project, the Project Workbench should show:

A breadcrumb path to the selected project.
A section containing actions directly under the selected project.
One section for each direct child project.
In each child project section, show that child project's direct actions.
Child project section headers are clickable and navigate into that child project.
Breadcrumb components are clickable and navigate back up the project path.

The view should not recursively expand the entire subtree by default.

## Example

Selected project:

Projects > NAM Desktop > UI

Workbench display:

Projects > NAM Desktop > UI

This project
- Add Project Workbench lens
- Add breadcrumb navigation
- Review dialog stacking issue

Dialog UX >
- Fix unbounded action/project dialog stack
- Decide whether project editing should move to main panel

Project Workbench >
- Define section view model
- Render child project sections
- Add tests for workbench projection

Testing >
- Add tests for project action projection
- Add tests for breadcrumb generation

The child project headers are navigation controls. Clicking Dialog UX opens the same Project Workbench view focused on that child project.

## Navigation Model

The Project Workbench navigates through the project hierarchy without showing the project hierarchy as an expanded tree.

### Upward navigation

A breadcrumb shows the path from the Projects root to the current project.

Example:

Projects > Astronomy > Solar Imaging > Processing

Each breadcrumb component should be clickable.

Clicking Solar Imaging moves the workbench focus to that project.

### Downward navigation

Each direct child project is shown as a section header.

Example:

Equipment >

Clicking the header moves the workbench focus to that child project.

The same view is rebuilt for the newly selected project.

## Display Rule

For the selected project node:

Show:

1. direct action children of the selected project
2. direct action children of each direct child project

Do not show all descendant actions recursively by default.

Grandchild projects are not expanded in the current view. They can be reached by clicking into their parent child-project section.

This gives one-level operational depth.

The user usually sees most relevant actions without navigation, but deeper structure remains available when needed.

## Why One-Level Depth Is Enough

Most project trees should not become very deep.

However, some projects genuinely need more than two levels. The Project Workbench should support that without making every project feel complex.

The one-level section model gives a good compromise:

Current project actions
+ direct child project actions
= most of the work visible most of the time

If deeper structure matters, the user can navigate into a child project.

This keeps the view calm while preserving the expressive power of the tree.

## Relationship to the Tree Model

The Project Workbench does not replace the project tree.

It is a lens over the tree.

The tree remains responsible for:

- containment
- project hierarchy
- ownership
- persistence
- breadcrumb paths
- child project sections

The Project Workbench is responsible for:

- making actions visible
- presenting local project work
- supporting navigation through breadcrumbs and child headers
- avoiding visual tree overload

This follows the broader NAM principle:

The persistent model is not the presentation model.

Action-Forward, Structure-Aware

The Project Workbench should be described as:

Action-forward, structure-aware.

## Action-forward means:

- actions are the dominant visible content
- the user should quickly see what can be done
- the view should support doing, not just inspecting

Structure-aware means:

- project hierarchy is still present
- child project headers preserve structure
- breadcrumbs preserve location
- the user can navigate the tree without staring at a tree widget
  
## Relationship to Existing Views

The Projects view may still list projects.

The Raw Tree view may still expose the underlying model for debugging and advanced inspection.

The Project Workbench is different from both.

Projects view:
  Where are my projects?

Raw Tree view:
  What does the underlying model look like?

Project Workbench:
  What actions are available in this project area?

## Relationship to Dialogs

The current ProjectDialog and ActionDialog are useful for focused editing.

However, the Project Workbench should probably become the main surface for engaging with project work.

A possible split:

Project Workbench:
  work the project

ProjectDialog:
  edit project details

ActionDialog:
  edit action details

Over time, some project editing may move from modal dialogs into the main workbench, but that is not required for the first implementation.

## A small first implementation could include:

- ProjectWorkbenchLens
- breadcrumb generation
- direct actions for selected project
- one child section per direct child project
- direct actions under each child project
- tests for the lens/projection

A second issue could render the view:

- ProjectWorkbenchPanel
- breadcrumb UI
- section headers
- action tables/lists
- clicking child project header navigates into that child project
- clicking breadcrumb navigates up

A later issue could add commands:

- mark action done
- promote to NEXT
- move to BACKLOG
- open action editor
- add action under current project
- add action under child project section
Out of Scope for First Implementation

The first Project Workbench implementation should not try to solve everything.

Out of scope initially:

- recursive expansion of all descendants
- drag-and-drop ordering
- inline editing
- filtering by context/tool
- Mission Control readiness
- expected artifacts
- complex child project summaries
- replacing all dialogs

## Design Notes

Direct child project headers may show summary information

Later, child project headers may show compact information:

Equipment — 2 NEXT, 5 BACKLOG, 1 child project >

or:

Equipment — NO NEXT ACTION >

This can help identify stuck child projects without expanding the whole tree.

### Done actions

The first implementation may show done actions visually distinct, or it may hide them depending on the active view mode.

This should be decided deliberately. For project engagement, it may be useful to hide done actions by default but allow showing them.

### Current project section name

The first section might be named:

This project

or:

Direct actions

or use the project title.

User-facing wording can be refined later.

### Guiding Principle

The Project Workbench should make this true:

The project tree is still there, but the user is working with actions.

The tree gives structure and navigation. The workbench gives an action-oriented working surface.