/*
 *  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.w   See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
var endpointList = [];
var sourcepointList = [];
var _saveFlowchart, elementCount = 0;
jsPlumb.ready(function () {
    var basicType = {
        connector: "StateMachine",
        paintStyle: {strokeStyle: "#216477", lineWidth: 4},
        hoverPaintStyle: {strokeStyle: "blue"}
    };
    jsPlumb.registerConnectionType("basic", basicType);

    var properties = [];
    var deleteConnection = null;

    //style for the connector
    var connectorPaintStyle = {
            lineWidth: 4,
            strokeStyle: "#61B7CF",
            joinstyle: "round",
            outlineColor: "white",
            outlineWidth: 2
        },

    //style for the connector hover
        connectorHoverStyle = {
            lineWidth: 4,
            strokeStyle: "#216477",
            outlineWidth: 2,
            outlineColor: "white"
        },
        endpointHoverStyle = {
            fillStyle: "#216477",
            strokeStyle: "#216477"
        },

    //the source endpoint definition from which a connection can be started
        sourceEndpoint = {
            endpoint: "Dot",
            paintStyle: {
                strokeStyle: "#7AB02C",
                fillStyle: "transparent",
                radius: 7,
                lineWidth: 3
            },
            isSource: true,
            connector: ["Flowchart", {stub: [40, 60], gap: 5, cornerRadius: 5, alwaysRespectStubs: true}],
            connectorStyle: connectorPaintStyle,
            connectorHoverStyle: connectorHoverStyle,
            EndpointOverlays: [],
            maxConnections: -1,
            dragOptions: {},
            connectorOverlays: [
                ["Arrow", {
                    location: 1,
                    visible: true,
                    id: "ARROW",
                    events: {
                        click: function () {
                            alert("you clicked on the arrow overlay")
                        }
                    }
                }]
            ]
        },

    //definition of the target endpoint the connector would end
        targetEndpoint = {
            endpoint: "Dot",
            paintStyle: {fillStyle: "#7AB02C", radius: 11},
            maxConnections: -1,
            dropOptions: {hoverClass: "hover", activeClass: "active"},
            isTarget: true
        };

    //to make the text field resizable when typing the input text.
    $.fn.textWidth = function(text, font){//get width of text with font.  usage: $("div").textWidth();
        var temp = $('<span>').hide().appendTo(document.body).text(text || this.val() || this.text()).css('font', font || this.css('font')),
            width = temp.width();
        temp.remove();
        return width;
    };

    $.fn.autoresize = function(options){//resizes elements based on content size.  usage: $('input').autoresize({padding:10,minWidth:0,maxWidth:100});
        options = $.extend({padding:10,minWidth:0,maxWidth:10000}, options||{});
        $(this).on('input', function() {
            $(this).css('width', Math.min(options.maxWidth,Math.max(options.minWidth,$(this).textWidth() + options.padding)));
        }).trigger('input');
        return this;
    }

    //resize the label text field when typing
    $('#canvas').on('keyup', '._jsPlumb_overlay.aLabel', function () {
        $(this).css('font-weight', 'bold');
        $(this).css('text-align', 'center');
        $(this).autoresize({padding:20,minWidth:20,maxWidth:100});
    });

    //set the label of the connector
    var initConn = function (connection) {
        connection.addOverlay(["Custom", {
            create:function(component) {
                return $("<input type=\"text\" value=\"\" autofocus style=\"position:absolute; width: 20px\"\/>");
            },
            location: 0.5,
            id: "label",
            cssClass: "aLabel"
        }]);
    };

    jsPlumb.bind("connection", function (connInfo, originalEvent) {
        initConn(connInfo.connection);
    });

    jsPlumb.bind("click", function (conn, originalEvent) {
        to_delete = "";
        jsPlumb.select().setPaintStyle({lineWidth: 4, strokeStyle: "#61B7CF"});
        conn.setPaintStyle({strokeStyle:"red", lineWidth:4});
        $('.step').css({'border-color': '#29e'});
        $('.diamond').css({'border-color': '#29e'});
        $('.start').css({'border-color': 'green'});
        $('.window.jsplumb-connected-end').css({'border-color': 'orangered'});
        deleteConnection = conn;
    });


    //add the endpoints for the elements
    var ep;
    var _addEndpoints = function (toId, sourceAnchors, targetAnchors) {
        for (var i = 0; i < sourceAnchors.length; i++) {
            var sourceUUID = toId + sourceAnchors[i];
            ep = jsPlumb.addEndpoint("flowchart" + toId, sourceEndpoint, {
                anchor: sourceAnchors[i], uuid: sourceUUID
            });
            sourcepointList.push(["flowchart" + toId, ep]);
            ep = null;
        }
        for (var j = 0; j < targetAnchors.length; j++) {
            var targetUUID = toId + targetAnchors[j];
            ep = jsPlumb.addEndpoint("flowchart" + toId, targetEndpoint, {
                anchor: targetAnchors[j], uuid: targetUUID
            });
            endpointList.push(["flowchart" + toId, ep]);
            ep = null;
        }
    };

    var element = "";
    var clicked = false;
    var to_delete = "";

    //load properties of a start element once the start element in the palette is clicked
    $('#startEv').click(function () {
        loadProperties("window start custom jtk-node jsplumb-connected", "5em", "5em", "start", ["BottomCenter"],
            [], false);
        clicked = true;
    });

    //load properties of a step element once the step element in the palette is clicked
    $('#stepEv').click(function () {
        loadProperties("window step custom jtk-node jsplumb-connected-step", "13em", "5em", "step",
            ["BottomCenter", "RightMiddle"], ["TopCenter", "LeftMiddle"], true);
        clicked = true;
    });

    //load properties of a decision element once the decision element in the palette is clicked
    $('#descEv').click(function () {
        loadProperties("window diamond custom jtk-node jsplumb-connected-step", "23em", "5em", "decision",
            ["LeftMiddle", "RightMiddle", "BottomCenter"], ["TopCenter"], true);
        clicked = true;
    });

    //load properties of a decision element once the input/output element in the palette is clicked
    $('#inpEv').click(function () {
        loadProperties("window parallelogram step custom jtk-node jsplumb-connected-step", "23em", "5em", "i/o",
            ["BottomCenter", "RightMiddle"], ["TopCenter", "LeftMiddle"], true);
        clicked = true;
    });


    //load properties of a end element once the end element in the palette is clicked
    $('#endEv').click(function () {
        loadProperties("window end jtk-node jsplumb-connected-end", "23em", "15em", "end",
            [], ["TopCenter"], false);
        clicked = true;
    });

    //once the user clicks on the canvas, the element is drawn
    $('#myDiagram').click(function () {
        if (clicked) {
            clicked = false;
            elementCount++;
            var name = "Window" + elementCount;
            var id = "flowchartWindow" + elementCount;
            element = createElement(id);
            if (elementCount == 1 && element.attr("class").indexOf("start") == -1) {
                alertify.error("The flowchart diagram should contain a start activity");
                elementCount = 0;
            } else {
                drawElement(element, "#canvas", name);
            }
            element = "";
        }
    });

    //load properties of a given element
    function loadProperties(clsName, left, top, label, startpoints, endpoints, contenteditable) {
        properties = [];
        properties.push({
            left: left,
            top: top,
            clsName: clsName,
            label: label,
            startpoints: startpoints,
            endpoints: endpoints,
            contenteditable: contenteditable
        });
    }

    //take the x, y coordinates of the current mouse position
    var x, y;
    $( document ).on( "mousemove", function( event ) {
        x = event.pageX;
        y = event.pageY;
        if(clicked){
            properties[0].top = y - 358;
            properties[0].left = x - 308;
        }
    });

    //create an element to be drawn on the canvas
    function createElement(id) {
        var elm = $('<div>').addClass(properties[0].clsName).attr('id', id);
        elm.css({
            'top': properties[0].top,
            'left': properties[0].left
        });

        var strong = $('<strong>');
        if (properties[0].clsName == "window diamond custom jtk-node jsplumb-connected-step") {
            var p = "<p style='line-height: 110%; margin-top: 25px' class='desc-text' contenteditable='true' ondblclick='$(this).focus();'>" + properties[0].label + "</p>";
            strong.append(p);
        }
        else if (properties[0].clsName == "window parallelogram step custom jtk-node jsplumb-connected-step") {
            var p = "<p style='line-height: 110%; margin-top: 25px' class='input-text' contenteditable='true' ondblclick='$(this).focus();'>" + properties[0].label
                + "</p>";
            strong.append(p);
        }
        else if (properties[0].contenteditable) {
            var p = "<p style='line-height: 110%; margin-top: 25px' contenteditable='true' ondblclick='$(this).focus();'>" + properties[0].label + "</p>";
            strong.append(p);
        } else {
            var p = $('<p>').text(properties[0].label);
            strong.append(p);
        }
        elm.append(strong);
        return elm;
    }

    //draw elements on the canvas
    function drawElement(element, canvasId, name) {
        $(canvasId).append(element);
        _addEndpoints(name, properties[0].startpoints, properties[0].endpoints);
        makeResizable('.custom.step');
        jsPlumb.draggable(jsPlumb.getSelector(".jtk-node"), {grid: [20, 20]});
    }

    //make an element resizable
    function makeResizable(classname) {
        $(classname).resizable({
            resize: function (event, ui) {
                jsPlumb.repaint(ui.helper);
            }
        });
    }

    //select the current element and make its boarder red.
    $('#canvas').on('click', '[id^="flowchartWindow"]', function () {
        to_delete = $(this).attr("id");
        deleteConnection = null;
        jsPlumb.select().setPaintStyle({lineWidth: 4, strokeStyle: "#61B7CF"});
        $('.step').not(this).css({'border-color': '#29e'});
        $('.diamond').not(this).css({'border-color': '#29e'});
        $('.start').not(this).css({'border-color': 'green'});
        $('.window.jsplumb-connected-end').not(this).css({'border-color': 'orangered'});
        $(this).css({'border-color': 'red'});
    });

    //to delete and resize the elements
    $(document).keypress(function (e) {
        if (e.which == 127) {
            if (to_delete != "") {
                jsPlumb.remove(to_delete);

                //if there are no elements in the canvas, ids start from 1
                if($(".jtk-node").length == 0){
                    elementCount = 0;
                }

                for (var i = 0; i < editorEndpointList.length; i++) {
                    if (editorEndpointList[i][0] == to_delete) {
                        for (var j = 0; j < editorEndpointList[i].length; j++) {
                            jsPlumb.deleteEndpoint(editorEndpointList[i][j]);
                            editorEndpointList[i][j] = null;
                        }
                    }
                }

                for (var i = 0; i < editorSourcepointList.length; i++) {
                    if (editorSourcepointList[i][0] == to_delete) {
                        for (var j = 0; j < editorSourcepointList[i].length; j++) {
                            jsPlumb.deleteEndpoint(editorSourcepointList[i][j]);
                            editorSourcepointList[i][j] = null;
                        }
                    }
                }
                to_delete = "";
            }else if(deleteConnection != null){
                jsPlumb.detach(deleteConnection);
                deleteConnection = null;
            }
        } else if (e.which == 43) {
            var elm = $('.diamond.custom').filter(function () {
                return $(this).css("border-color") == 'rgb(255, 0, 0)';
            });
            if (elm.outerWidth() < 150) {
                elm.outerWidth(elm.outerWidth() + 5);
                elm.outerHeight(elm.outerHeight() + 5);
                var p = elm.children()[0].firstChild;
                p.style.lineHeight = 110 + '%';
                jsPlumb.repaint(elm.attr("id"));
            }
        }
        else if (e.which == 45) {
            var elm = $('.diamond.custom').filter(function () {
                return $(this).css("border-color") == 'rgb(255, 0, 0)';
            });
            if (elm.outerWidth() > 80) {
                elm.outerWidth(elm.outerWidth() - 5);
                elm.outerHeight(elm.outerHeight() - 5);
                jsPlumb.repaint(elm.attr("id"));
                var p = elm.children()[0].firstChild;
                p.style.lineHeight = 110 + '%';
            }
        }
    });

    //save the edited flowchart to a json string
    _saveFlowchart = function () {
        var totalCount = 0;
        if (elementCount > 0) {
            var nodes = [];

            //check whether the diagram has a start element
            var elm = $(".start.jtk-node");
            if(elm.length == 0){
                alertify.error("The flowchart diagram should have a start element");
            }else{
                $(".jtk-node").each(function (index, element) {
                    totalCount++;
                    var $element = $(element);
                    var type = $element.attr('class').toString().split(" ")[1];
                    if (type == "step" || type == "diamond" || type == "parallelogram") {
                        nodes.push({
                            elementId: $element.attr('id'),
                            nodeType: type,
                            positionX: parseInt($element.css("left"), 10),
                            positionY: parseInt($element.css("top"), 10),
                            clsName: $element.attr('class').toString(),
                            label: $element.children()[0].firstChild.innerHTML,
                            width: $element.outerWidth(),
                            height: $element.outerHeight()
                        });
                    } else {
                        nodes.push({
                            elementId: $element.attr('id'),
                            nodeType: $element.attr('class').toString().split(" ")[1],
                            positionX: parseInt($element.css("left"), 10),
                            positionY: parseInt($element.css("top"), 10),
                            clsName: $element.attr('class').toString(),
                            label: $element.text()
                        });
                    }
                });

                var connections = [];
                $.each(jsPlumb.getConnections(), function (index, connection) {
                    connections.push({
                        connectionId: connection.id,
                        sourceUUId: connection.endpoints[0].getUuid(),
                        targetUUId: connection.endpoints[1].getUuid(),
                        label: connection.getOverlay("label").getElement().value,
                        labelWidth: connection.getOverlay("label").getElement().style.width
                    });
                });

                var sourceEps = [];
                var targetEps = [];
                var flowchart = {};
                flowchart.nodes = nodes;
                flowchart.connections = connections;
                flowchart.numberOfElements = totalCount;
                flowchart.lastElementId = elementCount;

                $.ajax({
                    url: '/publisher/assets/process/apis/upload_flowchart',
                    type: 'POST',
                    data: {
                        'processName': $("#pName").val(),
                        'processVersion': $("#pVersion").val(),
                        'flowchartJson': JSON.stringify(flowchart)
                    },
                    success: function (response) {
                        alertify.success("Successfully saved the flowchart.");
                        $("#flowchartOverviewLink").attr("href", "../process/details/" + response);
                    },
                    error: function () {
                        alertify.error('Flowchart saving error');
                    }
                });
            }

        } else {
            alertify.error('Flowchart content is empty.');
        }
    }
});