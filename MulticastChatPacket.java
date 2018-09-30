import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A multicast chat data packet, that corresponds to the multicast chat
 * protocol.
 * <br />
 * The bit order of a data packet following the protocol:
 * <pre>
 *     0                   1                   2                   3
 *     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *    |Version| Type  | dd      | mm    | yyyy                | Fill=0|
 *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *    | Client length |           max 127 bytes                       |
 *    +-+-+-+-+-+-+-+-+           The name of client program          |
 *    |                           ...                                 |
 *    +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 *    | User length   |           max 127 bytes                       |
 *    +-+-+-+-+-+-+-+-+           User name                           |
 *    |                           ...                                 |
 *    +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 *    | Message length|           max 127 bytes                       |
 *    +-+-+-+-+-+-+-+-+           Message                             |
 *    |                           ...                                 |
 *    +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 * </pre>
 *
 * @author Joonatan Kallio
 * Created on 2018-09-28
 */
public class MulticastChatPacket {
    public static final int MAX_STRING_FIELD_SIZE = 127;
    // Packet max length: 388 or 772 bytes
    public static final int MAX_PACKET_SIZE = 388;
    private static final Charset CHARSET = StandardCharsets.ISO_8859_1;
    private static final int CURRENT_PROTOCOL_VERSION = 1;

    private final int protocolVersion;
    private final PacketType packetType;
    private final LocalDate birthDate;
    private final String clientName;
    private final String userName;
    private final String message;
    private final LocalDateTime packetReceiveTime;

    /**
     * Parses the multicast chat packet from the given byte array using the bit
     * order of the multicast chat protocol. (Packet structure described in
     * {@code MulticastChatPacket}.)
     *
     * @param data the multicast chat packet as byte array.
     * @see MulticastChatPacket
     */
    public MulticastChatPacket(byte[] data) {
        protocolVersion = data[0] >>> 4;
        packetType = PacketType.getById(data[0] & 0x0F);

        int dateBits = (data[1] & 0xFF) << 16 | (data[2] & 0xFF) << 8 | (data[3] & 0xFF);
        birthDate = parseBirthDateBits(dateBits);

        int clientNameLength = data[4];
        clientName = new String(data, 5, clientNameLength, CHARSET);

        int userNameOffset = 4 + clientNameLength + 1;
        int userNameLength = data[userNameOffset];
        userName = new String(data, userNameOffset + 1, userNameLength, CHARSET);

        int messageOffset = userNameOffset + userNameLength + 1;
        int messageLength = data[messageOffset];
        message = new String(data, messageOffset + 1, messageLength, CHARSET);

        packetReceiveTime = LocalDateTime.now();
    }

    /**
     * Initializes a {@code MulticastChatPacket} using the given information.
     *
     * @param packetType the type of this packet.
     * @param birthDate  the birth date of the user.
     * @param clientName the name of the client application used to create and
     *                   send this packet.
     * @param userName   the name of the user using the client application.
     * @param message    the chat message to send.
     */
    public MulticastChatPacket(PacketType packetType, LocalDate birthDate, String clientName, String userName,
                               String message) {
        protocolVersion = CURRENT_PROTOCOL_VERSION;

        this.packetType = packetType;
        this.birthDate = birthDate;
        this.clientName = clientName;
        this.userName = userName;
        this.message = message;

        packetReceiveTime = LocalDateTime.now();
    }

    /**
     * Parses the birthDate out of the given bits. The birthDate is parsed from the given
     * integer using the following format:
     * <pre>
     *        3                   2                   1                   0
     *      1 0 9 8 7 6 5 4 3 2 1 0 9 8 7 6 5 4 3 2 1 0 9 8 7 6 5 4 3 2 1 0
     *     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *     | anything      | dd      | mm    | yyyy                |0 0 0 0|
     *     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * </pre>
     *
     * @param dateBits an integer that includes the birthDate.
     * @return the parsed birthDate as a {@code Calendar} object.
     */
    private LocalDate parseBirthDateBits(int dateBits) {
        int day = dateBits >>> 19 & 0x1F;
        int month = dateBits >>> 15 & 0x0F;
        int year = dateBits >>> 4 & 0x07FF;

        return LocalDate.of(year, month, day);
    }

    /**
     * Converts this {@code MulticastChatPacket} to a byte array that follows
     * the multicast chat protocol.
     *
     * @return byte array of data that follows the multicast chat protocol.
     * @see MulticastChatPacket
     */
    public byte[] toByteArray() {
        int clientNameOffset = 4;
        byte[] clientNameBytes = getClientName().getBytes(CHARSET);
        byte clientNameLength = (byte)(clientNameBytes.length <= MAX_STRING_FIELD_SIZE
                ? clientNameBytes.length
                : MAX_STRING_FIELD_SIZE);

        int userNameOffset = clientNameOffset + clientNameLength + 1;
        byte[] userNameBytes = getUserName().getBytes(CHARSET);
        byte userNameLength = (byte)(userNameBytes.length <= MAX_STRING_FIELD_SIZE
                ? userNameBytes.length
                : MAX_STRING_FIELD_SIZE);

        int messageOffset = userNameOffset + userNameLength + 1;
        byte[] messageBytes = getMessage().getBytes(CHARSET);
        byte messageLength = (byte)(messageBytes.length <= MAX_STRING_FIELD_SIZE
                ? messageBytes.length
                : MAX_STRING_FIELD_SIZE);

        byte[] data = new byte[messageOffset + 1 + messageLength];

        data[0] = (byte)((getProtocolVersion() & 0x0F) << 4 | (getPacketType().getId() & 0x0F));

        LocalDate date = getBirthDate();
        int dateBits = ((date.getDayOfMonth() & 0x1F) << 19)
                | ((date.getMonthValue() & 0x0F) << 15)
                | ((date.getYear() & 0x07FF) << 4);
        data[1] = (byte)(dateBits >>> 16 & 0xFF);
        data[2] = (byte)(dateBits >>> 8 & 0xFF);
        data[3] = (byte)(dateBits & 0xFF);

        data[clientNameOffset] = clientNameLength;
        System.arraycopy(clientNameBytes, 0, data, clientNameOffset + 1, clientNameLength);

        data[userNameOffset] = userNameLength;
        System.arraycopy(userNameBytes, 0, data, userNameOffset + 1, userNameLength);

        data[messageOffset] = messageLength;
        System.arraycopy(messageBytes, 0, data, messageOffset + 1, messageLength);

        return data;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public PacketType getPacketType() {
        return packetType;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getClientName() {
        return clientName;
    }

    public String getUserName() {
        return userName;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getPacketReceiveTime() {
        return packetReceiveTime;
    }

    /**
     * The types of multicast chat protocol packet.
     */
    public enum PacketType {
        JOIN(1),
        LEAVE(2),
        MESSAGE(3);

        private int id;

        PacketType(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        /**
         * Returns the packet type based on the ID of the packet type.
         *
         * @param id the ID of the packet type.
         * @return the packet type of the given id.
         * @throws IllegalArgumentException if no packet type is defined with
         *                                  the given id.
         */
        public static PacketType getById(int id) throws IllegalArgumentException {
            for (PacketType packetType : PacketType.values()) {
                if (packetType.id == id) return packetType;
            }

            throw new IllegalArgumentException(String.format("No packet type specified with id: %d", id));
        }
    }
}
