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
package org.glassfish.soteria.identitystores;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toMap;
import static javax.security.identitystore.CredentialValidationResult.INVALID_RESULT;
import static javax.security.identitystore.CredentialValidationResult.NOT_VALIDATED_RESULT;

import java.util.List;
import java.util.Map;

import javax.security.CallerPrincipal;
import javax.security.identitystore.CredentialValidationResult;
import javax.security.identitystore.IdentityStore;
import javax.security.identitystore.annotation.Credentials;
import javax.security.identitystore.annotation.EmbeddedIdentityStoreDefinition;
import javax.security.identitystore.credential.Credential;
import javax.security.identitystore.credential.UsernamePasswordCredential;

public class EmbeddedIdentityStore implements IdentityStore {

    private Map<String, Credentials> callerToCredentials;
    private EmbeddedIdentityStoreDefinition embeddedIdentityStoreDefinition;
    private ValidationType validationType;

    public EmbeddedIdentityStore(EmbeddedIdentityStoreDefinition embeddedIdentityStoreDefinition) {

        this.embeddedIdentityStoreDefinition = embeddedIdentityStoreDefinition;
        callerToCredentials = stream(embeddedIdentityStoreDefinition.value()).collect(toMap(
                e -> e.callerName(),
                e -> e)
        );
        determineValidationType();
    }
    
    @Override
    public CredentialValidationResult validate(Credential credential) {
        if (credential instanceof UsernamePasswordCredential) {
            return validate((UsernamePasswordCredential) credential);
        }

        return NOT_VALIDATED_RESULT;
    }

    private void determineValidationType() {
        validationType = ValidationType.BOTH;
        if (embeddedIdentityStoreDefinition.authenticateOnly()) {
            validationType = ValidationType.AUTHENTICATION;
        } else {
            if (embeddedIdentityStoreDefinition.authorizeOnly()) {
                validationType = ValidationType.AUTHORIZATION;
            }
        }
    }
    
    public CredentialValidationResult validate(UsernamePasswordCredential usernamePasswordCredential) {
        Credentials credentials = callerToCredentials.get(usernamePasswordCredential.getCaller());

        if (credentials != null && usernamePasswordCredential.getPassword().compareTo(credentials.password())) {
            return new CredentialValidationResult(
                new CallerPrincipal(credentials.callerName()), 
                asList(credentials.groups())
            );
        }

        return INVALID_RESULT;
    }
    
    @Override
    public List<String> getGroupsByCallerPrincipal(CallerPrincipal callerPrincipal) {
        
        Credentials credentials = callerToCredentials.get(callerPrincipal.getName());
        
        return credentials != null? asList(credentials.groups()) : emptyList();
    }

    public int priority() {
        return embeddedIdentityStoreDefinition.priority();
    }

    public ValidationType validationType() {
        return validationType;
    }
}
