package com.msgcore;

/**
 * Smoke-test / usage example for the Msgcore Panama bindings.
 *
 * Run with:
 *   java --enable-native-access=ALL-UNNAMED
 *        -Djava.library.path=<path-to-Msgcore.dll-directory>
 *        -cp target/msgcore-java-1.0.0.jar
 *        com.msgcore.MsgcoreExample
 */
public class MsgcoreExample {

    public static void main(String[] args) throws Exception {

        // --- Create an in-memory store and populate it ---
        try (MsgMgr mgr = new MsgMgr(MsgAddrMode.ADDR_32, 4096, 1024 * 1024)) {

            System.out.println("Manager created, valid=" + mgr.isValid());

            // Declare a child item with an integer value
            try (MsgField age = mgr.declareInt("Age", 42, false)) {
                System.out.println("Declared Age=" + age.getInt());
            }

            // Declare a string item
            try (MsgField name = mgr.declareString("Name", "Alice", false)) {
                System.out.println("Declared Name=" + name.getString());
            }

            // Declare a double item
            try (MsgField score = mgr.asField()) {
                try (MsgField s = score.declareDouble("Score", 98.6, false)) {
                    System.out.println("Declared Score=" + s.getDouble());
                }
            }

            // Iterate all top-level children
            System.out.println("\nAll items:");
            try (MsgField root = mgr.asField();
                 MsgCurs  curs = root.cursor()) {
                for (MsgField child : curs) {
                    try (child) {
                        System.out.printf("  %-12s  type=%d  value=%s%n",
                            child.getName(),
                            child.getDataType(),
                            readValue(child));
                    }
                }
            }

            // Save and reload
            String path = System.getProperty("java.io.tmpdir") + "\\msgcore_test.p2p";
            boolean saved = mgr.save(path);
            System.out.println("\nSaved to " + path + ": " + saved);
        }

        // --- Reload from file ---
        String path = System.getProperty("java.io.tmpdir") + "\\msgcore_test.p2p";
        try (MsgMgr mgr2 = new MsgMgr(path)) {
            System.out.println("\nReloaded from " + mgr2.getFilename());
            try (MsgField age = mgr2.selectItem("Age")) {
                System.out.println("Age after reload = " + age.getInt());
            }
            try (MsgField name = mgr2.selectItem("Name")) {
                System.out.println("Name after reload = " + name.getString());
            }
        }

        System.out.println("\nNative call succeeded!");
    }

    private static String readValue(MsgField f) {
        int type = f.getDataType();
        if (f.isNull()) return "<null>";
        return switch (type) {
            case MsgDataType.INT32  -> String.valueOf(f.getInt());
            case MsgDataType.INT64  -> String.valueOf(f.getLong());
            case MsgDataType.DOUBLE -> String.valueOf(f.getDouble());
            case MsgDataType.BOOL   -> String.valueOf(f.getBool());
            case MsgDataType.WSTR16,
                 MsgDataType.BSTR16 -> f.getString();
            default                 -> "(type " + type + ")";
        };
    }
}
