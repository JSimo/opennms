<%--
/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2002-2022 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2022 The OpenNMS Group, Inc.
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
		java.net.InetAddress,
		org.opennms.core.utils.InetAddressUtils,
		org.opennms.web.admin.notification.noticeWizard.*,
		org.opennms.web.api.Util,
        org.opennms.netmgt.filter.FilterDaoFactory,org.opennms.netmgt.filter.api.FilterParseException,
		org.opennms.netmgt.config.notifications.*
	"
%>
<%@ page import="org.opennms.core.utils.WebSecurityUtils" %>

<%!
    public void init() throws ServletException {
        try {
        }
        catch( Exception e ) {
            throw new ServletException( "Cannot load configuration file", e );
        }
    }
%>

<% HttpSession user = request.getSession(true);
   Notification newNotice = (Notification)user.getAttribute("newNotice");
   String newRule = (String)request.getParameter("newRule");
   String services[] = request.getParameterValues("services");
   if (services==null)
      services = new String[0];
   String notServices[] = request.getParameterValues("notServices");
   if (notServices==null)
      notServices = new String[0];
%>

<%@ page import="org.opennms.web.utils.Bootstrap" %>
<% Bootstrap.with(pageContext)
          .headTitle("Validate Rule")
          .headTitle("Admin")
          .breadcrumb("Admin", "admin/index.jsp")
          .breadcrumb("Configure Notifications", "admin/notification/index.jsp")
          .breadcrumb("Validate Rule")
          .build(request);
%>
<jsp:directive.include file="/includes/bootstrap.jsp" />

<script type="text/javascript" >
  
  function next()
  {
      document.addresses.userAction.value="next";
      document.addresses.submit();
  }
  
  function rebuild()
  {
      document.addresses.userAction.value="rebuild";
      document.addresses.submit();
  }
  
</script>

<h2><%=(newNotice.getName()!=null ? "Editing notice: " + WebSecurityUtils.sanitizeString(newNotice.getName()) + "<br/>" : "")%></h2>

<div class="row">
  <div class="col-md-12">
    <div class="card">
      <div class="card-header">
        <span>Check the TCP/IP addresses below to ensure that the rule has given the expected results. If it hasn't click the
              'Rebuild' link below the table. If the results look good continue by clicking the 'Next' link also below the table.</span>
      </div>
          <table class="table table-sm">
            <tr>
              <td width="10%">Current Rule:
              </td>
              <td align="left"> <%=newRule%>
              </td>
          </table>
        <form method="post" name="addresses" action="admin/notification/noticeWizard/notificationWizard">
            <%=Util.makeHiddenTags(request)%>
            <input type="hidden" name="userAction" value=""/>
            <input type="hidden" name="sourcePage" value="<%=NotificationWizardServlet.SOURCE_PAGE_VALIDATE%>"/>
            <table class="table table-sm">
                <tr>
                    <th>
                        Interfaces
                    </th>
                    <th>
                        Services Associated with the Interfaces
                    </th>
                </tr>
                <%=buildInterfaceTable(newRule, services, notServices)%>
            </table>
            <div class="card-footer">
                        <a class="btn btn-secondary" href="javascript:rebuild()"><i class="fa fa-arrow-left"></i> Rebuild</a>
                        <a class="btn btn-secondary" href="javascript:next()">Next <i class="fa fa-arrow-right"></i></a>
            </div>
        </form>
    </div> <!-- panel -->
  </div> <!-- column -->
</div> <!-- row -->

<jsp:include page="/includes/bootstrap-footer.jsp" flush="false" />

<%!
  public String buildInterfaceTable(String rule, String[] serviceList, String[] notServiceList)
      throws FilterParseException
  {
          StringBuffer buffer = new StringBuffer();
          // TODO: BUG 2009: Also list node names for each IP address that is selected by the
          // filter?
          
          final Map<InetAddress, Set<String>> interfaces = FilterDaoFactory.getInstance().getIPAddressServiceMap(rule);
          
          for (final InetAddress key : interfaces.keySet()) {
              buffer.append("<tr><td width=\"50%\" valign=\"top\">").append(InetAddressUtils.str(key)).append("</td>");
              buffer.append("<td width=\"50%\">");
              
              if (serviceList.length!=0 || notServiceList.length!=0) {
                  for (String service : interfaces.get(key)) { 
                      buffer.append(service).append("<br/>");
                  }
              } else {
                  buffer.append("All services");
              }
              
              buffer.append("</td>");
                  
              buffer.append("</tr>");
          }
          
          return buffer.toString();
  }
%>
