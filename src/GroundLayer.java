import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class GroundLayer {

  /**
   * This {@code Charset} is used to convert between our Java native String
   * encoding and a chosen encoding for the effective payloads that fly over the
   * network.
   */
  private static final Charset CONVERTER = StandardCharsets.UTF_8;

  /**
   * This value is used as the probability that {@code send} really sends a
   * datagram. This allows to simulate the loss of packets in the network.
   */
  public static double RELIABILITY = 1.0;

  private static DatagramSocket localSocket = null;
  private static Thread receiver = null;
  private static Handler handler = null;

  public static void start(int _localPort, Handler _handler)
      throws SocketException {
    if (handler != null)
      throw new IllegalStateException("GroundLayer is already started");
    handler = _handler;
    // TO BE COMPLETED
    receiver.setDaemon(true);
    receiver.start();
  }

  public static void send(String payload, SocketAddress destinationAddress) {
    if (Math.random() <= RELIABILITY) {
      // MUST SEND
    }
  }

  public static void close() {
    // TO BE COMPLETED
    System.err.println("GroundLayer closed");
  }

}
