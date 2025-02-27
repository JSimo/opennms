<%--
/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2002-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
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
	import="org.opennms.core.utils.WebSecurityUtils,
	org.opennms.web.element.*,
  org.opennms.web.api.Util,
	org.opennms.web.servlet.MissingParameterException
	"
%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%
  String nodeIdString = request.getParameter("node");
  String ipAddr = request.getParameter("ipaddr");
  
  if( nodeIdString == null ) {
    throw new MissingParameterException("node");
  }
  
  int nodeId = WebSecurityUtils.safeParseInt(nodeIdString);
  String nodeLabel = NetworkElementFactory.getInstance(getServletContext()).getNodeLabel(nodeId);
%>

<c:url var="nodeLink" value="element/node.jsp">
	<c:param name="node" value="<%=String.valueOf(nodeId)%>"/>
</c:url>
<c:choose>
	<c:when test="<%=(ipAddr == null)%>">
		<c:set var="returnUrl" value="${nodeLink}"/>
		<%@ page import="org.opennms.web.utils.Bootstrap" %>
<% Bootstrap.with(pageContext)
          .headTitle("Rescan")
          .headTitle("Element")
          .breadcrumb("Search", "element/index.jsp")
          .breadcrumb("Node", "${nodeLink}")
          .breadcrumb("Rescan")
          .build(request);
%>
<jsp:directive.include file="/includes/bootstrap.jsp" />
	</c:when>
	<c:otherwise>
		<c:url var="interfaceLink" value="element/interface.jsp">
			<c:param name="node" value="<%=String.valueOf(nodeId)%>"/>
			<c:param name="intf" value="<%=WebSecurityUtils.sanitizeString(ipAddr)%>"/>
		</c:url>
		<c:set var="returnUrl" value="${interfaceLink}"/>
		<%@ page import="org.opennms.web.utils.Bootstrap" %>
<% Bootstrap.with(pageContext)
          .headTitle("Rescan")
          .headTitle("Element")
          .breadcrumb("Search", "element/index.jsp")
          .breadcrumb("Node", "${nodeLink}")
          .breadcrumb("Interface", "${fn:escapeXml(interfaceLink)}")
          .breadcrumb("Rescan")
          .build(request);
%>
<jsp:directive.include file="/includes/bootstrap.jsp" />
	</c:otherwise>
</c:choose>

<div class="row">

  <div class="col-md-5">
    <div class="card">
      <div class="card-header">
        <span>Capability Rescan</span>
      </div>
      <div class="card-body">
        <p>Are you sure you want to rescan the <nobr><%=WebSecurityUtils.sanitizeString(nodeLabel)%></nobr>
          <% if( ipAddr==null ) { %>
            node?
          <% } else { %>
            interface <%= WebSecurityUtils.sanitizeString(ipAddr) %>?
          <% } %>
        </p>        
        <form method="post" action="element/rescan">
          <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
          <p>
            <input type="hidden" name="node" value="<%=nodeId%>" />
            <input type="hidden" name="returnUrl" value="${fn:escapeXml(returnUrl)}" />
            <div class="btn-group" role="group">
              <button class="btn btn-secondary" type="submit">Rescan</button>
              <button class="btn btn-secondary" type="button" onClick="window.open('<%= Util.calculateUrlBase(request)%>${returnUrl}', '_self')">Cancel</button>
            </div>
          </p>
        </form>
      </div>
    </div>
  </div>

  <div class="col-md-7">
    <div class="card">
      <div class="card-header">
        <span>Rescan Node</span>
      </div>
      <div class="card-body">
        <p>
          <em>Rescanning</em> a node tells the provisioning subsystem to re-detect what <em>services</em> appear on the node's interfaces and to re-apply the appropriate set of <em>policies</em>.
          If the node is correctly configured for SNMP, a rescan will also cause the node's SNMP attributes (<em>sysLocation</em>, <em>sysContact</em>, <em>etc.</em>) to be refreshed.
        </p>
      </div>
    </div>
  </div>

</div>

<jsp:include page="/includes/bootstrap-footer.jsp" flush="false" />
