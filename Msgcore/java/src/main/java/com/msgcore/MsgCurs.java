package com.msgcore;

import com.msgcore.native_.Msgcore_c;
import java.lang.foreign.*;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Java wrapper over P3PmsgCurs — sequential cursor over a field's children.
 *
 * Obtain from {@link MsgField#cursor()}, {@link MsgAttr#cursor()}, or
 * {@link MsgDesc#cursor()}.
 *
 * Implements {@link Iterable} so you can use it in a for-each loop:
 * <pre>{@code
 * try (MsgField root = mgr.asField();
 *      MsgCurs  curs = root.cursor()) {
 *     for (MsgField child : curs) {
 *         System.out.println(child.getName() + " = " + child.getString());
 *         child.close();
 *     }
 * }
 * }</pre>
 */
public class MsgCurs implements AutoCloseable, Iterable<MsgField> {

    private final MemorySegment handle;
    private boolean closed = false;

    MsgCurs(MemorySegment handle) {
        this.handle = handle;
    }

    @Override
    public void close() {
        if (!closed) {
            Msgcore_c.msgcore_curs_destroy(handle);
            closed = true;
        }
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    /** Advance to the next element. */
    public void next()  { checkOpen(); Msgcore_c.msgcore_curs_next(handle); }

    /** Rewind to start-of-cursor. */
    public void seek()  { checkOpen(); Msgcore_c.msgcore_curs_seek(handle); }

    /** Move to a named element; returns false if not found. */
    public boolean gotoItem(String name) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            return Msgcore_c.msgcore_curs_goto_name(handle, Msgcore_c.toWStr(a, name)) != 0;
        }
    }

    /** Move to element by index; returns false if out of range. */
    public boolean gotoIndex(int index) {
        checkOpen();
        return Msgcore_c.msgcore_curs_goto_index(handle, index) != 0;
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    public boolean isEndOfCursor()   { checkOpen(); return Msgcore_c.msgcore_curs_is_eo_cursor(handle) != 0; }
    public boolean isStartOfCursor() { checkOpen(); return Msgcore_c.msgcore_curs_is_so_cursor(handle) != 0; }
    public int getCount()            { checkOpen(); return Msgcore_c.msgcore_curs_get_count(handle); }
    public int currentIndex()        { checkOpen(); return Msgcore_c.msgcore_curs_item_index(handle); }
    public boolean isItem()          { checkOpen(); return Msgcore_c.msgcore_curs_is_item(handle) != 0; }
    public boolean isList()          { checkOpen(); return Msgcore_c.msgcore_curs_is_list(handle) != 0; }
    public boolean isVect()          { checkOpen(); return Msgcore_c.msgcore_curs_is_vect(handle) != 0; }

    // ------------------------------------------------------------------
    // Current element access
    // ------------------------------------------------------------------

    /** Returns the current element's name (no allocation). */
    public String currentName() {
        checkOpen();
        return Msgcore_c.fromWStr(Msgcore_c.msgcore_curs_get_name(handle));
    }

    /**
     * Returns the current element as a MsgField (caller must close).
     * Only valid when not at end-of-cursor.
     */
    public MsgField currentField() {
        checkOpen();
        MemorySegment h = Msgcore_c.msgcore_curs_get_field(handle);
        if (h.equals(MemorySegment.NULL)) throw new NoSuchElementException("cursor at end");
        return new MsgField(h);
    }

    /** Deletes the current element and advances the cursor. */
    public void deleteCurrent() {
        checkOpen();
        Msgcore_c.msgcore_curs_delete(handle);
    }

    // ------------------------------------------------------------------
    // Iterable support
    // ------------------------------------------------------------------

    @Override
    public Iterator<MsgField> iterator() {
        seek();
        return new Iterator<>() {
            @Override public boolean hasNext() { return !isEndOfCursor(); }
            @Override public MsgField next() {
                if (isEndOfCursor()) throw new NoSuchElementException();
                MsgField f = currentField();
                MsgCurs.this.next();
                return f;
            }
        };
    }

    // ------------------------------------------------------------------

    private void checkOpen() {
        if (closed) throw new IllegalStateException("MsgCurs already closed");
    }
}
