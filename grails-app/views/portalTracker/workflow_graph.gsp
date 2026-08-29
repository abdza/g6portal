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
            .tb-head, .tb-row {
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
            #builder-fields-table.tb-expanded .tb-row {
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
                                    <div class="tb-hint tb-xtra">These five are tracker-wide, not per status or transition - they save with the same Save button.</div>
                                </div>
                                <div class="tb-table">
                                    <h5 id="builder-roles-title">Roles</h5>
                                    <div class="tb-head tb-rolehead">
                                        <span>Role</span><span>Type</span><span class="tb-check">On</span>
                                    </div>
                                    <div class="tb-rows" id="builder-role-rows"></div>
                                </div>
                                <div class="tb-table" id="builder-prev-table" style="display:none">
                                    <h5>Comes from <span class="tb-hint" style="display:inline">which statuses this transition starts from</span></h5>
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

            /**
             * Fans out edges that share a pair of statuses so they stop lying on top of
             * each other. Two cases both occur in real trackers: antiparallel (ae_submission
             * has Submitted->Rework "Rework" against Rework->Submitted "Submit") and
             * parallel (two different transitions START->New). Both render at exactly the
             * same midpoint under the default cubicBezier, so one is unclickable.
             *
             * curvedCW/CCW is relative to each edge's own from->to direction, so for an
             * edge running against the pair's canonical order the type is flipped - that
             * makes a slot mean the same *absolute* side whichever way the edge points.
             * Edges with no sibling keep the default routing, so ordinary graphs are
             * unchanged.
             */
            window.spreadParallelEdges = function(edgeArray) {
                var groups = {};
                edgeArray.forEach(function(e) {
                    if (e.from === e.to) { return; }   // self-loops: vis places these itself
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
                    deleteTransition: '<g:createLink action="builder_delete_transition"/>'
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

            var hierarchicalLayout = {
                enabled: true,
                direction: 'LR',
                sortMethod: 'directed',
                nodeSpacing: 150,
                levelSeparation: 200
            };

            var options = {
                layout: {
                    hierarchical: hierarchicalLayout
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

            // Re-runs the hierarchical layout, then releases it again - the way back
            // to a tidy graph after dragging nodes around.
            function relayout() {
                // Register before setOptions: the layout change redraws synchronously,
                // so a handler attached afterwards misses the event and the nodes stay
                // pinned to their level axis.
                network.once('afterDrawing', function() {
                    releaseHierarchy();
                    network.fit();
                });
                network.setOptions({ layout: { hierarchical: hierarchicalLayout } });
            }

            network.once('afterDrawing', function() {
                releaseHierarchy();
                network.fit();
            });

            if (builderEnabled) {
                TrackerBuilder.selfShown = false;
                TrackerBuilder.init(builderConfig, network, nodes, edges);
            }
        </script>
    </body>
</html>
