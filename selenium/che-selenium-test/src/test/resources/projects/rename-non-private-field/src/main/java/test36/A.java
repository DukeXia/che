/*
 * Copyright (c) 2012-2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package test36;

import org.eclipse.osgi.util.NLS;
public class A extends NLS {
    private static final String BUNDLE_NAME = "test36.messages"; //$NON-NLS-1$
    public static String f;
    static {
        NLS.initializeMessages(BUNDLE_NAME, A.class);
    }
    private A() {}
}
