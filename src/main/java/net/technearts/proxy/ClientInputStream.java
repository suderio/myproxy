package net.technearts.proxy;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.technearts.proxy.Session.Status;

public class ClientInputStream extends BufferedInputStream {
  private static final Logger LOG = LogManager.getLogger();
  private static Server server;
  private String buffer;
  private int lread = 0;
  /**
   * The length of the header (with body, if one)
   */
  private int headerLength = 0;
  /**
   * The length of the (optional) body of the actual request
   */
  private int contentLength = 0;
  /**
   * This is set to true with requests with bodies, like "POST"
   */
  private boolean body = false;
  private final Session connection;
  private InetAddress remoteHost;
  private String remoteHostname;
  private boolean ssl = false;
  private String errorDescription;
  private Status statusCode;

  private String url;
  private String method;
  private int remotePort = 0;
  private int postLength = 0;
  private final boolean logAccess = true;

  public ClientInputStream(final Server server, final Session connection,
      final InputStream is) {
    super(is);
    ClientInputStream.server = server;
    this.connection = connection;
  }

  public String getErrorDescription() {
    return errorDescription;
  }

  public String getFullURL() {
    return "http" + (ssl ? "s" : "") + "://" + remoteHostname
        + (remotePort != 80 ? ":" + remotePort : "") + url;
  }

  public int getHeaderLength() {
    return headerLength;
  }

  private int getHttpMethod(final String d) {
    if (startsWith(d, "GET") || startsWith(d, "HEAD")) {
      return 0;
    }
    if (startsWith(d, "POST") || startsWith(d, "PUT")) {
      return 1;
    }
    if (startsWith(d, "CONNECT")) {
      return 2;
    }
    if (startsWith(d, "OPTIONS")) {
      return 3;
    }
    /*
     * No match...
     *
     * Following methods are not implemented: "TRACE"
     */
    return -1;
  }

  /**
   * reads a line
   *
   * @exception IOException
   */
  public String getLine() throws IOException {
    int l = 0;
    String line = "";
    lread = 0;
    while (l != '\n') {
      l = read();
      if (l != -1) {
        line += (char) l;
        lread++;
      } else {
        break;
      }
    }
    return line;
  }

  public InetAddress getRemoteHost() {
    return remoteHost;
  }

  public int getRemotePort() {
    return remotePort;
  }

  /**
   * @return status-code for the actual request
   */
  public Status getStatusCode() {
    return statusCode;
  }

  /**
   * @return boolean whether the actual connection was established with the
   *         CONNECT method.
   */
  public boolean isSSL() {
    return ssl;
  }

  private InetAddress parseRequest(final String a) {
    if (server.isDebug()) {
      LOG.debug(a);
    }
    String f;
    int pos;
    url = "";
    if (ssl) {
      f = a.substring(8);
    } else {
      method = a.substring(0, a.indexOf(" ")); // first word in the line
      pos = a.indexOf(":"); // locate first :
      f = a.substring(pos + 3); // removes "http://"
    }
    pos = f.indexOf(" "); // locate space, should be the space before "HTTP/1.1"
    if (pos == -1) { // buggy request
      statusCode = Status.SC_CLIENT_ERROR;
      errorDescription = "Your browser sent an invalid request: \"" + a + "\"";
      return null;
    }
    f = f.substring(0, pos); // removes all after space
    /*
     * if the url contains a space... it's not our mistake...(url's must never
     * contain a space character)
     */
    pos = f.indexOf("/"); // locate the first slash
    if (pos != -1) {
      url = f.substring(pos); // saves path without hostname
      f = f.substring(0, pos); // reduce string to the hostname
    } else {
      url = "/"; // occurs with this request: "GET http://localhost HTTP/1.1"
    }
    pos = f.indexOf(":"); // check for the portnumber
    if (pos != -1) {
      String l_port = f.substring(pos + 1);
      l_port = l_port.indexOf(" ") != -1
          ? l_port.substring(0, l_port.indexOf(" "))
          : l_port;
      int i_port = 80;
      try {
        i_port = Integer.parseInt(l_port);
      } catch (final NumberFormatException e_get_host) {
        LOG.error("get_Host :" + e_get_host + " !!!!");
      }
      f = f.substring(0, pos);
      remotePort = i_port;
    } else {
      remotePort = 80;
    }
    remoteHostname = f;
    InetAddress address = null;
    logAccess(connection.getLocalSocket().getInetAddress().getHostAddress()
        + " " + method + " " + getFullURL());
    try {
      address = InetAddress.getByName(f);
    } catch (final UnknownHostException e_u_host) {
      if (!server.isUseProxy()) {
        statusCode = Status.SC_HOST_NOT_FOUND;
      }
    }
    return address;
  }

