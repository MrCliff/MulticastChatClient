import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/**
 * A chat client that uses multicast.
 * <br />
 * Usage:
 * java MulticastChatClient [Multicast address] [Multicast port] [Username] [Birth date year] [Birth date month]
 * [Birth date day]
 * <br />
 * Example:
 * java MulticastChatClient 239.0.0.1 6666 "Test user" 1990 1 1
 *
 * @author Joonatan Kallio
 * Created on 2018-09-28
 */
public class MulticastChatClient {
    private static final String CLIENT_NAME = "MyClient";

    private static final String DEFAULT_MULTICAST_ADDRESS = "239.0.0.1";
    private static final int DEFAULT_PORT = 6666;
    private static final String DEFAULT_USER_NAME = "User";
    private static final LocalDate DEFAULT_BIRTH_DATE = LocalDate.of(1990, 1, 1);

    private static final DateTimeFormatter tFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM);
    private static final DateTimeFormatter dFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT);

    private static final String QUIT_REGEX = "(?i)quit|q";
    private static final int SOCKET_SO_TIMEOUT = 100;

    private static String multicastAddress;
    private static int port;
    private static String userName;
    private static LocalDate birthDate;

    public static void main(String[] args) {
        parseArgs(args);

        try {
            InetAddress inetAddress = InetAddress.getByName(multicastAddress);
            try (MulticastChatSocket socket = new MulticastChatSocket(port, inetAddress)) {
                socket.joinGroup(inetAddress);
                socket.setSoTimeout(SOCKET_SO_TIMEOUT);

                MulticastChatReceiver receiver = new MulticastChatReceiver(socket);
                receiver.start();

                MulticastChatSender sender = new MulticastChatSender(socket);
                sender.start();

                sender.join();
                receiver.stopReceiving();
                receiver.join();

                socket.leaveGroup(inetAddress);
            }
            catch (IOException | InterruptedException ex) {
                ex.printStackTrace();
            }
        }
        catch (UnknownHostException ex) {
            ex.printStackTrace();
        }
    }

    private static void parseArgs(String[] args) {
        multicastAddress = DEFAULT_MULTICAST_ADDRESS;
        port = DEFAULT_PORT;
        userName = DEFAULT_USER_NAME;
        birthDate = DEFAULT_BIRTH_DATE;

        if (args.length >= 1) {
            multicastAddress = args[0];
        }
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            }
            catch (NumberFormatException ignore) { }
        }
        if (args.length >= 3) {
            userName = args[2];
        }
        if (args.length >= 4) {
            try {
                int year = Integer.parseInt(args[3]);
                int month = Integer.parseInt(args[4]);
                int day = Integer.parseInt(args[5]);

                birthDate = LocalDate.of(year, month, day);
            }
            catch (NumberFormatException ignore) { }
        }
    }

    /**
     * A thread that listens to System.in for messages and packages them into
     * multicast chat packets and then sends them to the MulticastChatSocket.
     */
    public static class MulticastChatSender extends Thread {
        private static final int ERROR_SLEEP_MILLISECONDS = 1000;
        private MulticastChatSocket socket;

        public MulticastChatSender(MulticastChatSocket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            send(MulticastChatPacket.PacketType.JOIN, "\\join");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                while (true) {
                    String message = "";
                    try {
                        message = reader.readLine();
                    }
                    catch (IOException ex) {
                        ex.printStackTrace();
                        try {
                            sleep(ERROR_SLEEP_MILLISECONDS);
                        }
                        catch (InterruptedException iex) {
                            iex.printStackTrace();
                        }
                    }

                    if (message.matches(QUIT_REGEX)) break;

                    send(MulticastChatPacket.PacketType.MESSAGE, message);
                }
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }

            send(MulticastChatPacket.PacketType.LEAVE, "\\leave");
        }

        /**
         * Handles sending a packet with given message.
         *
         * @param packetType the type of the packet to send.
         * @param message the message to send.
         */
        private void send(MulticastChatPacket.PacketType packetType, String message) {
            MulticastChatPacket packet = new MulticastChatPacket(
                    packetType, birthDate, CLIENT_NAME, userName, message
            );

            try {
                socket.sendChatPacket(packet);
            }
            catch (IOException ex) {
                System.err.println("Packet sending failed:");
                ex.printStackTrace();
            }
        }

        /**
         * Updates the write message in System.out.
         */
        public static void updatePrompt() {
            System.out.println("Write a message (q = quit) > ");
        }
    }

    /**
     * A thread that listens to arriving multicast chat packets, reads them and
     * prints them to System.out.
     */
    public static class MulticastChatReceiver extends Thread {
        private static final int ERROR_SLEEP_MILLISECONDS = 1000;
        private MulticastChatSocket socket;
        private boolean canStop = false;

        public MulticastChatReceiver(MulticastChatSocket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            while (!canStop) {
                try {
                    MulticastChatPacket packet = socket.receiveChatPacket();

                    switch (packet.getPacketType()) {
                        case JOIN:
                            System.out.println();
                            handleJoin(packet);
                            break;
                        case LEAVE:
                            System.out.println();
                            handleLeave(packet);
                            break;
                        case MESSAGE:
                            System.out.println();
                            handleMessage(packet);
                            break;
                        case UNKNOWN_PACKET_TYPE:
                            continue;
                        default:
                            System.err.println(String.format(
                                    "Unknown protocol message type: %d, Protocol version: %d",
                                    packet.getPacketType().getId(),
                                    packet.getProtocolVersion()
                            ));
                    }

                    // Don't write the "Write a message" message to screen, if we are quiting.
                    if (!canStop) {
                        MulticastChatSender.updatePrompt();
                    }
                }
                catch (SocketTimeoutException ignore) {
                }
                catch (SocketException ex) {
                    break;
                }
                catch (IOException ex) {
                    ex.printStackTrace();
                    try {
                        sleep(ERROR_SLEEP_MILLISECONDS);
                    }
                    catch (InterruptedException iex) {
                        iex.printStackTrace();
                    }
                }
            }
        }

        /**
         * Stops receiving packets and shuts down this receiver.
         */
        public void stopReceiving() {
            canStop = true;
        }

        /**
         * Handles join packet.
         *
         * @param packet the packet to handle.
         */
        private void handleJoin(MulticastChatPacket packet) {
            String outputStr = String.format(
                    "[%s] %s(%s) liittyi. Asiakassovellus: %s, Protokollan versio: %d",
                    packet.getPacketReceiveTime().format(tFormat),
                    packet.getUserName(),
                    packet.getBirthDate().format(dFormat),
                    packet.getClientName(),
                    packet.getProtocolVersion()
            );
            System.out.println(outputStr);
        }

        /**
         * Handles leave packet.
         *
         * @param packet the packet to handle.
         */
        private void handleLeave(MulticastChatPacket packet) {
            String outputStr = String.format(
                    "[%s] %s poistui. (Asiakassovellus: %s, Protokollan versio: %d)",
                    packet.getPacketReceiveTime().format(tFormat),
                    packet.getUserName(),
                    packet.getClientName(),
                    packet.getProtocolVersion()
            );
            System.out.println(outputStr);
        }

        /**
         * Handles message packet.
         *
         * @param packet the packet to handle.
         */
        private void handleMessage(MulticastChatPacket packet) {
            String outputStr = String.format(
                    "[%s] %s: %s",
                    packet.getPacketReceiveTime().format(tFormat),
                    packet.getUserName(),
                    packet.getMessage()
            );
            System.out.println(outputStr);
        }
    }
}
