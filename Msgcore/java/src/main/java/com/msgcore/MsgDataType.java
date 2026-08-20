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

/** Mirrors the VBLockData_* data type constants from P2PmsgVBLock.h. */
public final class MsgDataType {
    private MsgDataType() {}

    public static final int NULL    = 0;
    public static final int INT8    = 1;
    public static final int UINT8   = 2;
    public static final int INT16   = 3;
    public static final int UINT16  = 4;
    public static final int INT32   = 5;
    public static final int UINT32  = 6;
    public static final int INT64   = 7;
    public static final int UINT64  = 8;
    public static final int FLOAT   = 9;
    public static final int DOUBLE  = 10;
    public static final int TIME32  = 11;
    public static final int TIME64  = 12;
    public static final int BOOL    = 13;
    public static final int WCHAR   = 14;
    public static final int BSTR16  = 18;   // ASCII string (16-bit length prefix)
    public static final int WSTR16  = 26;   // Unicode string (16-bit length prefix)
    public static final int BLOB16  = 34;   // Binary blob (16-bit length prefix)
    public static final int GUID    = 47;
}
