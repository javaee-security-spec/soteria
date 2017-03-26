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
package org.glassfish.soteria.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.logging.Logger;

public final class Assert {
    
    private static final Logger logger = Logger.getLogger(Assert.class.getName());
    
    public static void assertDefaultAuthenticated(String response) {
        assertAuthenticated("web", response);
    }
    
    public static void assertAuthenticated(String userType, String response) {
        try {
            assertTrue(
                "Should be authenticated as user reza but was not",
                response.contains(userType + " username: reza"));
            assertTrue(
                "Authenticated user should have role \"foo\", but did not",
                response.contains(userType + " user has role \"foo\": true"));
            assertTrue(
                "Authenticated user should have role \"bar\", but did not",
                response.contains(userType + " user has role \"bar\": true"));
        } catch (AssertionError e) {
            logger.severe("Assertion failed on input:" + response);
        }
    }
     
    public static void assertDefaultNotAuthenticated(String response) {
        try {
            assertFalse(
                "Should not be authenticated as user reza but was",
                response.contains("web username: reza"));
            assertFalse(
                "Authenticated user should not have role \"foo\", but did",
                response.contains("web user has role \"foo\": true"));
            assertFalse(
                "Authenticated user should not have role \"bar\", but did",
                response.contains("web user has role \"bar\": true"));
        } catch (AssertionError e) {
            logger.severe("Assertion failed on input:" + response);
        }
     }
    
    public static void assertNotAuthenticated(String userType, String response) {
        try {
            assertFalse(
                "Should not be authenticated as user reza but was",
                response.contains(userType + " username: reza"));
            assertFalse(
                "Authenticated user should not have role \"foo\", but did",
                response.contains(userType + " user has role \"foo\": true"));
            assertFalse(
                "Authenticated user should not have role \"bar\", but did",
                response.contains(userType + " user has role \"bar\": true"));
        } catch (AssertionError e) {
            logger.severe("Assertion failed on input:" + response);
        }
     }


}
