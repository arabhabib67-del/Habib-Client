package dev.habib.client.update;

/** Version comparison that handles numeric and pre-release parts ("beta.10" is newer than "beta.2"). */
public final class Versions {
    private Versions() {}

    /** Negative if a is older than b, 0 if equal, positive if a is newer. */
    public static int compare(String a, String b) {
        ParsedVersion x = parse(a);
        ParsedVersion y = parse(b);
        int n = Math.max(x.core.length, y.core.length);
        for (int i = 0; i < n; i++) {
            long p = i < x.core.length ? x.core[i] : 0L;
            long q = i < y.core.length ? y.core[i] : 0L;
            if (p != q) return p < q ? -1 : 1;
        }
        if (x.preRelease.length == 0) return y.preRelease.length == 0 ? 0 : 1;
        if (y.preRelease.length == 0) return -1;

        n = Math.max(x.preRelease.length, y.preRelease.length);
        for (int i = 0; i < n; i++) {
            if (i >= x.preRelease.length) return -1;
            if (i >= y.preRelease.length) return 1;
            int result = compareIdentifier(x.preRelease[i], y.preRelease[i]);
            if (result != 0) return result;
        }
        return 0;
    }

    private static ParsedVersion parse(String v) {
        if (v == null) return new ParsedVersion(new long[0], new String[0]);
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        int cut = s.indexOf('+');
        if (cut >= 0) s = s.substring(0, cut); // build metadata does not affect update order
        String[] preRelease = new String[0];
        cut = s.indexOf('-');
        if (cut >= 0) {
            preRelease = s.substring(cut + 1).split("\\.");
            s = s.substring(0, cut);
        }
        String[] bits = s.split("\\.");
        long[] out = new long[bits.length];
        for (int i = 0; i < bits.length; i++) {
            try {
                out[i] = Long.parseLong(bits[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                out[i] = 0L;
            }
        }
        return new ParsedVersion(out, preRelease);
    }

    private static int compareIdentifier(String a, String b) {
        boolean aNumeric = numeric(a);
        boolean bNumeric = numeric(b);
        if (aNumeric && bNumeric) {
            String left = stripLeadingZeros(a);
            String right = stripLeadingZeros(b);
            int lengthOrder = Integer.compare(left.length(), right.length());
            return lengthOrder != 0 ? lengthOrder : left.compareTo(right);
        }
        if (aNumeric != bNumeric) return aNumeric ? -1 : 1;
        return a.compareTo(b);
    }

    private static boolean numeric(String value) {
        if (value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) return false;
        }
        return true;
    }

    private static String stripLeadingZeros(String value) {
        int first = 0;
        while (first < value.length() - 1 && value.charAt(first) == '0') first++;
        return value.substring(first);
    }

    private record ParsedVersion(long[] core, String[] preRelease) {}

    /** True for strings that are safe to use inside a file name. */
    public static boolean safe(String v) {
        return v != null && v.matches("[0-9][0-9A-Za-z.+\\-]{0,30}");
    }
}
