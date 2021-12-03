  import javax.swing.*;
  import java.text.SimpleDateFormat;
  import java.util.ArrayList;
  import java.util.Date;
  import java.util.concurrent.ArrayBlockingQueue;
  import java.util.concurrent.ConcurrentHashMap;
  import java.util.regex.Matcher;
  import java.util.regex.Pattern;

  /**
 *       DispatchingHandler
 *       Authors:    VOSKA, Vojtech
 *                   WEIJUN, Huang
 *                   & Ecole staff
 *
 *       Date:       29.11.2021
 *
 *       Usage:
 *           see FileServer + ReceiverClient + SenderClient
 */

public class DispatchingHandler extends Handler
{

    /** one will need it */
    private static final String HELLO = "--HELLO--";
    private static final String ACK = "--ACK--";

    /** An arbitrary base value for the numbering of handlers. **/
    private static int counter = 35000;

    /** the queue for pending connections */
    private final ArrayBlockingQueue<ConnectionParameters> queue;

    private final ArrayList<Integer> established_connection_queue;
    private final ConcurrentHashMap<Integer, Integer> communication_partners;

    private static final int DEBUG = 5;

    /**
     * Initializes a new dispatching handler with the specified parameters
     *
     * @param _under
     *                         the {@link Handler} on which the new handler will
     *                         be stacked
     * @param _queueCapacity
     *                         the capacity of the queue of pending connections
     */
    public DispatchingHandler(final Handler _under, int _queueCapacity)
    {
        super(_under, ++counter, false);
        this.queue = new ArrayBlockingQueue<ConnectionParameters>(_queueCapacity);
        this.established_connection_queue = new ArrayList<>();
        this.communication_partners = new ConcurrentHashMap<>();
    }

    /**
     * Retrieves and removes the head of the queue of pending connections, waiting
     * if no elements are present on this queue.
     *
     * @return the connection parameters record at the head of the queue
     * @throws InterruptedException
     *                                if the calling thread is interrupted while
     *                                waiting
     */
    public ConnectionParameters accept() throws InterruptedException
    {
        return this.queue.take();
    }

    @Override
    public void send(String payload)
    {
        no_send();
    }

    @Override
    protected void send(String payload, String destinationAddress)
    {
        this.downside.send(payload, destinationAddress);
    }

    @Override
    public void handle(Message message)
    {
        debug("Got msg to dispatch: " + message.toString(), 2);

        Pattern p = Pattern.compile("(.+?);(.+?);(.+?);(.*)");
        Matcher m = p.matcher(message.payload);

        if (m.find())
        {
            int msg_senderID = Integer.parseInt(m.group(1));
            int msg_destinationID = Integer.parseInt(m.group(2));
            int msg_pn = Integer.parseInt(m.group(3));
            String msg_payload = m.group(4);

            // Case: first HELLO
            // It is a HELLO & I don't know the sender
            if (msg_payload.equals(HELLO) && !this.established_connection_queue.contains(msg_senderID))
            {
                debug("Message is new Hello");
                ConnectionParameters new_hello = new ConnectionParameters(msg_senderID, message.sourceAddress);
                // enqueue

                if (this.queue.offer(new_hello))
                    this.established_connection_queue.add(msg_senderID);
                else
                    drop_msg(message, "Queue full");
            }
            // Case: ACK to first HELLO
            // It is an ACK & I know the sender & It is a response to a hello (PN = 0)
            else if (msg_payload.equals(ACK) && this.established_connection_queue.contains(msg_senderID) && msg_pn == 0)
            {
                debug("Message is ACK to HELLO");
                this.communication_partners.put(msg_senderID, msg_destinationID);
                send_msg(msg_destinationID, message);
            }
            // Case: HELLO with unknown destination
            // It is a HELLO & I know the sender & It does not know its destination
            else if (msg_payload.equals(HELLO) && this.established_connection_queue.contains(msg_senderID) && msg_destinationID == -1)
            {
                debug("Message is stray HELLO");
                Integer dest = this.communication_partners.get(msg_senderID);
                if (dest != null)
                    send_msg(dest, message);
                else
                    drop_msg(message, "HELLO without known destination");
            }
            else if (msg_destinationID > 0)
            {
                debug("Message is message");
                send_msg(msg_destinationID, message);
            }
            else
                drop_msg(message, "RemoteID = -1 in nonHELLO msg");
        }
        else
            drop_msg(message, "Invalid message");
    }

    private void send_msg(int destinationID, Message message)
    {
        debug("Sending to destination " + destinationID);
        // get handler by ID
        Handler upside_handler = super.upsideHandlers.get(destinationID);
        // pass it on
        if (upside_handler != null)
            pass_msg(upside_handler, message);
        else
            drop_msg(message, "Handler not found");
    }

    private void drop_msg(Message msg, String reason)
    {
        debug("Message dropped: " + msg.toString() + " [" + reason + "]", 4);
    }

    private boolean pass_msg(Handler handler, Message message)
    {
        try
        {
            handler.receive(message);
            debug("Message passed to Handler: " + message, 1);
            return true;
        }
        catch (IllegalStateException e)
        {
            drop_msg(message, "Upside handler's queue is full");
            return false;
        }
    }

    /**
    *       MACROS
    **/
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
            String time = "[" + new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()) + "][dis] ";
            System.out.println(colour + time + msg + "\u001B[0m");
        }
    }
}
