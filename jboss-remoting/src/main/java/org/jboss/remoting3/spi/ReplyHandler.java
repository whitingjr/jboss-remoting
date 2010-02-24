/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.remoting3.spi;

import java.io.IOException;

/**
 * A handler for replies from a request.  The handler should respect the first invocation made on it, and ignore
 * any subsequent invocations.
 */
public interface ReplyHandler {

    /**
     * Handle a successful reply.  If the reply could not be forwarded, an exception is thrown.
     *
     * @param reply the reply
     */
    void handleReply(Object reply) throws IOException;

    /**
     * Handle an exception.  If the exception could not be forwarded, a (different) {@code IOException} is thrown.
     *
     * @param exception an exception which describes the problem
     */
    void handleException(IOException exception) throws IOException;

    /**
     * Handle a cancellation acknowledgement.  If the cancellation acknowledgement could not be forwarded, an
     * exception is thrown.
     */
    void handleCancellation() throws IOException;
}
