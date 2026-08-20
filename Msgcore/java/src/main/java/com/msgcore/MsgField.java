// Copyright © 2026 Khrustal & Mann
//              MELBOURNE, VICTORIA, AUSTRALIA, 3000
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied. See the License for the specific language governing
// permissions and limitations under the License.
//
package com.msgcore;

import com.msgcore.native_.Msgcore_c;
import java.lang.foreign.*;

/**
 * Java wrapper over P3PmsgField (= P3PmsgItem).
 * A field is the fundamental unit: it carries a name and a typed data value,
 * and may contain child items, a list, a vector, attributes, or descendants.
 *
 * Always use try-with-resources; every MsgField holds a native heap allocation.
 */
public class MsgField implements AutoCloseable {

    final MemorySegment handle;
    private boolean closed = false;

    /** Wraps an existing raw handle (takes ownership — will be destroyed on close). */
    MsgField(MemorySegment handle) {
        if (handle == null || handle.equals(MemorySegment.NULL))
            throw new IllegalArgumentException("null handle");
        this.handle = handle;
    }

    /** Creates a new empty field. */
    public MsgField() {
        this(Msgcore_c.msgcore_field_create());
    }

    /** Copy-constructs a field from another. */
    public MsgField clone() {
        checkOpen();
        MemorySegment h = Msgcore_c.msgcore_field_clone(handle);
        if (h.equals(MemorySegment.NULL)) throw new RuntimeException("clone failed");
        return new MsgField(h);
    }

