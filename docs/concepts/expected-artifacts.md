# Expected Artifacts and Evidence-Backed Completion

## Status

Concept note / future design direction.

This is not intended for immediate full implementation. The purpose is to define a landing place in the NAM model for future support of results, products, evidence, and machine-verifiable completion.

## Core Idea

NAM should not only track whether work is checked off.

Some actions and projects produce something: a file, image, document, receipt, note, commit, configuration, physical object, decision, or capability. In many cases, the produced thing is more important than the fact that the action was marked done.

A node may therefore optionally declare that it expects an artifact of a certain kind.

Example:

```text
Action:
  Process solar video

Done when:
  Final processed image exists.

Expected artifact:
  image/*
  