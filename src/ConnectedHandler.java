import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *       ConnectedHandler
 *       Authors:    VOSKA, Vojtech
 *                   WEIJUN, Huang
 *                   & Ecole staff
 *
 *       Date:       24.11.2021
 *
 *       Usage:
 *           see Talk_2
 */

public class ConnectedHandler extends Handler
{

    /**
     * @return an integer identifier, supposed to be unique.
     */
    public static int getUniqueID()
    {
        return (int) (Math.random() * Integer.MAX_VALUE);
    }

    // don't change the two following definitions
    private static final String HELLO = "--HELLO--";
    private static final String ACK = "--ACK--";

    /**
     * the two following parameters are suitable for manual experimentation and
     * automatic validation
     */
    static boolean DEBUG = true;
    /**
     * delay before retransmitting a non acked message
     */
    private static final int DELAY = 300;

    /**
     * number of times a non acked message is sent before timeout
     */
    private static final int MAX_REPEAT = 10;

    /**
     * A single Timer for all usages. Don't cancel it.
     **/
    private static final Timer TIMER = new Timer("ConnectedHandler's Timer", true);

    private final int local_ID;
    private int remote_ID;

    private volatile int local_packet_number; // the one I just sent to the Remote
    private volatile int remote_packet_number; // the last one I got from the Remote
    private final String destination;
    private Handler aboveHandler;
    private final Handler underHandler;
    private final Object lock;


    /**
     * Initializes a new connected handler with the specified parameters
     *
     * @param _under       the {@link Handler} on which the new handler will be stacked
     * @param _localId     the connection Id used to identify this connected handler
     * @param _destination a {@code String} identifying the destination
     */
    public ConnectedHandler(final Handler _under, int _localId, String _destination)
    {
        super(_under, _localId, true);
        this.local_ID = _localId;
        this.destination = _destination;
        this.remote_ID = -1;
        this.local_packet_number = 0;
        this.remote_packet_number = 0;
        this.underHandler = _under;
        this.lock = new Object();
        debug("LOCAL ID: " + this.local_ID);
        send(HELLO);

        while (this.remote_ID == -1)
        {
            // wait
        }
        debug("REMOTE ID: " + this.remote_ID);
    }

    // don't change this definition
    @Override
    public void bind(Handler above)
    {
        if (!this.upsideHandlers.isEmpty())
            throw new IllegalArgumentException("cannot bind a second handler onto this " + this.getClass().getName());
        this.aboveHandler = above;
        super.bind(above);
    }

    @Override
    public void handle(Message message)
    {
        debug("Got msg: " + message.toString());
        Pattern p = Pattern.compile("(.+?);(.+?);(.+?);(.*)");
        Matcher m = p.matcher(message.payload);

        if (m.find())
        {
            String senderID = m.group(1);
            String destinationID = m.group(2);
            int PN = Integer.parseInt(m.group(3));
            String payload = m.group(4);

            debug("SID: " + senderID + " DID: " + destinationID + " PN: " + PN + " PLD: " + payload);

            if (payload.equals(HELLO) && PN == 0)
            {
                debug("Processing as HELLO.");
                // get ID of the other guy
                this.remote_ID = Integer.parseInt(senderID);
                this.remote_packet_number = PN;
                synchronized (this.lock)
                {
                    this.lock.notify();
                }
                // ack the HELLO
                send(ACK);
            } else if (payload.equals(ACK) && check_IDs(destinationID, senderID))
            {
                if (PN == this.local_packet_number)
                {
                    debug("Processing as ACK OK.");
                    this.local_packet_number++;
                    synchronized (this.lock)
                    {
                        this.lock.notify();
                    }
                } else
                    drop_msg("Unexpected ACK PN");
            } else if (check_IDs(destinationID, senderID)) // actual message
            {

                if (PN == this.remote_packet_number + 1) // got next message
                {
                    debug("Processing as MSG OK.");
                    pass_msg_to_app(message);
                    this.remote_packet_number = PN;
                    send(ACK);
                } else if (PN == this.remote_packet_number) // got old message -> reACK
                {
                    debug("Processing as MSG OLD.");
                    send(ACK);
                } else
                    drop_msg("Unexpected MSG PN");
            } else
                drop_msg("ID mismatch");
        } else
        {
            drop_msg("Invalid format");
        }
    }

    private boolean check_IDs(String destinationID, String senderID)
    {
        debug(destinationID + "/" + senderID + ": " + (destinationID.equals(this.local_ID + "")) + " " + ((senderID.equals(this.remote_ID + ""))) + " " + (this.remote_ID == -1));
        return (destinationID.equals(this.local_ID + "") && (senderID.equals(this.remote_ID + "") || (this.remote_ID == -1)));
    }

    private void drop_msg(String reason)
    {
        debug("Message dropped: " + reason);
    }

    private void pass_msg_to_app(Message msg)
    {
        debug("Message sent to APP: " + msg.toString());
        aboveHandler.receive(msg);
    }

    @Override
    public void send(final String payload)
    {
        int PN;
        if (payload.equals(ACK))
            PN = this.remote_packet_number;
        else
            PN = this.local_packet_number;

        // me; you; number; payload
        String send_payload = this.local_ID + ";" + this.remote_ID + ";" + PN + ";" + payload;
        debug("Sent msg: " + send_payload);
        this.underHandler.send(send_payload, destination);

        // if we send ACK, we do not wait
        if (payload.equals(ACK))
            return;

        Handler t_under = this.underHandler;

        // create a new task
        TimerTask task = new TimerTask()
        {
            int cnt = 0;

            @Override
            public void run()
            {
                cnt += 1;
                t_under.send(send_payload, destination);
                debug("Resent: " + send_payload);
                if (cnt > MAX_REPEAT)
                {
                    Thread.currentThread().interrupt();
                }
            }
        };

        // assign tasks
        TIMER.schedule(task, DELAY, DELAY);
        while(this.local_packet_number == PN)
        {
            synchronized (this.lock)
            {
                try
                {
                    this.lock.wait();
                } catch (InterruptedException e)
                {
                    error("Interrupted!", e);
                }
            }
        }
        task.cancel();

        TIMER.purge();
    }

    @Override
    public void send(String payload, String destinationAddress)
    {
        no_send();
    }

    @Override
    public void close()
    {
//        TIMER.cancel();
        super.close();
    }

    //
    // MACROS
    //

    static void debug(String msg)
    {
        if (DEBUG)
            System.out.println(msg);
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