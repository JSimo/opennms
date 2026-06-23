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

/**
 * Root type for all filter expression AST nodes.
 * Supports the visitor pattern for traversal and SQL emission.
 */
interface Expr {

    /**
     * Accept a visitor (e.g. for SQL emission or optimization).
     *
     * @param visitor the visitor
     * @param <T>     result type of the visit
     * @return the result of the visit
     */
    <T> T accept(ExprVisitor<T> visitor);
}
