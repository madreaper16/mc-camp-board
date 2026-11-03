package com.clockworktown.campboard.storage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class MaterialListImport {
    private static final String ITEM = "(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+";
    private static final Pattern LINE = Pattern.compile("^(?:([0-9]+)[\\s,;|]+(?:-\\s*)?(" + ITEM + ")|(" + ITEM + ")[\\s,;|]+([0-9]+))$");
    private MaterialListImport() { }

    public static Map<String, Integer> parse(List<String> lines, Predicate<String> knownItem) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).split("#", 2)[0].strip();
            if (index == 0 && line.startsWith("\uFEFF")) line = line.substring(1).strip();
            if (line.isEmpty()) continue;
            var match = LINE.matcher(line);
            if (!match.matches()) throw new IllegalArgumentException("Invalid material format on line " + (index + 1));
            String id = match.group(2) != null ? match.group(2) : match.group(3);
            if (!id.contains(":")) id = "minecraft:" + id;
            if (!knownItem.test(id)) throw new IllegalArgumentException("Unknown material " + id + " on line " + (index + 1));
            try {
                int count = Integer.parseInt(match.group(1) != null ? match.group(1) : match.group(4));
                if (count < 1) throw new NumberFormatException();
                result.put(id, Math.addExact(result.getOrDefault(id, 0), count));
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid material amount on line " + (index + 1));
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("No material requests found.");
        return result;
    }
}
