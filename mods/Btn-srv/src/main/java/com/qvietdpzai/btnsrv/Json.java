package com.qvietdpzai.btnsrv;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON parser/serializer toi gian, tu chua (khong phu thuoc thu vien ngoai).
 * Ho tro: object, array, string (kem escape + chuoi U+XXXX), number, true/false/null.
 */
public final class Json {

    private Json() {}

    // ---------------- serialize ----------------

    public static String write(Object o) {
        StringBuilder sb = new StringBuilder();
        w(o, sb, -1, 0);
        return sb.toString();
    }

    public static String obj(Object... kv) {
        if (kv.length % 2 != 0) throw new IllegalArgumentException("so tham so phai chan");
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return write(m);
    }

    /** JSON san co (string) - ghi nguyen ven, khong escape. Dung de long object. */
    public static Raw raw(String json) {
        return new Raw(json);
    }

    /** Dung cho gia tri da la chuoi JSON (tranh bi escape thanh string khi long). */
    public static final class Raw {
        public final String json;
        Raw(String json) { this.json = json; }
    }

    private static void w(Object o, StringBuilder sb, int indent, int depth) {
        if (o == null) { sb.append("null"); return; }
        if (o instanceof String s) { str(s, sb); return; }
        if (o instanceof Boolean b) { sb.append(b ? "true" : "false"); return; }
        if (o instanceof Number n) { sb.append(n.toString()); return; }
        if (o instanceof Raw r) { sb.append(r.json); return; }
        if (o instanceof Map<?, ?> m) {
            if (m.isEmpty()) { sb.append("{}"); return; }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                nl(sb, indent, depth + 1);
                str(String.valueOf(e.getKey()), sb);
                sb.append(':');
                if (indent >= 0) sb.append(' ');
                w(e.getValue(), sb, indent, depth + 1);
            }
            nl(sb, indent, depth);
            sb.append('}');
            return;
        }
        if (o instanceof List<?> l) {
            if (l.isEmpty()) { sb.append("[]"); return; }
            sb.append('[');
            boolean first = true;
            for (Object v : l) {
                if (!first) sb.append(',');
                first = false;
                nl(sb, indent, depth + 1);
                w(v, sb, indent, depth + 1);
            }
            nl(sb, indent, depth);
            sb.append(']');
            return;
        }
        str(String.valueOf(o), sb);
    }

    private static void nl(StringBuilder sb, int indent, int depth) {
        if (indent < 0) return;
        sb.append('\n');
        for (int i = 0; i < indent * depth; i++) sb.append(' ');
    }

    private static void str(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        // ghi chuoi escape dang U+XXXX. KHONG dung literal chua backslash-u
                        // trong source vi javaxep xu ly unicode escape truoc khi lex.
                        char[] bs = { '\\', 'u' };
                        sb.append(bs);
                        String hex = Integer.toHexString(c);
                        for (int k = hex.length(); k < 4; k++) sb.append('0');
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ---------------- parse ----------------

    public static Object parse(String s) {
        P p = new P(s);
        p.ws();
        Object v = p.val();
        p.ws();
        return v;
    }

    public static Map<?, ?> parseObj(String s) {
        Object v = parse(s);
        if (v instanceof Map<?, ?> m) return m;
        return new LinkedHashMap<>();
    }

    private static final class P {
        private final String s;
        private int i;

        P(String s) { this.s = s; }

        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        char peek() {
            if (i >= s.length()) throw new IllegalStateException("JSON ket thuc som");
            return s.charAt(i);
        }

        Object val() {
            char c = peek();
            return switch (c) {
                case '{' -> map();
                case '[' -> arr();
                case '"' -> str();
                case 't' -> lit("true", Boolean.TRUE);
                case 'f' -> lit("false", Boolean.FALSE);
                case 'n' -> lit("null", null);
                default  -> num();
            };
        }

        Object lit(String word, Object v) {
            if (!s.startsWith(word, i)) throw new IllegalStateException("JSON khong hop le @ " + i);
            i += word.length();
            return v;
        }

        Map<String, Object> map() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // {
            ws();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws();
                String k = str();
                ws();
                if (peek() != ':') throw new IllegalStateException("thieu ':' @ " + i);
                i++;
                ws();
                m.put(k, val());
                ws();
                char c = peek();
                if (c == ',') { i++; continue; }
                if (c == '}') { i++; return m; }
                throw new IllegalStateException("JSON khong hop le @ " + i);
            }
        }

        List<Object> arr() {
            List<Object> l = new ArrayList<>();
            i++; // [
            ws();
            if (peek() == ']') { i++; return l; }
            while (true) {
                ws();
                l.add(val());
                ws();
                char c = peek();
                if (c == ',') { i++; continue; }
                if (c == ']') { i++; return l; }
                throw new IllegalStateException("JSON khong hop le @ " + i);
            }
        }

        String str() {
            if (peek() != '"') throw new IllegalStateException("khong phai chuoi @ " + i);
            i++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"'  -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/'  -> sb.append('/');
                        case 'n'  -> sb.append('\n');
                        case 'r'  -> sb.append('\r');
                        case 't'  -> sb.append('\t');
                        case 'b'  -> sb.append('\b');
                        case 'f'  -> sb.append('\f');
                        case 'u'  -> {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> throw new IllegalStateException("escape la @" + (i - 1));
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        Object num() {
            int st = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E' || (c >= '0' && c <= '9')) i++;
                else break;
            }
            String t = s.substring(st, i);
            if (t.isEmpty()) throw new IllegalStateException("so khong hop le @ " + st);
            if (t.contains(".") || t.contains("e") || t.contains("E")) return Double.parseDouble(t);
            try { return Long.parseLong(t); } catch (NumberFormatException ex) { return Double.parseDouble(t); }
        }
    }
}
