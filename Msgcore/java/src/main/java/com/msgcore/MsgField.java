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

    /** Returns a new MsgField for the named child item (caller owns it). */
    public MsgField selectItem(String name) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_field_select_item(handle, Msgcore_c.toWStr(a, name));
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("item not found: " + name);
            return new MsgField(h);
        }
    }

    public boolean exists(String name) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            return Msgcore_c.msgcore_field_exists(handle, Msgcore_c.toWStr(a, name)) != 0;
        }
    }

    public boolean deleteItem(String name) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            return Msgcore_c.msgcore_field_delete_item(handle, Msgcore_c.toWStr(a, name)) != 0;
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
            MemorySegment h = Msgcore_c.msgcore_field_declare_int(handle, Msgcore_c.toWStr(a, name), value, update ? 1 : 0);
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("declareInt failed");
            return new MsgField(h);
        }
    }

    public MsgField declareLong(String name, long value, boolean update) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_field_declare_int64(handle, Msgcore_c.toWStr(a, name), value, update ? 1 : 0);
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("declareLong failed");
            return new MsgField(h);
        }
    }

    public MsgField declareDouble(String name, double value, boolean update) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_field_declare_double(handle, Msgcore_c.toWStr(a, name), value, update ? 1 : 0);
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("declareDouble failed");
            return new MsgField(h);
        }
    }

    public MsgField declareBool(String name, boolean value, boolean update) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_field_declare_bool(handle, Msgcore_c.toWStr(a, name), value ? 1 : 0, update ? 1 : 0);
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("declareBool failed");
            return new MsgField(h);
        }
    }

    public MsgField declareString(String name, String value, boolean update) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_field_declare_wstr(
                handle, Msgcore_c.toWStr(a, name), Msgcore_c.toWStr(a, value), update ? 1 : 0);
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("declareString failed");
            return new MsgField(h);
        }
    }

    // ------------------------------------------------------------------
    // Name
    // ------------------------------------------------------------------

    public String getName() {
        checkOpen();
        return Msgcore_c.fromWStr(Msgcore_c.msgcore_field_get_name(handle));
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
        return Msgcore_c.fromWStr(Msgcore_c.msgcore_field_get_wstr(handle));
    }

    public void setString(String value) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            Msgcore_c.msgcore_field_set_wstr(handle, Msgcore_c.toWStr(a, value));
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
}
