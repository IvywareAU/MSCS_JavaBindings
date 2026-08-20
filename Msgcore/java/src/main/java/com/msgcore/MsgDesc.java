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
 * Java wrapper over P3PmsgDesc — the descendant (child) collection of a field.
 *
 * Obtain from {@link MsgField#desc()}.
 */
public class MsgDesc implements AutoCloseable {

    private final MemorySegment handle;
    private boolean closed = false;

    MsgDesc(MemorySegment handle) {
        this.handle = handle;
    }

    @Override
    public void close() {
        if (!closed) {
            Msgcore_c.msgcore_desc_destroy(handle);
            closed = true;
        }
    }

    public int getCount()    { checkOpen(); return Msgcore_c.msgcore_desc_get_count(handle); }
    public boolean isEmpty() { checkOpen(); return Msgcore_c.msgcore_desc_is_empty(handle) != 0; }

    public boolean exists(String name) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            return Msgcore_c.msgcore_desc_exists(handle, NativeStrings.toWStr(a, name)) != 0;
        }
    }

    public MsgField selectItem(String name) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_desc_select_item(handle, NativeStrings.toWStr(a, name));
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("desc item not found: " + name);
            return new MsgField(h);
        }
    }

    public MsgField declareInt(String name, int value, boolean update) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_desc_declare_int(handle, NativeStrings.toWStr(a, name), value, update ? 1 : 0);
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("desc declareInt failed");
            return new MsgField(h);
        }
    }

    public MsgField declareString(String name, String value, boolean update) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment h = Msgcore_c.msgcore_desc_declare_wstr(
                handle, NativeStrings.toWStr(a, name), NativeStrings.toWStr(a, value), update ? 1 : 0);
            if (h.equals(MemorySegment.NULL)) throw new RuntimeException("desc declareString failed");
            return new MsgField(h);
        }
    }

    public boolean delete(String name) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            return Msgcore_c.msgcore_desc_delete(handle, NativeStrings.toWStr(a, name)) != 0;
        }
    }

    public void truncate() {
        checkOpen();
        Msgcore_c.msgcore_desc_truncate(handle);
    }

    /** Opens a cursor over this descendant collection's items. */
    public MsgCurs cursor() {
        checkOpen();
        MemorySegment h = Msgcore_c.msgcore_curs_from_desc(handle);
        if (h.equals(MemorySegment.NULL)) throw new RuntimeException("desc cursor failed");
        return new MsgCurs(h);
    }

    // ------------------------------------------------------------------

    private void checkOpen() {
        if (closed) throw new IllegalStateException("MsgDesc already closed");
    }
}
