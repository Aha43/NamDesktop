# MCP Server Test Plan

Executed in the dogfooding chat against a running NamDesktop instance.
Assertions use `python3 -c` against `~/.namdesktop/workspace.json` — not visual inspection.
Test nodes use the `TEST_` prefix so they are easy to identify and clean up.

**Last tested:** —  
**App version:** 0.1.0  
**Update this stamp** after each run.

---

## Prerequisites

```bash
WS=~/.namdesktop/workspace.json
```

All write-tool tests require monitoring mode to be active before the call and inactive (or checkpointed) after. The read-tool tests are safe to run at any time.

---

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

**Setup:** None.  
**Action:** `list_done`  
**Assert:** Response is a JSON array. Each element has `status: "DONE"`.  
**Cleanup:** None.

---

### list_saved_views

**Setup:** None.  
**Action:** `list_saved_views`  
**Assert:** Response is a JSON array (may be empty). Each element has `name` and `tags`.  
**Cleanup:** None.

---

### get_monitoring_status

**Setup:** Monitoring mode off.  
**Action:** `get_monitoring_status`  
**Assert:** Response contains `not active` or equivalent.

**Setup:** Monitoring mode on.  
**Action:** `get_monitoring_status`  
**Assert:** Response contains `active` or equivalent.  
**Cleanup:** None.

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
parent = next(n for n in ws['nodes'].values() if projects_id in (n.get('childIds') or []) or n['id'] == projects_id)
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

### update_node

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
