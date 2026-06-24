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

import java.util.Objects;

/**
 * A single token from the filter rule.
 * For IDENTIFIER and keywords, value is the lexeme; for PLACEHOLDER, value is the index (as string).
 */
final class Token {

    private final TokenType type;
    private final String value;

    public Token(TokenType type, String value) {
        this.type = Objects.requireNonNull(type, "type");
        this.value = value != null ? value : "";
    }

    public TokenType getType() {
        return type;
    }

    /**
     * Lexeme (e.g. "catincRouters") or placeholder index (e.g. "0").
     */
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return type == TokenType.EOF ? "EOF" : (value.isEmpty() ? type.name() : type.name() + "(" + value + ")");
    }
}
