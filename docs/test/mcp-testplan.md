# MCP Server Test Plan

Executed in the dogfooding chat against a running NamDesktop instance.
Assertions use `python3 -c` against `~/.namdesktop/workspace.json` — not visual inspection.
Test nodes use the `TEST_` prefix so they are easy to identify and clean up.

**Last tested:** 2026-06-01  
**App version:** 0.1.0  
**Update this stamp** after each run.

---

## Prerequisites

```bash
WS=~/.namdesktop/workspace.json
```

All write-tool tests require monitoring mode to be active before the call and inactive (or checkpointed) after. The read-tool tests are safe to run at any time.

---

> **Note on read-tool assertions:** Most read tools are verified against the tool's response text rather than `workspace.json` directly. This is intentionally lighter — a follow-up pass can tighten these once the write-tool assertions are stable.

## Read tools

### get_workspace_context

**Setup:** None.  
**Action:** `get_workspace_context`  
**Assert:** Response contains `## Projects` and `## Inbox` headings.  
**Cleanup:** None.

---

### list_inbox

**Setup:** None.  
**Action:** `list_inbox`  
**Assert:** Response is a JSON array. Each element has `id`, `title`, `status`.  
**Cleanup:** None.

---

### list_projects

**Setup:** None.  
**Action:** `list_projects`  
**Assert:** Response is a JSON array. Each element has `id`, `title`.  
**Cleanup:** None.

---

### list_next_actions

**Setup:** None.  
**Action:** `list_next_actions`  
**Assert:** Response is a JSON array. Each element has `id`, `title`, `status: "NEXT"`.  
**Cleanup:** None.

---

### list_done

**Setup:** Create `TEST_done_action` via `add_inbox_item`, then `mark_done` it, and checkpoint. (Without a known DONE node the array may be empty, which vacuously passes any per-element assertion.)  
**Action:** `list_done`  
**Assert:**
```bash
python3 -c "
import json
ws = json.load(open('$WS'))
node = next((n for n in ws['nodes'].values() if n['title'] == 'TEST_done_action'), None)
assert node, 'TEST_done_action not found'
assert node['status'] == 'DONE', f'expected DONE got {node[\"status\"]}'
print('PASS')
"
```
**Cleanup:** Delete `TEST_done_action`.

---

### list_saved_views

**Setup:** None.  
**Action:** `list_saved_views`  
**Assert:** Response is a JSON array (may be empty). Each element has `name` and `tags`.  
**Cleanup:** None.

---

### get_monitoring_status

Run this test **last among read tools** so it doesn't leave monitoring mode on and interfere with write-tool tests that manage mode themselves.

**Setup:** Monitoring mode off.  
**Action:** `get_monitoring_status`  
**Assert:** Response contains `not active` or equivalent.

**Setup:** Enable monitoring mode (`Cmd+Shift+M`).  
**Action:** `get_monitoring_status`  
**Assert:** Response contains `active` or equivalent.  
**Cleanup:** Disable monitoring mode (`Cmd+Shift+M`) before proceeding to write-tool tests.

---

### find_node

**Setup:** A node with title `TEST_find_target` exists (created via `add_inbox_item` in monitoring mode, then checkpointed).  
**Action:** `find_node` with `title: "TEST_find"`  
**Assert:** Response contains a result with `title` matching `TEST_find_target`.  
**Cleanup:** Delete `TEST_find_target`.

---

### list_project_children

**Setup:** A project `TEST_parent` with one child action `TEST_child` exists (created and checkpointed).  
**Action:** `list_project_children` with the `TEST_parent` node id.  
**Assert:** Response contains an entry with `title: "TEST_child"`.  
**Cleanup:** Delete `TEST_child`, then `TEST_parent`.

---

### list_resources

