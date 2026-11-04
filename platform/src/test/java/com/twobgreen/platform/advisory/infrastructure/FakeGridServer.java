package com.twobgreen.platform.advisory.infrastructure;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Stand-in for grid-intelligence, bound to the loopback interface. Answers the scripted reply to every request. */
final class FakeGridServer implements AutoCloseable {

	private final HttpServer server;

	private final ExecutorService executor = Executors.newCachedThreadPool();

	private final List<URI> requests = new CopyOnWriteArrayList<>();

	private volatile int status = 418;

	private volatile String body = "no scripted reply";

	private volatile Duration delay = Duration.ZERO;

	FakeGridServer() {
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

	URI baseUrl() {
		return URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort());
	}

	FakeGridServer reply(int status, String body) {
		this.status = status;
		this.body = body;
		return this;
	}

	FakeGridServer replyAfter(Duration delay, int status, String body) {
		this.delay = delay;
		return reply(status, body);
	}

	List<URI> requests() {
		return requests;
	}

	private void handle(HttpExchange exchange) throws IOException {
		requests.add(exchange.getRequestURI());
		try {
			Thread.sleep(delay);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		}
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	@Override
	public void close() {
		server.stop(0);
		executor.shutdownNow();
	}
}