    @Override
    public void close() {
        if (!closed) {
            Msgcore_c.msgcore_field_destroy(handle);
            closed = true;
        }
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    /**
     * The named child, as a <b>LIVE</b> handle aliasing the node in the manager's
     * heap — declares, renames and retypes through it reach the tree and are
     * visible to {@code save()}.
     *
     * <p>Use this, not {@link #selectItem}, for anything that writes.
     *
     * <p><b>A live handle does not survive a mutation.</b> Any call that can grow
     * the heap may relocate the base image, and a handle taken before such a call
     * must not be used after it. Re-resolve per operation, or hold
     * {@link #p2pos()} — a heap offset rather than an address, so it still denotes
     * the same node after a relocation. It is not an identity: delete the node and
     * the offset can be handed out again, so a stale one resolves to whatever now
     * occupies that block rather than failing. Obtain, use, discard.
     *
     * @return the child, or {@code null} if there is no such child
     */
    public MsgField child(String name) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_field_child(handle, NativeStrings.toWStr(a, name));
            return h.equals(MemorySegment.NULL) ? null : new MsgField(h);
        }
    }

    /**
     * The field's stable positional handle — a heap offset, and the natural inode
     * number. 0 for an unaddressable (standalone) field. See {@link #child} for
     * why this, and not a field handle, is the thing to hold across a mutation.
     */
    public long p2pos() {
        checkOpen();
        return Msgcore_c.msgcore_field_get_p2pos(handle);
    }

    /**
     * Returns a new MsgField for the named child item (caller owns it).
     *
     * <p><b>DETACHED: this is a deep copy, and writes through it never reach the
     * tree.</b> It is safe to read and safe to keep across mutations, which is what
     * it is for. To modify anything, use {@link #child} instead.
     */
    public MsgField selectItem(String name) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_field_select_item(handle, NativeStrings.toWStr(a, name));
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("item not found: " + name);
            return new MsgField(h);
        }
    }

    public boolean exists(String name) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            return Msgcore_c.msgcore_field_exists(handle, NativeStrings.toWStr(a, name)) != 0;
        }
    }

    public boolean deleteItem(String name) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            return Msgcore_c.msgcore_field_delete_item(handle, NativeStrings.toWStr(a, name)) != 0;
        }
    }

    public void truncate() {
        checkOpen();
        Msgcore_c.msgcore_field_truncate(handle);
    }

    // ------------------------------------------------------------------
    // Declare (create or update) child items
    // ------------------------------------------------------------------

    public MsgField declareInt(String name, int value, boolean update) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_field_declare_int(handle, NativeStrings.toWStr(a, name), value, update ? 1 : 0);
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("declareInt failed");
            return new MsgField(h);
        }
    }

    public MsgField declareLong(String name, long value, boolean update) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_field_declare_int64(handle, NativeStrings.toWStr(a, name), value, update ? 1 : 0);
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("declareLong failed");
            return new MsgField(h);
        }
    }

    public MsgField declareDouble(String name, double value, boolean update) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_field_declare_double(handle, NativeStrings.toWStr(a, name), value, update ? 1 : 0);
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("declareDouble failed");
            return new MsgField(h);
        }
    }

    public MsgField declareBool(String name, boolean value, boolean update) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_field_declare_bool(handle, NativeStrings.toWStr(a, name), value ? 1 : 0, update ? 1 : 0);
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("declareBool failed");
            return new MsgField(h);
        }
    }

    public MsgField declareString(String name, String value, boolean update) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_field_declare_wstr(
                handle, NativeStrings.toWStr(a, name), NativeStrings.toWStr(a, value), update ? 1 : 0);
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("declareString failed");
            return new MsgField(h);
        }
    }

    // ------------------------------------------------------------------
    // Name
    // ------------------------------------------------------------------

    public String getName() {
        checkOpen();
        return NativeStrings.fromWStr(Msgcore_c.msgcore_field_get_name(handle));
    }

    // ------------------------------------------------------------------
    // Data getters / setters
    // ------------------------------------------------------------------

    public int getDataType() {
        checkOpen();
        return Byte.toUnsignedInt(Msgcore_c.msgcore_field_get_data_type(handle));
    }

    public boolean isNull() {
        checkOpen();
        return Msgcore_c.msgcore_field_is_null(handle) != 0;
    }

    public boolean isVoid() {
        checkOpen();
        return Msgcore_c.msgcore_field_is_void(handle) != 0;
    }

    public int getInt() {
        checkOpen();
        return Msgcore_c.msgcore_field_get_int(handle);
    }

    public void setInt(int value) {
        checkOpen();
        Msgcore_c.msgcore_field_set_int(handle, value);
    }

    public long getLong() {
        checkOpen();
        return Msgcore_c.msgcore_field_get_int64(handle);
    }

    public void setLong(long value) {
        checkOpen();
        Msgcore_c.msgcore_field_set_int64(handle, value);
    }

    public double getDouble() {
        checkOpen();
        return Msgcore_c.msgcore_field_get_double(handle);
    }

    public void setDouble(double value) {
        checkOpen();
        Msgcore_c.msgcore_field_set_double(handle, value);
    }

    public boolean getBool() {
        checkOpen();
        return Msgcore_c.msgcore_field_get_bool(handle) != 0;
    }

    public void setBool(boolean value) {
        checkOpen();
        Msgcore_c.msgcore_field_set_bool(handle, value ? 1 : 0);
    }

    public String getString() {
        checkOpen();
        return NativeStrings.fromWStr(Msgcore_c.msgcore_field_get_wstr(handle));
    }

    public void setString(String value) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            Msgcore_c.msgcore_field_set_wstr(handle, NativeStrings.toWStr(a, value));
        }
    }

    // ------------------------------------------------------------------
    // Structure queries
    // ------------------------------------------------------------------

    public boolean isList()         { checkOpen(); return Msgcore_c.msgcore_field_is_list(handle) != 0; }
    public boolean isVect()         { checkOpen(); return Msgcore_c.msgcore_field_is_vect(handle) != 0; }
    public boolean isAttributed()   { checkOpen(); return Msgcore_c.msgcore_field_is_attributed(handle) != 0; }
    public boolean isDescendant()   { checkOpen(); return Msgcore_c.msgcore_field_is_descendant(handle) != 0; }

    // ------------------------------------------------------------------
    // Sub-object factories
    // ------------------------------------------------------------------

    /** Opens the list view of this field. */
    public MsgList asList() {
        checkOpen();
        MemorySegment h = Msgcore_c.msgcore_list_from_field(handle);
        if (h.equals(MemorySegment.NULL)) throw new RuntimeException("not a list field");
        return new MsgList(h);
    }

    /** Opens the vector view of this field. */
    public MsgVect asVect() {
        checkOpen();
        MemorySegment h = Msgcore_c.msgcore_vect_from_field(handle);
        if (h.equals(MemorySegment.NULL)) throw new RuntimeException("not a vect field");
        return new MsgVect(h);
    }

    /** Opens (or creates) the attribute collection of this field. */
    public MsgAttr attr(boolean create) {
        checkOpen();
        MemorySegment h = Msgcore_c.msgcore_attr_from_field(handle, create ? 1 : 0);
        if (h.equals(MemorySegment.NULL)) throw new RuntimeException("attr access failed");
        return new MsgAttr(h);
    }

    public MsgAttr attr() { return attr(false); }

    /** Opens (or creates) the descendant collection of this field. */
    public MsgDesc desc(boolean create) {
        checkOpen();
        MemorySegment h = Msgcore_c.msgcore_desc_from_field(handle, create ? 1 : 0);
        if (h.equals(MemorySegment.NULL)) throw new RuntimeException("desc access failed");
        return new MsgDesc(h);
    }

    public MsgDesc desc() { return desc(false); }

    /** Opens a cursor over this field's direct children. */
    public MsgCurs cursor() {
        checkOpen();
        MemorySegment h = Msgcore_c.msgcore_curs_from_field(handle);
        if (h.equals(MemorySegment.NULL)) throw new RuntimeException("cursor failed");
        return new MsgCurs(h);
    }

    // ------------------------------------------------------------------

    private void checkOpen() {
        if (closed) throw new IllegalStateException("MsgField already closed");
    }

    @Override
    public String toString() {
        if (closed) return "MsgField[closed]";
        String name = getName();
        return "MsgField[" + (name != null ? name : "<unnamed>") + "]";
    }

    // ------------------------------------------------------------------
    // UTF-8 (portable) surface
    // ------------------------------------------------------------------
    //  wchar_t is 16 bits on Windows and 32 on Linux, so the wide entry points
    //  above cannot carry a string portably through an FFI. Every string-bearing
    //  function has a _u8 twin that takes and returns UTF-8 and is ABI-identical
    //  on both platforms -- 65 of them, none of which the bindings had until
    //  2026-08-20. Prefer these in anything that has to run on both.

    /** {@link #child} over the UTF-8 {@code _u8} C API. */
    public MsgField childUtf8(String name) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_field_child_u8(handle, NativeStrings.toU8(a, name));
            return h.equals(MemorySegment.NULL) ? null : new MsgField(h);
        }
    }

    /** {@link #exists} over the UTF-8 {@code _u8} C API. */
    public boolean existsUtf8(String name) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            return Msgcore_c.msgcore_field_exists_u8(handle, NativeStrings.toU8(a, name)) != 0;
        }
    }

    /** {@link #declareInt} over the UTF-8 {@code _u8} C API. */
    public MsgField declareIntUtf8(String name, int value, boolean update) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_field_declare_int_u8(
                    handle, NativeStrings.toU8(a, name), value, update ? 1 : 0);
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("declareIntUtf8 failed");
            return new MsgField(h);
        }
    }

    /** {@link #declareDouble} over the UTF-8 {@code _u8} C API. */
    public MsgField declareDoubleUtf8(String name, double value, boolean update) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_field_declare_double_u8(
                    handle, NativeStrings.toU8(a, name), value, update ? 1 : 0);
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("declareDoubleUtf8 failed");
            return new MsgField(h);
        }
    }

    /** {@link #declareString} over the UTF-8 {@code _u8} C API - name AND value. */
    public MsgField declareStringUtf8(String name, String value, boolean update) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_field_declare_wstr_u8(
                    handle, NativeStrings.toU8(a, name), NativeStrings.toU8(a, value), update ? 1 : 0);
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("declareStringUtf8 failed");
            return new MsgField(h);
        }
    }

    /** This field's name, decoded from UTF-8. */
    public String getNameUtf8() {
        checkOpen();
        return NativeStrings.fromU8(Msgcore_c.msgcore_field_get_name_u8(handle));
    }

    /** This field's string value, decoded from UTF-8. */
    public String getStringUtf8() {
        checkOpen();
        return NativeStrings.fromU8(Msgcore_c.msgcore_field_get_wstr_u8(handle));
    }
}
