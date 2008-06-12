package org.jboss.cx.remoting.spi.protocol;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 *
 */
public abstract class NumericIdentifier implements Externalizable {

    private static final long serialVersionUID = 1L;

    private /*final*/ boolean client;
    private /*final*/ int id;

    protected NumericIdentifier() {
    }

    protected NumericIdentifier(final boolean client, final int id) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be >= 0");
        }
        this.client = client;
        this.id = id;
    }

    public final void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(id << 1 | (client ? 0 : 1));
    }

    public final void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        int i = in.readInt();
        id = i >>> 1;
        client = (i & 1) == 0;
    }

    public int getId() {
        return id;
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof NumericIdentifier)) return false;
        NumericIdentifier other = (NumericIdentifier) obj;
        return other.id == id && other.client == client;
    }

    public int hashCode() {
        return id << 1 | (client ? 0 : 1);
    }
}