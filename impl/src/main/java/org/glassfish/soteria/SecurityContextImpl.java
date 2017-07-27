/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015, 2016 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.soteria;

import static javax.security.enterprise.AuthenticationStatus.SEND_FAILURE;
import static javax.security.enterprise.AuthenticationStatus.SUCCESS;
import static org.glassfish.soteria.mechanisms.jaspic.Jaspic.getLastAuthenticationStatus;

import java.io.Serializable;
import java.security.Principal;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.security.enterprise.AuthenticationStatus;
import javax.security.enterprise.SecurityContext;
import javax.security.enterprise.authentication.mechanism.http.AuthenticationParameters;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.glassfish.soteria.authorization.spi.CallerDetailsResolver;
import org.glassfish.soteria.authorization.spi.ResourceAccessResolver;
import org.glassfish.soteria.authorization.spi.impl.JaccResourceAccessResolver;
import org.glassfish.soteria.authorization.spi.impl.ReflectionAndJaccCallerDetailsResolver;
import org.glassfish.soteria.mechanisms.jaspic.Jaspic;

public class SecurityContextImpl implements SecurityContext, Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private CallerDetailsResolver callerDetailsResolver;
    private ResourceAccessResolver resourceAccessResolver;

    @PostConstruct
    public void init() {
       callerDetailsResolver = new ReflectionAndJaccCallerDetailsResolver();
       resourceAccessResolver = new JaccResourceAccessResolver();
    }

    @Override
    public Principal getCallerPrincipal() {
        return callerDetailsResolver.getCallerPrincipal();
    }

    @Override
    public <T extends Principal> Set<T> getPrincipalsByType(Class<T> pType) {
        return callerDetailsResolver.getPrincipalsByType(pType);
    }

    @Override
    public boolean isCallerInRole(String role) {
        return callerDetailsResolver.isCallerInRole(role);
    }

    // Implementation specific method, not present in API.
    public Set<String> getAllDeclaredCallerRoles() {
        return callerDetailsResolver.getAllDeclaredCallerRoles();
    }

    @Override
    public boolean hasAccessToWebResource(String resource, String... methods) {
        return resourceAccessResolver.hasAccessToWebResource(resource, methods);
    }

    @Override
    public AuthenticationStatus authenticate(HttpServletRequest request, HttpServletResponse response, AuthenticationParameters parameters) {
        
        try {
            if (Jaspic.authenticate(request, response, parameters)) {
                // All servers return true when authentication actually took place 
                return SUCCESS;
            }
            
            // GlassFish returns false when either authentication is in progress or authentication
            // failed (or was not done at all). 
            // Therefore we need to rely on the status we saved as a request attribute
            return getLastAuthenticationStatus(request);
        } catch (IllegalArgumentException e) { // TODO: exception type not ideal
            // JBoss returns false when authentication is in progress, but throws exception when
            // authentication fails (or was not done at all).
            return SEND_FAILURE;
        }
    }

}
