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

import javax.naming.AuthenticationException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.security.enterprise.CallerPrincipal;
import javax.security.enterprise.credential.Credential;
import javax.security.enterprise.credential.UsernamePasswordCredential;
import javax.security.enterprise.identitystore.CredentialValidationResult;
import javax.security.enterprise.identitystore.IdentityStore;
import javax.security.enterprise.identitystore.LdapIdentityStoreDefinition;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static javax.naming.Context.*;
import static javax.naming.directory.SearchControls.SUBTREE_SCOPE;
import static javax.security.enterprise.identitystore.CredentialValidationResult.INVALID_RESULT;
import static javax.security.enterprise.identitystore.CredentialValidationResult.NOT_VALIDATED_RESULT;

public class LdapIdentityStore implements IdentityStore {

    private static final Logger LOGGER = Logger.getLogger(LdapIdentityStore.class.getName());

    private final LdapIdentityStoreDefinition ldapIdentityStoreDefinition;
    private final Set<ValidationType> validationTypes;

    public LdapIdentityStore(LdapIdentityStoreDefinition ldapIdentityStoreDefinition) {
        this.ldapIdentityStoreDefinition = ldapIdentityStoreDefinition;
        validationTypes = unmodifiableSet(new HashSet<>(asList(ldapIdentityStoreDefinition.useFor())));
    }

    @Override
    public CredentialValidationResult validate(Credential credential) {
        if (credential instanceof UsernamePasswordCredential) {
            return validate((UsernamePasswordCredential) credential);
        }

        return NOT_VALIDATED_RESULT;
    }

    public CredentialValidationResult validate(UsernamePasswordCredential usernamePasswordCredential) {

        if (ldapIdentityStoreDefinition.bindDn().isEmpty()) {
            return checkDirectBinding(usernamePasswordCredential);
        } else {
            return checkThroughSearch(usernamePasswordCredential);
        }

    }

    @Override
    public Set<String> getCallerGroups(CredentialValidationResult validationResult) {
        LdapContext ldapContext = createLdapContext(
                ldapIdentityStoreDefinition.url(),
                ldapIdentityStoreDefinition.bindDn(),
                ldapIdentityStoreDefinition.bindDnPassword());

        if (ldapContext != null) {
            try {
                return new HashSet<>(retrieveGroupInformation(validationResult.getCallerPrincipal().getName(), ldapContext));
            } finally {
                closeContext(ldapContext);
            }
        }

        return emptySet();
    }

    private CredentialValidationResult checkThroughSearch(UsernamePasswordCredential usernamePasswordCredential) {
        LdapContext ldapContext = createLdapContext(
            ldapIdentityStoreDefinition.url(),
            ldapIdentityStoreDefinition.bindDn(),
            ldapIdentityStoreDefinition.bindDnPassword());
        
        if (ldapContext != null) {
            try {
                String callerDn = 
                    searchCaller(
                        ldapContext, 
                        ldapIdentityStoreDefinition.callerSearchBase(),
                        format(ldapIdentityStoreDefinition.callerSearchFilter(), usernamePasswordCredential.getCaller()));

                LdapContext ldapContextCaller = null;

                if (callerDn != null) {
                    // If this doesn't throw an exception internally, the password is correct

                    ldapContextCaller = createLdapContext(
                        ldapIdentityStoreDefinition.url(),
                        callerDn,
                        new String(usernamePasswordCredential.getPassword().getValue())
                    );
                }

                if (ldapContextCaller == null) {
                    closeContext(ldapContext);
                    return INVALID_RESULT;
                }

                Set<String> groups = Collections.emptySet();
                if(validationTypes.contains(ValidationType.PROVIDE_GROUPS)) {
                    groups = retrieveGroupInformation(callerDn, ldapContext);
                }

                closeContext(ldapContext);

                return new CredentialValidationResult(
                    new CallerPrincipal(usernamePasswordCredential.getCaller()),
                    groups
                );
            } catch (IllegalStateException e) {
                return NOT_VALIDATED_RESULT;
            }

        }

        return INVALID_RESULT;
    }

    private String searchCaller(LdapContext ldapContext, String searchBase, String searchExpression) {
        String result = null;
        List<SearchResult> callerDn = search(ldapContext, searchBase, searchExpression);

        if (callerDn.size() > 1) {
            // TODO User is found in multiple organizations

        }
        if (callerDn.size() == 1) {
            result = callerDn.get(0).getNameInNamespace();  // get the fully qualified identification like uid=arjan,ou=caller,dc=jsr375,dc=net
        }
        return result;

    }

