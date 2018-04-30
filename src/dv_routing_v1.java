import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class dv_routing_v1 {
    private static int maxValue;
    private static int maxNodePossible;
    private static ArrayList<ArrayList<String>> adjacentList;
    private static float[] distanceVector, DVArray;
    private static int[] receivedDVCount, nextHopToDestination, costOfAnEdgeFromSource;
    public static int[] sentDVCount;
    public static HashMap<String, Integer> nodeStringToDecimalMapping;
    public static HashMap<Integer, String> nodeDecimalToStringMapping;
    public static boolean[] isAlive, isRanOnTerminal;
    private static boolean printFlag, isStabled;
    private static DatagramSocket socket;
    public static String hostName;


    public static void main(String[] args) throws IOException {

        new dv_routing_v1().InitVariables();
        for (char i = 'A'; i <= 'Z'; i++) {
            String nodeLetter = "";
            nodeLetter += i;
            nodeStringToDecimalMapping.put(nodeLetter, i - 'A');
            nodeDecimalToStringMapping.put(i - 'A', nodeLetter);
        }

        hostName = args[0];
        int portNumber = Integer.parseInt(args[1]);
        String fileName = args[2];

        FileReader fileReader = new FileReader(fileName);
        Scanner sc = new Scanner(fileReader);

        sc.nextInt();
        sc.nextLine();
        while (sc.hasNext()) {
            String lineFromFile = sc.nextLine();
            String str[] = lineFromFile.split("\\s+");
            ArrayList<String> adjNodeInfoList = new ArrayList<>();
            Collections.addAll(adjNodeInfoList, str);  //adjNodeInfoList contains the info of one adjacent node
            adjacentList.add(adjNodeInfoList);  //adjacentList contains the info of every adjacent nodes
        }

        IntStream.range(0, maxNodePossible).forEach(i -> {
            isAlive[i] = true;   //all nodes are alive at first
            distanceVector[i] = maxValue;  //initializing the DV with INT_MAX
            isRanOnTerminal[i] = false;  //at init stage i assumed that the nodes are not running at the terminal
        });
        new dv_routing_v1().makeDV();

        socket = new DatagramSocket(portNumber);
        new ReceiveThread(socket).start();  //Receive Thread will receive all the info from the neighbours

        for (ArrayList<String> anAdjacentList : adjacentList) {
            int adjNodePortNum = Integer.parseInt(anAdjacentList.get(2));
            String adjNode = anAdjacentList.get(0);
            int cost = Integer.parseInt(anAdjacentList.get(1));
            int nodeInDecimal = nodeStringToDecimalMapping.get(adjNode);
            costOfAnEdgeFromSource[nodeInDecimal] = cost;  //updating cost of the adjacent node
            new SendThread(socket, adjNodePortNum, adjNode).start();  //sending the DV to all the adjacent nodes
        }


        IntStream.range(0, maxNodePossible).forEach(i -> {
            sentDVCount[i] = 0;  //nothing sent
            receivedDVCount[i] = 0;  //assumed nothing received
        });

        isRanOnTerminal[nodeStringToDecimalMapping.get(hostName)] = true;  //node is running on the terminal

        while (true) {
            isStabled = true;  //boolean to track stability
            for (ArrayList<String> anAdjacentList : adjacentList) {
                int adjNodeInDecimal = nodeStringToDecimalMapping.get(anAdjacentList.get(0));
                if (receivedDVCount[adjNodeInDecimal] <= 10 && isAlive[adjNodeInDecimal]) {
                    isStabled = false;  //received 10 DV? if true, stable. else not stable
                }
            }
            int i = 0;
            while (i < maxNodePossible) {
                if (sentDVCount[i] >= 3 && isAlive[i]) {
                    new dv_routing_v1().nodeFailure(i);  //if don't get any reply after sending 3 msgs, the node is assumed to be dead one
                }
                i++;
            }

            if (isStabled) {
                if (!printFlag) {
                    for (int j = 0; j < maxNodePossible; j++) {
                        int sourceNode = nodeStringToDecimalMapping.get(hostName);
                        if (sourceNode == j) continue;
                    }
                    printFlag = true;
                    new dv_routing_v1().printResult();  //if stable and printflag is false, print the routing table
                    new dv_routing_v1().makeDV();
                }
                //if stable, receiveDVcount is 0
                IntStream.range(0, maxNodePossible).forEach(j -> receivedDVCount[j] = 0);
                new dv_routing_v1().makeDV();
            }
        }
    }

    //End of Main Function


    //Public and private functions start


    //Initializing all the variables and arrays
    private void InitVariables() {
        int maxArraySize = 100;
        maxNodePossible = 26;
        maxValue = 2147483647;  //used for initializing the DV
        adjacentList = new ArrayList<>();  //stored all the adjacent nodes info
        distanceVector = new float[maxArraySize];
        DVArray = new float[maxArraySize];  //stored the DV
        receivedDVCount = new int[maxArraySize];  //stored number of information received
        nextHopToDestination = new int[maxArraySize];  //next hop to destination
        costOfAnEdgeFromSource = new int[maxArraySize];
        nodeStringToDecimalMapping = new HashMap<>();  //mapped A-Z to 0-25
        nodeDecimalToStringMapping = new HashMap<>();
        isAlive = new boolean[maxArraySize];
        sentDVCount = new int[maxArraySize];  //stored number of information received
        isRanOnTerminal = new boolean[maxArraySize];
        isStabled = false;
        printFlag = false;  //used for printing
    }



    //this method is used to calculate the DV by using BellmenFord equation
    public void UpdateDVBellmenFord(String dvInfoFromNeighbour) {
        float[] getDistance = new float[100];
        int[] senderAllDest = new int[100];
        IntStream.range(0, dv_routing_v1.maxNodePossible).forEach(i -> getDistance[i] = dv_routing_v1.maxValue);

        String str[] = dvInfoFromNeighbour.split("@");
        String senderNode = str[0];
        IntStream.range(1, str.length).forEach(i -> {
            String senderNeighbours[] = str[i].split("\\s+");
            senderAllDest[i] = Integer.parseInt(senderNeighbours[0]);
            getDistance[senderAllDest[i]] = Float.parseFloat(senderNeighbours[1]);
        });
        int senderNodeInt = dv_routing_v1.nodeStringToDecimalMapping.get(senderNode);
        dv_routing_v1.isAlive[senderNodeInt] = true;
        dv_routing_v1.isRanOnTerminal[senderNodeInt] = true;
        dv_routing_v1.sentDVCount[senderNodeInt] = 0;
        dv_routing_v1.receivedDVCount[senderNodeInt] += 1;

        for (int i = 0; i < dv_routing_v1.maxNodePossible; i++) {
            if (!dv_routing_v1.isAlive[i]) continue;
            int sourceNode = dv_routing_v1.nodeStringToDecimalMapping.get(dv_routing_v1.hostName);
            if (i == sourceNode) {
                dv_routing_v1.DVArray[sourceNode] = 0;
                dv_routing_v1.nextHopToDestination[sourceNode] = sourceNode;
                continue;
            }
            if (dv_routing_v1.DVArray[i] > (getDistance[i] + dv_routing_v1.costOfAnEdgeFromSource[senderNodeInt]) && dv_routing_v1.isAlive[senderNodeInt]) {
                dv_routing_v1.DVArray[i] = getDistance[i] + dv_routing_v1.costOfAnEdgeFromSource[senderNodeInt];
                dv_routing_v1.nextHopToDestination[i] = senderNodeInt;
            }
        }
    }


    //This method will print the failed node
    public void nodeFailure(int failedNode) throws UnknownHostException {
        if (dv_routing_v1.isAlive[failedNode] && dv_routing_v1.isRanOnTerminal[failedNode]) {
            System.out.println();
            System.out.println("Node " + dv_routing_v1.nodeDecimalToStringMapping.get(failedNode) + " has failed");
            System.out.println();
            dv_routing_v1.printFlag = false;
            dv_routing_v1.isAlive[failedNode] = false;
            AdvertiseNodeFailureToAdjacentNode(dv_routing_v1.socket, failedNode);
            makeDV();
        }
    }


    //this method will make the Distance Vector
    private void makeDV() {
        IntStream.range(0, dv_routing_v1.maxNodePossible).forEach(i -> {
            dv_routing_v1.receivedDVCount[i] = 0;  //did not receive anything from neighbour
            dv_routing_v1.DVArray[i] = dv_routing_v1.maxValue;  //initializing DV with INT_MAX
            dv_routing_v1.nextHopToDestination[i] = -1;  //next hop equals minus one at init step
            int sourceNode = dv_routing_v1.nodeStringToDecimalMapping.get(dv_routing_v1.hostName);  //source node
            if (i == sourceNode) {
                dv_routing_v1.DVArray[i] = 0;  //self DV
                dv_routing_v1.nextHopToDestination[i] = sourceNode;  //self nexthop
            }
        });

        IntStream.range(0, dv_routing_v1.adjacentList.size()).forEach(i -> {
            int adjacentNode = dv_routing_v1.nodeStringToDecimalMapping.get(dv_routing_v1.adjacentList.get(i).get(0));  //adjacent node of source
            if (!dv_routing_v1.isAlive[adjacentNode]) return;
            int cost = Integer.parseInt(dv_routing_v1.adjacentList.get(i).get(1));  //cost of the adjacent node from the source
            dv_routing_v1.DVArray[adjacentNode] = cost;  //updating DV
            dv_routing_v1.nextHopToDestination[adjacentNode] = adjacentNode;  //updating nexthop
        });
    }


    //this method will tell its neighbour about a node that has failed
    private void AdvertiseNodeFailureToAdjacentNode(DatagramSocket datagramSocket, int node) throws UnknownHostException {

        DatagramPacket datagramPacket;
        byte[] byteArray = new byte[100];
        int receiverPort;
        InetAddress receiverAddress;
        receiverAddress = InetAddress.getByName("localhost");
        int i = 0;
        while (i < dv_routing_v1.adjacentList.size()) {
            int adjacentNode = dv_routing_v1.nodeStringToDecimalMapping.get(dv_routing_v1.adjacentList.get(i).get(0));
            if (!dv_routing_v1.isAlive[adjacentNode]) {
                i++;
                continue;
            }
            if (adjacentNode == node) {
                i++;
                continue;
            }
            receiverPort = Integer.parseInt(dv_routing_v1.adjacentList.get(i).get(2));
            String msg = "Failed@" + node;
            byteArray = msg.getBytes();
            datagramPacket = new DatagramPacket(byteArray, byteArray.length, receiverAddress, receiverPort);
            try {
                datagramSocket.send(datagramPacket);
            } catch (IOException ignored) {}
            i++;
        }
    }


    //this method will be called to print the result
    private void printResult() {
        IntStream.range(0, dv_routing_v1.maxNodePossible).forEach(i -> {
            int sourceNode = dv_routing_v1.nodeStringToDecimalMapping.get(dv_routing_v1.hostName);
            if (i == sourceNode) return;
            if (!dv_routing_v1.isAlive[i]) return;
            if (dv_routing_v1.DVArray[i] < dv_routing_v1.maxValue) {
                System.out.println("shortest path to node " + (char) (i + 'A') + ": the next hop is " + (char) (dv_routing_v1.nextHopToDestination[i] + 'A') + " and the cost is " + dv_routing_v1.DVArray[i]);
            }
        });
    }


    //this method is used to generate the message format
    public String GenerateMessage(String source, int destination) {
        String generatedMessage = IntStream.range(0, dv_routing_v1.maxNodePossible).filter(i -> dv_routing_v1.nextHopToDestination[i] != destination).
                filter(i -> dv_routing_v1.isAlive[i]).filter(i -> dv_routing_v1.DVArray[i] != dv_routing_v1.maxValue).mapToObj(i -> Integer.toString(i) + " " +
                Float.toString(dv_routing_v1.DVArray[i]) + '@').collect(Collectors.joining("", source + "@", ""));
        return generatedMessage;
    }
}