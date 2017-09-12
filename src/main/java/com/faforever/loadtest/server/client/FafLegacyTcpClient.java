package com.faforever.loadtest.server.client;

import com.google.common.io.ByteStreams;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Component
public class FafLegacyTcpClient {

  private static final Charset CHARSET = StandardCharsets.UTF_16BE;

  public void write(OutputStream out, InputStream inputStream) throws IOException {
    byte[] bytes = ByteStreams.toByteArray(inputStream);

    writeInt32(out, bytes.length);
    out.write(bytes);
  }

  public void write(OutputStream out, String s) throws IOException {
    if (s == null) {
      writeInt32(out, -1);
      return;
    }

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    appendWithSize(byteArrayOutputStream, s.getBytes(CHARSET));
    appendWithSize(out, byteArrayOutputStream.toByteArray());
    out.flush();
  }

  public String read(InputStream inputStream) throws IOException {
    byte[] buffer = new byte[0];
    int responseSize = readInt(inputStream);
    int stringSize = readInt(inputStream);
    if (stringSize == -1) {
      return null;
    }
    buffer = new byte[stringSize];
    readFully(inputStream, buffer, 0, buffer.length);
    return new String(buffer, CHARSET);
  }

  /**
   * Appends the size of the given byte array to the stream followed by the byte array itself.
   */
  private void appendWithSize(OutputStream out, byte[] bytes) throws IOException {
    writeInt32(out, bytes.length);
    out.write(bytes);
  }

  private void writeInt32(OutputStream out, int v) throws IOException {
    out.write((v >>> 24) & 0xFF);
    out.write((v >>> 16) & 0xFF);
    out.write((v >>> 8) & 0xFF);
    out.write(v & 0xFF);
  }

  private int readInt(InputStream inputStream) throws IOException {
    int ch1 = inputStream.read();
    int ch2 = inputStream.read();
    int ch3 = inputStream.read();
    int ch4 = inputStream.read();
    if ((ch1 | ch2 | ch3 | ch4) < 0) {
      throw new EOFException();
    }
    return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
  }

  private void readFully(InputStream inputStream, byte b[], int off, int len) throws IOException {
    if (len < 0) {
      throw new IndexOutOfBoundsException();
    }
    int n = 0;
    while (n < len) {
      int count = inputStream.read(b, off + n, len - n);
      if (count < 0) {
        throw new EOFException();
      }
      n += count;
    }
  }
}
