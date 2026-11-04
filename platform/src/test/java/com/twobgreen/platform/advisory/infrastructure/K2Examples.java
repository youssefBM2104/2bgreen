package com.twobgreen.platform.advisory.infrastructure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The examples of contract K2, read from {@code docs/api/examples} by path and never copied. Maven runs from the module directory. */
final class K2Examples {

	private static final Path EXAMPLES = Path.of("..", "docs", "api", "examples").toAbsolutePath().normalize();

	private K2Examples() {
	}

	static String read(String name) {
		try {
			return Files.readString(EXAMPLES.resolve(name));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