**Setup:** A node `TEST_res_node` with one URI resource `https://example.com` exists.  
**Action:** `list_resources` with the node id.  
**Assert:**
```bash
python3 -c "
import json, sys
ws = json.load(open('$WS'))
node = next((n for n in ws['nodes'].values() if n['title'] == 'TEST_res_node'), None)
assert node, 'TEST_res_node not found'
assert len(node['resources']) == 1, f'expected 1 resource, got {len(node[\"resources\"])}'
assert node['resources'][0]['value'] == 'https://example.com'
print('PASS')
"
```
**Cleanup:** Remove resource, delete node.

---

## Write tools

All write-tool tests follow this wrapper:

1. Enable monitoring mode (`Cmd+Shift+M`).
2. Execute the MCP call.
3. Checkpoint (`Cmd+Shift+S`) and accept.
4. Run the assertion against `workspace.json`.
5. Clean up.

---

### create_project — top-level

**Action:** `create_project` with `title: "TEST_toplevel_project"`  
**Assert:**
```bash
python3 -c "
import json
ws = json.load(open('$WS'))
node = next((n for n in ws['nodes'].values() if n['title'] == 'TEST_toplevel_project'), None)
assert node, 'TEST_toplevel_project not found'
assert node['project'] == True
projects_id = ws['projectsNodeId']
assert node['id'] in ws['nodes'][projects_id]['childIds'], 'not a child of the Projects root'
print('PASS')
"
```
**Cleanup:** `delete_node` the project.

---

### create_project — nested

**Setup:** `TEST_parent_project` exists.  
**Action:** `create_project` with `title: "TEST_nested_project"`, `parent_id: <TEST_parent_project id>`  
**Assert:**
```bash
python3 -c "
import json
ws = json.load(open('$WS'))
parent = next(n for n in ws['nodes'].values() if n['title'] == 'TEST_parent_project')
children_ids = parent.get('childIds', [])
nested = next((n for n in ws['nodes'].values() if n['title'] == 'TEST_nested_project'), None)
assert nested, 'TEST_nested_project not found'
assert nested['id'] in children_ids, 'nested project not a child of parent'
print('PASS')
"
```
**Cleanup:** Delete `TEST_nested_project`, then `TEST_parent_project`.

---

### add_action

**Setup:** `TEST_action_project` exists.  
**Action:** `add_action` with `title: "TEST_action"`, `project_id: <TEST_action_project id>`, `status: "NEXT"`  
**Assert:**
```bash
python3 -c "
import json
ws = json.load(open('$WS'))
project = next(n for n in ws['nodes'].values() if n['title'] == 'TEST_action_project')
action = next((n for n in ws['nodes'].values() if n['title'] == 'TEST_action'), None)
assert action, 'TEST_action not found'
assert action['status'] == 'NEXT'
assert action['id'] in project.get('childIds', [])
print('PASS')
"
```
**Cleanup:** Delete `TEST_action`, then `TEST_action_project`.

---

### add_inbox_item

**Action:** `add_inbox_item` with `title: "TEST_inbox_item"`  
**Assert:**
```bash
python3 -c "
import json
ws = json.load(open('$WS'))
inbox_id = ws['inboxNodeId']
inbox = ws['nodes'][inbox_id]
item = next((n for n in ws['nodes'].values() if n['title'] == 'TEST_inbox_item'), None)
assert item, 'TEST_inbox_item not found'
assert item['id'] in inbox.get('childIds', [])
print('PASS')
"
```
**Cleanup:** Delete `TEST_inbox_item`.

---

### add_next_action

**Action:** `add_next_action` with `title: "TEST_next_action"`  
**Assert:**
```bash
python3 -c "
import json
ws = json.load(open('$WS'))
action = next((n for n in ws['nodes'].values() if n['title'] == 'TEST_next_action'), None)
assert action, 'TEST_next_action not found'
assert action['status'] == 'NEXT'
print('PASS')
"
```
**Cleanup:** Delete `TEST_next_action`.

---

### mark_next / mark_done / mark_backlog

