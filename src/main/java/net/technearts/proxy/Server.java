package net.technearts.proxy;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Server implements Runnable {

  private static final Logger LOG = LogManager.getLogger();
  private static final String serverVersion = "0.0.1";

  private static final String httpVersion = "HTTP/1.1";
  private static final String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.113 Safari/537.36 ";
  private ServerSocket listen;

  private final boolean enableCookiesByDefault = true;

  private final int port = 8088;

  private InetAddress proxy; // localhost

  private final int proxyPort = 0; // 8080

  private final boolean useProxy = false;

  private final boolean filterHttp = false;

  private final boolean debug = true;

  public Server() {
    LOG.info("server startup...");
    String errorMsg = null;
    try {
      listen = new ServerSocket(port);
    } catch (final BindException e_bind_socket) {
      errorMsg = "Socket " + port
          + " is already in use (Another jHTTPp2 proxy running?) "
          + e_bind_socket.getMessage();
    } catch (final IOException e_io_socket) {
      errorMsg = "IO Exception while creating server socket on port " + port
          + ". " + e_io_socket.getMessage();
    } finally {
      if (errorMsg != null) {
        LOG.error(errorMsg);
        throw new IllegalArgumentException(errorMsg);
      }
    }
  }

  public boolean enableCookiesByDefault() {
    return enableCookiesByDefault;
  }

  public String getHttpVersion() {
    return httpVersion;
  }

  public int getPort() {
    return port;
  }

  public InetAddress getProxy() {
    return proxy;
  }

  public int getProxyPort() {
    return proxyPort;
  }

  public String getServerIdentification() {
    return "JProxy/" + getServerVersion();
  }

  public String getServerVersion() {
    return serverVersion;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public boolean isDebug() {
    return debug;
  }

  public boolean isFilterHttp() {
    return filterHttp;
  }

  public boolean isUseProxy() {
    return useProxy;
  }

  @Override
  public void run() {
    LOG.info("Server running.");
    try {
      while (true) {
        final Socket client = listen.accept();
        new Session(this, client);
      }
    } catch (final Exception e) {
      e.printStackTrace();
      LOG.error("Exception in Server.serve(): " + e.toString());
    }
  }
}