  @Override
  public int read(final byte[] a) throws IOException {
    statusCode = Status.SC_OK;
    if (ssl) {
      return super.read(a);
    }
    final boolean cookies_enabled = server.enableCookiesByDefault();
    String rq = "";
    headerLength = 0;
    postLength = 0;
    contentLength = 0;
    boolean start_line = true;
    buffer = getLine(); // reads the first line

    while (lread > 2) {
      if (start_line) {
        start_line = false;
        final int methodID = getHttpMethod(buffer);
        switch (methodID) {
        case -1:
          statusCode = Status.SC_NOT_SUPPORTED;
          break;
        case 2:
          ssl = true;
        default:
          final InetAddress host = parseRequest(buffer);
          if (!statusCode.equals(Status.SC_OK)) {
            break; // error occured, go on with the next line
          }

          if (!server.isUseProxy() && !ssl) {
            /* creates a new request without the hostname */
            buffer = method + " " + url + " " + server.getHttpVersion()
                + "\r\n";
            lread = buffer.length();
          }
          if (server.isUseProxy() && connection.notConnected()
              || !host.equals(remoteHost)) {
            if (server.isDebug()) {
              LOG.debug("read_f: STATE_CONNECT_TO_NEW_HOST");
            }
            statusCode = Status.SC_CONNECTING_TO_HOST;
            remoteHost = host;
          }
        }
      } else {
        /*-----------------------------------------------
        * Content-Length parsing
        *-----------------------------------------------*/
        if (startsWith(buffer.toUpperCase(), "CONTENT-LENGTH")) {
          String clen = buffer.substring(16);
          if (clen.indexOf("\r") != -1) {
            clen = clen.substring(0, clen.indexOf("\r"));
          } else if (clen.indexOf("\n") != -1) {
            clen = clen.substring(0, clen.indexOf("\n"));
          }
          try {
            contentLength = Integer.parseInt(clen);
          } catch (final NumberFormatException e) {
            statusCode = Status.SC_CLIENT_ERROR;
          }
          if (server.isDebug()) {
            LOG.debug("read_f: contentLength: " + contentLength);
          }
          if (!ssl) {
            body = true; // Note: in HTTP/1.1 any method can have a body, not
                         // only "POST"
          }
        } else if (startsWith(buffer, "Proxy-Connection:")) {
          if (!server.isUseProxy()) {
            buffer = null;
          } else {
            buffer = "Proxy-Connection: Keep-Alive\r\n";
            lread = buffer.length();
          }
        }
        /*-----------------------------------------------
        * cookie crunch section
         *-----------------------------------------------*/
        else if (startsWith(buffer, "Cookie:")) {
          if (!cookies_enabled) {
            buffer = null;
          }
        }
        /*------------------------------------------------
         * Http-Header filtering section
         *------------------------------------------------*/
        else if (server.isFilterHttp()) {
          if (startsWith(buffer, "Referer:")) {// removes "Referer"
            buffer = null;
          } else if (startsWith(buffer, "User-Agent")) {
            // changes User-Agent
            buffer = "User-Agent: " + server.getUserAgent() + "\r\n";
            lread = buffer.length();
          }
        }
      }
      if (buffer != null) {
        rq += buffer;
        if (server.isDebug()) {
          LOG.debug(buffer);
        }
        headerLength += lread;
      }
      buffer = getLine();
    }
    rq += buffer; // adds last line (should be an empty line) to the header
                  // String
    headerLength += lread;

    if (headerLength == 0) {
      if (server.isDebug()) {
        LOG.debug(
            "headerLength=0, setting status to SC_CONNECTION_CLOSED (buggy request)");
      }
      statusCode = Status.SC_CONNECTION_CLOSED;
    }

    for (int i = 0; i < headerLength; i++) {
      a[i] = (byte) rq.charAt(i);
    }

    if (body) {// read the body, if "Content-Length" given
      postLength = 0;
      while (postLength < contentLength) {
        // writes data into the array
        a[headerLength + postLength] = (byte) read();
        postLength++;
      }
      headerLength += contentLength; // add the body-length to the header-length
      body = false;
    }
    // return -1 with an error
    return statusCode.equals(Status.SC_OK) ? headerLength : -1;
  }

  private boolean startsWith(final String a, final String what) {
    final int l = what.length();
    final int l2 = a.length();
    return l2 >= l ? a.substring(0, l).equals(what) : false;
  }

  /**
   * faz log de todos os acessos TODO configurar log
   */
  private void logAccess(final String s) {
    if (logAccess) {
      try {
        LOG.trace("[" + new Date().toString() + "] " + s + "\r\n");
      } catch (final Exception e) {
        LOG.trace("Server.access(String): " + e.getMessage());
      }
    }
  }
}