**Setup:** `TEST_status_action` exists with status `BACKLOG`.  
**Action:** `mark_next` with node id.  
**Assert:**
```bash
python3 -c "
import json
ws = json.load(open('$WS'))
node = next(n for n in ws['nodes'].values() if n['title'] == 'TEST_status_action')
assert node['status'] == 'NEXT', f'expected NEXT got {node[\"status\"]}'
print('PASS')
"
```
Repeat for `mark_done` (expect `DONE`) and `mark_backlog` (expect `BACKLOG`).  
**Cleanup:** Delete `TEST_status_action`.

---

### add_resource — all four types

**Setup:** `TEST_resource_node` exists.  
**Action:** Call `add_resource` four times — one for each type:

| Call | type | value |
|------|------|-------|
| 1 | `URI`   | `https://example.com` |
| 2 | `EMAIL` | `test@example.com` |
| 3 | `FILE`  | `/tmp/testfile.txt` |
| 4 | `TEXT`  | `plain note` |

**Assert:**
```bash
python3 -c "
import json
ws = json.load(open('$WS'))
node = next(n for n in ws['nodes'].values() if n['title'] == 'TEST_resource_node')
res = node['resources']
assert len(res) == 4, f'expected 4 resources, got {len(res)}'
types = [r['type'] for r in res]
assert 'URI'   in types
assert 'EMAIL' in types
assert 'FILE'  in types
assert 'TEXT'  in types
print('PASS')
"
```
**Cleanup:** Remove all resources (indices 3, 2, 1, 0 in reverse), then delete `TEST_resource_node`.

---

### edit_resource

**Setup:** `TEST_edit_res_node` exists with one TEXT resource `original value`.  
**Action:** `edit_resource` with `index: 0`, `value: "updated value"`, `description: "updated note"`  
**Assert:**
```bash
python3 -c "
import json
ws = json.load(open('$WS'))
node = next(n for n in ws['nodes'].values() if n['title'] == 'TEST_edit_res_node')
r = node['resources'][0]
assert r['value'] == 'updated value', f'got {r[\"value\"]}'
assert r['description'] == 'updated note', f'got {r[\"description\"]}'
print('PASS')
"
```
**Cleanup:** Delete `TEST_edit_res_node`.

---

### remove_resource

**Setup:** `TEST_remove_res_node` exists with two resources.  
**Action:** `remove_resource` with `index: 0`  
**Assert:**
```bash
python3 -c "
import json
ws = json.load(open('$WS'))
node = next(n for n in ws['nodes'].values() if n['title'] == 'TEST_remove_res_node')
assert len(node['resources']) == 1, f'expected 1, got {len(node[\"resources\"])}'
print('PASS')
"
```
**Cleanup:** Delete `TEST_remove_res_node`.

---

### update_node *(implemented — Closes #303)*

**Setup:** `TEST_update_node` exists with title `TEST_update_node`, no tags.  
**Action:** `update_node` with `title: "TEST_update_node_renamed"`, `tags: ["@test"]`, `description: "new desc"`  
**Assert:**
```bash
python3 -c "
import json
ws = json.load(open('$WS'))
node = next((n for n in ws['nodes'].values() if n['title'] == 'TEST_update_node_renamed'), None)
assert node, 'renamed node not found'
assert '@test' in node.get('tags', [])
assert node.get('description') == 'new desc'
print('PASS')
"
```
**Cleanup:** Delete `TEST_update_node_renamed`.

---

### set_tags *(superseded — closed as #301; use `update_node` with `tags` field instead)*

No dedicated test needed. Tag-setting is covered by the `update_node` test above.

---

### move_node — action between projects *(Closes #309)*

