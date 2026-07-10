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
