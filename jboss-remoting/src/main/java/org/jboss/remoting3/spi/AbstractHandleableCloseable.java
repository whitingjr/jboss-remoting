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
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.HandleableCloseable;
import org.jboss.remoting3.NotOpenException;
import org.jboss.remoting3.RemotingException;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;

/**
 * A basic implementation of a closeable resource.  Use as a convenient base class for your closeable resources.
 * Ensures that the {@code close()} method is idempotent; implements the registry of close handlers.
 */
public abstract class AbstractHandleableCloseable<T extends HandleableCloseable<T>> implements HandleableCloseable<T> {

    private static final Logger log = Logger.getLogger("org.jboss.remoting.resource");
    private static final boolean LEAK_DEBUGGING;

    private final Executor executor;
    private final StackTraceElement[] backtrace;

    private final Object closeLock = new Object();
    private boolean closed;
    private Map<Key, CloseHandler<? super T>> closeHandlers = null;

    static {
        boolean b = false;
        try {
            b = Boolean.parseBoolean(AccessController.doPrivileged(new PrivilegedAction<String>() {
                public String run() {
                    return System.getProperty("jboss.remoting.leakdebugging", "false");
                }
            }));
        } catch (SecurityException se) {
            b = false;
        }
        LEAK_DEBUGGING = b;
    }

    /**
     * Basic constructor.
     *
     * @param executor the executor used to execute the close notification handlers
     */
    protected AbstractHandleableCloseable(final Executor executor) {
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        this.executor = executor;
        backtrace = LEAK_DEBUGGING ? new Throwable().getStackTrace() : null;
    }

    /**
     * Read the status of this resource.  This is just a snapshot in time; there is no guarantee that the resource
     * will remain open for any amount of time, even if this method returns {@code true}.
     *
     * @return {@code true} if the resource is still open
     */
    protected boolean isOpen() {
        synchronized (closeLock) {
            return ! closed;
        }
    }

    /**
     * Called exactly once when the {@code close()} method is invoked; the actual close operation should take place here.
     *
     * @throws RemotingException if the close failed
     */
    protected void closeAction() throws IOException {}

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        final Map<Key, CloseHandler<? super T>> closeHandlers;
        synchronized (closeLock) {
            if (closed) {
                return;
            }
            closed = true;
            closeHandlers = this.closeHandlers;
            this.closeHandlers = null;
        }
        if (closeHandlers != null) {
            log.trace("Closed %s", this);
            if (closeHandlers != null) {
                for (final CloseHandler<? super T> handler : closeHandlers.values()) {
                    runCloseTask(executor, new CloseHandlerTask(handler));
                }
            }
        }
        closeAction();
    }

    /**
     * {@inheritDoc}
     */
    public Key addCloseHandler(final CloseHandler<? super T> handler) {
        if (handler == null) {
            throw new NullPointerException("handler is null");
        }
        synchronized (closeLock) {
            if (! closed) {
                final Key key = new KeyImpl<T>(this);
                final Map<Key, CloseHandler<? super T>> closeHandlers = this.closeHandlers;
                if (closeHandlers == null) {
                    final IdentityHashMap<Key, CloseHandler<? super T>> newMap = new IdentityHashMap<Key, CloseHandler<? super T>>();
                    this.closeHandlers = newMap;
                    newMap.put(key, handler);
                } else {
                    closeHandlers.put(key, handler);
                }
                return key;
            }
        }
        runCloseTask(executor, new CloseHandlerTask(handler));
        return new NullKey();
    }

    private static void runCloseTask(final Executor executor, final Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException ree) {
            task.run();
        }
    }

    private static final class NullKey implements Key {
        public void remove() {
        }
    }

    private static final class KeyImpl<T extends HandleableCloseable<T>> implements Key {

        private final AbstractHandleableCloseable<T> instance;

        private KeyImpl(final AbstractHandleableCloseable<T> instance) {
            this.instance = instance;
        }

        public void remove() {
            synchronized (instance.closeLock) {
                final Map<Key, CloseHandler<? super T>> closeHandlers = instance.closeHandlers;

                if (closeHandlers != null) {
                    closeHandlers.remove(this);
                }
            }
        }
    }

    /**
     * Get the executor to use for handler invocation.
     *
     * @return the executor
     */
    protected Executor getExecutor() {
        return executor;
    }

    /**
     * Finalize this closeable instance.  If the instance hasn't been closed, it is closed and a warning is logged.
     */
    protected void finalize() throws Throwable {
        try {
            super.finalize();
        } finally {
            if (! isOpen()) {
                if (LEAK_DEBUGGING) {
                    final Throwable t = new LeakThrowable();
                    t.setStackTrace(backtrace);
                    log.warn(t, "Leaked a %s instance: %s", getClass().getName(), this);
                } else {
                    log.warn("Leaked a %s instance: %s", getClass().getName(), this);
                }
                IoUtils.safeClose(this);
            }
        }
    }

    /**
     * Check if open, throwing an exception if it is not.
     *
     * @throws org.jboss.remoting3.NotOpenException if not open
     */
    protected void checkOpen() throws NotOpenException {
        synchronized (closeLock) {
            if (closed) {
                throw new NotOpenException(toString() + " is not open");
            }
        }
    }

    @SuppressWarnings({ "serial" })
    private static final class LeakThrowable extends Throwable {

        public LeakThrowable() {
        }

        public String toString() {
            return "a leaked reference";
        }
    }

    private final class CloseHandlerTask implements Runnable {

        private final CloseHandler<? super T> handler;

        private CloseHandlerTask(final CloseHandler<? super T> handler) {
            this.handler = handler;
        }

        @SuppressWarnings({ "unchecked" })
        public void run() {
            SpiUtils.safeHandleClose(handler, (T) AbstractHandleableCloseable.this);
        }
    }
}
