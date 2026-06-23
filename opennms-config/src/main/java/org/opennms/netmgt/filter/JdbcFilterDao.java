/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.netmgt.filter;

import static org.opennms.core.utils.InetAddressUtils.addr;

import java.lang.reflect.UndeclaredThrowableException;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;

import org.opennms.core.utils.DBUtils;
import org.opennms.core.utils.InetAddressComparator;
import org.opennms.netmgt.config.api.DatabaseSchemaConfig;
import org.opennms.netmgt.config.filter.Table;
import org.opennms.netmgt.filter.api.FilterDao;
import org.opennms.netmgt.filter.api.FilterParseException;
import org.opennms.netmgt.filter.ast.FilterRuleParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.util.Assert;

import com.codahale.metrics.jmx.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

/**
 * <p>JdbcFilterDao class.</p>
 *
 * @author <a href="mailto:dj@opennms.org">DJ Gregor</a>
 * @version $Id: $
 */
public class JdbcFilterDao implements FilterDao, InitializingBean {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcFilterDao.class);

	private DataSource m_dataSource;
    private DatabaseSchemaConfig m_databaseSchemaConfigFactory;

    private final static MetricRegistry metricRegistry = new MetricRegistry();

    private JmxReporter jmxReporter;
    private final Timer getIpListTimer;

    public JdbcFilterDao() {
        getIpListTimer = metricRegistry.timer("getIPAddressListForFilter");
    }

    /**
     * <p>setDataSource</p>
     *
     * @param dataSource a {@link javax.sql.DataSource} object.
     */
    public void setDataSource(final DataSource dataSource) {
        m_dataSource = dataSource;
    }

    /**
     * <p>getDataSource</p>
     *
     * @return a {@link javax.sql.DataSource} object.
     */
    public DataSource getDataSource() {
        return m_dataSource;
    }

    /**
     * <p>setDatabaseSchemaConfigFactory</p>
     *
     * @param factory a {@link org.opennms.netmgt.config.DatabaseSchemaConfigFactory} object.
     */
    public void setDatabaseSchemaConfigFactory(final DatabaseSchemaConfig factory) {
        m_databaseSchemaConfigFactory = factory;
    }

    /**
     * <p>getDatabaseSchemaConfigFactory</p>
     *
     * @return a {@link org.opennms.netmgt.config.DatabaseSchemaConfigFactory} object.
     */
    public DatabaseSchemaConfig getDatabaseSchemaConfigFactory() {
        return m_databaseSchemaConfigFactory;
    }

    /**
     * <p>afterPropertiesSet</p>
     */
    @Override
    public void afterPropertiesSet() {
        Assert.state(m_dataSource != null, "property dataSource cannot be null");
        Assert.state(m_databaseSchemaConfigFactory != null, "property databaseSchemaConfigFactory cannot be null");
        jmxReporter = JmxReporter.forRegistry(metricRegistry).inDomain("org.opennms.netmgt.config.filterdao").build();
        jmxReporter.start();
    }

    @PreDestroy
    public void destroy() throws Exception {
        if (jmxReporter != null) {
            jmxReporter.stop();
            jmxReporter = null;
        }
    }

    /**
     * {@inheritDoc}
     *
     * This method returns a map of all nodeids and nodelabels that match
     * the rule that is passed in, sorted by nodeid.
     * @exception FilterParseException
     *                if a rule is syntactically incorrect or failed in
     *                executing the SQL statement
     */
    @Override
    public SortedMap<Integer, String> getNodeMap(final String rule) throws FilterParseException {
    	final SortedMap<Integer, String> resultMap = new TreeMap<Integer, String>();
        String sqlString;

        LOG.debug("Filter.getNodeMap({})", rule);

        // get the database connection
        Connection conn = null;
        final DBUtils d = new DBUtils(getClass());
        try {
            conn = getDataSource().getConnection();
            d.watch(conn);

            // parse the rule and get the sql select statement
            sqlString = getNodeMappingStatement(rule);
            LOG.debug("Filter.getNodeMap({}): SQL statement: {}", rule, sqlString);

            // execute query
            final Statement stmt = conn.createStatement();
            d.watch(stmt);
            final ResultSet rset = stmt.executeQuery(sqlString);
            d.watch(rset);

            if (rset != null) {
                // Iterate through the result and build the map
                while (rset.next()) {
                    resultMap.put(Integer.valueOf(rset.getInt(1)), rset.getString(2));
                }
            }
        } catch (final FilterParseException e) {
            LOG.warn("Filter Parse Exception occurred getting node map.", e);
            throw new FilterParseException("Filter Parse Exception occurred getting node map: " + e.getLocalizedMessage(), e);
        } catch (final SQLException e) {
            LOG.warn("SQL Exception occurred getting node map.", e);
            throw new FilterParseException("SQL Exception occurred getting node map: " + e.getLocalizedMessage(), e);
        } catch (final Throwable e) {
            LOG.error("Exception getting database connection.", e);
            throw new UndeclaredThrowableException(e);
        } finally {
            d.cleanUp();
        }

        return Collections.unmodifiableSortedMap(resultMap);
    }

    /** {@inheritDoc} */
    @Override
    public Map<InetAddress, Set<String>> getIPAddressServiceMap(final String rule) throws FilterParseException {
        final Map<Integer, Map<InetAddress, Set<String>>> nodeIpServices = getNodeIPAddressServiceMap(rule);

        // Flatten the map, remove the node
        final Map<InetAddress, Set<String>> ipServices = new TreeMap<>(new InetAddressComparator());
        nodeIpServices.values().forEach(ipServicesForNode -> {
            ipServicesForNode.forEach((ipAddr, services) -> {
                ipServices.computeIfAbsent(ipAddr, key -> new TreeSet<>()).addAll(services);
            });
        });

        return ipServices;
    }

    @Override
    public Map<Integer, Map<InetAddress, Set<String>>> getNodeIPAddressServiceMap(String rule) throws FilterParseException {
        final Map<Integer, Map<InetAddress, Set<String>>> nodeIpServices = new TreeMap<>();
        String sqlString;

        LOG.debug("Filter.getNodeIPAddressServiceMap({})", rule);

        // get the database connection
        Connection conn = null;
        final DBUtils d = new DBUtils(getClass());
        try {
            conn = getDataSource().getConnection();
            d.watch(conn);

            // parse the rule and get the sql select statement
            sqlString = getNodeIPServiceMappingStatement(rule);
            LOG.debug("Filter.getNodeIPAddressServiceMap({}): SQL statement: {}", rule, sqlString);

            // execute query
            final Statement stmt = conn.createStatement();
            d.watch(stmt);
            final ResultSet rset = stmt.executeQuery(sqlString);
            d.watch(rset);

            // fill up the array list if the result set has values
            if (rset != null) {
                // Iterate through the result and build the array list
                while (rset.next()) {
                    final Integer nodeId = rset.getInt(1);
                    final InetAddress ipaddr = addr(rset.getString(2));
                    final String serviceName = rset.getString(3);
                    if (ipaddr == null || serviceName == null) {
                        continue;
                    }
                    Map<InetAddress, Set<String>> ifServices = nodeIpServices.computeIfAbsent(nodeId, key -> new TreeMap<>(new InetAddressComparator()));
                    ifServices.computeIfAbsent(ipaddr, key -> new TreeSet<>()).add(serviceName);
                }
            }

        } catch (final FilterParseException e) {
            LOG.warn("Filter Parse Exception occurred getting IP Service List.", e);
            throw new FilterParseException("Filter Parse Exception occurred getting IP Service List: " + e.getLocalizedMessage(), e);
        } catch (final SQLException e) {
            LOG.warn("SQL Exception occurred getting IP Service List.", e);
            throw new FilterParseException("SQL Exception occurred getting IP Service List: " + e.getLocalizedMessage(), e);
        } catch (final RuntimeException e) {
            LOG.error("Unexpected exception getting database connection.", e);
            throw e;
        } catch (final Error e) {
            LOG.error("Unexpected exception getting database connection.", e);
            throw e;
        } finally {
            d.cleanUp();
        }

        return nodeIpServices;
    }

    @Override
    @CacheEvict(value="activeIpAddressList", allEntries=true)
    public void flushActiveIpAddressListCache() {}

    /**
     * {@inheritDoc}
     */
    @Cacheable("activeIpAddressList")
    @Override
    public List<InetAddress> getActiveIPAddressList(final String rule) throws FilterParseException {
    	return getIPAddressList(rule, true);
    }

    protected InetAddress getActiveIPAddress(final String rule, final String address) {
        final List<InetAddress> ipAddressList = getIPAddressList(rule, true, address);
        if (ipAddressList.isEmpty()) {
            return null;
        }
        return ipAddressList.get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<InetAddress> getIPAddressList(final String rule) throws FilterParseException {
    	return getIPAddressList(rule, false);
    }

    private List<InetAddress> getIPAddressList(final String rule, final boolean filterDeleted) throws FilterParseException {
        return getIPAddressList(rule, filterDeleted, null);

    }

    private List<InetAddress> getIPAddressList(final String rule, final boolean filterDeleted, final String address) throws FilterParseException {
    	final List<InetAddress> resultList = new ArrayList<>();
    	final boolean filterByAddress = address != null && address.length() > 0;
        String sqlString;

        LOG.debug("Filter.getIPAddressList({})", rule);

        // get the database connection
        Connection conn = null;
        final DBUtils d = new DBUtils(getClass());
        try (final Timer.Context ctx = getIpListTimer.time()) {
            // parse the rule and get the sql select statement
            sqlString = getSQLStatement(rule);

            if (filterDeleted) {
            	if (!sqlString.contains("isManaged")) {
            		sqlString += " AND (ipInterface.isManaged != 'D' or ipInterface.isManaged IS NULL)";
            	}
            }
            if (filterByAddress) {
                sqlString += " AND ipInterface.ipaddr = ?";
            }

            conn = getDataSource().getConnection();
            d.watch(conn);

            LOG.debug("Filter.getIPAddressList({}): SQL statement: {}", rule, sqlString);

            // execute query and return the list of ip addresses
            final ResultSet rset;
            if (filterByAddress) {
                final PreparedStatement preparedStatement = conn.prepareStatement(sqlString);
                preparedStatement.setString(1, address);
                d.watch(preparedStatement);
                rset = preparedStatement.executeQuery();
            } else {
                final Statement stmt = conn.createStatement();
                d.watch(stmt);
                rset = stmt.executeQuery(sqlString);
            }
            d.watch(rset);

            // fill up the array list if the result set has values
            if (rset != null) {
                // Iterate through the result and build the array list
                while (rset.next()) {
                	resultList.add(addr(rset.getString(1)));
                }
            }

        } catch (final FilterParseException e) {
            LOG.warn("Filter Parse Exception occurred getting IP List.", e);
            throw new FilterParseException("Filter Parse Exception occurred getting IP List: " + e.getLocalizedMessage(), e);
        } catch (final SQLException e) {
            LOG.warn("SQL Exception occurred getting IP List.", e);
            throw new FilterParseException("SQL Exception occurred getting IP List: " + e.getLocalizedMessage(), e);
        } catch (final Throwable e) {
            LOG.error("Exception getting database connection.", e);
            throw new UndeclaredThrowableException(e);
        } finally {
            d.cleanUp();
        }

        LOG.debug("Filter.getIPAddressList({}): resultList.size = {}", rule, resultList.size());
        return resultList;
    }

	/**
     * {@inheritDoc}
     *
     * This method verifies if an ip address adheres to a given rule.
     * @exception FilterParseException
     *                if a rule is syntactically incorrect or failed in
     *                executing the SQL statement.
     */
    @Override
    public boolean isValid(final String addr, final String rule) throws FilterParseException {
        if (rule.length() == 0) {
            return true;
        } else {
            return getActiveIPAddress(rule, addr) != null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isRuleMatching(final String rule) throws FilterParseException {
        boolean matches = false;
        String sqlString;

        LOG.debug("Filter.isRuleMatching({})", rule);

        final DBUtils d = new DBUtils(getClass());

        // get the database connection
        Connection conn = null;
        try {
            conn = getDataSource().getConnection();
            d.watch(conn);

            // parse the rule and get the sql select statement
            sqlString = getSQLStatement(rule) + " LIMIT 1";
            LOG.debug("Filter.isRuleMatching({}): SQL statement: {}", rule, sqlString);

            // execute query and return the list of ip addresses
            final Statement stmt = conn.createStatement();
            d.watch(stmt);
            final ResultSet rset = stmt.executeQuery(sqlString);
            d.watch(rset);

            // we only want to check if zero or one rows were fetched, so just
            // return the output from rset.next()
            matches = rset.next();
            LOG.debug("isRuleMatching: rule \"{}\" {} an entry in the database", rule, matches? "matches" : "does not match");
        } catch (final FilterParseException e) {
            LOG.warn("Filter Parse Exception occurred testing rule \"{}\" for matching results.", rule, e);
            throw new FilterParseException("Filter Parse Exception occurred testing rule \"" + rule + "\" for matching results: " + e.getLocalizedMessage(), e);
        } catch (final SQLException e) {
            LOG.warn("SQL Exception occurred testing rule \"{}\" for matching results.", e);
            throw new FilterParseException("SQL Exception occurred testing rule \""+ rule + "\" for matching results: " + e.getLocalizedMessage(), e);
        } catch (final Throwable e) {
            LOG.error("Exception getting database connection.", e);
            throw new UndeclaredThrowableException(e);
        } finally {
            d.cleanUp();
        }

        return matches;
    }

	/** {@inheritDoc} */
    @Override
    public void validateRule(final String rule) throws FilterParseException {
        // Since parseRule does not do complete syntax checking,
        // we need to call a function that will actually execute the generated SQL
        isRuleMatching(rule);
    }

    /**
     * <p>getNodeMappingStatement</p>
     *
     * @param rule a {@link java.lang.String} object.
     * @return a {@link java.lang.String} object.
     * @throws org.opennms.netmgt.filter.api.FilterParseException if any.
     */
    public String getNodeMappingStatement(final String rule) throws FilterParseException {
        final List<Table> tables = new ArrayList<>();

        final StringBuilder columns = new StringBuilder();
        columns.append(m_databaseSchemaConfigFactory.addColumn(tables, "nodeID"));
        columns.append(", " + m_databaseSchemaConfigFactory.addColumn(tables, "nodeLabel"));

        final String where = parseRule(tables, rule);
        final String from = m_databaseSchemaConfigFactory.constructJoinExprForTables(tables);

        return "SELECT DISTINCT " + columns.toString() + " " + from + " " + where;
    }

    /**
     * <p>getNodeIPServiceMappingStatement</p>
     *
     * @param rule a {@link java.lang.String} object.
     * @return a {@link java.lang.String} object.
     * @throws org.opennms.netmgt.filter.api.FilterParseException if any.
     */
    public String getNodeIPServiceMappingStatement(final String rule) throws FilterParseException {
    	final List<Table> tables = new ArrayList<>();

    	final StringBuilder columns = new StringBuilder();
        columns.append(m_databaseSchemaConfigFactory.addColumn(tables, "nodeID"));
        columns.append(", " + m_databaseSchemaConfigFactory.addColumn(tables, "ipAddr"));
        columns.append(", " + m_databaseSchemaConfigFactory.addColumn(tables, "serviceName"));

        final String where = parseRule(tables, rule);
        final String from = m_databaseSchemaConfigFactory.constructJoinExprForTables(tables);

        return "SELECT " + columns.toString() + " " + from + " " + where;
    }

    /**
     * <p>getInterfaceWithServiceStatement</p>
     *
     * @param rule a {@link java.lang.String} object.
     * @return a {@link java.lang.String} object.
     * @throws org.opennms.netmgt.filter.api.FilterParseException if any.
     */
    public String getInterfaceWithServiceStatement(final String rule) throws FilterParseException {
    	final List<Table> tables = new ArrayList<>();

    	final StringBuilder columns = new StringBuilder();
        columns.append(m_databaseSchemaConfigFactory.addColumn(tables, "ipAddr"));
        columns.append(", " + m_databaseSchemaConfigFactory.addColumn(tables, "serviceName"));
        columns.append(", " + m_databaseSchemaConfigFactory.addColumn(tables, "nodeID"));

        final String where = parseRule(tables, rule);
        final String from = m_databaseSchemaConfigFactory.constructJoinExprForTables(tables);

        return "SELECT DISTINCT " + columns.toString() + " " + from + " " + where;
    }

    /**
     * This method parses a rule and returns the SQL select statement equivalent
     * of the rule.
     *
     * @return the SQL select statement
     * @param rule a {@link java.lang.String} object.
     * @throws org.opennms.netmgt.filter.api.FilterParseException if any.
     */
    protected String getSQLStatement(final String rule) throws FilterParseException {
        final List<Table> tables = new ArrayList<>();

        final StringBuilder columns = new StringBuilder();
        columns.append(m_databaseSchemaConfigFactory.addColumn(tables, "ipAddr"));

        final String where = parseRule(tables, rule);
        final String from = m_databaseSchemaConfigFactory.constructJoinExprForTables(tables);

        return "SELECT DISTINCT " + columns.toString() + " " + from + " " + where;
    }

    /**
     * This method should be called if you want to put constraints on the node,
     * interface or service that is returned in the rule. This is useful to see
     * if a particular node, interface, or service matches in the rule, and is
     * primarily used to filter notices. A sub-select is built containing joins
     * constrained by node, interface, and service if they are not null or
     * blank. This select is then ANDed with the filter rule to get the complete
     * SQL statement.
     *
     * @param nodeId
     *            a node id to constrain against
     * @param ipaddr
     *            an ipaddress to constrain against
     * @param service
     *            a service name to constrain against
     * @param rule a {@link java.lang.String} object.
     * @return a {@link java.lang.String} object.
     * @throws org.opennms.netmgt.filter.api.FilterParseException if any.
     */
    protected String getSQLStatement(final String rule, final long nodeId, final String ipaddr, final String service) throws FilterParseException {
        final List<Table> tables = new ArrayList<>();

        final StringBuilder columns = new StringBuilder();
        columns.append(m_databaseSchemaConfigFactory.addColumn(tables, "ipAddr"));

        final StringBuffer where = new StringBuffer(parseRule(tables, rule));
        if (nodeId != 0)
            where.append(" AND " + m_databaseSchemaConfigFactory.addColumn(tables, "nodeID") + " = " + nodeId);
        if (ipaddr != null && !ipaddr.equals(""))
            where.append(" AND " + m_databaseSchemaConfigFactory.addColumn(tables, "ipAddr") + " = '" + ipaddr + "'");
        if (service != null && !service.equals(""))
            where.append(" AND " + m_databaseSchemaConfigFactory.addColumn(tables, "serviceName") + " = '" + service + "'");

        final String from = m_databaseSchemaConfigFactory.constructJoinExprForTables(tables);

        return "SELECT DISTINCT " + columns.toString() + " " + from + " " + where;
    }

    /**
     * Parse a filter rule into a SQL WHERE clause using the AST pipeline
     * (tokenizer → parser → optimizer → SQL emitter).
     * <p>
     * Supported grammar (filter rules are not arbitrary SQL):
     * <ul>
     *   <li>Boolean: {@code &}, {@code |}, {@code !} (also {@code &&}, {@code ||})</li>
     *   <li>Comparisons: {@code ==}, {@code !=}, {@code LIKE}, {@code NOT LIKE}</li>
     *   <li>Null checks: {@code IS NULL}, {@code IS NOT NULL}</li>
     *   <li>Distinct: {@code IS DISTINCT FROM}, {@code IS NOT DISTINCT FROM}</li>
     *   <li>Membership: {@code IN (...)}, {@code NOT IN (...)}</li>
     *   <li>Type casts: {@code ::TIMESTAMP}, {@code ::INET} on column references</li>
     *   <li>{@code IPLIKE} as operator or function</li>
     *   <li>Shorthands: {@code isSERVICE}, {@code notisSERVICE}, {@code catincCATEGORY}</li>
     *   <li>Columns from database-schema.xml (case-insensitive)</li>
     * </ul>
     * Identifiers starting with {@code is} are treated as service shorthands (same as legacy regex).
     * Optimizers may rewrite equivalent expressions into more efficient SQL (e.g. merged catinc terms).
     *
     * @param tables list populated with tables referenced by the rule
     * @param rule   filter rule string
     * @return SQL WHERE clause including the {@code WHERE} keyword, or empty string
     * @throws FilterParseException if the rule is invalid or uses an unknown column
     */
    private String parseRule(final List<Table> tables, final String rule) throws FilterParseException {
        if (rule != null && rule.length() > 0) {
            String whereBody = FilterRuleParser.parseWhereBody(tables, rule, m_databaseSchemaConfigFactory);
            return whereBody.isEmpty() ? "" : "WHERE " + whereBody;
        }
        return "";
    }

}
