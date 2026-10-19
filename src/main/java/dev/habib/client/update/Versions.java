package dev.habib.client.update;

/** Plain version comparison ("1.10.0" vs "v1.9.3"); only the numeric parts count. */
public final class Versions {
    private Versions() {}

    /** Negative if a is older than b, 0 if equal, positive if a is newer. */
    public static int compare(String a, String b) {
        long[] x = parts(a);
        long[] y = parts(b);
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            long p = i < x.length ? x[i] : 0L;
            long q = i < y.length ? y[i] : 0L;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    private static long[] parts(String v) {
        if (v == null) return new long[0];
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        int cut = s.indexOf('-'); // drop "-beta", "-rc1" ...
        if (cut >= 0) s = s.substring(0, cut);
        cut = s.indexOf('+');
        if (cut >= 0) s = s.substring(0, cut);
        String[] bits = s.split("\\.");
        long[] out = new long[bits.length];
        for (int i = 0; i < bits.length; i++) {
            try {
                out[i] = Long.parseLong(bits[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                out[i] = 0L;
            }
        }
        return out;
    }

    /** True for strings that are safe to use inside a file name. */
    public static boolean safe(String v) {
        return v != null && v.matches("[0-9][0-9A-Za-z.+\\-]{0,30}");
    }
}
