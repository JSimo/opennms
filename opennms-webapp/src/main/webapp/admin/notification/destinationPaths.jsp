<%--
/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2002-2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

--%>

<%@page language="java"
	contentType="text/html"
	session="true"
	import="java.util.*,
		org.opennms.netmgt.config.*,
		org.opennms.netmgt.config.destinationPaths.*
	"
%>

<%!
    public void init() throws ServletException {
        try {
            DestinationPathFactory.init();
        }
        catch( Exception e ) {
            throw new ServletException( "Cannot load configuration file", e );
        }
    }
%>

<%@ page import="org.opennms.web.utils.Bootstrap" %>
<% Bootstrap.with(pageContext)
          .headTitle("Destination Paths")
          .headTitle("Admin")
          .breadcrumb("Admin", "admin/index.jsp")
          .breadcrumb("Configure Notifications", "admin/notification/index.jsp")
          .breadcrumb("Destination Paths")
          .build(request);
%>
<jsp:directive.include file="/includes/bootstrap.jsp" />

<script type="text/javascript" >

    function editPath() 
    {
        if (document.path.paths.selectedIndex==-1)
        {
            alert("Please select a path to edit.");
        }
        else
        {
            document.path.userAction.value="edit";
            document.path.submit();
        }
    }
    
    function newPath()
    {
        document.path.userAction.value="new";
        return true;
    }
    
    function deletePath()
    {
        if (document.path.paths.selectedIndex==-1)
        {
            alert("Please select a path to delete.");
        }
        else
        {
            message = "Are you sure you want to delete the path " + document.path.paths.options[document.path.paths.selectedIndex].value + "?";
            if (confirm(message))
            {
                document.path.userAction.value="delete";
                document.path.submit();
            }
        }
    }

    function testPath() {
        if (document.path.paths.selectedIndex === -1) {
            alert("Please select a path to test.");
        } else {
            var destinationPath = encodeURIComponent(document.path.paths.options[document.path.paths.selectedIndex].value);
            var xhr = new XMLHttpRequest();
            xhr.onreadystatechange = function readystatechange() {
                try {
                    if (xhr.readyState === XMLHttpRequest.DONE && xhr.status === 202) {
                        console.log("successfully triggered", destinationPath);
                    }
                } catch (err) {
                    console.error("failed to trigger notifications", err);
                }
            };
            xhr.open('POST', 'rest/notifications/destination-paths/' + destinationPath + '/trigger');
            xhr.send();
        }
    }
</script>

<form method="post" name="path" action="admin/notification/destinationWizard" onsubmit="return newPath();">
    <input type="hidden" name="userAction" value=""/>
    <input type="hidden" name="sourcePage" value="destinationPaths.jsp"/>
    <div class="row">
        <div class="col-md-6">
    <div class="card">
        <div class="card-header">
            <div class="pull-left">
                <h4>Destination Paths</h4>
            </div>
                <input type="submit" class="btn btn-secondary pull-right" value="New Path"/>
        </div>
        <div class="card-body">

            <div class="mb-2">
                <select NAME="paths" class="custom-select">
                    <% Map<String, Path> pathsMap = new TreeMap<String, Path>(DestinationPathFactory.getInstance().getPaths());
                        for (String key : pathsMap.keySet()) {
                    %>
                    <option VALUE=<%=key%>><%=key%>
                    </option>
                    <% } %>
                </select>
            </div>
            <input type="button" class="btn btn-secondary" value="Edit" onclick="editPath()"/>
            <input type="button" class="btn btn-secondary" value="Delete" onclick="deletePath()"/>
            <input type="button" class="btn btn-success pull-right" value="Test" onclick="testPath()"/>
        </div>
    </div>
        </div>
    </div>
</form>
    
<jsp:include page="/includes/bootstrap-footer.jsp" flush="false" />
