package com.msgcore;

import com.msgcore.native_.Msgcore_c;
import java.lang.foreign.*;

/**
 * Java wrapper over P3PmsgVect — an indexed vector of P3PmsgField elements.
 *
 * Obtain from {@link MsgField#asVect()}.
 */
public class MsgVect implements AutoCloseable {

    private final MemorySegment handle;
    private boolean closed = false;

    MsgVect(MemorySegment handle) {
        this.handle = handle;
    }

    @Override
    public void close() {
        if (!closed) {
            Msgcore_c.msgcore_vect_destroy(handle);
            closed = true;
        }
    }

    // ------------------------------------------------------------------
    // Element type queries
    // ------------------------------------------------------------------

    public boolean isData(int elem)  { checkOpen(); return Msgcore_c.msgcore_vect_is_data(handle, elem)  != 0; }
    public boolean isField(int elem) { checkOpen(); return Msgcore_c.msgcore_vect_is_field(handle, elem) != 0; }
    public boolean isList(int elem)  { checkOpen(); return Msgcore_c.msgcore_vect_is_list(handle, elem)  != 0; }
    public boolean isVect(int elem)  { checkOpen(); return Msgcore_c.msgcore_vect_is_vect(handle, elem)  != 0; }

    // ------------------------------------------------------------------
    // Data access by element index
    // ------------------------------------------------------------------

    public int getInt(int elem) {
        checkOpen();
        return Msgcore_c.msgcore_vect_get_int(handle, elem);
    }

    public double getDouble(int elem) {
        checkOpen();
        return Msgcore_c.msgcore_vect_get_double(handle, elem);
    }

    public String getString(int elem) {
        checkOpen();
        return Msgcore_c.fromWStr(Msgcore_c.msgcore_vect_get_wstr(handle, elem));
    }

    /**
     * Returns element as a MsgField (caller must close).
     * Suitable when the element is a field or a named item.
     */
    public MsgField getItem(int elem) {
        checkOpen();
        MemorySegment h = Msgcore_c.msgcore_vect_get_item(handle, elem);
        if (h.equals(MemorySegment.NULL)) throw new RuntimeException("vect element " + elem + " not found");
        return new MsgField(h);
    }

    public void truncate() {
        checkOpen();
        Msgcore_c.msgcore_vect_truncate(handle);
    }

    // ------------------------------------------------------------------

    private void checkOpen() {
        if (closed) throw new IllegalStateException("MsgVect already closed");
    }
}
