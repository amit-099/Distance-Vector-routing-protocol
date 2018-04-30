import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.UnknownHostException;

public class ReceiveThread extends Thread {
    private DatagramSocket datagramSocket;
    private byte[] buffer = new byte[500];
    ReceiveThread(DatagramSocket datagramSocket) {
        this.datagramSocket = datagramSocket;
    }
    public void run() {
        while (true) {
            DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
            try {
                datagramSocket.receive(datagramPacket);
            } catch (IOException e) {
                e.printStackTrace();
            }
            String received = new String(datagramPacket.getData(), 0, datagramPacket.getLength());
            if(received.startsWith("Failed")) {
                try {
                    int failedNodeInt;
                    String ar[] = received.split("@");
                    failedNodeInt = Integer.parseInt(ar[1]);
                    dv_routing_v1.isRanOnTerminal[failedNodeInt] = true;
                    new dv_routing_v1().nodeFailure(failedNodeInt);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            } else new dv_routing_v1().UpdateDVBellmenFord(received);
        }
    }
}
