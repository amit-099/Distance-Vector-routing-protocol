import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class SendThread extends Thread {
    private DatagramSocket datagramSocket;
    private int receiverPort;
    private String receiverHost;
    private InetAddress receiverAddress;

    SendThread(DatagramSocket datagramSocket, int receiverPort, String receiverHost) throws UnknownHostException {
        this.datagramSocket = datagramSocket;
        this.receiverPort = receiverPort;
        receiverAddress = InetAddress.getByName("localhost");
        this.receiverHost = receiverHost;
    }
    public void run() {
        while(true) {
            String sendMessage = new dv_routing_v1().GenerateMessage(dv_routing_v1.hostName, dv_routing_v1.nodeStringToDecimalMapping.get(receiverHost));
            byte[] byteArray = sendMessage.getBytes();
            DatagramPacket datagramPacket = new DatagramPacket(byteArray, byteArray.length, receiverAddress, receiverPort);
            try {
                datagramSocket.send(datagramPacket);
                int receivingAdjNode = dv_routing_v1.nodeStringToDecimalMapping.get(receiverHost);
                if(dv_routing_v1.isAlive[receivingAdjNode] && dv_routing_v1.isRanOnTerminal[receivingAdjNode])
                    dv_routing_v1.sentDVCount[receivingAdjNode] += 1;
            } catch (IOException ignored) {}
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