**Setup:** `TEST_source_proj` and `TEST_target_proj` exist; `TEST_movable_action` is a child of `TEST_source_proj`.  
**Action:** `move_node` with `node_id: <TEST_movable_action id>`, `new_parent_id: <TEST_target_proj id>`  
**Assert:**
```bash
python3 -c "
import json
ws = json.load(open('$WS'))
src  = next(n for n in ws['nodes'].values() if n['title'] == 'TEST_source_proj')
tgt  = next(n for n in ws['nodes'].values() if n['title'] == 'TEST_target_proj')
act  = next(n for n in ws['nodes'].values() if n['title'] == 'TEST_movable_action')
assert act['id'] in tgt.get('childIds', []),  'action not in target project'
assert act['id'] not in src.get('childIds', []), 'action still in source project'
print('PASS')
"
```
**Cleanup:** Delete `TEST_movable_action`, `TEST_source_proj`, `TEST_target_proj`.

---

### move_node — nest project under another *(Closes #309)*

**Setup:** `TEST_proj_A` and `TEST_proj_B` are both top-level projects.  
**Action:** `move_node` with `node_id: <TEST_proj_B id>`, `new_parent_id: <TEST_proj_A id>`  
**Assert:**
```bash
python3 -c "
import json
ws = json.load(open('$WS'))
a = next(n for n in ws['nodes'].values() if n['title'] == 'TEST_proj_A')
b = next(n for n in ws['nodes'].values() if n['title'] == 'TEST_proj_B')
assert b['id'] in a.get('childIds', []), 'TEST_proj_B not nested under TEST_proj_A'
print('PASS')
"
```

---

### move_node — detach action to free actions area *(Closes #314 MCP side)*

**Setup:** `TEST_detach_proj` exists; `TEST_detach_action` is a child of it.  
**Action:** `move_node` with `node_id: <TEST_detach_action id>` — omit `new_parent_id`  
**Assert:**
```bash
python3 -c "
import json
ws = json.load(open('$WS'))
next_id = ws['nextActionsNodeId']
proj = next(n for n in ws['nodes'].values() if n['title'] == 'TEST_detach_proj')
act  = next(n for n in ws['nodes'].values() if n['title'] == 'TEST_detach_action')
assert act['id'] in ws['nodes'][next_id].get('childIds', []), 'action not in free actions area'
assert act['id'] not in proj.get('childIds', []), 'action still in project'
print('PASS')
"
```
**Cleanup:** Delete `TEST_detach_action`, then `TEST_detach_proj`.

---

### move_node — promote nested project to top-level *(Closes #309)*

**Setup:** `TEST_proj_B` is nested under `TEST_proj_A` (from previous sub-case, or re-created).  
**Action:** `move_node` with `node_id: <TEST_proj_B id>` — omit `new_parent_id`  
**Assert:**
```bash
python3 -c "
import json
ws = json.load(open('$WS'))
projects_id = ws['projectsNodeId']
b = next(n for n in ws['nodes'].values() if n['title'] == 'TEST_proj_B')
a = next(n for n in ws['nodes'].values() if n['title'] == 'TEST_proj_A')
assert b['id'] in ws['nodes'][projects_id].get('childIds', []), 'TEST_proj_B not at top level'
assert b['id'] not in a.get('childIds', []), 'TEST_proj_B still nested under TEST_proj_A'
print('PASS')
"
```
**Cleanup:** Delete `TEST_proj_B`, then `TEST_proj_A`.

---

### delete_node

**Setup:** `TEST_delete_me` exists as a leaf node.  
**Action:** `delete_node` with node id.  
**Assert:**
```bash
python3 -c "
import json
ws = json.load(open('$WS'))
found = any(n['title'] == 'TEST_delete_me' for n in ws['nodes'].values())
assert not found, 'node still present after delete'
print('PASS')
"
```
**Cleanup:** None needed.

---

## Cleanup pass

After all tests, confirm no `TEST_` nodes remain:

```bash
python3 -c "
import json
ws = json.load(open('$WS'))
leftovers = [n['title'] for n in ws['nodes'].values() if n['title'].startswith('TEST_')]
assert not leftovers, f'leftover test nodes: {leftovers}'
print('Workspace clean.')
"
```
