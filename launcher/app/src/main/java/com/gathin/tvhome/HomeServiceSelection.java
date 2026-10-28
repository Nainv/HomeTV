package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/** Changes only our component; all unrelated entries and their order are retained. */
final class HomeServiceSelection {
    private HomeServiceSelection() { }
    static String update(String previous, String full, String shortName, boolean enable) {
        List<String> entries = new ArrayList<>();
        if (previous != null) for (String entry : previous.split(":")) {
            if (!entry.isEmpty() && !entry.equals(full) && !entry.equals(shortName)) entries.add(entry);
        }
        if (enable) entries.add(full);
        return String.join(":", entries);
    }
}
