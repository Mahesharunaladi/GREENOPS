package com.greenops.workflow.spire;

import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.net.StandardProtocolFamily;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Checks whether the configured SPIRE Agent Workload API Unix socket accepts connections. */
@Component
public final class SpireAgentSocketProbe {
    private final String socketPath;

    public SpireAgentSocketProbe(@Value("${greenops.spire.agent-socket:}") String socketPath) {
        this.socketPath = socketPath == null ? "" : socketPath.trim();
    }

    public boolean isConfigured() { return !socketPath.isBlank(); }

    public boolean isReachable() {
        if (!isConfigured()) return false;
        try {
            Path path = Path.of(socketPath);
            if (!Files.exists(path)) return false;
            try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
                channel.configureBlocking(false);
                return channel.connect(UnixDomainSocketAddress.of(path));
            }
        } catch (Exception ignored) {
            return false;
        }
    }
}
