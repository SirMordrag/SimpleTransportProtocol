import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** delay before retransmitting a non acked message */
    private static final int DELAY = 300;

    /** number of times a non acked message is sent before timeout */
    private static final int MAX_REPEAT = 10;

    /** A single Timer for all usages. Don't cancel it. **/
    private static final Timer TIMER = new Timer("ConnectedHandler's Timer",true);

    private final int localId;
    private final int remoteId;
    private final int senderPN;
    private final int receiverPN;
    private final String destination;
    private Handler aboveHandler;
    private Handler under;
    private final Object lock ;
    // to be completed

    /**
     * Initializes a new connected handler with the specified parameters
     *
     * @param _under
     *          the {@link Handler} on which the new handler will be stacked
     * @param _localId
     *          the connection Id used to identify this connected handler
     * @param _destination
     *          a {@code String} identifying the destination
     */
    public ConnectedHandler(final Handler _under, int _localId, String _destination)
    {
        super(_under, _localId, true);
        this.localId = _localId;
        this.destination = _destination;
        // to be completed
        this.remoteId = -1;
        this.senderPN = 0;
        this.receiverPN = 0;
        this.under = _under;
        this.lock = new Object();
        send(HELLO);
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
        // to be completed
        Pattern p = Pattern.compile("([^;]+);([^;]+);([^;]+);([^;]+)");
        Matcher m = p.matcher(message.payload);

        if (m.find())
        {
            String respondMsg = m.group(4);
            if (respondMsg.equals(HELLO))
            {

            }
            else if (respondMsg.equals(ACK))
            {

            }
        }
        else
        {
            debug("Message is valid");
        }
    }

    @Override
    public void send(final String payload)
    {
        // to be completed
        int PN = this.senderPN;
        if (payload.equals("ACK"))
        {
            String ackPayload = Integer.toString(this.localId)+";"+Integer.toString(this.remoteId)+";"
                    +Integer.toString(this.receiverPN)+";"+payload;
            debug(ackPayload);
            this.under.send(ackPayload, destination);
        }
        else
        {
            String send_payload = Integer.toString(this.localId) + ";" + Integer.toString(this.remoteId) + ";"
                    + Integer.toString(this.senderPN) + ";" + payload;
            System.out.println(send_payload);
            this.under.send(send_payload, destination);
            Handler t_under = this.under;

            // create a new task
            TimerTask task = new TimerTask()
            {
                int cnt = 0;
                @Override
                public void run()
                {
                    cnt += 1;
                    t_under.send(send_payload, destination);
                    if (cnt > MAX_REPEAT)
                    {
                        Thread.currentThread().interrupt();
                    }
                }
            };

            // assign tasks
            TIMER.schedule(task, DELAY);
            while(this.senderPN == PN)
            {
                synchronized (this)
                {
                    try
                    {
                        wait();
                    } catch (InterruptedException e) {
                        error("Interrupted!", e);
                    }
                }
                task.cancel();
            }
        }
        TIMER.purge();
    }

    @Override
    public void send(String payload, String destinationAddress)
    {
        no_send();
    }

    @Override
    public void close() {
        // to be completed
        TIMER.cancel();
        super.close();
    }

    //
    // MACROS
    //

    static void debug(String msg)
    {
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