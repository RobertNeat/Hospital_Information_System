package robert_neat.his_backend.common.text;

/**
 * Zwijanie tekstu do porownan bez rozroznienia wielkosci liter i znakow diakrytycznych (polskie litery i
 * popularne europejskie) - bez rozszerzenia `unaccent`. Ta sama tabela sluzy po stronie Javy ({@link #fold})
 * i bazy ({@link #SQL_FROM}/{@link #SQL_TO} w `translate(...)`), wiec obie strony zawsze sie zgadzaja,
 * niezaleznie od locale bazy (`lower()` dziala wtedy tylko na ASCII).
 */
public final class TextFolding {

    private static final String[] PAIRS = {
        "ą=a", "ć=c", "ę=e", "ł=l", "ń=n", "ó=o", "ś=s", "ź=z", "ż=z",
        "á=a", "à=a", "â=a", "ä=a", "ã=a", "å=a", "é=e", "è=e", "ê=e", "ë=e", "í=i", "ì=i", "î=i", "ï=i",
        "ò=o", "ô=o", "ö=o", "õ=o", "ø=o", "ú=u", "ù=u", "û=u", "ü=u", "ý=y", "ÿ=y", "ç=c", "ñ=n",
        "š=s", "ž=z", "č=c", "ř=r", "ě=e", "ď=d", "ť=t", "ň=n", "ů=u"
    };

    /** Znaki zrodlowe (male i wielkie) oraz ich odpowiedniki ASCII (male) dla `translate(kolumna, from, to)`. */
    public static final String SQL_FROM;
    public static final String SQL_TO;

    static {
        StringBuilder from = new StringBuilder();
        StringBuilder to = new StringBuilder();
        for (String pair : PAIRS) {
            from.append(pair.charAt(0));
            to.append(pair.charAt(2));
        }
        for (String pair : PAIRS) {
            from.append(Character.toUpperCase(pair.charAt(0)));
            to.append(pair.charAt(2));
        }
        SQL_FROM = from.toString();
        SQL_TO = to.toString();
    }

    private TextFolding() {
    }

    /** Odpowiednik SQL `lower(translate(x, SQL_FROM, SQL_TO))`. */
    public static String fold(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            int idx = SQL_FROM.indexOf(c);
            out.append(idx >= 0 ? SQL_TO.charAt(idx) : c);
        }
        return asciiLower(out);
    }

    private static String asciiLower(CharSequence s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            out.append(c >= 'A' && c <= 'Z' ? (char) (c + 32) : c);
        }
        return out.toString();
    }
}
