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

    function renderFieldRows() {
        if (!B.model.fields.length) {
            return '<div class="tb-empty">This tracker has no fields yet. Use <strong>Add Fields</strong> above.</div>';
        }
        var byName = {};
        B.model.fields.forEach(function (f) { byName[f.name] = f; });
        var active = !!B.work;

        return B.order.map(function (name) {
            var f = byName[name];
            if (!f) { return ''; }
            var viewOn = active && B.work.view.indexOf(name) >= 0;
            var editOn = active && B.work.edit.indexOf(name) >= 0;
            return '<div class="tb-row" data-field="' + esc(name) + '">' +
                   '<span class="tb-grip" title="Drag to reorder">&#8942;&#8942;</span>' +
                   '<span class="tb-name">' + esc(name) + '</span>' +
                   '<span class="tb-label">' + esc(f.label || '') + '</span>' +
                   '<span class="tb-type">' + esc(f.field_type) + '</span>' +
                   '<span class="tb-check"><input type="checkbox" class="tb-view" data-field="' + esc(name) + '"' +
                       (viewOn ? ' checked' : '') + (active ? '' : ' disabled') + '></span>' +
                   '<span class="tb-check"><input type="checkbox" class="tb-edit" data-field="' + esc(name) + '"' +
                       (editOn ? ' checked' : '') + (active ? '' : ' disabled') + '></span>' +
                   // Tracker-wide, so these stay enabled even with nothing selected.
                   LISTS.map(function (key) {
                       var on = B.lists && B.lists[key].indexOf(name) >= 0;
                       return '<span class="tb-check tb-xtra">' +
                              '<input type="checkbox" class="tb-list" data-list="' + key + '" data-field="' + esc(name) + '"' +
                              (on ? ' checked' : '') + '></span>';
                   }).join('') +
                   '</div>';
        }).join('');
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

        bindPanel();
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
