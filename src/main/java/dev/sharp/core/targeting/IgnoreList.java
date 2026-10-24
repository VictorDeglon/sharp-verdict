package dev.sharp.core.targeting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Entity types that are never targeted, whatever else says they could be. Each entry is a type id such as {@code minecraft:villager};
 * a missing namespace means {@code minecraft}, and {@code *} matches any run of characters, so {@code minecraft:*_boat} covers every boat.
 */
public final class IgnoreList {
    public static final IgnoreList NONE = new IgnoreList(List.of());

    private final List<Pattern> patterns;

    private IgnoreList(List<Pattern> patterns) {
        this.patterns = patterns;
    }

    public static IgnoreList of(List<String> entries) {
        List<Pattern> out = new ArrayList<>(entries.size());
        for (String entry : entries) {
            String id = normalise(entry);
            if (!id.isEmpty()) {
                out.add(Pattern.compile(glob(id)));
            }
        }
        return new IgnoreList(List.copyOf(out));
    }

    /** True when the type id (for example {@code minecraft:end_crystal}) matches an entry. */
    public boolean ignores(String typeId) {
        String id = normalise(typeId);
        for (Pattern p : patterns) {
            if (p.matcher(id).matches()) {
                return true;
            }
        }
        return false;
    }

    /** Lower case, trimmed, with the default namespace added. Blank stays blank. */
    public static String normalise(String entry) {
        if (entry == null) {
            return "";
        }
        String id = entry.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            return "";
        }
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    private static String glob(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("\\*", -1)) {
            if (sb.length() > 0 || id.startsWith("*")) {
                sb.append(".*");
            }
            sb.append(Pattern.quote(part));
        }
        return sb.toString();
    }
}