    private CredentialValidationResult checkDirectBinding(UsernamePasswordCredential usernamePasswordCredential) {
        // Construct the full distinguished name (dn) of the caller
        String callerDn = createCallerDn(
                ldapIdentityStoreDefinition.callerNameAttribute(),
                usernamePasswordCredential.getCaller(),
                ldapIdentityStoreDefinition.callerBaseDn()
        );

        // If this doesn't throw an exception internally, the caller dn exists and the password is correct
        LdapContext ldapContext = createLdapContext(
                ldapIdentityStoreDefinition.url(),
                callerDn,
                new String(usernamePasswordCredential.getPassword().getValue())
        );

        if (ldapContext == null) {
            return INVALID_RESULT;
        }

        Set<String> groups = Collections.emptySet();
        if(validationTypes.contains(ValidationType.PROVIDE_GROUPS)) {
            groups = retrieveGroupInformation(callerDn, ldapContext);
        }

        closeContext(ldapContext);

        return new CredentialValidationResult(
                new CallerPrincipal(usernamePasswordCredential.getCaller()),
                groups
        );
    }

    private void closeContext(LdapContext ldapContext) {
        try {
            ldapContext.close();
        } catch (NamingException e) {
            // We can silently ignore this, no?
        }
    }

    private Set<String> retrieveGroupInformation(String callerDn, LdapContext ldapContext) {
        // Search for the groups starting from the groupBaseDn,
        // Search for groupCallerDnAttribute equal to callerDn
        // Return groupNameAttribute
        List<SearchResult> searchResults = search(
                ldapContext,
                ldapIdentityStoreDefinition.groupSearchBase(),
                ldapIdentityStoreDefinition.groupMemberAttribute(),
                callerDn,
                ldapIdentityStoreDefinition.groupNameAttribute()
        );

        // Collect the groups from the search results
        Set<String> groups = new HashSet<>();
        for (SearchResult searchResult : searchResults) {
            for (Object group : get(searchResult, ldapIdentityStoreDefinition.groupNameAttribute())) {
                groups.add(group.toString());
            }
        }
        return groups;
    }

    private static String createCallerDn(String callerNameAttribute, String callerName, String callerBaseDn) {
        return String.format("%s=%s,%s", callerNameAttribute, callerName, callerBaseDn);
    }

    private static LdapContext createLdapContext(String url, String bindDn, String bindCredential) {
        try {
            return new InitialLdapContext(getConnectionEnvironment(url, bindDn, bindCredential), null);
        } catch (AuthenticationException e) {
            return null;
        } catch (NamingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Hashtable<String, String> getConnectionEnvironment(String url, String bindDn, String bindCredential) {

        Hashtable<String, String> environment = new Hashtable<>();

        environment.put(INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(PROVIDER_URL, url);

        environment.put(SECURITY_AUTHENTICATION, "simple");
        environment.put(SECURITY_PRINCIPAL, bindDn);
        environment.put(SECURITY_CREDENTIALS, bindCredential);

        return environment;
    }

    private static List<SearchResult> search(LdapContext ldapContext, String searchBase, String filterAttribute, String filterValue, String returnAttribute) {
        SearchControls controls = new SearchControls();
        controls.setReturningAttributes(new String[]{returnAttribute}); // e.g. cn

        try {
            return list(ldapContext.search(
                    searchBase,                             // e.g. ou=group,dc=jsr375,dc=net
                    format("(%s={0})", filterAttribute),    // e.g. (member={0})
                    new Object[]{filterValue},              // e.g. uid=reza,ou=caller,dc=jsr375,dc=net
                    controls
            ));
        } catch (NamingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<SearchResult> search(LdapContext ldapContext, String searchBase, String searchExpression) {
        SearchControls controls = new SearchControls();
        // Specify the search scope
        controls.setSearchScope(SUBTREE_SCOPE);

        try {
            return list(ldapContext.search(searchBase, searchExpression, controls));
        } catch (NamingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<?> get(SearchResult searchResult, String attributeName) {
        try {
            Attribute attribute = searchResult.getAttributes().get(attributeName);
            if (attribute == null) {
                LOGGER.log(Level.WARNING, "Attribute name '{0}' is invalid, returning empty List", new Object[]{attributeName});
                return Collections.emptyList();
            }
            return list(attribute.getAll());
        } catch (NamingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int priority() {
        return ldapIdentityStoreDefinition.priority();
    }

    @Override
    public Set<ValidationType> validationTypes() {
        return validationTypes;
    }

}
