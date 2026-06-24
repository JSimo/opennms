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
package org.opennms.netmgt.filter.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds SQL subqueries for category_node/categories (single source of shape for IN/NOT IN/EXCEPT).
 */
final class CategorySubquery {

    private static String escape(String name) {
        return "'" + name.replace("'", "''") + "'";
    }

    /** SELECT cn.nodeID FROM category_node cn JOIN categories c ... WHERE c.categoryName = 'X' or IN (...). */
    static String forNames(List<String> names) {
        if (names.size() == 1) {
            return "SELECT cn.nodeID FROM category_node cn JOIN categories c ON c.categoryID = cn.categoryID "
                    + "WHERE c.categoryName = " + escape(names.get(0));
        }
        String inList = names.stream().map(CategorySubquery::escape).collect(Collectors.joining(", "));
        return "SELECT cn.nodeID FROM category_node cn JOIN categories c ON c.categoryID = cn.categoryID "
                + "WHERE c.categoryName IN (" + inList + ")";
    }

    /** SELECT cn1.nodeID FROM category_node cn1 JOIN categories c1 ... (multiple groups, same shape as CatincAndIn). */
    static String forGroups(List<List<String>> groups) {
        if (groups.size() == 1) {
            return forNames(groups.get(0));
        }
        StringBuilder sb = new StringBuilder("SELECT cn1.nodeID FROM category_node cn1 JOIN categories c1 ON c1.categoryID = cn1.categoryID");
        List<String> whereClauses = new ArrayList<>();
        String inList0 = groups.get(0).stream().map(CategorySubquery::escape).collect(Collectors.joining(", "));
        whereClauses.add("c1.categoryName IN (" + inList0 + ")");
        for (int i = 1; i < groups.size(); i++) {
            String cn = "cn" + (i + 1);
            String c = "c" + (i + 1);
            sb.append(" JOIN category_node ").append(cn).append(" ON cn1.nodeID = ").append(cn).append(".nodeID");
            sb.append(" JOIN categories ").append(c).append(" ON ").append(c).append(".categoryID = ").append(cn).append(".categoryID");
            String inList = groups.get(i).stream().map(CategorySubquery::escape).collect(Collectors.joining(", "));
            whereClauses.add(c + ".categoryName IN (" + inList + ")");
        }
        sb.append(" WHERE ").append(String.join(" AND ", whereClauses));
        return sb.toString();
    }

    private CategorySubquery() {}
}
