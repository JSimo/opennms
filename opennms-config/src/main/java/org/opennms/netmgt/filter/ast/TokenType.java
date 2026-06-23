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
 * Types of tokens produced by the filter tokenizer.
 * The tokenizer runs on the preprocessed rule (after quote extraction,
 * AND/OR/NOT translation, and IPLIKE normalization).
 */
enum TokenType {
    /** Identifier: catincRouters, isICMP, nodeLabel, etc. */
    IDENTIFIER,
    /** AND keyword */
    AND,
    /** OR keyword */
    OR,
    /** NOT keyword */
    NOT,
    /** Left parenthesis ( */
    LPAREN,
    /** Right parenthesis ) */
    RPAREN,
    /** Equals = or == */
    EQ,
    /** Not equals != */
    NE,
    /** Placeholder for extracted string: ###@n@###; value is the index as string */
    PLACEHOLDER,
    /** Single-quoted string literal (e.g. from IPLIKE value); value is unescaped content */
    STRING,
    /** Comma , (e.g. in IPLIKE(col, value)) */
    COMMA,
    /** PostgreSQL typecast operator :: */
    DOUBLE_COLON,
    /** End of input */
    EOF
}
