import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/**
 * A socket for receiving multicast chat protocol packets.
 *
 * @author Joonatan Kallio
 * Created on 2018-09-28
 */
public class MulticastChatSocket extends MulticastSocket {
    private byte[] receiveBuffer = new byte[MulticastChatPacket.MAX_PACKET_SIZE];
    private byte[] sendBuffer = new byte[MulticastChatPacket.MAX_PACKET_SIZE];
    private final DatagramPacket receiveDatagramPacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
    private final DatagramPacket sendDatagramPacket = new DatagramPacket(sendBuffer, sendBuffer.length);
    private final InetAddress multicastAddress;

    /**
     * Create a multicast chat socket.
     *
     * @throws IOException       if an I/O exception occurs
     *                           while creating the MulticastSocket
     * @throws SecurityException if a security manager exists and its
     *                           {@code checkListen} method doesn't allow the
     *                           operation.
     * @see SecurityManager#checkListen
     */
    public MulticastChatSocket(InetAddress multicastAddress) throws IOException {
        this.multicastAddress = multicastAddress;
    }

    /**
     * Create a multicast chat socket and bind it to a specific port.
     *
     * @param port port to use
     * @throws IOException       if an I/O exception occurs
     *                           while creating the MulticastSocket
     * @throws SecurityException if a security manager exists and its
     *                           {@code checkListen} method doesn't allow the
     *                           operation.
     * @see SecurityManager#checkListen
     */
    public MulticastChatSocket(int port, InetAddress multicastAddress) throws IOException {
        super(port);
        this.multicastAddress = multicastAddress;
    }

    /**
     * Receives chat data from the opened connection.
     *
     * @throws IOException if an I/O error occurs.
     */
    public MulticastChatPacket receiveChatPacket() throws IOException {
        synchronized (receiveDatagramPacket) {
            byte[] data;
            receive(receiveDatagramPacket); // TODO: Check if this blocking statement is bad inside a synchronized method.
            data = receiveDatagramPacket.getData();
            return new MulticastChatPacket(data);
        }
    }

    /**
     * Sends chat data to the opened connection.
     *
     * @param packet the chat packet to send.
     * @throws IOException if an I/O error occurs.
     */
    public void sendChatPacket(MulticastChatPacket packet) throws IOException {
        synchronized (sendDatagramPacket) {
            sendDatagramPacket.setAddress(multicastAddress);
            sendDatagramPacket.setPort(getLocalPort());

            byte[] data = packet.toByteArray();
            sendDatagramPacket.setData(data);
            sendDatagramPacket.setLength(data.length);

            send(sendDatagramPacket);
        }
    }
}
