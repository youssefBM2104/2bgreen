package com.twobgreen.gridintelligence.infrastructure.odre;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Stand-in for ODRÉ, bound to the loopback interface: tests never reach the network. Answers the scripted
 * responses in order and records every request it receives.
 */
final class FakeOdreServer implements AutoCloseable {

	private record Reply(int status, String body, Duration delay) {
	}

	private final HttpServer server;

	private final ExecutorService executor = Executors.newCachedThreadPool();

	private final Queue<Reply> replies = new ConcurrentLinkedQueue<>();

	private final List<URI> requests = new CopyOnWriteArrayList<>();

	FakeOdreServer() {
		try {
			server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		server.createContext("/", this::handle);
		server.setExecutor(executor);
		server.start();
	}

	/** Same path as the real API, so the client builds its URLs exactly as in production. */
	URI baseUrl() {
		return URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort()
				+ "/api/explore/v2.1");
	}

	FakeOdreServer reply(int status, String body) {
		return replyAfter(Duration.ZERO, status, body);
	}

	FakeOdreServer replyAfter(Duration delay, int status, String body) {
		replies.add(new Reply(status, body, delay));
		return this;
	}

	List<URI> requests() {
		return requests;
	}

	/** A committed ODRÉ response, from {@code src/test/resources/odre/}. */
	static String fixture(String name) {
		try (InputStream in = FakeOdreServer.class.getResourceAsStream("/odre/" + name)) {
			if (in == null) {
				throw new IllegalArgumentException("no fixture " + name);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void handle(HttpExchange exchange) throws IOException {
		requests.add(exchange.getRequestURI());
		// An unexpected request gets a status no test scripts, so it fails loudly instead of hanging
		Reply reply = replies.poll();
		if (reply == null) {
			reply = new Reply(418, "no scripted reply", Duration.ZERO);
		}
		try {
			Thread.sleep(reply.delay());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		}
		byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(reply.status(), body.length == 0 ? -1 : body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
	}

	@Override
	public void close() {
		server.stop(0);
		executor.shutdownNow();
	}
}
