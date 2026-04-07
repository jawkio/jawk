package io.jawk.jrt;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * Jawk
 * ჻჻჻჻჻჻
 * Copyright (C) 2006 - 2026 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Text {@link AwkSink} backed by an {@link Appendable}.
 */
public final class AppendableAwkSink extends AwkSink {

	private final Appendable appendable;
	private final PrintStream printStream;

	/**
	 * Creates a sink backed by an {@link Appendable}.
	 *
	 * @param appendableParam appendable that should receive AWK output
	 */
	public AppendableAwkSink(Appendable appendableParam) {
		this(appendableParam, Locale.US);
	}

	/**
	 * Creates a sink backed by an {@link Appendable}.
	 *
	 * @param appendableParam appendable that should receive AWK output
	 * @param locale locale used for numeric formatting
	 */
	public AppendableAwkSink(Appendable appendableParam, Locale locale) {
		super(locale);
		this.appendable = Objects.requireNonNull(appendableParam, "appendable");
		try {
			this.printStream = new PrintStream(
					new AppendableOutputStream(appendable),
					true,
					StandardCharsets.UTF_8.name());
		} catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void print(String ofs, String ors, String ofmt, Object... values) throws IOException {
		for (int i = 0; i < values.length; i++) {
			appendable.append(formatPrintArgument(values[i], ofmt));
			if (i < values.length - 1) {
				appendable.append(ofs);
			}
		}
		appendable.append(ors);
	}

	@Override
	public void printf(String ofs, String ors, String ofmt, String format, Object... values)
			throws IOException {
		appendable.append(formatPrintfResult(format, values));
	}

	@Override
	public void flush() throws IOException {
		if (appendable instanceof Flushable) {
			((Flushable) appendable).flush();
		}
	}

	@Override
	@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Callers need the live PrintStream used for process output pumping.")
	public PrintStream getPrintStream() {
		return printStream;
	}

	private static final class AppendableOutputStream extends OutputStream {

		private final Appendable appendable;
		private final CharsetDecoder decoder;
		private byte[] trailingBytes = new byte[0];

		private AppendableOutputStream(Appendable appendableParam) {
			this.appendable = appendableParam;
			this.decoder = StandardCharsets.UTF_8.newDecoder();
		}

		@Override
		public void write(int value) throws IOException {
			write(new byte[] { (byte) value }, 0, 1);
		}

		@Override
		public void write(byte[] bytes, int off, int len) throws IOException {
			// Merge with any trailing incomplete UTF-8 sequence from the previous call
			ByteBuffer input;
			if (trailingBytes.length > 0) {
				byte[] merged = new byte[trailingBytes.length + len];
				System.arraycopy(trailingBytes, 0, merged, 0, trailingBytes.length);
				System.arraycopy(bytes, off, merged, trailingBytes.length, len);
				input = ByteBuffer.wrap(merged);
			} else {
				input = ByteBuffer.wrap(bytes, off, len);
			}

			CharBuffer output = CharBuffer.allocate(input.remaining());
			decoder.decode(input, output, false);
			output.flip();
			if (output.hasRemaining()) {
				appendable.append(output);
			}

			// Save any remaining bytes (incomplete trailing sequence)
			if (input.hasRemaining()) {
				trailingBytes = new byte[input.remaining()];
				input.get(trailingBytes);
			} else {
				trailingBytes = new byte[0];
			}
		}

		@Override
		public void flush() throws IOException {
			if (appendable instanceof Flushable) {
				((Flushable) appendable).flush();
			}
		}

		@Override
		public void close() throws IOException {
			// Finalize any remaining trailing bytes
			if (trailingBytes.length > 0) {
				ByteBuffer input = ByteBuffer.wrap(trailingBytes);
				CharBuffer output = CharBuffer.allocate(trailingBytes.length);
				decoder.decode(input, output, true);
				decoder.flush(output);
				output.flip();
				if (output.hasRemaining()) {
					appendable.append(output);
				}
				trailingBytes = new byte[0];
			}
			flush();
		}
	}
}
