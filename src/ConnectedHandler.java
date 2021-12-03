import java.text.SimpleDateFormat;
import java.util.Date;
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

    // 0 for all, 5 for nothing; levels: 0 normal, 1 green, 2 yellow, 3 purple, 4 cyan
    static int DEBUG = 5;

    /**
     * the two following parameters are suitable for manual experimentation and
     * automatic validation
     *
     * delay before retransmitting a non acked message
     */
    private static final int DELAY = 1000;

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
        debug("LOCAL ID: " + this.local_ID, 4);
        send(HELLO);
        debug("Terminating constructor.", 2);
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
        debug("Got msg: " + message.toString(), 1);
        Pattern p = Pattern.compile("(.+?);(.+?);(.+?);(.*)");
        Matcher m = p.matcher(message.payload);

        if (m.find())
        {
            String senderID = m.group(1);
            String destinationID = m.group(2);
            int PN = Integer.parseInt(m.group(3));
            String payload = m.group(4);

            debug("SID: " + senderID + " DID: " + destinationID + " PN: " + PN + " PLD: " + payload);
            if (payload.equals(HELLO))
            {
                debug("Processing as HELLO.");
                if (destinationID.equals("-1") && PN == 0 && Integer.parseInt(senderID) >= 0) {
                    if (this.remote_ID == -1) {
                        synchronized (this.lock)
                        {
                            this.remote_ID = Integer.parseInt(senderID);
                        }
                        send(ACK);
                        synchronized (this.lock)
                        {
                            this.remote_packet_number = 1;
                        }
                    }
                    else if (this.remote_ID == Integer.parseInt(senderID)) {
                        synchronized (this.lock)
                        {
                            this.remote_packet_number = 0;
                        }
                        send(ACK);
                        synchronized (this.lock)
                        {
                            this.remote_packet_number = 1;
                        }
                    }
                }
            }
            else if (payload.equals(ACK) && check_IDs(destinationID, senderID) && this.remote_ID != -1)
            {

                if (PN == this.local_packet_number )
                {
                    debug("Processing as ACK OK.");
                    synchronized (this.lock)
                    {
                        this.local_packet_number++;
                        this.lock.notify();
                    }
                } else
                    debug("local_packet_number: " + this.local_packet_number);
                    drop_msg("Unexpected ACK PN");
            }
            else if (check_IDs(destinationID, senderID) && this.remote_ID != -1) // actual message
            {
                if (aboveHandler == null) return;
                if (PN == this.remote_packet_number - 1) // got last message
                {
                    debug("Processing as MSG OK.");
                    int temp = this.remote_packet_number;
                    synchronized (this.lock)
                    {
                        this.remote_packet_number = PN;
                    }
                    send(ACK);
                    synchronized (this.lock)
                    {
                        this.remote_packet_number = temp;
                    }
                }
                else if (PN == this.remote_packet_number) // got new message
                {
                    debug("Processing as MSG OLD.");
                    pass_msg_to_app(new Message(payload, Integer.toString(this.local_ID)));
                    send(ACK);
                    synchronized (this.lock)
                    {
                        this.remote_packet_number++;
                    }
                } else {
                    drop_msg("Unexpected MSG PN");
                }
            } else
                drop_msg("ID mismatch");
        } else
        {
            drop_msg("Invalid format");
        }
    }

    private boolean check_IDs(String destinationID, String senderID)
    {
        debug(destinationID + "/" + senderID + ": " + (destinationID.equals(this.local_ID + "")) + " " + ((senderID.equals(this.remote_ID + ""))));
        return (destinationID.equals(this.local_ID + "") && (senderID.equals(this.remote_ID + "")));
    }

    private void drop_msg(String reason)
    {
        debug("Message dropped: " + reason, 4);
    }

    private void pass_msg_to_app(Message msg)
    {
        debug("Message sent to APP: " + msg.toString(), 4);
        aboveHandler.receive(msg);
    }

    @Override
    public void send(final String payload)
    {
        debug("send() ThreadID: " + Thread.currentThread().getId());
        debug("Sending " + payload);

        int PN;
        if (payload.equals(ACK))
            PN = this.remote_packet_number;
        else
            PN = this.local_packet_number;

        // me; you; number; payload
        String send_payload = this.local_ID + ";" + this.remote_ID + ";" + PN + ";" + payload;

        // create a new task
        TimerTask task = new TimerTask()
        {
            int cnt = 0;

            @Override
            public void run()
            {
                cnt++;
                debug("TimerTask ThreadID: " + Thread.currentThread().getId());
                underHandler.send(send_payload, destination);
                debug("Sent msg: " + send_payload, 1);
                if (cnt > MAX_REPEAT && false) // max_cnt disabled for now
                {
                    debug("Maxed out, cancelling sending of " + send_payload, 4);
                    synchronized (lock)
                    {
                        lock.notify();
                    }
                    this.cancel();
                }
            }
        };

        // assign tasks

        if(payload.equals(ACK))
        {
            underHandler.send(send_payload, destination);
            return;
        }

        TIMER.schedule(task, 0, DELAY);
        while(this.local_packet_number == PN)
        {
            debug("Waiting");
            synchronized (this.lock)
            {
                try
                {
                    this.lock.wait();
                    debug("Notified");
                } catch (InterruptedException e)
                {
                    error("Interrupted!", e);
                }
            }
            task.cancel();
        }
    }

    @Override
    public void send(String payload, String destinationAddress)
    {
        no_send();
    }

    @Override
    public void close()
    {
        super.close();
    }

    //
    // MACROS
    //

    static void debug(String msg)
    {
        debug(msg, 0);
    }

    static void debug(String msg, int level)
    {
        String colour = "";
        switch (level)
        {
            case (1): colour = "\u001B[32m"; break; // green
            case (2): colour = "\u001B[33m"; break; // yellow
            case (3): colour = "\u001B[35m"; break; // purple
            case (4): colour = "\u001B[36m"; break; // cyan
        }

        if (level >= DEBUG)
        {
            String time = "[" + new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()) + "][con] ";
            System.out.println(colour + time + msg + "\u001B[0m");
        }
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