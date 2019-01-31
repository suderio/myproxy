package net.technearts.proxy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;

import net.technearts.proxy.Session.Status;

public class Reader extends Thread {
  private final int BUFFER_SIZE = 96000;
  private final BufferedInputStream in;
  private final BufferedOutputStream out;
  private final Session connection;

  public Reader(final Session connection, final BufferedInputStream in,
      final BufferedOutputStream out) {
    this.in = in;
    this.out = out;
    this.connection = connection;
    setPriority(Thread.MIN_PRIORITY);
    start();
  }

  public void close() {
    try {
      in.close();
    } catch (final Exception e) {}
  }

  @Override
  public void run() {
    int bytes_read = 0;
    final byte[] buf = new byte[BUFFER_SIZE];
    try {
      while (true) {
        bytes_read = in.read(buf);
        if (bytes_read != -1) {
          out.write(buf, 0, bytes_read);
          out.flush();
        } else {
          break;
        }
      }
    } catch (final IOException e) {}

    try {
      // *uaaahhh*: fixes a very strange bug
      if (!connection.getStatus().equals(Status.SC_CONNECTING_TO_HOST)) {
        connection.getLocalSocket().close();
        /*
         * why? If we are connecting to a new host (and this thread is already
         * running!) , the upstream socket will be closed. So we get here and
         * close our own downstream socket..... and the browser displays an
         * empty page because jhttpp2 closes the connection..... so close the
         * downstream socket only when NOT connecting to a new host....
         */
      }
    } catch (final IOException e_socket_close) {}
  }
}
