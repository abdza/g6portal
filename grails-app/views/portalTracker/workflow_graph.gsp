<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <title>Workflow Graph - ${tracker.name}</title>
        <%-- Served locally, not from a CDN: SecurityHeadersInterceptor sets a
             script-src 'self' CSP, which silently blocks any external CDN. --%>
        <script src="/assets/vis-network/vis-network.min.js"></script>
        <g:if test="${canEdit}">
        <asset:javascript src="tracker_builder.js"/>
        </g:if>
        <style>
            #workflow-network {
                width: 100%;
                height: 700px;
                border: 1px solid lightgray;
                background-color: #fafafa;
            }
            .legend {
                background: white;
                border: 1px solid #ccc;
                padding: 15px;
                margin-top: 20px;
                border-radius: 5px;
            }
            .legend-item {
                display: flex;
                align-items: center;
                margin-bottom: 10px;
            }
            .legend-icon {
                width: 30px;
                height: 30px;
                margin-right: 10px;
                border-radius: 50%;
                display: inline-block;
            }
            .status-node {
                background-color: #97C2FC;
                border: 2px solid #2B7CE9;
            }
            .initial-status {
                background-color: #7BE141;
                border: 2px solid #41A906;
            }
            .updateable-status {
                background-color: #FFA807;
                border: 2px solid #FA8E06;
            }
            .transition-edge {
                stroke: #848484;
                stroke-width: 2;
            }
            .controls {
                margin: 20px 0;
                display: flex;
                gap: 10px;
                flex-wrap: wrap;
            }
            .controls button {
                padding: 8px 16px;
                border-radius: 4px;
                border: 1px solid #ccc;
                background: white;
                cursor: pointer;
            }
            .controls button:hover {
                background: #f0f0f0;
            }
            #node-info {
                background: white;
                border: 1px solid #ccc;
                padding: 15px;
                margin-top: 20px;
                border-radius: 5px;
                display: none;
            }
            #node-info.active {
                display: block;
            }

            /* ---- workflow builder ---- */
            #builder-panel {
                border: 1px solid #ccc;
                border-radius: 5px;
                background: #fff;
                padding: 15px;
                margin-bottom: 20px;
            }
            #builder-selection { margin-bottom: 10px; }
            .tb-hint { color: #777; font-size: 12px; display: block; margin-top: 3px; }
            .tb-dirty {
                background: #FFA807; color: #fff; border-radius: 3px;
                padding: 1px 7px; font-size: 11px; margin-left: 6px;
            }
            .builder-message { display: none; padding: 8px 12px; border-radius: 4px; margin: 10px 0; font-size: 13px; }
            .builder-message.show { display: block; }
            .builder-message.ok    { background: #e6f7e6; border: 1px solid #7BE141; }
            .builder-message.warn  { background: #fff6e0; border: 1px solid #FFA807; }
            .builder-message.error { background: #fdecea; border: 1px solid #e57373; }
            .builder-message.info  { background: #eef4fd; border: 1px solid #97C2FC; }

            .tb-props { display: flex; flex-wrap: wrap; gap: 15px; align-items: center; margin-bottom: 12px; }
            .tb-props label { display: flex; align-items: center; gap: 6px; font-size: 13px; margin: 0; }
            .tb-props input[type=text], .tb-props select { padding: 3px 6px; }
            .tb-props .tb-off { opacity: 0.4; }

            .tb-tables { display: flex; gap: 20px; flex-wrap: wrap; align-items: flex-start; }
            .tb-table { flex: 1 1 420px; min-width: 320px; }
            .tb-table h5 { margin: 0 0 6px; font-size: 13px; }
            /* Rows are divs, not a real <table>: the drag-sorter animates the drop gap
               with margins, which table rows ignore. */
            .tb-head, .tb-row, .tb-subrow {
                display: grid;
                grid-template-columns: 22px 1.4fr 1.4fr 1fr 46px 46px;
                align-items: center;
                gap: 6px;
                padding: 4px 6px;
                font-size: 12px;
            }
            .tb-rolerow, .tb-rolehead { grid-template-columns: 1.4fr 1fr 46px; }
            .tb-prevrow, .tb-prevhead { grid-template-columns: 1.4fr 46px; }
            /* Tracker-wide columns are collapsed by default; a grid child set to
               display:none takes no track, so the layout closes up cleanly. */
            .tb-xtra { display: none; }
            #builder-fields-table.tb-expanded { flex-basis: 100%; }
            #builder-fields-table.tb-expanded .tb-xtra { display: block; }
            #builder-fields-table.tb-expanded .tb-head,
            #builder-fields-table.tb-expanded .tb-row,
            #builder-fields-table.tb-expanded .tb-subrow {
                grid-template-columns: 22px 1.4fr 1.4fr 1fr 46px 46px 46px 56px 46px 56px 46px;
            }
            #builder-fields-table.tb-expanded .tb-hint.tb-xtra { display: block; margin-top: 6px; }
            .tb-linkish {
                border: none !important; background: none !important; color: #2B7CE9;
                cursor: pointer; font-size: 12px; padding: 0 !important; text-decoration: underline;
            }
            .tb-table { flex: 1 1 300px; }
            .tb-head { font-weight: bold; border-bottom: 2px solid #ddd; color: #555; }
            .tb-row { border-bottom: 1px solid #f0f0f0; background: #fff; }
            .tb-row:hover { background: #f7fbff; }
            .tb-rows { max-height: 320px; overflow-y: auto; border: 1px solid #eee; }
            .tb-grip { cursor: grab; color: #bbb; letter-spacing: -3px; user-select: none; }
            .tb-name { font-family: monospace; }
            .tb-type { color: #888; }
            .tb-check { text-align: center; }
            .tb-empty { padding: 12px; color: #888; font-size: 12px; }
            .dragsort--dragElem { opacity: 0.4; }

            /* ---- field groups -------------------------------------------------
               Sub-rows are deliberately NOT .tb-row: the drag sorter's selector and
               its dragEnd handler both key on that class, so giving a nested preview
               row the same class would make it draggable and inject it into the saved
               field order. */
            .tb-subrow { background: #fbfcfe; border-bottom: 1px dotted #eef1f4; color: #666; }
            .tb-subrow .tb-name { font-size: 11px; }
            .tb-subrow .tb-type { font-size: 11px; font-style: italic; }
            .tb-caret {
                cursor: pointer; user-select: none; color: #2B7CE9;
                text-align: center; font-size: 10px;
            }
            .tb-tree { color: #c3c9d1; font-family: monospace; white-space: pre; }
            .tb-gbadge {
                font-size: 10px; color: #5a6472; background: #eef1f4;
                border-radius: 3px; padding: 0 4px; margin-left: 5px; white-space: nowrap;
            }
            .tb-gbadge.tb-gon { background: #e0efe6; color: #2c6b4f; }
            .tb-implied { background: #f4fbf7; }
            .tb-dot { color: #2c6b4f; font-weight: bold; }
            .tb-groupbtn {
                border: none !important; background: none !important; color: #2B7CE9;
                cursor: pointer; font-size: 11px; padding: 0 !important; text-decoration: underline;
            }
            .tb-memberbox { max-height: 260px; overflow-y: auto; border: 1px solid #ddd;
                            background: #fff; padding: 6px; margin: 6px 0; }
            .tb-memberbox label { display: block; font-size: 12px; padding: 1px 0; }
            .tb-memberbox .tb-mtype { color: #888; font-size: 11px; }
            .tb-memberbox .tb-mdis { opacity: 0.45; }

            /* ---- collapsing the side tables ----------------------------------
               Fields, Roles and Comes-from share one flex row, so Fields gets a third of
               the width however little the other two need. Collapsing one leaves just its
               heading and hands the freed space back to Fields, which is the table that
               actually has to hold long names, labels and six-plus checkbox columns. */
            .tb-table.tb-collapsed { flex: 0 0 auto; min-width: 0; }
            .tb-table.tb-collapsed .tb-head,
            .tb-table.tb-collapsed .tb-rows,
            .tb-table.tb-collapsed .tb-hint { display: none; }
            .tb-table.tb-collapsed h5 { margin-bottom: 0; white-space: nowrap; opacity: 0.7; }
            .tb-sidetoggle { margin-left: 6px; font-weight: normal; }

            .tb-actions { margin-top: 12px; display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
            .tb-actions button, .tb-inline button {
                padding: 6px 14px; border-radius: 4px; border: 1px solid #ccc; background: #fff; cursor: pointer;
            }
            .tb-actions button:disabled { opacity: 0.45; cursor: default; }
            .tb-danger { color: #c62828; border-color: #e57373 !important; }
            .tb-danger.tb-armed { background: #c62828 !important; color: #fff; }

            .tb-inline { margin-top: 12px; padding: 10px; background: #f7f7f7; border-radius: 4px; }
            .tb-inline input[type=text], .tb-inline select { padding: 4px 6px; }
            .tb-newfield, .tb-newrole { display: flex; gap: 8px; margin-bottom: 6px; align-items: center; }
            .tb-newfield input, .tb-newrole input { flex: 1 1 auto; }
            .tb-newrole .nr-rule { flex: 2 1 auto; font-family: monospace; font-size: 12px; }
            .nf-remove { border: none !important; background: none !important; color: #c62828; font-size: 18px; cursor: pointer; }
        </style>
    </head>
    <body>
        <div id="content" role="main">
            <div class="container">
                <section class="row">
                    <div class="nav" role="navigation">
                        <ul>
                            <li><a class="home" href="${createLink(uri: '/')}">Home</a></li>
                            <li><g:link class="list" action="index">Tracker List</g:link></li>
                            <li><g:link class="show" action="show" id="${tracker.id}">Back to ${tracker.name}</g:link></li>
                        </ul>
                    </div>
                </section>

                <section class="row">
                    <div class="col-12">
                        <h1>Workflow Graph: ${tracker.name}</h1>
                        <p class="text-muted">${tracker.module} - ${tracker.slug}</p>

                        <g:if test="${flash.message}">
                            <div class="alert alert-info" role="status">${flash.message}</div>
                        </g:if>

                        <div class="controls">
                            <button onclick="network.fit();">Fit to Screen</button>
                            <button onclick="network.moveTo({scale: 1.0});">Reset Zoom</button>
                            <button onclick="relayout();">Re-apply Layout</button>
                            <g:if test="${canEdit}">
                            <button id="tb-add-status">+ Status</button>
                            <button id="tb-add-transition">+ Transition</button>
                            </g:if>
                            <button onclick="togglePhysics();">Toggle Physics</button>
                            <button onclick="toggleSelfTransitions();">Toggle Self-Transitions</button>
                            <button onclick="exportGraph();">Export as Image</button>
                        </div>

                        <g:if test="${canEdit}">
                        <div id="builder-panel">
                            <div id="builder-selection"></div>
                            <div id="builder-message" class="builder-message"></div>
                            <div id="builder-props"></div>

                            <div class="tb-tables">
                                <div class="tb-table" id="builder-fields-table">
                                    <h5>Fields
                                        <span class="tb-hint" style="display:inline">drag a row to set the tracker's field order</span>
                                        <button type="button" id="tb-toggle-cols" class="tb-linkish">show tracker columns</button>
                                        <button type="button" id="tb-infer-order" class="tb-linkish"
                                            title="Work out an order from the field lists already stored on every status and transition">suggest order from forms</button>
                                    </h5>
                                    <div class="tb-head">
                                        <span></span><span>Name</span><span>Label</span><span>Type</span>
                                        <span class="tb-check">View</span><span class="tb-check">Edit</span>
                                        <span class="tb-check tb-xtra" title="Columns shown on the tracker's list page">List</span>
                                        <span class="tb-check tb-xtra" title="Fetched with each row but not displayed - available to row classes and links">Hidden</span>
                                        <span class="tb-check tb-xtra" title="Columns included in the Excel download">Excel</span>
                                        <span class="tb-check tb-xtra" title="Fields the list page's search box looks in">Search</span>
                                        <span class="tb-check tb-xtra" title="Fields offered as filters on the list page">Filter</span>
                                    </div>
                                    <div class="tb-rows" id="builder-field-rows"></div>
                                    <div class="tb-hint">A <strong>Field Group</strong> row expands (&#9654;) to show what it contains.
                                        Ticking the group renders every field inside it as one fieldset, so its members show a
                                        &middot; rather than needing their own tick.</div>
                                    <div class="tb-hint tb-xtra">These five are tracker-wide, not per status or transition - they save with the same Save button.</div>
                                </div>
                                <div class="tb-table" id="builder-roles-table">
                                    <%-- The title is a span of its own: renderPanel() rewrites its
                                         textContent every render, which would wipe a sibling button
                                         placed directly inside the h5. --%>
                                    <h5><span id="builder-roles-title">Roles</span>
                                        <button type="button" class="tb-linkish tb-sidetoggle"
                                                data-panel="builder-roles-table">hide</button></h5>
                                    <div class="tb-head tb-rolehead">
                                        <span>Role</span><span>Type</span><span class="tb-check">On</span>
                                    </div>
                                    <div class="tb-rows" id="builder-role-rows"></div>
                                </div>
                                <div class="tb-table" id="builder-prev-table" style="display:none">
                                    <h5>Comes from <span class="tb-hint" style="display:inline">which statuses this transition starts from</span>
                                        <button type="button" class="tb-linkish tb-sidetoggle"
                                                data-panel="builder-prev-table">hide</button></h5>
                                    <div class="tb-head tb-prevhead">
                                        <span>Status</span><span class="tb-check">On</span>
                                    </div>
                                    <div class="tb-rows" id="builder-prev-rows"></div>
                                    <div class="tb-hint" id="builder-prev-note"></div>
                                </div>
                            </div>

                            <div class="tb-actions">
                                <button id="tb-save">Save</button>
                                <button id="tb-revert">Revert</button>
                                <button id="tb-add-self" style="display:none">+ Self-transition</button>
                                <button id="tb-delete" class="tb-danger" style="display:none">Delete</button>
                                <button id="tb-toggle-fields">Add Fields</button>
                                <button id="tb-toggle-roles">Add Roles</button>
                            </div>

                            <div class="tb-inline" id="tb-groupbox" style="display:none"></div>

                            <div class="tb-inline" id="tb-addfields-box" style="display:none">
                                <strong>Add fields</strong>
                                <div class="tb-hint">Columns are created in the database as soon as you add them.</div>
                                <div id="tb-newfields"></div>
                                <button type="button" id="tb-add-field-row">+ Row</button>
                                <button type="button" id="tb-create-fields">Create Fields</button>
                            </div>

                            <div class="tb-inline" id="tb-addroles-box" style="display:none">
                                <strong>Add roles</strong>
                                <div class="tb-hint">A <em>User Role</em> gets its members from the module's roles and needs no rule.
                                    A <em>Data Compare</em> role matches a record against the rule - a GSP expression rendering a SQL
                                    condition, e.g. <code>reviewed_by_staff='${'$'}{curuser?.id}'</code>. Leave the rule blank to fill it in later.</div>
                                <div id="tb-newroles"></div>
                                <button type="button" id="tb-add-role-row">+ Row</button>
                                <button type="button" id="tb-create-roles">Create Roles</button>
                            </div>

                            <div class="tb-inline" id="tb-add-status-box" style="display:none">
                                <strong>New status</strong>
                                <input type="text" id="tb-new-status-name" placeholder="Status name">
                                <button type="button" id="tb-create-status">Create</button>
                                <button type="button" id="tb-cancel-status">Cancel</button>
                                <div class="tb-hint">You can also double-click an empty spot on the canvas.</div>
                            </div>

                            <div class="tb-inline" id="tb-add-transition-box" style="display:none">
                                <strong>New transition</strong> <span id="tb-new-transition-where"></span>
                                <input type="text" id="tb-new-transition-name" placeholder="Transition name">
                                <button type="button" id="tb-create-transition">Create</button>
                                <button type="button" id="tb-cancel-transition">Cancel</button>
                            </div>
                        </div>
                        </g:if>

                        <div id="workflow-network"></div>

                        <div id="node-info">
                            <h4 id="info-title">Node Information</h4>
                            <div id="info-content"></div>
                        </div>

                        <div class="legend">
                            <h4>Legend</h4>
                            <div class="legend-item">
                                <span class="legend-icon initial-status"></span>
                                <span>Initial Status (Entry Point)</span>
                            </div>
                            <div class="legend-item">
                                <span class="legend-icon updateable-status"></span>
                                <span>Updateable Status</span>
                            </div>
                            <div class="legend-item">
                                <span class="legend-icon status-node"></span>
                                <span>Regular Status</span>
                            </div>
                            <div class="legend-item">
                                <span style="display: inline-block; width: 60px; height: 3px; background: #848484; margin-right: 10px;"></span>
                                <span>Transition (with role information)</span>
                            </div>
                            <div class="legend-item">
                                <span style="display: inline-block; width: 60px; height: 0; border-top: 3px dashed #BBBBBB; margin-right: 10px;"></span>
                                <span>Self-transition - stays in the same status (hidden by default, use Toggle Self-Transitions)</span>
                            </div>
                        </div>

                        <div class="mt-4">
                            <h4>Tracker Statistics</h4>
                            <ul>
                                <li><strong>Total Statuses:</strong> ${nodes.size()}</li>
                                <li><strong>Total Transitions:</strong> ${edges.size()}</li>
                                <li><strong>Initial Status:</strong> ${tracker.initial_status?.name ?: 'Not set'}</li>
                                <li><strong>Tracker Type:</strong> ${tracker.tracker_type ?: 'Not specified'}</li>
                            </ul>
                        </div>
                    </div>
                </section>
            </div>
        </div>

        <script type="text/javascript">
            // Parse the nodes and edges data from the controller
            var nodesData = ${raw(nodesJson)};
            var edgesData = ${raw(edgesJson)};
            var initialStatusId = "${tracker.initial_status?.id ?: ''}";

            // Self-loops get their own pass. vis draws every self-reference at the same
            // angle and radius, so two transitions looping on one status (itis_reporting
            // has Admin Edit and Update TAT both on New) come out as a single loop with
            // two labels on top of each other. v10 exposes per-edge selfReference, so
            // each loop gets its own corner AND its own radius: the angle alone separates
            // the arcs but leaves the labels ~3px apart, which any longer transition name
            // would close up. Angles go round the diagonals first - that is where ordinary
            // edges are least likely to attach - and wrap after four, by which point the
            // growing radius is what keeps them apart.
            //
            // A status carrying a single loop is left on the library default, so a graph
            // that never had the problem renders exactly as it did before.
            var SELF_ANGLE_SLOTS = 4;
            function spreadSelfLoops(edgeArray) {
                var byNode = {};
                edgeArray.forEach(function(e) {
                    if (e.from !== e.to) { return; }
                    (byNode[e.from] = byNode[e.from] || []).push(e);
                });
                Object.keys(byNode).forEach(function(nodeId) {
                    var group = byNode[nodeId];
                    if (group.length < 2) { return; }
                    group.forEach(function(e, i) {
                        e.selfReference = {
                            size: 20 + i * 14,
                            angle: (Math.PI / 4) + (i % SELF_ANGLE_SLOTS) * (Math.PI / 2),
                            renderBehindTheNode: true
                        };
                    });
                });
            }

            /**
             * Fans out edges that would otherwise lie on top of each other. Three cases
             * all occur in real trackers: antiparallel (ae_submission has Submitted->Rework
             * "Rework" against Rework->Submitted "Submit"), parallel (two different
             * transitions START->New), and several self-loops on one status (itis_reporting
             * has both "Admin Edit" and "Update TAT" sitting on New). The first two render
             * at exactly the same midpoint under the default cubicBezier; the third is
             * worse, because vis gives every self-reference the same angle and radius, so
             * the loops coincide exactly and only the topmost label is legible.
             *
             * curvedCW/CCW is relative to each edge's own from->to direction, so for an
             * edge running against the pair's canonical order the type is flipped - that
             * makes a slot mean the same *absolute* side whichever way the edge points.
             * Edges with no sibling keep the default routing, so ordinary graphs are
             * unchanged.
             */
            window.spreadParallelEdges = function(edgeArray) {
                spreadSelfLoops(edgeArray);
                var groups = {};
                edgeArray.forEach(function(e) {
                    if (e.from === e.to) { return; }   // handled by spreadSelfLoops above
                    var key = [e.from, e.to].sort().join('\u0000');
                    (groups[key] = groups[key] || []).push(e);
                });
                Object.keys(groups).forEach(function(key) {
                    var group = groups[key];
                    if (group.length < 2) { return; }
                    var canonicalFrom = key.split('\u0000')[0];
                    group.forEach(function(e, i) {
                        var type = (i % 2 === 0) ? 'curvedCW' : 'curvedCCW';
                        if (e.from !== canonicalFrom) {
                            type = (type === 'curvedCW') ? 'curvedCCW' : 'curvedCW';
                        }
                        e.smooth = {
                            enabled: true,
                            type: type,
                            roundness: 0.2 * (Math.floor(i / 2) + 1)
                        };
                    });
                });
                return edgeArray;
            };

            var builderEnabled = <g:if test="${canEdit}">true</g:if><g:else>false</g:else>;
            var builderConfig = <g:if test="${canEdit}">{
                trackerId: ${tracker.id},
                model: ${raw(builderJson)},
                fieldTypes: ${raw(fieldTypesJson)},
                roleTypes: ${raw(roleTypesJson)},
                urls: {
                    addFields:        '<g:createLink action="builder_add_fields"/>',
                    addRoles:         '<g:createLink action="builder_add_roles"/>',
                    saveFieldOrder:   '<g:createLink action="builder_save_field_order"/>',
                    saveTrackerLists: '<g:createLink action="builder_save_tracker_lists"/>',
                    saveStatus:       '<g:createLink action="builder_save_status"/>',
                    deleteStatus:     '<g:createLink action="builder_delete_status"/>',
                    saveTransition:   '<g:createLink action="builder_save_transition"/>',
                    deleteTransition: '<g:createLink action="builder_delete_transition"/>',
                    saveFieldGroup:   '<g:createLink action="builder_save_field_group"/>'
                }
            }</g:if><g:else>null</g:else>;

            // In edit mode the graph is derived from the builder model rather than from
            // nodesData/edgesData, so that node and edge ids match the ids the save
            // endpoints hand back and a redraw can re-select what was just saved.
            var nodes, edges;
            if (builderEnabled) {
                var builderGraph = TrackerBuilder.graphData(builderConfig.model);
                nodes = new vis.DataSet(builderGraph.nodes);
                edges = new vis.DataSet(builderGraph.edges);
            }
            else {

            // Create nodes array for vis.js
            nodes = new vis.DataSet(nodesData.map(function(node) {
                var color = '#97C2FC';  // Default blue
                var borderColor = '#2B7CE9';
                var font = { size: 14 };

                // Color initial status green
                if (node.id === initialStatusId) {
                    color = '#7BE141';
                    borderColor = '#41A906';
                    font.bold = true;
                }
                // Color updateable status orange
                else if (node.updateable) {
                    color = '#FFA807';
                    borderColor = '#FA8E06';
                }

                return {
                    id: node.id,
                    label: node.label,
                    title: 'Status: ' + node.label +
                           '<br>Updateable: ' + (node.updateable ? 'Yes' : 'No') +
                           '<br>Attachable: ' + (node.attachable ? 'Yes' : 'No') +
                           (node.flow ? '<br>Flow: ' + node.flow : ''),
                    color: {
                        background: color,
                        border: borderColor,
                        highlight: {
                            background: color,
                            border: '#000000'
                        }
                    },
                    font: font,
                    shape: 'box',
                    margin: 10,
                    data: node
                };
            }));

            // Create edges array for vis.js
            edges = new vis.DataSet(window.spreadParallelEdges(edgesData.filter(function(edge) {
                return edge.to !== null && edge.to !== undefined;
            }).map(function(edge) {
                var label = edge.displayName;
                if (edge.roles) {
                    label += '\\n[' + edge.roles + ']';
                }

                return {
                    id: edge.id,
                    from: edge.from || 'start',
                    to: edge.to,
                    label: label,
                    title: 'Transition: ' + edge.label +
                           '<br>Roles: ' + (edge.roles || 'None') +
                           (edge.sameStatus ? '<br>Stays in the same status' : ''),
                    arrows: 'to',
                    // Same-status transitions are self-loops; draw them dashed and pale
                    // so they read as annotations rather than as flow between statuses.
                    dashes: edge.sameStatus ? true : false,
                    color: {
                        color: edge.sameStatus ? '#BBBBBB' : '#848484',
                        highlight: '#FF0000'
                    },
                    font: {
                        align: 'middle',
                        size: 11,
                        color: edge.sameStatus ? '#999999' : '#343434'
                    },
                    smooth: {
                        type: 'cubicBezier',
                        roundness: 0.5
                    },
                    hidden: edge.sameStatus ? true : false,
                    data: edge
                };
            })));

            // Add a virtual start node for new transitions
            var hasNewTransitions = edgesData.some(function(edge) {
                return edge.isNew;
            });

            if (hasNewTransitions) {
                nodes.add({
                    id: 'start',
                    label: 'START',
                    color: {
                        background: '#DDDDDD',
                        border: '#888888'
                    },
                    shape: 'ellipse',
                    font: { bold: true }
                });
            }

            }  // end read-only graph construction

            // Create the network
            var container = document.getElementById('workflow-network');
            var data = {
                nodes: nodes,
                edges: edges
            };

            // Spacing is worked out from the graph's own shape rather than fixed - see
            // applyLayout(). This first pass only has to produce *something* for the
            // initial draw; applyLayout() runs immediately after it and replaces it.
            function hierarchicalLayout(nodeSpacing, levelSeparation) {
                return {
                    enabled: true,
                    direction: 'LR',
                    sortMethod: 'directed',
                    nodeSpacing: nodeSpacing,
                    levelSeparation: levelSeparation,
                    // Statuses nothing points at are each their own tree, and the default
                    // treeSpacing of 200 lays a blank band between every one of them.
                    treeSpacing: 60
                };
            }

            var options = {
                layout: {
                    hierarchical: hierarchicalLayout(100, 200)
                },
                physics: {
                    enabled: false
                },
                interaction: {
                    hover: true,
                    tooltipDelay: 100,
                    navigationButtons: true,
                    keyboard: true
                },
                nodes: {
                    borderWidth: 2,
                    borderWidthSelected: 3
                },
                edges: {
                    width: 2,
                    selectionWidth: 4
                }
            };

            var network = new vis.Network(container, data, options);
            var physicsEnabled = false;

            // Event handlers
            network.on('click', function(params) {
                var nodeInfo = document.getElementById('node-info');
                var infoContent = document.getElementById('info-content');
                var infoTitle = document.getElementById('info-title');

                if (params.nodes.length > 0) {
                    var nodeId = params.nodes[0];
                    var node = nodes.get(nodeId);

                    if (node && node.data) {
                        infoTitle.textContent = 'Status: ' + node.label;
                        var html = '<dl>';
                        html += '<dt>ID:</dt><dd>' + node.id + '</dd>';
                        html += '<dt>Updateable:</dt><dd>' + (node.data.updateable ? 'Yes' : 'No') + '</dd>';
                        html += '<dt>Attachable:</dt><dd>' + (node.data.attachable ? 'Yes' : 'No') + '</dd>';
                        if (node.data.flow) {
                            html += '<dt>Flow Order:</dt><dd>' + node.data.flow + '</dd>';
                        }
                        html += '</dl>';

                        // Find incoming and outgoing transitions
                        var incoming = [];
                        var outgoing = [];
                        edges.forEach(function(edge) {
                            if (edge.to === nodeId) {
                                incoming.push(edge);
                            }
                            if (edge.from === nodeId) {
                                outgoing.push(edge);
                            }
                        });

                        if (incoming.length > 0) {
                            html += '<h5>Incoming Transitions:</h5><ul>';
                            incoming.forEach(function(edge) {
                                html += '<li>' + edge.data.label + ' (Roles: ' + (edge.data.roles || 'None') + ')</li>';
                            });
                            html += '</ul>';
                        }

                        if (outgoing.length > 0) {
                            html += '<h5>Outgoing Transitions:</h5><ul>';
                            outgoing.forEach(function(edge) {
                                html += '<li>' + edge.data.label + ' (Roles: ' + (edge.data.roles || 'None') + ')</li>';
                            });
                            html += '</ul>';
                        }

                        infoContent.innerHTML = html;
                        nodeInfo.className = 'active';
                    }
                } else if (params.edges.length > 0) {
                    var edgeId = params.edges[0];
                    var edge = edges.get(edgeId);

                    if (edge && edge.data) {
                        infoTitle.textContent = 'Transition: ' + edge.data.label;
                        var html = '<dl>';
                        html += '<dt>Display Name:</dt><dd>' + edge.data.displayName + '</dd>';
                        html += '<dt>Roles:</dt><dd>' + (edge.data.roles || 'None') + '</dd>';
                        var fromNode = nodes.get(edge.from);
                        var toNode = nodes.get(edge.to);
                        html += '<dt>From:</dt><dd>' + (edge.from === 'start' ? 'New Record' : (fromNode ? fromNode.label : edge.from)) + '</dd>';
                        html += '<dt>To:</dt><dd>' + (toNode ? toNode.label : edge.to) + '</dd>';
                        html += '</dl>';

                        infoContent.innerHTML = html;
                        nodeInfo.className = 'active';
                    }
                } else {
                    nodeInfo.className = '';
                }
            });

            function togglePhysics() {
                physicsEnabled = !physicsEnabled;
                network.setOptions({ physics: { enabled: physicsEnabled } });
            }

            // Self-transitions start hidden: a single same_status transition is usually
            // wired to every status, so showing them by default buries the real flow.
            var selfTransitionsShown = false;

            // Single entry point: the builder calls this to reveal self-transitions when
            // you create or select one, and it must not desync from the toggle button.
            // Keyed on `dashes`, which both edge builders set - the read-only path
            // carries edge.data.sameStatus but the builder's does not, and keying on
            // that left this doing nothing whenever the builder was active.
            window.setSelfTransitions = function(show) {
                selfTransitionsShown = !!show;
                // The builder rebuilds the edge set on every save; it needs to know
                // whether self-transitions are currently meant to be visible.
                if (window.TrackerBuilder) { TrackerBuilder.selfShown = selfTransitionsShown; }
                edges.forEach(function(edge) {
                    if (edge.dashes) {
                        edges.update({ id: edge.id, hidden: !selfTransitionsShown });
                    }
                });
            };

            function toggleSelfTransitions() {
                window.setSelfTransitions(!selfTransitionsShown);
            }

            function exportGraph() {
                // This would require additional libraries like html2canvas
                alert('Export functionality would require additional libraries. Consider using browser screenshot tools for now.');
            }

            // While a hierarchical layout is active vis pins every node to its level's
            // axis - with direction 'LR' that means fixed.x, so nodes could only be
            // dragged up and down. Let the layout run once to get a readable
            // left-to-right arrangement, then hand its output back as ordinary
            // coordinates so nodes drag freely in both directions.
            function releaseHierarchy() {
                var positions = network.getPositions();
                network.setOptions({ layout: { hierarchical: { enabled: false } } });
                nodes.update(Object.keys(positions).map(function(id) {
                    return { id: id, x: positions[id].x, y: positions[id].y, fixed: false };
                }));
            }

            // Statuses that no transition enters or leaves. A self-transition does not
            // connect a status to anything, so it does not count as flow.
            function isolatedNodeIds() {
                var degree = {};
                nodes.getIds().forEach(function(id) { degree[id] = 0; });
                edges.forEach(function(edge) {
                    if (edge.from === edge.to) { return; }
                    if (degree[edge.from] !== undefined) { degree[edge.from]++; }
                    if (degree[edge.to] !== undefined) { degree[edge.to]++; }
                });
                return nodes.getIds().filter(function(id) { return !degree[id]; });
            }

            // How many levels the flow occupies, and how many statuses sit on its
            // busiest one. With direction 'LR' a level is an x coordinate. Isolated
            // statuses are excluded: they all land on one level and would make every
            // graph look far busier than its flow actually is.
            function flowShape(isolated) {
                var isIsolated = {};
                isolated.forEach(function(id) { isIsolated[id] = true; });
                var positions = network.getPositions();
                var perLevel = {};
                Object.keys(positions).forEach(function(id) {
                    if (isIsolated[id]) { return; }
                    var level = Math.round(positions[id].x);
                    perLevel[level] = (perLevel[level] || 0) + 1;
                });
                var levels = Object.keys(perLevel);
                return {
                    levels: levels.length,
                    busiest: levels.reduce(function(most, level) {
                        return Math.max(most, perLevel[level]);
                    }, 0)
                };
            }

            var ISOLATED_ROW = 70;   // row pitch of the unwired-status block

            // Packs the isolated statuses into rows beneath the flow. Left to the
            // hierarchical layout they get a full row each: ecdd2 has 10 of them against
            // 10 statuses of actual flow, which stretched the graph to 3650px tall for
            // 1000px of width, and fit() then had to zoom out to 17% to show it.
            function gridIsolated(isolated) {
                if (!isolated.length) { return; }
                var isIsolated = {};
                isolated.forEach(function(id) { isIsolated[id] = true; });
                var flow = nodes.getIds().filter(function(id) { return !isIsolated[id]; });
                var positions = network.getPositions();

                var left = 0, flowWidth = 0, bottom = -140;
                if (flow.length) {
                    var xs = flow.map(function(id) { return positions[id].x; });
                    var ys = flow.map(function(id) { return positions[id].y; });
                    left = Math.min.apply(null, xs);
                    flowWidth = Math.max.apply(null, xs) - left;
                    bottom = Math.max.apply(null, ys);
                }

                // Wrap at the width of the flow above, so the block sits under it rather
                // than widening the graph - but never narrower than a readable few nodes.
                var rowWidth = Math.max(flowWidth, 600);
                var x = left, y = bottom + 140;
                isolated.forEach(function(id) {
                    var box = network.getBoundingBox(id);
                    var width = (box && box.right - box.left) || 120;
                    if (x > left && (x + width) > (left + rowWidth)) {
                        x = left;
                        y += ISOLATED_ROW;
                    }
                    nodes.update({ id: id, x: x + width / 2, y: y, fixed: false });
                    x += width + 26;
                });
            }

            function clamp(value, low, high) {
                return Math.max(low, Math.min(high, value));
            }

            // Which level (with direction 'LR', which column) each status sits in, as its
            // distance from the start of the flow. vis can work levels out itself, but
            // 'directed' is a heuristic that turns order-sensitive as soon as the flow has
            // a cycle - and a rework loop is a cycle, so nearly every tracker has one.
            // On ecdd2 it put New, the entry status, three columns in, behind statuses
            // that only New leads to. Walking out from the statuses nothing enters is both
            // stable and the order a workflow is meant to be read in.
            function assignLevels() {
                var ids = nodes.getIds();
                var outgoing = {}, incoming = {};
                ids.forEach(function(id) { outgoing[id] = []; incoming[id] = 0; });
                edges.forEach(function(edge) {
                    // A self-transition says nothing about ordering.
                    if (edge.from === edge.to) { return; }
                    if (!outgoing[edge.from] || incoming[edge.to] === undefined) { return; }
                    outgoing[edge.from].push(edge.to);
                    incoming[edge.to]++;
                });

                var level = {};
                var queue = ids.filter(function(id) { return !incoming[id]; });
                // A flow that is one closed loop has nothing with a free entry point;
                // start from the initial status so the walk still has somewhere to begin.
                if (!queue.length && ids.length) {
                    queue = [ids.indexOf(initialStatusId) >= 0 ? initialStatusId : ids[0]];
                }
                queue.forEach(function(id) { level[id] = 0; });
                for (var i = 0; i < queue.length; i++) {
                    var here = queue[i];
                    outgoing[here].forEach(function(next) {
                        if (level[next] !== undefined) { return; }
                        level[next] = level[here] + 1;
                        queue.push(next);
                    });
                }

                // Only reachable from a cycle the walk never entered - park it at the front
                // rather than leave vis to guess.
                nodes.update(ids.map(function(id) {
                    return { id: id, level: level[id] === undefined ? 0 : level[id] };
                }));
            }

            // One level can hold more statuses than the canvas is tall, and no amount of
            // spacing tuning helps: ecdd2's rfi tracker has 20 statuses that nothing
            // transitions into, so every one of them sits on the first level as a single
            // 1200px column with all its edges converging into a tangle. A level taller
            // than the canvas is dealt out into sub-columns instead, keeping the order vis
            // chose for it (its crossing-minimisation is worth reusing) and shifting the
            // levels after it across to make room.
            //
            // Graphs where every level fits are left exactly where vis put them.
            function wrapCrowdedLevels(availableHeight) {
                var positions = network.getPositions();
                var byLevel = {};
                nodes.get().forEach(function(node) {
                    var level = node.level || 0;
                    (byLevel[level] = byLevel[level] || []).push(node.id);
                });

                var rowPitch = 62;   // a box is 46 high
                var capacity = Math.max(3, Math.floor(availableHeight / rowPitch));
                var levels = Object.keys(byLevel).sort(function(a, b) { return a - b; });
                var crowded = levels.some(function(level) { return byLevel[level].length > capacity; });
                if (!crowded) { return; }

                var cursor = 0;
                levels.forEach(function(level) {
                    // vis's vertical order within the level is the one to preserve.
                    var column = byLevel[level].sort(function(a, b) {
                        return positions[a].y - positions[b].y;
                    });
                    var widest = column.reduce(function(most, id) {
                        var box = network.getBoundingBox(id);
                        return Math.max(most, (box && box.right - box.left) || 120);
                    }, 0);

                    var rows = Math.min(column.length, capacity);
                    var columnPitch = widest + 60;
                    var top = -((rows - 1) * rowPitch) / 2;
                    column.forEach(function(id, index) {
                        nodes.update({
                            id: id,
                            x: cursor + Math.floor(index / rows) * columnPitch,
                            y: top + (index % rows) * rowPitch,
                            fixed: false
                        });
                    });
                    cursor += Math.ceil(column.length / rows) * columnPitch + 80;
                });
            }

            // Lays the graph out so it roughly fills the canvas, then hands back free
            // coordinates. Spacing has to be derived rather than fixed: ecdd2's flow puts
            // only 3 statuses on its busiest level and wants them far apart, while a
            // tracker with 15 on one level needs them tight or the graph smears over
            // several thousand pixels and fit() zooms out past the point of legibility.
            // Two passes - the first is only measured, never shown, because the layout is
            // what decides the levels and we need those before we can pick the spacing.
            function applyLayout() {
                var isolated = isolatedNodeIds();
                var isIsolated = {};
                isolated.forEach(function(id) { isIsolated[id] = true; });

                // The isolated statuses come out of the graph while the layout runs. vis
                // gives every node on a level a row to itself, so ecdd2's ten unwired
                // statuses - all of which land on one level - were shoving the flow's own
                // statuses thousands of pixels apart, which is the real reason the graph
                // was unreadable. They go back afterwards, in a block of their own.
                var parkedNodes = nodes.get(isolated);
                var parkedEdges = edges.get({
                    filter: function(edge) { return isIsolated[edge.from] || isIsolated[edge.to]; }
                });
                var selected = network.getSelectedNodes();

                if (parkedNodes.length) {
                    edges.remove(parkedEdges.map(function(edge) { return edge.id; }));
                    nodes.remove(isolated);
                }

                if (nodes.length) {
                    assignLevels();
                    network.setOptions({ layout: { hierarchical: hierarchicalLayout(100, 200) } });
                    var shape = flowShape([]);

                    // The block of unwired statuses goes below the flow, so its rows come
                    // out of the height the flow has to play with - otherwise the two
                    // together overshoot the canvas and fit() shrinks everything again.
                    // Two rows is an estimate: the real count depends on name lengths,
                    // which are not known until the block is packed.
                    var canvas = container.getBoundingClientRect();
                    var reserved = 120 + (isolated.length ? 140 + ISOLATED_ROW * 2 : 0);
                    var nodeSpacing = clamp((canvas.height - reserved) / Math.max(shape.busiest, 1), 55, 200);
                    var levelSeparation = clamp((canvas.width - 160) / Math.max(shape.levels - 1, 1), 170, 320);

                    network.setOptions({
                        layout: { hierarchical: hierarchicalLayout(nodeSpacing, levelSeparation) }
                    });
                    releaseHierarchy();
                    // Deliberately the whole canvas height, not the flow's share of it: a
                    // sub-column short of rows is a sub-column wide of extra width, and
                    // overshooting the height slightly costs far less than that.
                    wrapCrowdedLevels(canvas.height - 100);
                }
                else {
                    // Nothing but unwired statuses: the layout never ran, so turn it off
                    // by hand or it would re-pin them the moment they are added back.
                    network.setOptions({ layout: { hierarchical: { enabled: false } } });
                }

                if (parkedNodes.length) {
                    nodes.add(parkedNodes);
                    edges.add(parkedEdges);
                    // getBoundingBox only knows a node's width once it has been drawn, and
                    // the widths are what the block is packed by.
                    network.redraw();
                    gridIsolated(isolated);
                    if (selected.length) { network.selectNodes(selected, false); }
                }
                network.fit();
            }

            // Re-runs the layout - the way back to a tidy graph after dragging nodes
            // around. setOptions applies the layout synchronously, so the positions are
            // there to be read and released as soon as it returns.
            function relayout() {
                applyLayout();
            }

            network.once('afterDrawing', function() {
                applyLayout();
            });

            if (builderEnabled) {
                TrackerBuilder.selfShown = false;
                TrackerBuilder.init(builderConfig, network, nodes, edges);
            }
        </script>
    </body>
</html>
