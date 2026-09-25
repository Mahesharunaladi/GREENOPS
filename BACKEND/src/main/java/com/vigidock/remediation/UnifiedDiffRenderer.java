package com.vigidock.remediation;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders compact, complete diffs for VigiDock dry-run output.
 */
final class UnifiedDiffRenderer {

    private UnifiedDiffRenderer() {}

    static String render(String path, String original, String updated) {
        if (original.equals(updated)) {
            return "";
        }
        String[] originalLines = lines(original);
        String[] updatedLines = lines(updated);
        StringBuilder diff = new StringBuilder();
        diff.append("--- a/").append(path).append('\n');
        diff.append("+++ b/").append(path).append('\n');
        diff.append("@@ -1,").append(originalLines.length)
                .append(" +1,").append(updatedLines.length).append(" @@\n");
        for (String line : originalLines) {
            diff.append('-').append(line).append('\n');
        }
        for (String line : updatedLines) {
            diff.append('+').append(line).append('\n');
        }
        return diff.toString();
    }

    static List<FixProposal.DiffLine> sideBySide(String original, String updated) {
        String[] originalLines = lines(original);
        String[] updatedLines = lines(updated);
        List<FixProposal.DiffLine> result = new ArrayList<>();
        int maximum = Math.max(originalLines.length, updatedLines.length);
        for (int index = 0; index < maximum; index++) {
            boolean hasOriginal = index < originalLines.length;
            boolean hasUpdated = index < updatedLines.length;
            String originalValue = hasOriginal ? originalLines[index] : "";
            String updatedValue = hasUpdated ? updatedLines[index] : "";
            FixProposal.ChangeType type = !hasOriginal
                    ? FixProposal.ChangeType.ADDED
                    : !hasUpdated
                            ? FixProposal.ChangeType.REMOVED
                            : originalValue.equals(updatedValue)
                                    ? FixProposal.ChangeType.UNCHANGED
                                    : FixProposal.ChangeType.MODIFIED;
            result.add(new FixProposal.DiffLine(
                    hasOriginal ? index + 1 : 0,
                    originalValue,
                    hasUpdated ? index + 1 : 0,
                    updatedValue,
                    type));
        }
        return List.copyOf(result);
    }

    private static String[] lines(String value) {
        return value.split("\\R", -1);
    }
}
