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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opennms.netmgt.config.api.DatabaseSchemaConfig;
import org.opennms.netmgt.config.filter.Table;
import org.opennms.netmgt.filter.api.FilterParseException;

/**
 * Test-only copy of the pre-AST regex filter parser (release-36.x {@code parseRule()}).
 * Used by {@link org.opennms.netmgt.filter.ast.FilterParityTest} to validate AST coverage.
 */
public final class LegacyFilterParser {

    private static final Pattern SQL_KEYWORD_PATTERN = Pattern.compile(
            "\\s+(?:AND|OR|(?:NOT )?(?:LIKE|IN)|IS (?:NOT )?DISTINCT FROM)\\s+"
                    + "|(?:\\s+IS (?:NOT )?NULL|::(?:TIMESTAMP|INET))(?!\\w)"
                    + "|(?<!\\w)(?:NOT\\s+|IPLIKE(?=\\())",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SQL_QUOTE_PATTERN = Pattern.compile("'(?:[^']|'')*'|\"(?:[^\"]|\"\")*\"");
    private static final Pattern SQL_ESCAPED_PATTERN = Pattern.compile("###@(\\d+)@###");
    private static final Pattern SQL_VALUE_COLUMN_PATTERN = Pattern.compile("[a-zA-Z0-9_\\-]*[a-zA-Z][a-zA-Z0-9_\\-]*");
    private static final Pattern SQL_IPLIKE_PATTERN = Pattern.compile(
            "(\\w+)\\s+IPLIKE\\s+([0-9a-f.:*,-]+|###@\\d+@###)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final String SQL_IPLIKE6_RHS_REGEX = "^[0-9A-Fa-f:*,-]+$";

    private LegacyFilterParser() {
    }

    public static String parse(final List<Table> tables, final String rule, final DatabaseSchemaConfig schema)
            throws FilterParseException {
        if (rule == null || rule.length() == 0) {
            return "";
        }
        final List<String> extractedStrings = new ArrayList<>();
        String sqlRule = rule;

        Matcher regex = SQL_QUOTE_PATTERN.matcher(sqlRule);
        StringBuffer tempStringBuff = new StringBuffer();
        while (regex.find()) {
            final String tempString = regex.group();
            if (tempString.charAt(0) == '"') {
                extractedStrings.add("'" + tempString.substring(1, tempString.length() - 1)
                        .replaceAll("\"\"", "\"").replaceAll("'", "''") + "'");
            } else {
                extractedStrings.add(regex.group());
            }
            regex.appendReplacement(tempStringBuff, "###@" + (extractedStrings.size() - 1) + "@###");
        }
        final int tempIndex = tempStringBuff.length();
        regex.appendTail(tempStringBuff);
        if (tempStringBuff.substring(tempIndex).indexOf('\'') > -1) {
            throw new FilterParseException("Unmatched ' in filter rule '" + rule + "'");
        }
        if (tempStringBuff.substring(tempIndex).indexOf('"') > -1) {
            throw new FilterParseException("Unmatched \" in filter rule '" + rule + "'");
        }
        sqlRule = tempStringBuff.toString();

        sqlRule = sqlRule.replaceAll("\\s*(?:&|&&)\\s*", " AND ");
        sqlRule = sqlRule.replaceAll("\\s*(?:\\||\\|\\|)\\s*", " OR ");
        sqlRule = sqlRule.replaceAll("\\s*!(?!=)\\s*", " NOT ");
        sqlRule = sqlRule.replaceAll("==", "=");

        regex = SQL_IPLIKE_PATTERN.matcher(sqlRule);
        tempStringBuff = new StringBuffer();
        while (regex.find()) {
            if (regex.group().charAt(0) == '#') {
                regex.appendReplacement(tempStringBuff, "IPLIKE($1, $2)");
            } else {
                regex.appendReplacement(tempStringBuff, "IPLIKE($1, '$2')");
            }
        }
        regex.appendTail(tempStringBuff);
        sqlRule = tempStringBuff.toString();

        regex = SQL_KEYWORD_PATTERN.matcher(sqlRule);
        tempStringBuff = new StringBuffer();
        while (regex.find()) {
            extractedStrings.add(regex.group().toUpperCase());
            regex.appendReplacement(tempStringBuff, "###@" + (extractedStrings.size() - 1) + "@###");
        }
        regex.appendTail(tempStringBuff);
        sqlRule = tempStringBuff.toString();

        regex = SQL_VALUE_COLUMN_PATTERN.matcher(sqlRule);
        tempStringBuff = new StringBuffer();
        while (regex.find()) {
            if (regex.group().startsWith("is")) {
                regex.appendReplacement(tempStringBuff,
                        schema.addColumn(tables, "serviceName") + " = '" + regex.group().substring(2) + "'");
            } else if (regex.group().startsWith("notis")) {
                regex.appendReplacement(tempStringBuff,
                        schema.addColumn(tables, "ipAddr") + " NOT IN (SELECT ifServices.ipAddr FROM ifServices, service WHERE service.serviceName ='"
                                + regex.group().substring(5) + "' AND service.serviceID = ifServices.serviceID)");
            } else if (regex.group().startsWith("catinc")) {
                regex.appendReplacement(tempStringBuff,
                        schema.addColumn(tables, "nodeID") + " IN (SELECT category_node.nodeID FROM category_node, categories WHERE categories.categoryID = category_node.categoryID AND categories.categoryName = '"
                                + regex.group().substring(6) + "')");
            } else if (regex.group().matches(SQL_IPLIKE6_RHS_REGEX)) {
                // IPv6 IPLIKE right-hand side
            } else {
                regex.appendReplacement(tempStringBuff, schema.addColumn(tables, regex.group()));
            }
        }
        regex.appendTail(tempStringBuff);
        sqlRule = tempStringBuff.toString();

        regex = SQL_ESCAPED_PATTERN.matcher(sqlRule);
        tempStringBuff = new StringBuffer();
        while (regex.find()) {
            regex.appendReplacement(tempStringBuff,
                    Matcher.quoteReplacement(extractedStrings.get(Integer.parseInt(regex.group(1)))));
        }
        regex.appendTail(tempStringBuff);
        sqlRule = tempStringBuff.toString();
        return "WHERE " + sqlRule;
    }
}
