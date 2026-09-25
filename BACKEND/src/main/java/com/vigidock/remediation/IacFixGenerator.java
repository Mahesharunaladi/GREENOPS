package com.vigidock.remediation;

import com.vigidock.model.ScanRecord;
import com.vigidock.policy.GuardrailEvaluation;
import com.vigidock.policy.IacArtifact;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Generates deterministic, in-memory Dockerfile and Kubernetes hardening diffs.
 *
 * <p>The generator only applies narrowly safe posture changes: replacing explicit root users in a
 * Dockerfile and adding container security contexts in Kubernetes manifests. CVE remediation text is
 * carried into the proposal as review guidance rather than pretending to infer package versions.
 */
@Service
public final class IacFixGenerator {

    private static final Pattern ROOT_USER = Pattern.compile("(?im)^([ \\t]*USER[ \\t]+)(root|0)([ \\t]*)$");
    private static final Pattern CONTAINERS = Pattern.compile("^(\\s*)containers:\\s*$");
    private static final Pattern LIST_ITEM_NAME = Pattern.compile("^(\\s*)-\\s+name:\\s+.+$");

    /**
     * Creates a dry-run fix proposal without writing to a repository or filesystem.
     *
     * @param artifact Dockerfile or Kubernetes manifest
     * @param scanRecord vulnerability findings used for remediation guidance
     * @param policyEvaluation completed policy evaluation
     * @return complete proposed content, unified patch, side-by-side lines, and explanations
     */
    public FixProposal generate(
            IacArtifact artifact, ScanRecord scanRecord, GuardrailEvaluation policyEvaluation) {
        Objects.requireNonNull(artifact, "artifact must not be null");
        Objects.requireNonNull(scanRecord, "scanRecord must not be null");
        Objects.requireNonNull(policyEvaluation, "policyEvaluation must not be null");

        List<String> explanations = new ArrayList<>();
        String updatedContent = switch (artifact.type()) {
            case DOCKERFILE -> hardenDockerfile(artifact.content(), explanations);
            case KUBERNETES -> hardenKubernetesManifest(artifact.content(), explanations);
        };
        scanRecord.findings().forEach(finding -> explanations.add(
                finding.cveId() + ": " + finding.remediationFix()));
        policyEvaluation.violations().forEach(violation -> explanations.add(
                "Policy " + violation.policyId() + ": " + violation.message()));

        return new FixProposal(
                artifact,
                updatedContent,
                UnifiedDiffRenderer.render(artifact.path(), artifact.content(), updatedContent),
                UnifiedDiffRenderer.sideBySide(artifact.content(), updatedContent),
                explanations,
                null);
    }

    private String hardenDockerfile(String content, List<String> explanations) {
        String updated = ROOT_USER.matcher(content).replaceAll(match -> match.group(1) + "10001" + match.group(3));
        if (!updated.equals(content)) {
            explanations.add("Replaced explicit root execution with non-root UID 10001.");
        }
        return updated;
    }

    private String hardenKubernetesManifest(String content, List<String> explanations) {
        String[] lines = content.split("\\R", -1);
        List<String> updatedLines = new ArrayList<>(lines.length + 12);
        boolean changed = false;
        int containersIndent = -1;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            int indentation = indentation(line);
            var containersMatcher = CONTAINERS.matcher(line);
            if (containersMatcher.matches()) {
                containersIndent = containersMatcher.group(1).length();
                updatedLines.add(line);
                continue;
            }
            if (containersIndent >= 0 && !line.isBlank() && indentation <= containersIndent) {
                containersIndent = -1;
            }
            updatedLines.add(line);
            var containerMatcher = LIST_ITEM_NAME.matcher(line);
            if (containersIndent >= 0
                    && containerMatcher.matches()
                    && containerMatcher.group(1).length() == containersIndent + 2
                    && !hasSecurityContext(lines, index + 1, containersIndent + 2)) {
                String indent = " ".repeat(containersIndent + 4);
                updatedLines.add(indent + "securityContext:");
                updatedLines.add(indent + "  runAsNonRoot: true");
                updatedLines.add(indent + "  allowPrivilegeEscalation: false");
                updatedLines.add(indent + "  capabilities:");
                updatedLines.add(indent + "    drop: [\"ALL\"]");
                changed = true;
            }
        }
        if (changed) {
            explanations.add("Added non-root and least-privilege container security contexts.");
        }
        return String.join("\n", updatedLines);
    }

    private boolean hasSecurityContext(String[] lines, int startIndex, int containerIndent) {
        for (int index = startIndex; index < lines.length; index++) {
            String line = lines[index];
            int indentation = indentation(line);
            if (!line.isBlank() && indentation < containerIndent) {
                return false;
            }
            if (LIST_ITEM_NAME.matcher(line).matches() && indentation == containerIndent) {
                return false;
            }
            if (line.trim().startsWith("securityContext:") && indentation == containerIndent + 2) {
                return true;
            }
        }
        return false;
    }

    private int indentation(String line) {
        int indentation = 0;
        while (indentation < line.length() && line.charAt(indentation) == ' ') {
            indentation++;
        }
        return indentation;
    }
}
