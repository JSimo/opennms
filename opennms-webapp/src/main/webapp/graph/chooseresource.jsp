<%--
/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2016-2022 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2022 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/
--%>

<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="https://www.owasp.org/index.php/OWASP_Java_Encoder_Project" prefix="e"%>
<%
  String node = request.getParameter("node");
  if (node == null) {
    String parentResource = request.getParameter("parentResourceId");
    if (parentResource != null) {
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("node\\[([^\\]]+)\\]").matcher(parentResource);
      if (m.find()) {
        node = m.group(1);
      }
    }
  }
  String reports = request.getParameter("reports");
  String endUrl = request.getParameter("endUrl");
  pageContext.setAttribute("node", node == null ? "null" : node);
  pageContext.setAttribute("reports", reports == null ? "" : reports);
  pageContext.setAttribute("endUrl", endUrl == null ? "" : endUrl);
%>

<%@ page import="org.opennms.web.utils.Bootstrap" %>
<% Bootstrap.with(pageContext)
          .headTitle("Choose")
          .headTitle("Resource Graphs")
          .headTitle("Reports")
          .breadcrumb("Reports", "report/index.jsp")
          .breadcrumb("Resource Graphs", "graph/index.jsp")
          .breadcrumb("Choose")
          .ngApp("onms-resources")
          .build(request);
%>
<jsp:directive.include file="/includes/bootstrap.jsp" />

<div class="" ng-controller="NodeResourcesCtrl" ng-init="init('${e:forJavaScript(node)}','${e:forJavaScript(reports)}','${e:forJavaScript(endUrl)}')">

  <div growl></div>

  <h4>
    Node: <strong><a href="{{ nodeLink }}">{{ nodeLabel }}</a></strong>
  </h4>

  <!-- Loading Message -->
  <div class="jumbotron" ng-show="!loaded">
    <div class="container">
      <h1>Loading Resources</h1>
      <p>Please wait while all the resources are loaded into your browser.</p>
      <p>This could take time, depending on the number of resources for this node.</p>
    </div>
  </div>

  <div class="jumbotron" ng-show="loaded && !hasResources">
    <div class="container">
      <h1>There are no resources for this node</h1>
      <p>Please check the data collection configuration for this node.</p>
    </div>
  </div>

  <div class="row" ng-show="hasResources">
    <div class="col-md-6">
      <h4>Resources</h4>
    </div>
    <div class="col-md-6">
      <form class="form-inline pull-right mb-4">
        <div class="input-group mr-4">
          <div class="input-group-prepend">
            <div class="input-group-text">
              <span class="fa fa-search"></span>
            </div>
          </div>
          <input class="form-control" type="text" placeholder="Search/Filter Resources" ng-model="searchQuery"></input>
          <div class="input-group-prepend" ng-show="searchQuery.length > 0">
            <div class="input-group-text">
              <span class="fa fa-remove" ng-click="searchQuery = ''"></span>
            </div>
          </div>
        </div>
        <div class="btn-group">
          <button type="button" class="btn btn-secondary" ng-click="checkAll(true)"><i class="fa fa-check"></i> Select All</button>
          <button type="button" class="btn btn-secondary" ng-click="checkAll(false)"><i class="fa fa-remove"></i> Clear All</button>
          <button type="button" class="btn btn-secondary" ng-click="graphSelected()"><i class="fa fa-th-large"></i> Graph Selection</button>
          <button type="button" class="btn btn-secondary" ng-click="graphAll()"><i class="fa fa-th"></i> Graph All</button>
        </div>
      </form>
    </div>
  </div>

  <div class="row" ng-show="hasResources">
    <div class="col-md-12">
      <uib-accordion close-others="false">
        <div ng-click="isCollapsed[type] = !isCollapsed[type]" uib-accordion-group is-open="true" class="card-default" ng-repeat="(type, group) in filteredResources" ng-show="group.length > 0">
          <uib-accordion-heading>
            {{ type }} <i class="pull-right fa" ng-class="{'fa-chevron-down': isCollapsed[type], 'fa-chevron-right': !isCollapsed[type]}"></i>
          </uib-accordion-heading>
            <div class="checkbox" ng-repeat="resource in group | orderBy:'label'">
              <label><input type="checkbox" class="mr-1" ng-model="resource.selected"/>{{ resource.label }}</label>
              <a ng-if="resource.hasIngressFlows===true && resource.hasEgressFlows===true" ng-href="{{resource.flowGraphUrl}}" target="_blank" title="Open flow graphs"><span class="badge badge-secondary" title="Flows: ingress/egress flow data available"><i class="fa fa-exchange"></i>&nbsp;flow data</span></a>
              <a ng-if="resource.hasIngressFlows===true && resource.hasEgressFlows===false" ng-href="{{resource.flowGraphUrl}}" target="_blank" title="Open flow graphs"><span class="badge badge-secondary" title="Flows: ingress flow data available"><i class="fa fa-long-arrow-left"></i>&nbsp;flow data</span></a>
              <a ng-if="resource.hasIngressFlows===false && resource.hasEgressFlows===true" ng-href="{{resource.flowGraphUrl}}" target="_blank" title="Open flow graphs"><span class="badge badge-secondary" title="Flows: egress flow data available"><i class="fa fa-long-arrow-right"></i>&nbsp;flow data</span></a>
            </div>
        </div>
      </uib-accordion>
    </div>
  </div>

</div>

<jsp:include page="/assets/load-assets.jsp" flush="false">
    <jsp:param name="asset" value="angular-js" />
</jsp:include>
<jsp:include page="/assets/load-assets.jsp" flush="false">
    <jsp:param name="asset" value="onms-resources" />
</jsp:include>

<jsp:include page="/includes/bootstrap-footer.jsp" flush="false"/>
