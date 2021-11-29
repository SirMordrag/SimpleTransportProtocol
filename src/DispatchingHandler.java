  import java.text.SimpleDateFormat;
  import java.util.Date;
  import java.util.concurrent.ArrayBlockingQueue;

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

    /** An arbitrary base value for the numbering of handlers. **/
    private static int counter = 35000;

    /** the queue for pending connections */
    private final ArrayBlockingQueue<ConnectionParameters> queue;

    // to be completed

    private static final int DEBUG = 0;

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
        // add other initializations if needed
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
        // to be completed
    }

    // MACROS
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
            String time = "[" + new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()) + "] ";
            System.out.println(colour + time + msg + "\u001B[0m");
        }
    }
}
