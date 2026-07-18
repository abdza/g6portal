<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <title>Workflow Graph - ${tracker.name}</title>
        <script src="/assets/vis-network/vis-network.min.js"></script>
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
                            <button onclick="togglePhysics();">Toggle Physics</button>
                            <button onclick="exportGraph();">Export as Image</button>
                        </div>

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

            // Create nodes array for vis.js
            var nodes = new vis.DataSet(nodesData.map(function(node) {
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
            var edges = new vis.DataSet(edgesData.filter(function(edge) {
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
                           '<br>Roles: ' + (edge.roles || 'None'),
                    arrows: 'to',
                    color: {
                        color: '#848484',
                        highlight: '#FF0000'
                    },
                    font: {
                        align: 'middle',
                        size: 11
                    },
                    smooth: {
                        type: 'cubicBezier',
                        roundness: 0.5
                    },
                    data: edge
                };
            }));

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

            // Create the network
            var container = document.getElementById('workflow-network');
            var data = {
                nodes: nodes,
                edges: edges
            };

            var options = {
                layout: {
                    hierarchical: {
                        enabled: true,
                        direction: 'LR',
                        sortMethod: 'directed',
                        nodeSpacing: 150,
                        levelSeparation: 200
                    }
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
                        html += '<dt>From:</dt><dd>' + (edge.from === 'start' ? 'New Record' : nodes.get(edge.from).label) + '</dd>';
                        html += '<dt>To:</dt><dd>' + nodes.get(edge.to).label + '</dd>';
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

            function exportGraph() {
                // This would require additional libraries like html2canvas
                alert('Export functionality would require additional libraries. Consider using browser screenshot tools for now.');
            }

            // Fit the network after a short delay to ensure proper rendering
            setTimeout(function() {
                network.fit();
            }, 100);
        </script>
    </body>
</html>
