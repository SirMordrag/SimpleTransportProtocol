import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class GroundLayer
{
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

    public static void start(int _localPort, Handler _handler) throws SocketException
    {
        if (handler != null)
            throw new IllegalStateException("GroundLayer is already started");
        handler = _handler;

        localSocket = new DatagramSocket(_localPort);
        receiver = new receiverThread(handler, localSocket, CONVERTER);

        receiver.setDaemon(true);
        receiver.start();
    }

    public static void send(String payload, SocketAddress destinationAddress)
    {
        if (Math.random() <= RELIABILITY)
        {
            try
            {
                byte[] data = payload.getBytes(CONVERTER);
                DatagramPacket packet = new DatagramPacket(data, data.length, destinationAddress);
                localSocket.send(packet);
            } catch (IOException ignored) {}
        }
    }

    public static void close()
    {
        receiver.interrupt();
        localSocket.close();
        handler = null;
    }
}


class receiverThread extends Thread implements Runnable
{
    Handler handler;
    DatagramSocket socket;
    Charset charset;

    public receiverThread(Handler handler, DatagramSocket socket, Charset charset)
    {
        this.handler = handler;
        this.socket = socket;
        this.charset = charset;
    }

    @Override
    public void run()
    {
        while(!Thread.interrupted())
        {
            try
            {
                DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
                socket.receive(packet);
                Message msg = new Message(getDataFromPacket(packet), getAddressFromPacket(packet));
                handler.receive(msg);
            }
            catch (IOException e)
            {
                interrupt();
            }

        }
    }

    String getDataFromPacket(DatagramPacket packet)
    {
        // src: https://stackoverflow.com/questions/10159732/how-to-retrieve-string-from-datagrampacket/41518150
        return new String(packet.getData(), packet.getOffset(), packet.getLength(), charset);
    }

    String getAddressFromPacket(DatagramPacket packet)
    {
        return packet.getSocketAddress().toString();
//        String address = "";
//        InetAddress inet_addr = packet.getAddress();
//        String ip = inet_addr.getHostAddress() ;
//        String hostname = inet_addr.getHostName();
//        String port = Integer.toString(packet.getPort());
//
//        if (hostname == null && ip == null)
//            error("Invalid address");
//
//        if (hostname != null)
//            address = hostname;
//        else
//            address = ip;
//
//        if (hostname != null && ip != null)
//            address += "/" + ip;
//
//        address += ":" + port;
//
//        return address;
    }

    // print to sys.err and terminate with err_code 1
    static void error(String err_msg, Exception except)
    {
        System.err.println("Error: " + err_msg);
        if (except != null)
        {
            System.err.println("This error was caused by the following exception:");
            System.err.println(except.getMessage());
            System.err.println("Stacktrace follows:");
            except.printStackTrace();
        }
        System.exit(1);
    }

    static void error(String err_msg)
    {
        error(err_msg, null);
    }
}
