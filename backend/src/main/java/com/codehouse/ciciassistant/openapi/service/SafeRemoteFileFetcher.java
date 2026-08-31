package com.codehouse.ciciassistant.openapi.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SafeRemoteFileFetcher {

    static final long MAX_BYTES = 15L * 1024L * 1024L;
    private static final int MAX_REDIRECTS = 3;
    private final HttpClient client;

    public SafeRemoteFileFetcher() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    SafeRemoteFileFetcher(HttpClient client) {
        this.client = client;
    }

    public FetchedFile fetch(String rawUrl, String suggestedName) {
        URI current = requireSafeUri(rawUrl);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            requirePublicHost(current);
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "image/png,image/jpeg,image/webp,text/plain,text/markdown,text/csv,application/json,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    .header("User-Agent", "AgentCiCi-OpenAPI-FileFetcher/1.0")
                    .GET()
                    .build();
            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    closeQuietly(response.body());
                    if (redirects == MAX_REDIRECTS) {
                        throw fetchFailed("Remote file exceeded the redirect limit");
                    }
                    String location = response.headers().firstValue("Location")
                            .orElseThrow(() -> fetchFailed("Remote redirect did not include a location"));
                    current = requireSafeUri(current.resolve(location).toString());
                    continue;
                }
                if (status < 200 || status >= 300) {
                    closeQuietly(response.body());
                    throw fetchFailed("Remote file returned a non-success status");
                }
                long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                if (declaredLength > MAX_BYTES) {
                    closeQuietly(response.body());
                    throw new AgentOpenApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Remote file exceeds 15MB");
                }
                byte[] bytes = readBounded(response.body());
                String contentType = response.headers().firstValue("Content-Type")
                        .map(value -> value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT))
                        .orElse("application/octet-stream");
                String filename = safeName(suggestedName, current);
                return new FetchedFile(bytes, filename, contentType, current.getHost());
            } catch (AgentOpenApiException exception) {
                throw exception;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw fetchFailed("Remote file download was interrupted");
            } catch (IOException exception) {
                throw fetchFailed("Remote file download failed");
            }
        }
        throw fetchFailed("Remote file download failed");
    }

    static URI requireSafeUri(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl == null ? "" : rawUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null || uri.getHost().isBlank()
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_URL", "File URL must be a public HTTPS URL");
            }
            return uri.normalize();
        } catch (RuntimeException exception) {
            if (exception instanceof AgentOpenApiException apiException) {
                throw apiException;
            }
            throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_URL", "File URL is invalid");
        }
    }

    static void requirePublicAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        boolean forbidden = address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            forbidden = forbidden
                    || first == 0 || first >= 224
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 192 && second == 0)
                    || (first == 192 && second == 0 && (bytes[2] & 0xff) == 2)
                    || (first == 198 && (second == 18 || second == 19))
                    || (first == 198 && second == 51 && (bytes[2] & 0xff) == 100)
                    || (first == 203 && second == 0 && (bytes[2] & 0xff) == 113);
        } else if (bytes.length == 16) {
            int first = bytes[0] & 0xff;
            forbidden = forbidden || (first & 0xfe) == 0xfc
                    || (first == 0x20 && (bytes[1] & 0xff) == 0x01
                    && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8);
        }
        if (forbidden) {
            throw new AgentOpenApiException(HttpStatus.FORBIDDEN, "REMOTE_URL_FORBIDDEN", "Remote file host is not allowed");
        }
    }

    private void requirePublicHost(URI uri) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            if (addresses.length == 0) {
                throw fetchFailed("Remote file host could not be resolved");
            }
            for (InetAddress address : addresses) {
                requirePublicAddress(address);
            }
        } catch (AgentOpenApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw fetchFailed("Remote file host could not be resolved");
        }
    }

    private byte[] readBounded(InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                total += read;
                if (total > MAX_BYTES) {
                    throw new AgentOpenApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Remote file exceeds 15MB");
                }
                output.write(buffer, 0, read);
            }
            if (total == 0) {
                throw fetchFailed("Remote file is empty");
            }
            return output.toByteArray();
        }
    }

    private static String safeName(String suggestedName, URI uri) {
        String name = suggestedName == null ? "" : suggestedName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[\\r\\n\\t]", " ").trim();
        if (name.isBlank()) {
            String path = uri.getPath() == null ? "" : uri.getPath();
            int pathSlash = path.lastIndexOf('/');
            name = pathSlash >= 0 ? path.substring(pathSlash + 1) : path;
        }
        if (name.isBlank()) name = "remote-file";
        return name.length() <= 255 ? name : name.substring(0, 255);
    }

    private static AgentOpenApiException fetchFailed(String message) {
        return new AgentOpenApiException(HttpStatus.UNPROCESSABLE_ENTITY, "REMOTE_FILE_FETCH_FAILED", message);
    }

    private static void closeQuietly(InputStream input) {
        if (input == null) return;
        try {
            input.close();
        } catch (IOException ignored) {
        }
    }

    public record FetchedFile(byte[] bytes, String name, String declaredMimeType, String host) {
    }
}
