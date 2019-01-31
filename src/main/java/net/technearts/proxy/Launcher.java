package net.technearts.proxy;

import java.io.File;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Launcher {

  static Server server;
  private static final Logger LOG = LogManager.getLogger();
  public static void main(final String[] args) {
    Configurations configs = new Configurations();
    try {
      // TODO passar todas as configurações via properties
      Configuration config = configs.properties(new File("server.properties"));

      server = new Server();
      new Thread(server).start();
      LOG.info("Running on port " + server.getPort());
    } catch (final IllegalArgumentException e) {
      LOG.error("Error: " + e.getMessage());
    } catch (ConfigurationException e) {
      LOG.error("Error: " + e.getMessage());
    }
  }
}
