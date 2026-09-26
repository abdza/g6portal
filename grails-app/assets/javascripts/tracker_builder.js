/*
 * Workflow builder for the tracker graph page.
 *
 * Turns the read-only workflow graph into an editor: add statuses on the canvas, draw
 * transitions between them, and drive each selection's field and role lists from the
 * panel above the graph.
 *
 * Two things about the framework shape this file:
 *
 *  1. Field order is a single canonical list per tracker (PortalTrackerField.field_order).
 *     Every status's and transition's displayfields/editfields CSV is written in that
 *     order, because TrackerTagLib renders strictly in CSV order.
 *
 *  2. displayfields and editfields are NOT a view/edit pair of the same list. On a
 *     transition, editfields is the form and displayfields is the read-only context
 *     shown beside it; on a status, displayfields is the record display. So the two
 *     columns are deliberately independent - ticking Edit does not tick View.
 *
 * Nothing here mutates the graph optimistically: every change POSTs, and the server's
 * fresh model is what gets redrawn. That keeps the canvas from drifting from the DB.
 */
(function () {
    'use strict';

    var B = {};
    window.TrackerBuilder = B;

    B.cfg = null;
    B.model = null;
    B.order = [];          // working field order (names)
    B.sel = null;          // {type:'status'|'transition', id:'123'} | null
    B.work = null;         // working copy of the selection
    B.orderDirty = false;
    B.lists = null;        // working copy of the tracker-wide field lists
    B.colsExpanded = false;
    B.expandedGroups = {};   // group name -> true while its members are shown
    B.memberEdit = null;     // {name, id, members:[...]} while the member editor is open

    // Tracker-level lists, in the order their columns appear in the field table.
    var LISTS = ['listfields', 'hiddenlistfields', 'excelfields', 'searchfields', 'filterfields'];
    B.pendingNodePos = null;
    B.confirmingDelete = false;

    // ---------------------------------------------------------------- helpers

    function el(id) { return document.getElementById(id); }

    function esc(s) {
        return String(s === null || s === undefined ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function post(url, body) {
        return fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin',
            body: JSON.stringify(body || {})
        }).then(function (r) {
            return r.json().catch(function () { return { error: 'Server returned a non-JSON response (' + r.status + ')' }; })
                .then(function (data) {
                    if (!r.ok || data.error) { throw new Error(data.error || ('Request failed (' + r.status + ')')); }
                    return data;
                });
        });
    }

    function notify(message, kind) {
        var box = el('builder-message');
        if (!box) { return; }
        box.textContent = message || '';
        box.className = 'builder-message' + (message ? ' show ' + (kind || 'info') : '');
        if (message && kind === 'ok') {
            clearTimeout(B._notifyTimer);
            B._notifyTimer = setTimeout(function () {
                box.textContent = '';
                box.className = 'builder-message';
            }, 4000);
        }
    }

    /**
     * Self-transitions are hidden by default - right for reading a busy workflow, wrong
     * the moment you are authoring one, since a newly created "Edit" would land invisible
     * and look like nothing happened.
     */
    function revealSelfTransitions() {
        if (window.setSelfTransitions) { window.setSelfTransitions(true); }
        else { B.selfShown = true; }
    }

    function statusById(id) {
        return B.model.statuses.filter(function (s) { return s.id === String(id); })[0] || null;
    }

    function transitionById(id) {
        return B.model.transitions.filter(function (t) { return t.id === String(id); })[0] || null;
    }

    // ------------------------------------------------------- graph from model

    /**
     * Same node/edge shape the read-only page builds server-side, but derived from the
     * builder model so ids stay stable across a save-and-redraw.
     */
    B.graphData = function (model) {
        var nodes = model.statuses.map(function (s) {
            var color = '#97C2FC', border = '#2B7CE9';
            if (s.id === model.tracker.initial_status_id) { color = '#7BE141'; border = '#41A906'; }
            else if (s.updateable) { color = '#FFA807'; border = '#FA8E06'; }
            return {
                id: s.id,
                label: s.name,
                title: 'Status: ' + s.name +
                       '\nUpdateable: ' + (s.updateable ? 'Yes' : 'No') +
                       '\nAttachable: ' + (s.attachable ? 'Yes' : 'No'),
                color: { background: color, border: border, highlight: { background: color, border: '#000000' } },
                font: { size: 14, bold: s.id === model.tracker.initial_status_id },
                shape: 'box',
                margin: 10,
                kind: 'status'
            };
        });

        var edges = [];
        model.transitions.forEach(function (t) {
            var label = (t.display_name || t.name);
            var roleNames = t.role_ids.map(function (rid) {
                var r = model.roles.filter(function (x) { return x.id === rid; })[0];
                return r ? r.name : rid;
            });
            if (roleNames.length) { label += '\n[' + roleNames.join(', ') + ']'; }

            var mk = function (from, to, idSuffix) {
                return {
                    id: 't' + t.id + '_' + idSuffix,
                    from: from,
                    to: to,
                    label: label,
                    title: 'Transition: ' + t.name +
                           '\nRoles: ' + (roleNames.join(', ') || 'None') +
                           (t.same_status ? '\nStays in the same status' : ''),
                    arrows: 'to',
                    dashes: !!t.same_status,
                    color: { color: t.same_status ? '#BBBBBB' : '#848484', highlight: '#FF0000' },
                    font: { align: 'middle', size: 11, color: t.same_status ? '#999999' : '#343434' },
                    smooth: { type: 'cubicBezier', roundness: 0.5 },
                    hidden: !!t.same_status,
                    kind: 'transition',
                    transition_id: t.id
                };
            };

            if (t.prev_status_ids.length) {
                t.prev_status_ids.forEach(function (pid) {
                    var to = t.same_status ? pid : t.next_status_id;
                    if (!to) { return; }
                    edges.push(mk(pid, to, pid));
                });
            } else {
                var to = t.same_status ? model.tracker.initial_status_id : t.next_status_id;
                if (!to) { return; }
                edges.push(mk('start', to, 'new'));
            }
        });

        // Always present, unlike the read-only graph which only draws it when a
        // new-record transition already exists. Here it is the thing you drag FROM to
        // make one, so gating it on one existing means it can never be created.
        nodes.push({
            id: 'start', label: 'START', kind: 'start',
            title: 'Where a new record enters.\nDrag from here to a status to add a ' +
                   'transition that creates a record (no previous status).',
            color: { background: '#DDDDDD', border: '#888888' },
            shape: 'ellipse', font: { bold: true },
            borderWidth: 2, shapeProperties: { borderDashes: [5, 4] }
        });
        // Separates transitions that share a pair of statuses (see the GSP); without it
        // an antiparallel Submit/Rework pair renders as one line you cannot click apart.
        if (window.spreadParallelEdges) { window.spreadParallelEdges(edges); }
        return { nodes: nodes, edges: edges };
    };

    /** Redraws the canvas from a model, keeping wherever the user had dragged things. */
    B.redraw = function (model, selectAfter) {
        B.model = model;
        var positions = {};
        try { positions = B.network.getPositions(); } catch (e) { positions = {}; }

        var data = B.graphData(model);
        var selfShown = B.selfShown;
        data.edges.forEach(function (e) { if (e.dashes) { e.hidden = !selfShown; } });

        // Nodes vis has never laid out would all land on the same spot with physics off,
        // so anything without a remembered position is parked to the right of the graph,
        // stepped down, where it is visible and draggable rather than stacked.
        var known = Object.keys(positions).map(function (k) { return positions[k]; });
        var baseX = known.length ? Math.max.apply(null, known.map(function (p) { return p.x; })) + 220 : 0;
        var baseY = known.length ? Math.min.apply(null, known.map(function (p) { return p.y; })) : 0;
        var placed = 0;
        data.nodes.forEach(function (n) {
            if (positions[n.id]) { n.x = positions[n.id].x; n.y = positions[n.id].y; }
            else if (B.pendingNodePos) { n.x = B.pendingNodePos.x; n.y = B.pendingNodePos.y; }
            else { n.x = baseX; n.y = baseY + placed * 90; placed++; }
            n.fixed = false;
        });
        var addedNode = placed > 0 || !!B.pendingNodePos;
        B.pendingNodePos = null;

        B.nodes.clear();
        B.edges.clear();
        B.nodes.add(data.nodes);
        B.edges.add(data.edges);

        B.lists = JSON.parse(JSON.stringify(model.lists));

        // Reconcile the working order with fields that may have appeared or gone.
        var names = model.fields.map(function (f) { return f.name; });
        B.order = B.order.filter(function (n) { return names.indexOf(n) >= 0; });
        names.forEach(function (n) { if (B.order.indexOf(n) < 0) { B.order.push(n); } });

        if (selectAfter) { B.select(selectAfter.type, selectAfter.id, true); }
        else if (B.sel) { B.select(B.sel.type, B.sel.id, true); }
        else { B.renderPanel(); }

        // Only refit when something new appeared - refitting on every save would yank the
        // view out from under someone who had panned to where they were working.
        if (addedNode) { B.network.fit(); }
    };

    // ------------------------------------------------------------- selection

    function workingCopy(type, id) {
        if (type === 'status') {
            var s = statusById(id);
            if (!s) { return null; }
            return {
                type: 'status', id: s.id, name: s.name,
                updateable: !!s.updateable, attachable: !!s.attachable,
                view: s.displayfields.slice(), edit: s.editfields.slice(),
                roles: s.editroles.slice()
            };
        }
        var t = transitionById(id);
        if (!t) { return null; }
        return {
            type: 'transition', id: t.id, name: t.name, display_name: t.display_name || '',
            same_status: !!t.same_status, next_status_id: t.next_status_id || '',
            postprocess_id: t.postprocess_id || '',
            prev_status_ids: t.prev_status_ids.slice(),
            view: t.displayfields.slice(), edit: t.editfields.slice(),
            roles: t.role_ids.slice()
        };
    }

    B.select = function (type, id, keepDirty) {
        if (!keepDirty && B.isDirty() && !B.confirmDiscard()) { return; }
        B.confirmingDelete = false;
        if (!type) { B.sel = null; B.work = null; }
        else {
            var w = workingCopy(type, id);
            if (!w) { B.sel = null; B.work = null; }
            else {
                B.sel = { type: type, id: String(id) }; B.work = w;
                if (w.type === 'transition' && w.same_status) { revealSelfTransitions(); }
            }
        }
        B.renderPanel();
    };

    /** True when the selected status/transition differs from what the server holds. */
    B.selDirty = function () {
        if (!B.work || !B.sel) { return false; }
        var orig = workingCopy(B.sel.type, B.sel.id);
        return orig ? JSON.stringify(orig) !== JSON.stringify(B.work) : false;
    };

    /** True when the tracker-wide field lists differ from what the server holds. */
    B.listsDirty = function () {
        if (!B.lists) { return false; }
        return JSON.stringify(B.lists) !== JSON.stringify(B.model.lists);
    };

    B.isDirty = function () {
        return B.orderDirty || B.listsDirty() || B.selDirty();
    };

    B.confirmDiscard = function () {
        // Uses the panel rather than a modal dialog: a native confirm() blocks the page.
        notify('That selection has unsaved changes - Save or Revert first.', 'warn');
        return false;
    };

    // ------------------------------------------------------------- rendering

    // ------------------------------------------------------- field groups
    //
    // Membership is held on the GROUP (its `members` list, from field_options); a member
    // knows nothing about its groups. So "which groups is this field in?" is an inverted
    // index built here, and a field appearing in two groups needs no special case - it is
    // simply named by both.

    function fieldsByName() {
        var m = {};
        B.model.fields.forEach(function (f) { m[f.name] = f; });
        return m;
    }

    function membersOf(name) {
        var f = fieldsByName()[name];
        return (f && f.field_type === 'FieldGroup' && f.members) ? f.members : [];
    }

    /** name -> [group names naming it]. Rebuilt per render; the model is small. */
    function groupIndex() {
        var idx = {};
        B.model.fields.forEach(function (g) {
            if (g.field_type !== 'FieldGroup' || !g.members) { return; }
            g.members.forEach(function (m) {
                (idx[m] = idx[m] || []).push(g.name);
            });
        });
        return idx;
    }

    /**
     * Every field a selection actually renders, following groups into their members.
     * Putting a group in displayfields/editfields makes TrackerTagLib render the whole
     * fieldset, so the members are covered without ever being listed themselves - that
     * is the coverage this returns, and what the greyed marks in the table show.
     *
     * `seen` also guards traversal: a group cycle would otherwise loop forever here even
     * though the server refuses to create one.
     */
    function expandNames(names) {
        var out = {}, seen = {}, stack = (names || []).slice();
        while (stack.length) {
            var n = stack.shift();
            if (seen[n]) { continue; }
            seen[n] = true;
            out[n] = true;
            membersOf(n).forEach(function (m) { stack.push(m); });
        }
        return out;
    }

    /** Nested preview rows under an expanded group. Read-only: the group's tick governs them. */
    function renderSubRows(groupName, depth, impliedView, impliedEdit, trail, seen) {
        var byName = fieldsByName();
        var cols = LISTS.map(function () { return '<span class="tb-check tb-xtra"></span>'; }).join('');
        var html = '';
        var mems = membersOf(groupName);
        mems.forEach(function (mname, i) {
            var f = byName[mname];
            var last = (i === mems.length - 1);
            var branch = trail + (last ? '\u2514 ' : '\u251c ');
            if (!f) {
                html += '<div class="tb-subrow">' +
                        '<span></span>' +
                        '<span class="tb-name"><span class="tb-tree">' + branch + '</span>' + esc(mname) + '</span>' +
                        '<span class="tb-label" style="color:#c62828">no such field on this tracker</span>' +
                        '<span class="tb-type"></span><span class="tb-check"></span><span class="tb-check"></span>' +
                        cols + '</div>';
                return;
            }
            var isGroup = (f.field_type === 'FieldGroup');
            // A cycle cannot be created through the builder, but one already in the data
            // would hang the browser, so stop at the second visit and say so.
            var looped = isGroup && seen[mname];
            html += '<div class="tb-subrow">' +
                    '<span></span>' +
                    '<span class="tb-name"><span class="tb-tree">' + branch + '</span>' + esc(mname) + '</span>' +
                    '<span class="tb-label">' + esc(f.label || '') + '</span>' +
                    '<span class="tb-type">' + (isGroup ? 'group' : esc(f.field_type)) +
                        (looped ? ' <span style="color:#c62828">(cycle)</span>' : '') + '</span>' +
                    '<span class="tb-check">' + (impliedView[mname] ? '<span class="tb-dot" title="rendered because its group is ticked">&middot;</span>' : '') + '</span>' +
                    '<span class="tb-check">' + (impliedEdit[mname] ? '<span class="tb-dot" title="editable because its group is ticked">&middot;</span>' : '') + '</span>' +
                    cols + '</div>';
            if (isGroup && !looped) {
                var nseen = Object.assign({}, seen);
                nseen[mname] = true;
                html += renderSubRows(mname, depth + 1, impliedView, impliedEdit, trail + (last ? '\u00a0\u00a0 ' : '\u2502\u00a0 '), nseen);
            }
        });
        return html;
    }

    function renderFieldRows() {
        if (!B.model.fields.length) {
            return '<div class="tb-empty">This tracker has no fields yet. Use <strong>Add Fields</strong> above.</div>';
        }
        var byName = fieldsByName();
        var active = !!B.work;
        var index = groupIndex();
        // What the selection really renders once groups are followed into their members.
        var impliedView = active ? expandNames(B.work.view) : {};
        var impliedEdit = active ? expandNames(B.work.edit) : {};

        return B.order.map(function (name) {
            var f = byName[name];
            if (!f) { return ''; }
            var isGroup = (f.field_type === 'FieldGroup');
            var viewOn = active && B.work.view.indexOf(name) >= 0;
            var editOn = active && B.work.edit.indexOf(name) >= 0;
            // Covered through a group rather than ticked in its own right. The field keeps
            // its own checkbox - listing it directly as well is legal and renders it
            // outside the fieldset - so this only tints the row.
            var viaView = !viewOn && !!impliedView[name];
            var viaEdit = !editOn && !!impliedEdit[name];
            var owners = index[name] || [];

            var badge = '';
            if (owners.length) {
                var on = owners.some(function (g) { return impliedView[g] || impliedEdit[g]; });
                badge = '<span class="tb-gbadge' + (on ? ' tb-gon' : '') + '" title="' +
                        (on ? 'Included because ' + esc(owners.join(', ')) + ' is ticked'
                            : 'Belongs to ' + esc(owners.join(', '))) + '">in ' +
                        esc(owners.join(', ')) + '</span>';
            }
            // The caret lives in the NAME cell, not the first column: column one is the drag
            // grip, and a group row has to stay draggable like any other row.
            var caret = isGroup
                ? '<span class="tb-caret" data-group="' + esc(name) + '" title="Show what this group contains">' +
                  (B.expandedGroups[name] ? '\u25bc' : '\u25b6') + '</span> '
                : '';
            var groupBtn = isGroup
                ? ' <button type="button" class="tb-groupbtn" data-editgroup="' + esc(name) + '">edit members</button>'
                : '';

            var row = '<div class="tb-row' + ((viaView || viaEdit) ? ' tb-implied' : '') + '" data-field="' + esc(name) + '">' +
                   '<span class="tb-grip" title="Drag to reorder">&#8942;&#8942;</span>' +
                   '<span class="tb-name">' + caret + esc(name) + badge + '</span>' +
                   '<span class="tb-label">' + esc(f.label || '') +
                       (isGroup ? ' <span class="tb-type">(' + membersOf(name).length + ')</span>' : '') + '</span>' +
                   '<span class="tb-type">' + esc(f.field_type) + groupBtn + '</span>' +
                   '<span class="tb-check"><input type="checkbox" class="tb-view" data-field="' + esc(name) + '"' +
                       (viewOn ? ' checked' : '') + (active ? '' : ' disabled') + '>' +
                       (viaView ? '<span class="tb-dot" title="already shown via its group">&middot;</span>' : '') + '</span>' +
                   '<span class="tb-check"><input type="checkbox" class="tb-edit" data-field="' + esc(name) + '"' +
                       (editOn ? ' checked' : '') + (active ? '' : ' disabled') + '>' +
                       (viaEdit ? '<span class="tb-dot" title="already editable via its group">&middot;</span>' : '') + '</span>' +
                   // Tracker-wide, so these stay enabled even with nothing selected.
                   LISTS.map(function (key) {
                       var on = B.lists && B.lists[key].indexOf(name) >= 0;
                       return '<span class="tb-check tb-xtra">' +
                              '<input type="checkbox" class="tb-list" data-list="' + key + '" data-field="' + esc(name) + '"' +
                              (on ? ' checked' : '') + '></span>';
                   }).join('') +
                   '</div>';

            // Sub-rows sit outside the .tb-row so the sorter neither drags them nor reads
            // them back as field-order entries.
            if (isGroup && B.expandedGroups[name]) {
                var seen = {}; seen[name] = true;
                row += renderSubRows(name, 1, impliedView, impliedEdit, '\u00a0\u00a0 ', seen);
            }
            return row;
        }).join('');
    }

    // --------------------------------------------------- inferring an order
    //
    // Every status and transition stores its fields as an ORDERED csv, so the forms
    // themselves are dozens of partial opinions about what order the fields go in. This
    // aggregates them into one.
    //
    // It is a proposal, not a rule: the result is loaded into the table as an unsaved
    // order for review, and only the existing Save writes it. orderedFields() on the
    // server stays cheap and deterministic.
    //
    // Note this is worth running exactly once per tracker - saving an order renormalises
    // every csv into it, after which the lists all agree and re-inferring just echoes the
    // current order back.

    /** One csv, with groups expanded in place so members sit right after their group. */
    function votingList(csv) {
        var out = [], seen = {};
        var byName = fieldsByName();
        var walk = function (names, depth) {
            (names || []).forEach(function (n) {
                if (!byName[n] || seen[n]) { return; }
                seen[n] = true;
                out.push(n);
                // Depth guard: TrackerTagLib itself only recurses three levels, and a
                // cycle in the data would otherwise spin here.
                if (byName[n].field_type === 'FieldGroup' && depth < 4) {
                    walk(membersOf(n), depth + 1);
                }
            });
        };
        walk(csv, 0);
        return out;
    }

    /**
     * Consensus order across every form, by Copeland score: a field's score is the number
     * of fields it usually precedes minus the number that usually precede it. Pairs that
     * never share a form contribute nothing, so disjoint sets of fields do not fight, and
     * a genuine disagreement is settled by the majority rather than by whichever form was
     * read last. Cycles (a<b<c<a) cannot deadlock it the way a topological sort would.
     */
    B.inferOrder = function () {
        var lists = [];
        (B.model.statuses || []).forEach(function (s) {
            lists.push(votingList(s.displayfields));
            lists.push(votingList(s.editfields));
        });
        (B.model.transitions || []).forEach(function (t) {
            lists.push(votingList(t.displayfields));
            lists.push(votingList(t.editfields));
        });
        lists = lists.filter(function (l) { return l.length > 1; });

        if (!lists.length) {
            notify('No status or transition lists any fields yet - nothing to infer an order from.', 'warn');
            return;
        }

        var before = {}, appears = {};
        lists.forEach(function (l) {
            l.forEach(function (n) { appears[n] = (appears[n] || 0) + 1; });
            for (var i = 0; i < l.length; i++) {
                for (var j = i + 1; j < l.length; j++) {
                    var k = l[i] + '\u0000' + l[j];
                    before[k] = (before[k] || 0) + 1;
                }
            }
        });

        var known = Object.keys(appears);
        var score = {};
        known.forEach(function (a) {
            var sc = 0;
            known.forEach(function (b) {
                if (a === b) { return; }
                var ab = before[a + '\u0000' + b] || 0;
                var ba = before[b + '\u0000' + a] || 0;
                if (ab > ba) { sc++; } else if (ba > ab) { sc--; }
            });
            score[a] = sc;
        });

        var pos = {};
        B.order.forEach(function (n, i) { pos[n] = i; });
        var ranked = known.slice().sort(function (a, b) {
            if (score[b] !== score[a]) { return score[b] - score[a]; }
            // A field on more forms is the more established one; then keep what we have,
            // so the suggestion is stable and re-running it changes nothing.
            if (appears[b] !== appears[a]) { return appears[b] - appears[a]; }
            return pos[a] - pos[b];
        });

        // Fields no form mentions keep their current relative order and go last - there is
        // no evidence about them, so inventing a position would be noise.
        var seen = {};
        ranked.forEach(function (n) { seen[n] = true; });
        var rest = B.order.filter(function (n) { return !seen[n]; });

        var next = ranked.concat(rest);
        var changed = next.some(function (n, i) { return B.order[i] !== n; });
        B.order = next;
        B.orderDirty = changed;
        B.renderPanel();
        notify(changed
            ? 'Order suggested from ' + lists.length + ' form list(s): ' + ranked.length +
              ' field(s) placed by consensus, ' + rest.length + ' left in place at the end. ' +
              'Review it, drag anything that looks wrong, then Save.'
            : 'The forms already agree with the current order - nothing to change.', 'info');
    };

    /** The inline member editor for one group. */
    function renderMemberEditor() {
        var box = el('tb-groupbox');
        if (!box) { return; }
        if (!B.memberEdit) { box.style.display = 'none'; box.innerHTML = ''; return; }
        var g = B.memberEdit;
        var byName = fieldsByName();

        // Anything that would put the group inside itself, directly or through a nested
        // group, cannot be offered. The server refuses these too.
        var forbidden = {};
        forbidden[g.name] = true;
        B.model.fields.forEach(function (f) {
            if (f.field_type !== 'FieldGroup' || f.name === g.name) { return; }
            if (expandNames([f.name])[g.name]) { forbidden[f.name] = true; }
        });

        var rows = B.order.map(function (name) {
            var f = byName[name];
            if (!f || name === g.name) { return ''; }
            var checked = g.members.indexOf(name) >= 0;
            var bad = !!forbidden[name];
            var others = (groupIndex()[name] || []).filter(function (x) { return x !== g.name; });
            return '<label class="' + (bad ? 'tb-mdis' : '') + '">' +
                   '<input type="checkbox" class="tb-member" data-field="' + esc(name) + '"' +
                   (checked ? ' checked' : '') + (bad ? ' disabled' : '') + '> ' +
                   esc(name) + ' <span class="tb-mtype">' + esc(f.field_type) +
                   (others.length ? ' &middot; also in ' + esc(others.join(', ')) : '') +
                   (bad ? ' &middot; would nest this group inside itself' : '') +
                   '</span></label>';
        }).join('');

        box.style.display = '';
        box.innerHTML = '<strong>Members of ' + esc(g.name) + '</strong>' +
            '<div class="tb-hint">Ticking this group on a status or transition renders every field below, ' +
            'inside one fieldset. A field may belong to several groups - ticking any of them covers it. ' +
            'Existing members keep their order (that is the order the fieldset renders in); new ones are appended.</div>' +
            '<div class="tb-memberbox">' + (rows || '<div class="tb-empty">No other fields.</div>') + '</div>' +
            '<button type="button" id="tb-group-save">Save members</button> ' +
            '<button type="button" id="tb-group-cancel">Cancel</button>';
    }

    function renderRoleRows() {
        if (!B.model.roles.length) {
            return '<div class="tb-empty">This tracker has no roles defined.</div>';
        }
        var active = !!B.work;
        return B.model.roles.map(function (r) {
            // A status stores role NAMES in editroles; a transition stores role IDs.
            var key = (B.work && B.work.type === 'status') ? r.name : r.id;
            var on = active && B.work.roles.indexOf(key) >= 0;
            return '<div class="tb-row tb-rolerow">' +
                   '<span class="tb-name">' + esc(r.name) + '</span>' +
                   '<span class="tb-type">' + esc(r.role_type || '') + '</span>' +
                   '<span class="tb-check"><input type="checkbox" class="tb-role" data-role="' + esc(key) + '"' +
                       (on ? ' checked' : '') + (active ? '' : ' disabled') + '></span>' +
                   '</div>';
        }).join('');
    }

    function renderPrevRows() {
        if (!B.work || B.work.type !== 'transition') { return ''; }
        if (!B.model.statuses.length) {
            return '<div class="tb-empty">This tracker has no statuses yet.</div>';
        }
        return B.model.statuses.map(function (s) {
            var on = B.work.prev_status_ids.indexOf(s.id) >= 0;
            return '<div class="tb-row tb-prevrow">' +
                   '<span class="tb-name">' + esc(s.name) + '</span>' +
                   '<span class="tb-check"><input type="checkbox" class="tb-prev" data-status="' + esc(s.id) + '"' +
                       (on ? ' checked' : '') + '></span>' +
                   '</div>';
        }).join('');
    }

    /**
     * Options for a transition's postprocess: a script run after the transition commits.
     * Runable pages come first under their own heading because that is what a postprocess
     * is meant to be, but the rest of the module's pages stay selectable - the framework
     * only evaluates the page's content, and some trackers already point at a page that
     * was never flagged runable. Labelled by slug, which is the identity the tracker
     * export writes and the importer resolves.
     */
    function renderPostprocessOptions() {
        var pages = B.model.pages || [];
        var current = B.work.postprocess_id || '';
        var opt = function (p) {
            return '<option value="' + esc(p.id) + '"' + (p.id === current ? ' selected' : '') +
                   ' title="' + esc(p.title || p.slug) + '">' + esc(p.slug) + '</option>';
        };
        var group = function (label, list) {
            return list.length ? '<optgroup label="' + esc(label) + '">' + list.map(opt).join('') + '</optgroup>' : '';
        };
        var html = '<option value=""' + (current ? '' : ' selected') + '>(none)</option>' +
                   group('Runable pages', pages.filter(function (p) { return p.runable; })) +
                   group('Other pages',   pages.filter(function (p) { return !p.runable; }));
        // A page that has since been deleted, or one from another module left over from
        // before this picker existed, would otherwise silently reset itself to (none).
        if (current && !pages.some(function (p) { return p.id === current; })) {
            html += '<option value="' + esc(current) + '" selected>(page #' + esc(current) + ', not in this module)</option>';
        }
        return html;
    }

    function renderProps() {
        if (!B.work) { return ''; }
        if (B.work.type === 'status') {
            return '<div class="tb-props">' +
                '<label>Name <input type="text" id="tb-prop-name" value="' + esc(B.work.name) + '"></label>' +
                '<label><input type="checkbox" id="tb-prop-updateable"' + (B.work.updateable ? ' checked' : '') + '> Updateable</label>' +
                '<label><input type="checkbox" id="tb-prop-attachable"' + (B.work.attachable ? ' checked' : '') + '> Attachable</label>' +
                '</div>';
        }
        var opts = ['<option value="">(none)</option>'].concat(B.model.statuses.map(function (s) {
            return '<option value="' + esc(s.id) + '"' + (s.id === B.work.next_status_id ? ' selected' : '') + '>' + esc(s.name) + '</option>';
        })).join('');
        return '<div class="tb-props">' +
            '<label>Name <input type="text" id="tb-prop-name" value="' + esc(B.work.name) + '"></label>' +
            '<label>Button text <input type="text" id="tb-prop-display" value="' + esc(B.work.display_name) + '"></label>' +
            '<label><input type="checkbox" id="tb-prop-same"' + (B.work.same_status ? ' checked' : '') + '> Stays in same status</label>' +
            '<label class="tb-next' + (B.work.same_status ? ' tb-off' : '') + '">Goes to <select id="tb-prop-next">' + opts + '</select></label>' +
            '<label>Postprocess <select id="tb-prop-postprocess">' + renderPostprocessOptions() + '</select></label>' +
            '</div>';
    }

    B.renderPanel = function () {
        var head = el('builder-selection');
        var dirty = B.isDirty();

        if (!B.work) {
            head.innerHTML = '<strong>Nothing selected</strong> ' +
                '<span class="tb-hint">Click a status or a transition in the graph below to set its fields and roles.' +
                (B.orderDirty ? ' Field order has unsaved changes.' : '') +
                (B.listsDirty() ? ' Tracker columns have unsaved changes.' : '') + '</span>';
        } else {
            var kind = B.work.type === 'status' ? 'Status' : 'Transition';
            head.innerHTML = '<strong>' + kind + ': ' + esc(B.work.name) + '</strong>' +
                (dirty ? ' <span class="tb-dirty">unsaved changes</span>' : '') +
                '<span class="tb-hint">' +
                (B.work.type === 'status'
                    ? 'View = the fields shown on the record in this status (displayfields).'
                    : 'Edit = the fields on this transition\'s form (editfields). View = the read-only context shown beside it (displayfields).' +
                      (B.work.prev_status_ids.length ? '' : ' This transition creates a new record.')) +
                '</span>';
        }

        el('builder-props').innerHTML = renderProps();
        el('builder-field-rows').innerHTML = renderFieldRows();
        el('builder-role-rows').innerHTML = renderRoleRows();
        el('builder-roles-title').textContent = !B.work ? 'Roles'
            : (B.work.type === 'status' ? 'Roles that can edit in this status'
                                        : 'Roles that can perform this transition');

        // "Comes from" is the only place the new-record state (no previous status) can be
        // set or seen, so it is shown for every transition, not just ones that have one.
        var prevTable = el('builder-prev-table');
        prevTable.style.display = (B.work && B.work.type === 'transition') ? '' : 'none';
        el('builder-prev-rows').innerHTML = renderPrevRows();
        el('builder-prev-note').textContent = (B.work && B.work.type === 'transition' && !B.work.prev_status_ids.length)
            ? 'Nothing ticked - this transition creates a new record.'
            : '';

        el('tb-save').disabled = !dirty;
        el('tb-revert').disabled = !dirty;
        // Dragging a node back onto itself works, but nobody finds it - so a selected
        // status offers the same thing as a button.
        var self = el('tb-add-self');
        self.style.display = (B.work && B.work.type === 'status') ? '' : 'none';

        var del = el('tb-delete');
        del.style.display = B.work ? '' : 'none';
        del.textContent = B.confirmingDelete ? 'Really delete?' : 'Delete';
        del.className = B.confirmingDelete ? 'tb-danger tb-armed' : 'tb-danger';

        renderMemberEditor();
        bindPanel();
        B.applyCollapsed();
        B.attachDragSort();
    };

    function bindPanel() {
        var wrap = el('builder-panel');

        wrap.querySelectorAll('.tb-view').forEach(function (cb) {
            cb.onchange = function () { toggleIn(B.work.view, this.dataset.field, this.checked); B.renderPanel(); };
        });
        wrap.querySelectorAll('.tb-edit').forEach(function (cb) {
            cb.onchange = function () { toggleIn(B.work.edit, this.dataset.field, this.checked); B.renderPanel(); };
        });
        wrap.querySelectorAll('.tb-role').forEach(function (cb) {
            cb.onchange = function () { toggleIn(B.work.roles, this.dataset.role, this.checked); B.renderPanel(); };
        });
        wrap.querySelectorAll('.tb-list').forEach(function (cb) {
            cb.onchange = function () {
                toggleIn(B.lists[this.dataset.list], this.dataset.field, this.checked);
                B.renderPanel();
            };
        });
        wrap.querySelectorAll('.tb-prev').forEach(function (cb) {
            cb.onchange = function () { toggleIn(B.work.prev_status_ids, this.dataset.status, this.checked); B.renderPanel(); };
        });

        // Expanding a group is pure display state - it never marks the panel dirty.
        wrap.querySelectorAll('.tb-caret').forEach(function (c) {
            c.onmousedown = function (e) { e.stopPropagation(); };
            c.onclick = function () {
                var g = this.dataset.group;
                if (B.expandedGroups[g]) { delete B.expandedGroups[g]; } else { B.expandedGroups[g] = true; }
                B.renderPanel();
            };
        });
        wrap.querySelectorAll('[data-editgroup]').forEach(function (btn) {
            btn.onmousedown = function (e) { e.stopPropagation(); };
            btn.onclick = function () {
                var name = this.dataset.editgroup;
                var f = fieldsByName()[name];
                if (!f) { return; }
                B.memberEdit = { name: name, id: f.id, members: (f.members || []).slice() };
                B.expandedGroups[name] = true;
                B.renderPanel();
            };
        });
        wrap.querySelectorAll('.tb-member').forEach(function (cb) {
            cb.onchange = function () {
                if (!B.memberEdit) { return; }
                toggleIn(B.memberEdit.members, this.dataset.field, this.checked);
            };
        });
        var gsave = el('tb-group-save');
        if (gsave) { gsave.onclick = function () { B.saveGroup(); }; }
        var gcancel = el('tb-group-cancel');
        if (gcancel) { gcancel.onclick = function () { B.memberEdit = null; B.renderPanel(); }; }

        var name = el('tb-prop-name');
        if (name) { name.oninput = function () { B.work.name = this.value; markDirtyOnly(); }; }
        var disp = el('tb-prop-display');
        if (disp) { disp.oninput = function () { B.work.display_name = this.value; markDirtyOnly(); }; }
        var upd = el('tb-prop-updateable');
        if (upd) { upd.onchange = function () { B.work.updateable = this.checked; B.renderPanel(); }; }
        var att = el('tb-prop-attachable');
        if (att) { att.onchange = function () { B.work.attachable = this.checked; B.renderPanel(); }; }
        var same = el('tb-prop-same');
        if (same) { same.onchange = function () { B.work.same_status = this.checked; B.renderPanel(); }; }
        var next = el('tb-prop-next');
        if (next) { next.onchange = function () { B.work.next_status_id = this.value; B.renderPanel(); }; }
        // Not named `post`: that is the module-level AJAX helper, and shadowing it here
        // would be a live grenade for anything later added to this function.
        var ppSel = el('tb-prop-postprocess');
        if (ppSel) { ppSel.onchange = function () { B.work.postprocess_id = this.value; B.renderPanel(); }; }
    }

    // Text inputs re-render on every keystroke otherwise, which steals the caret.
    function markDirtyOnly() {
        var dirty = B.isDirty();
        el('tb-save').disabled = !dirty;
        el('tb-revert').disabled = !dirty;
    }

    function toggleIn(list, value, on) {
        var i = list.indexOf(value);
        if (on && i < 0) { list.push(value); }
        if (!on && i >= 0) { list.splice(i, 1); }
    }

    // ---------------------------------------------------- side-panel collapsing
    //
    // Fields, Roles and Comes-from share one flex row. Roles and Comes-from are short and
    // narrow; Fields carries long names, labels, group badges and six-plus checkbox
    // columns, so it is the one that runs out of room. Collapsing either neighbour hands
    // the space back to it.
    //
    // The choice is remembered per browser: re-collapsing on every page load would make
    // the option more annoying than the problem. It is a display preference only - never
    // part of the model, and it never marks the panel dirty.
    var COLLAPSE_KEY = 'g5.builder.collapsed';

    function readCollapsed() {
        try { return JSON.parse(localStorage.getItem(COLLAPSE_KEY)) || {}; }
        catch (e) { return {}; }   // private windows and blocked site data both throw
    }

    function writeCollapsed(state) {
        try { localStorage.setItem(COLLAPSE_KEY, JSON.stringify(state)); } catch (e) { }
    }

    B.applyCollapsed = function () {
        var state = readCollapsed();
        document.querySelectorAll('.tb-sidetoggle').forEach(function (btn) {
            var panel = el(btn.dataset.panel);
            if (!panel) { return; }
            var on = !!state[btn.dataset.panel];
            panel.classList.toggle('tb-collapsed', on);
            btn.textContent = on ? 'show' : 'hide';
        });
    };

    B.bindCollapse = function () {
        document.querySelectorAll('.tb-sidetoggle').forEach(function (btn) {
            btn.onclick = function () {
                var state = readCollapsed();
                var key = this.dataset.panel;
                if (state[key]) { delete state[key]; } else { state[key] = true; }
                writeCollapsed(state);
                B.applyCollapsed();
            };
        });
    };

    B.attachDragSort = function () {
        var rows = el('builder-field-rows');
        if (!rows || !rows.firstElementChild || typeof DragSort === 'undefined') { return; }
        // DragSort caches itself on the element; re-running after innerHTML replaced the
        // children would otherwise leave a sorter bound to detached nodes.
        rows.DragSort = null;
        B._sorter = new DragSort(rows, {
            selector: '.tb-row',
            mode: 'vertical',
            callbacks: {
                dragEnd: function () {
                    B.order = Array.prototype.map.call(rows.querySelectorAll('.tb-row'), function (r) {
                        return r.dataset.field;
                    });
                    B.orderDirty = true;
                    B.renderPanel();
                }
            }
        });
    };

    // ---------------------------------------------------------------- saving

    B.save = function () {
        var chain = Promise.resolve(null);

        if (B.orderDirty) {
            chain = chain.then(function () {
                return post(B.cfg.urls.saveFieldOrder, { tracker_id: B.cfg.trackerId, field_names: B.order });
            }).then(function (res) { B.orderDirty = false; return res; });
        }

        // After the order, so the lists are written in the order that was just saved.
        if (B.listsDirty()) {
            chain = chain.then(function () {
                return post(B.cfg.urls.saveTrackerLists, { tracker_id: B.cfg.trackerId, lists: B.lists });
            });
        }

        if (B.selDirty()) {
            var w = B.work;
            chain = chain.then(function () {
                if (w.type === 'status') {
                    return post(B.cfg.urls.saveStatus, {
                        tracker_id: B.cfg.trackerId, id: w.id, name: w.name,
                        updateable: w.updateable, attachable: w.attachable,
                        view_fields: w.view, edit_fields: w.edit, editroles: w.roles
                    });
                }
                return post(B.cfg.urls.saveTransition, {
                    tracker_id: B.cfg.trackerId, id: w.id, name: w.name,
                    display_name: w.display_name, same_status: w.same_status,
                    next_status_id: w.next_status_id, prev_status_ids: w.prev_status_ids,
                    postprocess_id: w.postprocess_id,
                    role_ids: w.roles, view_fields: w.view, edit_fields: w.edit
                });
            });
        }

        chain.then(function (res) {
            if (!res) { notify('Nothing to save.', 'info'); return; }
            B.redraw(res.model, B.sel);
            notify('Saved.', 'ok');
        }).catch(function (err) {
            notify(err.message, 'error');
        });
    };

    /**
     * Membership saves on its own, not with the panel's Save: it belongs to the field, not
     * to the selected status or transition, and the server rewrites field_options directly.
     * The selection is re-applied from the returned model so nothing in the panel is lost.
     */
    B.saveGroup = function () {
        if (!B.memberEdit) { return; }
        var g = B.memberEdit;
        post(B.cfg.urls.saveFieldGroup, {
            tracker_id: B.cfg.trackerId, field_id: g.id, members: g.members
        }).then(function (res) {
            B.memberEdit = null;
            B.redraw(res.model, B.sel);
            notify('Members of ' + g.name + ' saved.', 'ok');
        }).catch(function (err) {
            notify(err.message, 'error');
        });
    };

    B.revert = function () {
        B.orderDirty = false;
        B.order = B.model.fields.map(function (f) { return f.name; });
        B.lists = JSON.parse(JSON.stringify(B.model.lists));
        if (B.sel) { B.work = workingCopy(B.sel.type, B.sel.id); }
        notify('');
        B.renderPanel();
    };

    B.remove = function () {
        if (!B.work) { return; }
        if (!B.confirmingDelete) {
            B.confirmingDelete = true;
            B.renderPanel();
            return;
        }
        var url = B.work.type === 'status' ? B.cfg.urls.deleteStatus : B.cfg.urls.deleteTransition;
        post(url, { tracker_id: B.cfg.trackerId, id: B.work.id })
            .then(function (res) {
                B.confirmingDelete = false;
                B.sel = null; B.work = null;
                B.redraw(res.model, null);
                notify('Deleted.', 'ok');
            })
            .catch(function (err) { B.confirmingDelete = false; notify(err.message, 'error'); });
    };

    // ------------------------------------------------------- add status/edge

    B.showAddStatus = function (pos) {
        B.pendingNodePos = pos || null;
        el('tb-add-status-box').style.display = '';
        el('tb-new-status-name').value = '';
        el('tb-new-status-name').focus();
    };

    B.createStatus = function () {
        var name = el('tb-new-status-name').value.trim();
        if (!name) { notify('Give the status a name.', 'warn'); return; }
        post(B.cfg.urls.saveStatus, { tracker_id: B.cfg.trackerId, name: name })
            .then(function (res) {
                el('tb-add-status-box').style.display = 'none';
                B.redraw(res.model, { type: 'status', id: res.saved_id });
                notify('Status "' + name + '" created.', 'ok');
            })
            .catch(function (err) { notify(err.message, 'error'); });
    };

    B.startAddEdge = function () {
        notify('Drag from one status to another to create a transition. ' +
               'Drop it back on the same status for an edit-in-place transition, or drag from START for a new record.', 'info');
        B.network.addEdgeMode();
    };

    B.showAddTransition = function (from, to) {
        B.pendingEdge = { from: from, to: to };
        var fromName = from === 'start' ? 'a new record' : (statusById(from) || {}).name;
        var toName = (statusById(to) || {}).name;
        el('tb-add-transition-box').style.display = '';
        el('tb-new-transition-where').textContent = (from !== 'start' && from === to)
            ? fromName + ' → itself (stays in the same status - an edit-in-place transition)'
            : fromName + '  →  ' + toName;
        el('tb-new-transition-name').value = '';
        el('tb-new-transition-name').focus();
    };

    B.createTransition = function () {
        var name = el('tb-new-transition-name').value.trim();
        if (!name) { notify('Give the transition a name.', 'warn'); return; }
        var e = B.pendingEdge;
        post(B.cfg.urls.saveTransition, {
            tracker_id: B.cfg.trackerId, name: name,
            prev_status_ids: e.from === 'start' ? [] : [e.from],
            next_status_id: e.to, same_status: e.from === e.to
        }).then(function (res) {
            el('tb-add-transition-box').style.display = 'none';
            var made = res.model.transitions.filter(function (t) { return t.id === String(res.saved_id); })[0];
            // Before redraw: redraw reads B.selfShown to decide each edge's visibility.
            if (made && made.same_status) { revealSelfTransitions(); }
            B.redraw(res.model, { type: 'transition', id: res.saved_id });
            notify('Transition "' + name + '" created' +
                   (made && made.same_status ? ' - it stays in the same status, shown as a dashed loop.' : '.'), 'ok');
        }).catch(function (err) { notify(err.message, 'error'); });
    };

    // ------------------------------------------------------------ add fields

    B.addFieldRow = function () {
        var body = el('tb-newfields');
        var row = document.createElement('div');
        row.className = 'tb-newfield';
        row.innerHTML =
            '<input type="text" class="nf-name" placeholder="field_name">' +
            '<input type="text" class="nf-label" placeholder="Label">' +
            '<select class="nf-type">' + B.cfg.fieldTypes.map(function (t) {
                return '<option value="' + esc(t) + '"' + (t === 'Text' ? ' selected' : '') + '>' + esc(t) + '</option>';
            }).join('') + '</select>' +
            '<button type="button" class="nf-remove" title="Remove">&times;</button>';
        row.querySelector('.nf-remove').onclick = function () { row.remove(); };
        body.appendChild(row);
        row.querySelector('.nf-name').focus();
    };

    B.addRoleRow = function () {
        var body = el('tb-newroles');
        var row = document.createElement('div');
        row.className = 'tb-newrole';
        row.innerHTML =
            '<input type="text" class="nr-name" placeholder="Role name">' +
            '<select class="nr-type">' + B.cfg.roleTypes.map(function (t) {
                return '<option value="' + esc(t) + '">' + esc(t) + '</option>';
            }).join('') + '</select>' +
            '<input type="text" class="nr-rule" placeholder="Rule (Data Compare only, optional)">' +
            '<button type="button" class="nf-remove" title="Remove">&times;</button>';
        row.querySelector('.nf-remove').onclick = function () { row.remove(); };
        body.appendChild(row);
        row.querySelector('.nr-name').focus();
    };

    B.createRoles = function () {
        var rows = Array.prototype.slice.call(document.querySelectorAll('#tb-newroles .tb-newrole'));
        var roles = rows.map(function (r) {
            return {
                name: r.querySelector('.nr-name').value.trim(),
                role_type: r.querySelector('.nr-type').value,
                role_rule: r.querySelector('.nr-rule').value.trim()
            };
        }).filter(function (r) { return r.name; });

        if (!roles.length) { notify('Add at least one role name.', 'warn'); return; }

        post(B.cfg.urls.addRoles, { tracker_id: B.cfg.trackerId, roles: roles })
            .then(function (res) {
                el('tb-newroles').innerHTML = '';
                B.addRoleRow();
                B.redraw(res.model, B.sel);
                var msg = res.added.length ? 'Added ' + res.added.join(', ') + '.' : 'Nothing added.';
                if (res.skipped && res.skipped.length) { msg += ' Skipped: ' + res.skipped.join('; ') + '.'; }
                notify(msg, (res.skipped && res.skipped.length) ? 'warn' : 'ok');
            })
            .catch(function (err) { notify(err.message, 'error'); });
    };

    B.createFields = function () {
        var rows = Array.prototype.slice.call(document.querySelectorAll('#tb-newfields .tb-newfield'));
        var fields = rows.map(function (r) {
            return {
                name: r.querySelector('.nf-name').value.trim(),
                label: r.querySelector('.nf-label').value.trim(),
                field_type: r.querySelector('.nf-type').value
            };
        }).filter(function (f) { return f.name; });

        if (!fields.length) { notify('Add at least one field name.', 'warn'); return; }

        post(B.cfg.urls.addFields, { tracker_id: B.cfg.trackerId, fields: fields })
            .then(function (res) {
                el('tb-newfields').innerHTML = '';
                B.addFieldRow();
                B.redraw(res.model, B.sel);
                var msg = res.added.length ? 'Added ' + res.added.join(', ') + '.' : 'Nothing added.';
                if (res.skipped && res.skipped.length) { msg += ' Skipped: ' + res.skipped.join('; ') + '.'; }
                if (res.dbnote) { msg += ' ' + res.dbnote; }
                notify(msg, res.dbnote || (res.skipped && res.skipped.length) ? 'warn' : 'ok');
            })
            .catch(function (err) { notify(err.message, 'error'); });
    };

    // ------------------------------------------------------------------ init

    B.init = function (cfg, network, nodes, edges) {
        B.cfg = cfg;
        B.model = cfg.model;
        B.network = network;
        B.nodes = nodes;
        B.edges = edges;
        B.order = B.model.fields.map(function (f) { return f.name; });
        B.lists = JSON.parse(JSON.stringify(B.model.lists));

        // Bound once: the toggles live in the GSP, not in any re-rendered block.
        B.bindCollapse();
        B.applyCollapsed();

        network.setOptions({
            manipulation: {
                enabled: false,
                addEdge: function (data, callback) {
                    callback(null);   // never let vis mutate the dataset itself
                    if (data.from === 'start') { B.showAddTransition('start', data.to); }
                    else { B.showAddTransition(data.from, data.to); }
                }
            }
        });

        network.on('click', function (params) {
            if (params.nodes.length) {
                var node = nodes.get(params.nodes[0]);
                if (node && node.kind === 'status') { B.select('status', node.id); }
                else { B.select(null); }
            } else if (params.edges.length) {
                var edge = edges.get(params.edges[0]);
                if (edge && edge.transition_id) { B.select('transition', edge.transition_id); }
            } else {
                B.select(null);
            }
        });

        network.on('doubleClick', function (params) {
            if (!params.nodes.length && !params.edges.length) {
                B.showAddStatus(params.pointer ? params.pointer.canvas : null);
            }
        });

        el('tb-save').onclick = B.save;
        el('tb-revert').onclick = B.revert;
        el('tb-delete').onclick = B.remove;
        el('tb-add-status').onclick = function () { B.showAddStatus(null); };
        el('tb-create-status').onclick = B.createStatus;
        el('tb-cancel-status').onclick = function () { el('tb-add-status-box').style.display = 'none'; };
        el('tb-add-transition').onclick = B.startAddEdge;
        el('tb-add-self').onclick = function () {
            if (B.work && B.work.type === 'status') { B.showAddTransition(B.work.id, B.work.id); }
        };
        el('tb-create-transition').onclick = B.createTransition;
        el('tb-cancel-transition').onclick = function () { el('tb-add-transition-box').style.display = 'none'; };
        el('tb-add-field-row').onclick = B.addFieldRow;
        el('tb-create-fields').onclick = B.createFields;
        el('tb-add-role-row').onclick = B.addRoleRow;
        el('tb-create-roles').onclick = B.createRoles;
        el('tb-toggle-fields').onclick = function () {
            var box = el('tb-addfields-box');
            box.style.display = box.style.display === 'none' ? '' : 'none';
        };
        el('tb-toggle-cols').onclick = function () {
            B.colsExpanded = !B.colsExpanded;
            el('builder-fields-table').classList.toggle('tb-expanded', B.colsExpanded);
            this.textContent = B.colsExpanded ? 'hide tracker columns' : 'show tracker columns';
        };

        el('tb-infer-order').onclick = function () {
            if (B.isDirty() && !confirm('This replaces the current unsaved order. Continue?')) { return; }
            B.inferOrder();
        };
        el('tb-toggle-roles').onclick = function () {
            var box = el('tb-addroles-box');
            box.style.display = box.style.display === 'none' ? '' : 'none';
        };

        el('tb-new-status-name').addEventListener('keydown', function (e) {
            if (e.key === 'Enter') { e.preventDefault(); B.createStatus(); }
        });
        el('tb-new-transition-name').addEventListener('keydown', function (e) {
            if (e.key === 'Enter') { e.preventDefault(); B.createTransition(); }
        });

        B.addFieldRow();
        B.addRoleRow();
        B.renderPanel();
    };
})();
