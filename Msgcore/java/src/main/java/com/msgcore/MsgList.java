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
 * Java wrapper over P3PmsgList — a doubly-linked list of P3PmsgData elements.
 *
 * Obtain from {@link MsgField#asList()}.
 */
public class MsgList implements AutoCloseable {

    private final MemorySegment handle;
    private boolean closed = false;

    MsgList(MemorySegment handle) {
        this.handle = handle;
    }

    @Override
    public void close() {
        if (!closed) {
            Msgcore_c.msgcore_list_destroy(handle);
            closed = true;
        }
    }

    public int getCount() {
        checkOpen();
        return Msgcore_c.msgcore_list_get_count(handle);
    }

    // ------------------------------------------------------------------
    // Add to tail (default queue behaviour)
    // ------------------------------------------------------------------

    public void addTail(int value)    { checkOpen(); Msgcore_c.msgcore_list_add_tail_int(handle, value); }
    public void addTail(double value) { checkOpen(); Msgcore_c.msgcore_list_add_tail_double(handle, value); }
    public void addTail(String value) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            Msgcore_c.msgcore_list_add_tail_wstr(handle, Msgcore_c.toWStr(a, value));
        }
    }

    // ------------------------------------------------------------------
    // Add to head (stack behaviour)
    // ------------------------------------------------------------------

    public void addHead(int value)    { checkOpen(); Msgcore_c.msgcore_list_add_head_int(handle, value); }
    public void addHead(double value) { checkOpen(); Msgcore_c.msgcore_list_add_head_double(handle, value); }
    public void addHead(String value) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            Msgcore_c.msgcore_list_add_head_wstr(handle, Msgcore_c.toWStr(a, value));
        }
    }

    public void dropHead() { checkOpen(); Msgcore_c.msgcore_list_drop_head(handle); }
    public void dropTail() { checkOpen(); Msgcore_c.msgcore_list_drop_tail(handle); }
    public void truncate()  { checkOpen(); Msgcore_c.msgcore_list_truncate(handle); }

    // ------------------------------------------------------------------

    private void checkOpen() {
        if (closed) throw new IllegalStateException("MsgList already closed");
    }
}
