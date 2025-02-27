/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2006-2015 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2015 The OpenNMS Group, Inc.
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

package org.opennms.web.springframework.security;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.Assert;

public class OpenNMSUserDetailsService implements UserDetailsService, InitializingBean {
	private SpringSecurityUserDao m_userDao;
	private boolean m_trimRealm = false;
	
    public OpenNMSUserDetailsService() {
    }

    public OpenNMSUserDetailsService(final SpringSecurityUserDao userDao) {
        m_userDao = userDao;
    }

	@Override
	public void afterPropertiesSet() throws Exception {
	    Assert.notNull(m_userDao);
	}

	/** {@inheritDoc} */
        @Override
	public UserDetails loadUserByUsername(final String rawUsername) throws UsernameNotFoundException, DataAccessException {
            final String username;
            if (m_trimRealm && rawUsername.contains("@")) {
                username = rawUsername.substring(0, rawUsername.indexOf("@"));
            } else {
                username = rawUsername;
            }
	    final UserDetails userDetails = m_userDao.getByUsername(username);
		
		if (userDetails == null) {
			throw new UsernameNotFoundException("Unable to locate " + username + " in the userDao");
		}
		
		return userDetails;
	}

	public void setUserDao(final SpringSecurityUserDao userDao) {
		m_userDao = userDao;
		
	}

	public SpringSecurityUserDao getUserDao() {
		return m_userDao;
	}

	/**
	 * 
	 * @param trimRealm Defaults to false. If set to true, trim the realm
	 * portion (e.g. @EXAMPLE.ORG) from the authenticated user principal
	 * name (e.g. user@EXAMPLE.ORG). Useful when authenticating against a
	 * Kerberos realm or possibly other realm- / domain-aware technologies
	 * such as OAUTH.
	 */
	public void setTrimRealm(boolean trimRealm) {
	    m_trimRealm = trimRealm;
	}

	public boolean getTrimRealm() {
	    return m_trimRealm;
	}
}
