package com.msgcore;

import com.msgcore.native_.Msgcore_c;
import java.lang.foreign.*;

/**
 * Java wrapper over P2PmsgMgr — the top-level persistent message store.
 *
 * P2PmsgMgr inherits from P3PmsgItem (= P3PmsgField), so the manager IS a
 * field.  Use {@link #asField()} to obtain a MsgField view for full
 * navigation and data access.
 *
 * Usage:
 * <pre>{@code
 * try (MsgMgr mgr = new MsgMgr()) {
 *     mgr.load("store.p2p");
 *     try (MsgField root = mgr.asField();
 *          MsgField item = root.selectItem(L"MyItem")) {
 *         System.out.println(item.getInt());
 *     }
 *     mgr.save("store.p2p");
 * }
 * }</pre>
 */
public class MsgMgr implements AutoCloseable {

    private final MemorySegment handle;
    private boolean closed = false;

    /** Creates an empty manager (default 32-bit addressing, 2 KB initial heap). */
    public MsgMgr() {
        handle = Msgcore_c.msgcore_mgr_create();
        if (handle.equals(MemorySegment.NULL)) throw new RuntimeException("msgcore_mgr_create failed");
    }

    /**
     * Creates a manager with explicit parameters.
     * @param addrMode  {@link MsgAddrMode} constant
     * @param sizeInitial  initial heap size in bytes
     * @param sizeMax      maximum heap size in bytes
     */
    public MsgMgr(int addrMode, int sizeInitial, int sizeMax) {
        handle = Msgcore_c.msgcore_mgr_create_nn((byte) addrMode, sizeInitial, sizeMax);
        if (handle.equals(MemorySegment.NULL)) throw new RuntimeException("msgcore_mgr_create_nn failed");
    }

    /**
     * Opens an existing .p2p file.
     * @param filename  absolute or relative path
     */
    public MsgMgr(String filename) {
        try (Arena a = Arena.ofConfined()) {
            handle = Msgcore_c.msgcore_mgr_open_file(Msgcore_c.toWStr(a, filename));
        }
        if (handle.equals(MemorySegment.NULL)) throw new RuntimeException("Cannot open: " + filename);
    }

    @Override
    public void close() {
        if (!closed) {
            Msgcore_c.msgcore_mgr_destroy(handle);
            closed = true;
        }
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public boolean load(String filename) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            return Msgcore_c.msgcore_mgr_load(handle, Msgcore_c.toWStr(a, filename)) != 0;
        }
    }

    public boolean save(String filename) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            return Msgcore_c.msgcore_mgr_save(handle, Msgcore_c.toWStr(a, filename)) != 0;
        }
    }

    public boolean save() {
        checkOpen();
        return Msgcore_c.msgcore_mgr_save(handle, MemorySegment.NULL) != 0;
    }

    public void nullify() {
        checkOpen();
        Msgcore_c.msgcore_mgr_nullify(handle);
    }

    public boolean rename(String newName) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            return Msgcore_c.msgcore_mgr_rename(handle, Msgcore_c.toWStr(a, newName)) != 0;
        }
    }

    // ------------------------------------------------------------------
    // Properties
    // ------------------------------------------------------------------

    public String getFilename() {
        checkOpen();
        return Msgcore_c.fromWStr(Msgcore_c.msgcore_mgr_get_filename(handle));
    }

    public String getRootname() {
        checkOpen();
        return Msgcore_c.fromWStr(Msgcore_c.msgcore_mgr_get_rootname(handle));
    }

    public boolean isDirty() {
        checkOpen();
        return Msgcore_c.msgcore_mgr_is_dirty(handle) != 0;
    }

    public void setDirty(boolean dirty) {
        checkOpen();
        Msgcore_c.msgcore_mgr_set_dirty(handle, dirty ? 1 : 0);
    }

    public int sizeof() {
        checkOpen();
        return Msgcore_c.msgcore_mgr_sizeof(handle);
    }

    public boolean isValid() {
        checkOpen();
        return Msgcore_c.msgcore_mgr_is_valid(handle) != 0;
    }

    // ------------------------------------------------------------------
    // Field access — P2PmsgMgr IS-A P3PmsgField
    // ------------------------------------------------------------------

    /**
     * Returns a MsgField copy-view of this manager's root item.
     * The returned MsgField must be closed independently.
     */
    public MsgField asField() {
        checkOpen();
        MemorySegment h = Msgcore_c.msgcore_mgr_as_field(handle);
        if (h.equals(MemorySegment.NULL)) throw new RuntimeException("asField failed");
        return new MsgField(h);
    }

    // ------------------------------------------------------------------
    // Convenience navigation (shortcut over asField())
    // ------------------------------------------------------------------

    public MsgField selectItem(String name) {
        try (MsgField root = asField()) {
            return root.selectItem(name);
        }
    }

    public boolean exists(String name) {
        try (MsgField root = asField()) {
            return root.exists(name);
        }
    }

    public MsgField declareInt(String name, int value, boolean update) {
        try (MsgField root = asField()) {
            return root.declareInt(name, value, update);
        }
    }

    public MsgField declareString(String name, String value, boolean update) {
        try (MsgField root = asField()) {
            return root.declareString(name, value, update);
        }
    }

    public MsgCurs cursor() {
        try (MsgField root = asField()) {
            return root.cursor();
        }
    }

    // ------------------------------------------------------------------

    private void checkOpen() {
        if (closed) throw new IllegalStateException("MsgMgr already closed");
    }

    @Override
    public String toString() {
        if (closed) return "MsgMgr[closed]";
        return "MsgMgr[" + getFilename() + "]";
    }
}
