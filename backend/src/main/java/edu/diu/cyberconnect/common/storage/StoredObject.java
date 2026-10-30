package edu.diu.cyberconnect.common.storage;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/** An object's metadata plus a content stream that the caller must close. */
public final class StoredObject implements Closeable {

    private final ObjectInfo info;
    private final InputStream content;

    public StoredObject(ObjectInfo info, InputStream content) {
        this.info = info;
        this.content = content;
    }

    public ObjectInfo info() {
        return info;
    }

    public InputStream content() {
        return content;
    }

    @Override
    public void close() throws IOException {
        content.close();
    }
}
