package com.vigidock.remediation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Creates one-file GitHub pull requests from approved VigiDock fix proposals.
 *
 * <p>Calls are made only when an API endpoint explicitly requests PR creation. The GitHub token is
 * read from {@code VIGIDOCK_GITHUB_TOKEN}; it is never returned, logged, or stored in a model.
 */
@Service
public final class GitHubPullRequestService {

    private static final URI GITHUB_API = URI.create("https://api.github.com");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GitHubPullRequestService(ObjectMapper objectMapper) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), objectMapper);
    }

    GitHubPullRequestService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Creates a branch, commits the proposed artifact, and opens a GitHub pull request.
     *
     * @param repository GitHub repository and branch details
     * @param plan policy-reviewed remediation plan
     * @return created pull request metadata
     */
    public PullRequestResult createPullRequest(GitHubRepository repository, RemediationPlan plan) {
        Objects.requireNonNull(repository, "repository must not be null");
        Objects.requireNonNull(plan, "plan must not be null");
        if (!plan.guardrailEvaluation().allowed()) {
            throw new IllegalStateException("Blocking VigiDock guardrail violations prevent pull-request creation");
        }
        if (plan.fixProposal().unifiedDiff().isBlank()) {
            throw new IllegalStateException("No safe automated source change was generated for this artifact");
        }

        String token = System.getenv("VIGIDOCK_GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("VIGIDOCK_GITHUB_TOKEN must be configured to create a GitHub pull request");
        }
        try {
            String baseSha = branchSha(repository, token);
            String branch = repository.headBranch();
            createBranch(repository, branch, baseSha, token);
            updateFile(repository, branch, token, plan.fixProposal());
            return openPullRequest(repository, branch, token, plan);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Unable to create GitHub pull request", ex);
        }
    }

    private String branchSha(GitHubRepository repository, String token) throws IOException, InterruptedException {
        JsonNode response = send(repositoryPath(repository) + "/git/ref/heads/" + encode(repository.baseBranch()), "GET", null, token);
        String sha = response.path("object").path("sha").asText();
        if (sha.isBlank()) {
            throw new IllegalStateException("Unable to resolve the GitHub base branch SHA");
        }
        return sha;
    }

    private void createBranch(GitHubRepository repository, String branch, String baseSha, String token)
            throws IOException, InterruptedException {
        send(repositoryPath(repository) + "/git/refs", "POST", Map.of("ref", "refs/heads/" + branch, "sha", baseSha), token);
    }

    private void updateFile(GitHubRepository repository, String branch, String token, FixProposal proposal)
            throws IOException, InterruptedException {
        String path = proposal.artifact().path();
        JsonNode current = send(repositoryPath(repository) + "/contents/" + encodePath(path)
                + "?ref=" + encode(repository.baseBranch()), "GET", null, token);
        String existingSha = current.path("sha").asText();
        if (existingSha.isBlank()) {
            throw new IllegalStateException("The proposed artifact does not exist on the configured base branch");
        }
        Map<String, String> request = new LinkedHashMap<>();
        request.put("message", "fix: apply VigiDock security remediation");
        request.put("content", Base64.getEncoder().encodeToString(proposal.updatedContent().getBytes(StandardCharsets.UTF_8)));
        request.put("branch", branch);
        request.put("sha", existingSha);
        send(repositoryPath(repository) + "/contents/" + encodePath(path), "PUT", request, token);
    }

    private PullRequestResult openPullRequest(
            GitHubRepository repository, String branch, String token, RemediationPlan plan)
            throws IOException, InterruptedException {
        Map<String, String> request = Map.of(
                "title", "fix: VigiDock security remediation for " + plan.fixProposal().artifact().path(),
                "head", branch,
                "base", repository.baseBranch(),
                "body", pullRequestBody(plan));
        JsonNode response = send(repositoryPath(repository) + "/pulls", "POST", request, token);
        int number = response.path("number").asInt(0);
        String url = response.path("html_url").asText();
        if (number <= 0 || url.isBlank()) {
            throw new IllegalStateException("GitHub did not return pull request metadata");
        }
        return new PullRequestResult(number, url, branch, Instant.now());
    }

    private String pullRequestBody(RemediationPlan plan) {
        return "## VigiDock AI remediation\n\n"
                + "This pull request was generated from a policy-approved dry-run.\n\n"
                + "### Proposed changes\n"
                + String.join("\n", plan.fixProposal().explanations().stream().map(value -> "- " + value).toList());
    }

    private JsonNode send(String path, String method, Object payload, String token)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(GITHUB_API + path))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "vigidock-security-agent");
        if (payload == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("GitHub API returned HTTP " + response.statusCode() + ": " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    private static String repositoryPath(GitHubRepository repository) {
        return "/repos/" + encode(repository.owner()) + "/" + encode(repository.name());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encodePath(String path) {
        String[] segments = path.split("/", -1);
        StringBuilder encodedPath = new StringBuilder();
        for (int index = 0; index < segments.length; index++) {
            if (index > 0) {
                encodedPath.append('/');
            }
            encodedPath.append(encode(segments[index]));
        }
        return encodedPath.toString();
    }

    /**
     * Repository details for GitHub pull-request creation.
     */
    public record GitHubRepository(String owner, String name, String baseBranch, String headBranch) {
        public GitHubRepository {
            owner = required(owner, "owner");
            name = required(name, "name");
            baseBranch = required(baseBranch, "baseBranch");
            headBranch = headBranch == null || headBranch.isBlank()
                    ? "vigidock/autofix-" + Instant.now().toEpochMilli()
                    : headBranch.trim();
        }

        private static String required(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value.trim();
        }
    }

    /**
     * GitHub pull request metadata returned after a successful write.
     */
    public record PullRequestResult(int number, String url, String branch, Instant createdAt) {}
}
