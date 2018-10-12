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

    private static MulticastChatReceiver receiver;
    private static MulticastChatSender sender;

    private String multicastAddress;
    private int port;
    private User user;
    private UserList userList = new UserListImpl();

    public void setMulticastAddress(String multicastAddress) {
        this.multicastAddress = multicastAddress;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getMulticastAddress() {
        return multicastAddress;
    }

    public int getPort() {
        return port;
    }

    public User getUser() {
        return user;
    }

    /**
     * A factory for {@link MulticastChatSender}.
     *
     * @param socket the socket for the sender.
     * @return new {@link MulticastChatSender} instance.
     */
    private MulticastChatSender createChatSender(MulticastChatSocket socket) {
        return new MulticastChatSender(socket);
    }

    /**
     * A factory for {@link MulticastChatReceiver}.
     *
     * @param socket the socket for the receiver.
     * @return new {@link MulticastChatReceiver} instance.
     */
    private MulticastChatReceiver createChatReceiver(MulticastChatSocket socket) {
        return new MulticastChatReceiver(socket);
    }

    /**
     * Closes all open threads.
     */
    public void close() {
        userList.close();
    }

    public static void main(String[] args) {
        MulticastChatClient client = parseArgs(args);

        try {
            InetAddress inetAddress = InetAddress.getByName(client.getMulticastAddress());
            try (MulticastChatSocket socket = new MulticastChatSocket(client.getPort(), inetAddress)) {
                socket.joinGroup(inetAddress);
                socket.setSoTimeout(SOCKET_SO_TIMEOUT);

                receiver = client.createChatReceiver(socket);
                receiver.start();

                sender = client.createChatSender(socket);
                sender.start();

                sender.join();
                receiver.stopReceiving();
                receiver.join();

                client.close();

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

    /**
     * Parses the given arguments and constructs a new chat client using them.
     *
     * @param args the arguments to parse.
     * @return new {@link MulticastChatClient} instance.
     */
    private static MulticastChatClient parseArgs(String[] args) {
        String multicastAddress = DEFAULT_MULTICAST_ADDRESS;
        int port = DEFAULT_PORT;
        String userName = DEFAULT_USER_NAME;
        LocalDate birthDate = DEFAULT_BIRTH_DATE;

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

                int month = birthDate.getMonthValue();
                int day = birthDate.getDayOfMonth();

                if (args.length >= 5) {
                    month = Integer.parseInt(args[4]);
                }
                if (args.length >= 6) {
                    day = Integer.parseInt(args[5]);
                }

                birthDate = LocalDate.of(year, month, day);
            }
            catch (NumberFormatException ignore) { }
        }

        MulticastChatClient client = new MulticastChatClient();
        client.setUser(new User(userName, birthDate));
        client.setMulticastAddress(multicastAddress);
        client.setPort(port);

        return client;
    }

    /**
     * A thread that listens to System.in for messages and packages them into
     * multicast chat packets and then sends them to the MulticastChatSocket.
     */
    public class MulticastChatSender extends Thread {
        private static final String PROMPT_MESSAGE = "Write a message (q = quit) > ";
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

                    // Quit
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
                    packetType, user.getBirthDate(), CLIENT_NAME, user.getUserName(), message
            );

            try {
                socket.sendChatPacket(packet);

                switch (packetType) {
                    case JOIN:
                        userList.addDistinct(user);
                        break;
                }
            }
            catch (IOException ex) {
                System.err.println("Packet sending failed:");
                ex.printStackTrace();
            }
        }

        /**
         * Sends the update message of the user list.
         */
        public void sendUserListUpdate() {
            MulticastChatPacket packet = new MulticastChatPacket(
                    MulticastChatPacket.PacketType.USER_LIST_UPDATE,
                    user.getBirthDate(),
                    CLIENT_NAME,
                    user.getUserName(),
                    userList.asCollection()
            );

            try {
                socket.sendChatPacket(packet);
            }
            catch (IOException ex) {
                System.err.println("Packet sending failed:");
                ex.printStackTrace();
            }
        }
    }

    /**
     * A thread that listens to arriving multicast chat packets, reads them and
     * prints them to System.out.
     */
    public class MulticastChatReceiver extends Thread {
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
                        case USER_LIST_UPDATE:
                            handleUserListUpdate(packet);
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
                        System.out.println(MulticastChatSender.PROMPT_MESSAGE);
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

            User joinedUser = packet.getUser();

            // Only send list update, if the join message comes from a new user.
            if (userList.addDistinct(joinedUser)) {
                userList.scheduleListUpdate(() -> sender.sendUserListUpdate());
            }
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

            User leftUser = packet.getUser();
            userList.removeByName(leftUser);

            System.out.println(getUserListUpdateMessage());
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

        /**
         * Handles user list update packet.
         *
         * @param packet the packet to handle.
         */
        private void handleUserListUpdate(MulticastChatPacket packet) {
            userList.cancelListUpdate();

            if (userList.getNumberOfUsers() <= 1) {
                userList.addAllDistinct(packet.getUsers());
            }

            // Print user list
            System.out.println(getUserListUpdateMessage());
        }

        /**
         * Formats the user list to a nice update message.
         *
         * @return the formatted update message of the user list.
         */
        private String getUserListUpdateMessage() {
            String userListStr = userList.toString();
            return String.format(
                    "Paikalla olevat käyttäjät: %s",
                    userListStr
            );
        }
    }
}
