package com.msgcore;

import com.msgcore.native_.Msgcore_c;
import java.lang.foreign.*;

/**
 * Java wrapper over P3PmsgAttr — the attribute collection attached to a field.
 * Attributes are accessed via the '@' path character in the C++ API.
 *
 * Obtain from {@link MsgField#attr()}.
 */
public class MsgAttr implements AutoCloseable {

    private final MemorySegment handle;
    private boolean closed = false;

    MsgAttr(MemorySegment handle) {
        this.handle = handle;
    }

    @Override
    public void close() {
        if (!closed) {
            Msgcore_c.msgcore_attr_destroy(handle);
            closed = true;
        }
    }

    public int getCount()  { checkOpen(); return Msgcore_c.msgcore_attr_get_count(handle); }
    public boolean isEmpty() { checkOpen(); return Msgcore_c.msgcore_attr_is_empty(handle) != 0; }

    public boolean exists(String name) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            return Msgcore_c.msgcore_attr_exists(handle, Msgcore_c.toWStr(a, name)) != 0;
        }
    }

    public MsgField selectItem(String name) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_attr_select_item(handle, Msgcore_c.toWStr(a, name));
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("attr item not found: " + name);
            return new MsgField(h);
        }
    }

    public MsgField declareInt(String name, int value, boolean update) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_attr_declare_int(handle, Msgcore_c.toWStr(a, name), value, update ? 1 : 0);
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("attr declareInt failed");
            return new MsgField(h);
        }
    }

    public MsgField declareDouble(String name, double value, boolean update) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_attr_declare_double(handle, Msgcore_c.toWStr(a, name), value, update ? 1 : 0);
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("attr declareDouble failed");
            return new MsgField(h);
        }
    }

    public MsgField declareString(String name, String value, boolean update) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_attr_declare_wstr(
                handle, Msgcore_c.toWStr(a, name), Msgcore_c.toWStr(a, value), update ? 1 : 0);
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("attr declareString failed");
            return new MsgField(h);
        }
    }

    public boolean delete(String name) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            return Msgcore_c.msgcore_attr_delete(handle, Msgcore_c.toWStr(a, name)) != 0;
        }
    }

    public void truncate() {
        checkOpen();
        Msgcore_c.msgcore_attr_truncate(handle);
    }

    /** Opens a cursor over this attribute collection's items. */
    public MsgCurs cursor() {
        checkOpen();
        MemorySegment h = Msgcore_c.msgcore_curs_from_attr(handle);
        if (h.equals(MemorySegment.NULL)) throw new RuntimeException("attr cursor failed");
        return new MsgCurs(h);
    }

    // ------------------------------------------------------------------

    private void checkOpen() {
        if (closed) throw new IllegalStateException("MsgAttr already closed");
    }
}
