package dev.minted.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * A tiny Redis pub/sub client: just enough of the RESP protocol to subscribe
 * to one channel, announce uuids on it and read the announcements other
 * servers publish. It exists so cross-server balance notifications cost zero
 * new dependencies and zero shaded bytes.
 *
 * <p>Everything network-facing happens on one daemon thread: it connects,
 * authenticates, subscribes, then alternates between publishing queued uuids
 * and reading messages (with a short socket timeout so the loop stays
 * responsive). A dropped connection reconnects after a fixed pause. Publishing
 * is a non-blocking queue offer, so any thread may announce.
 *
 * <p>Failure is never fatal: when Redis is absent or unreachable this class
 * simply never reports a connection, and the network coordinator keeps the
 * database-delta guarantees on its own.
 */
public final class RedisSync {

    private static final int RECONNECT_MILLIS = 5000;
    private static final int READ_TIMEOUT_MILLIS = 250;

    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final int database;
    private final String channel;
    private final Consumer<UUID> handler;
    private final Logger log;

    private final BlockingQueue<String> pending = new LinkedBlockingQueue<String>(512);
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Socket socket;
    private volatile OutputStream out;
    private volatile boolean connected;
    private volatile String lastError;

    private RedisSync(String host, int port, String user, String password, int database,
                      String channel, Consumer<UUID> handler, Logger log) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.database = database;
        this.channel = channel;
        this.handler = handler;
        this.log = log;
    }

    /**
     * Builds a client from {@code redis://[user:password@]host:port[/db]}.
     *
     * @throws IllegalArgumentException when the URI cannot be understood
     */
    public static RedisSync create(String uri, String channel, Consumer<UUID> handler, Logger log) {
        String rest = uri.trim();
        if (rest.startsWith("redis://")) {
            rest = rest.substring("redis://".length());
        } else if (rest.startsWith("rediss://")) {
            throw new IllegalArgumentException("rediss:// (TLS) is not supported; use a local redis or a proxy");
        }
        String user = null;
        String password = null;
        int at = rest.lastIndexOf('@');
        if (at >= 0) {
            String credentials = rest.substring(0, at);
            rest = rest.substring(at + 1);
            int colon = credentials.indexOf(':');
            if (colon >= 0) {
                user = decode(credentials.substring(0, colon));
                password = decode(credentials.substring(colon + 1));
            } else {
                password = decode(credentials);
            }
        }
        int database = 0;
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            try {
                database = Integer.parseInt(rest.substring(slash + 1));
            } catch (NumberFormatException ignored) {
                database = 0;
            }
            rest = rest.substring(0, slash);
        }
        int colon = rest.lastIndexOf(':');
        String host = colon >= 0 ? rest.substring(0, colon) : rest;
        int port = 6379;
        if (colon >= 0) {
            try {
                port = Integer.parseInt(rest.substring(colon + 1));
            } catch (NumberFormatException ignored) {
                port = 6379;
            }
        }
        if (host.isEmpty()) {
            host = "localhost";
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range in '" + uri + "'");
        }
        return new RedisSync(host, port, user, password, database, channel, handler, log);
    }

    /** Starts the background thread; safe to call once. */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "Minted-Redis");
        thread.setDaemon(true);
        thread.start();
    }

    /** Queues an announcement. Never blocks, safe from any thread. */
    public void publish(UUID uuid) {
        if (running.get()) {
            pending.offer(uuid.toString());
        }
    }

    public boolean isConnected() {
        return connected;
    }

    /** The last connection problem, or null when healthy/absent. */
    public String lastError() {
        return lastError;
    }

    public void stop() {
        running.set(false);
        closeQuietly();
    }

    // --- the connection loop ---------------------------------------------------

    private void loop() {
        byte[] buffer = new byte[8192];
        int filled = 0;
        while (running.get()) {
            try {
                connect();
                while (running.get() && connected) {
                    publishPending();
                    filled = readAndParse(buffer, filled);
                }
            } catch (Throwable failure) {
                lastError = failure.getClass().getSimpleName()
                        + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
            } finally {
                closeQuietly();
            }
            if (running.get()) {
                sleep(RECONNECT_MILLIS);
            }
        }
    }

    private void connect() throws IOException {
        Socket fresh = new Socket();
        fresh.setSoTimeout(READ_TIMEOUT_MILLIS);
        fresh.connect(new InetSocketAddress(host, port), 5000);
        socket = fresh;
        out = fresh.getOutputStream();
        connected = true;
        lastError = null;
        if (password != null) {
            if (user != null) {
                command("AUTH", user, password);
            } else {
                command("AUTH", password);
            }
        }
        if (database > 0) {
            command("SELECT", String.valueOf(database));
        }
        command("SUBSCRIBE", channel);
    }

    private void publishPending() throws IOException {
        String uuid = pending.poll();
        if (uuid == null || out == null) {
            return;
        }
        command("PUBLISH", channel, uuid);
    }

    /** Writes one RESP command; only ever called from the worker thread. */
    private void command(String... parts) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append('*').append(parts.length).append("\r\n");
        for (String part : parts) {
            byte[] raw = part.getBytes("UTF-8");
            builder.append('$').append(raw.length).append("\r\n").append(part).append("\r\n");
        }
        OutputStream stream = out;
        if (stream == null) {
            throw new IOException("not connected");
        }
        stream.write(builder.toString().getBytes("UTF-8"));
        stream.flush();
    }
    // --- RESP reading ----------------------------------------------------------

    /** @return the new fill level of {@code buffer} */
    private int readAndParse(byte[] buffer, int filled) throws IOException {
        Socket current = socket;
        if (current == null) {
            throw new IOException("not connected");
        }
        InputStream in = current.getInputStream();
        try {
            int read = in.read(buffer, filled, buffer.length - filled);
            if (read < 0) {
                throw new IOException("Redis closed the connection");
            }
            filled += read;
        } catch (SocketTimeoutException nothingYet) {
            // No data in this slice; fall through and parse what we have.
        }
        return parse(buffer, filled);
    }

    /** Consumes every complete frame in the buffer; a partial frame waits. */
    private int parse(byte[] buffer, int filled) throws IOException {
        int offset = 0;
        while (offset < filled) {
            char type = (char) (buffer[offset] & 0xFF);
            if (type == '+' || type == '-' || type == ':') {
                int line = endOfLine(buffer, offset, filled);
                if (line < 0) {
                    break;
                }
                offset = line;
                continue;
            }
            if (type != '*') {
                throw new IOException("Unexpected RESP frame: " + type);
            }
            Integer count = readIntLine(buffer, offset, filled);
            if (count == null) {
                break;
            }
            int cursor = offset;
            String[] bulks = new String[count];
            boolean complete = true;
            for (int i = 0; i < count; i++) {
                if (cursor >= filled || buffer[cursor] != '$') {
                    complete = false;
                    break;
                }
                Integer length = readIntLine(buffer, cursor, filled);
                if (length == null) {
                    complete = false;
                    break;
                }
                int end = cursor + length;
                if (end + 2 > filled) {
                    complete = false;
                    break;
                }
                bulks[i] = new String(buffer, cursor, length, StandardCharsets.UTF_8);
                cursor = end + 2;
            }
            if (!complete) {
                break;
            }
            if (count == 3 && "message".equals(bulks[0])) {
                deliver(bulks[2]);
            }
            offset = cursor;
        }
        if (offset > 0) {
            System.arraycopy(buffer, offset, buffer, 0, filled - offset);
            filled -= offset;
        }
        return filled;
    }

    private void deliver(String payload) {
        try {
            handler.accept(UUID.fromString(payload));
        } catch (IllegalArgumentException notAUuid) {
            // Ignore anything we did not publish ourselves.
        }
    }

    /** @return the index just past the CRLF, or -1 when it is not there yet */
    private int endOfLine(byte[] buffer, int offset, int filled) {
        for (int i = offset; i + 1 < filled; i++) {
            if (buffer[i] == '\r' && buffer[i + 1] == '\n') {
                return i + 2;
            }
        }
        return -1;
    }

    /** @return the integer on the line starting at {@code offset}, or null if incomplete */
    private Integer readIntLine(byte[] buffer, int offset, int filled) {
        int end = endOfLine(buffer, offset, filled);
        if (end < 0) {
            return null;
        }
        String digits = new String(buffer, offset + 1, end - offset - 3, StandardCharsets.UTF_8);
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    // --- plumbing --------------------------------------------------------------

    private void closeQuietly() {
        connected = false;
        Socket current = socket;
        socket = null;
        out = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // Closing a dead socket is fine.
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (IOException unsupported) {
            return value;
        } catch (RuntimeException bad) {
            return value;
        }
    }
}