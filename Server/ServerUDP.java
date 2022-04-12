import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class ServerUDP {
    private enum OPCODES{
        ReadRQ, WriteRQ, DATA, ACK, ERR;
        public short getValue() {
            return (short)(ordinal() + 1);
        }
    }

    private DatagramChannel dgs;
    private Selector sel;
    private SelectionKey skey;

    private final int PORT;
    private static final int DEFUALT_PORT = 26920; //have from here to 26929

    private boolean upload;
    private final boolean RAND_DROPS;

    private final long keyToSend;
    private long key;
    private short shortKey;

    private ByteBuffer[] outgoing;
    private ByteBuffer[] incoming;
    private int curOutgoingBuffer = 0;
    private int curIncomingBuffer = 0;
    private int curAwaitingAck = 0;
    private short curPacket = 0;
    private int minAwaitingBuffer = 0;

    private int windowSize = 10;
    private FileOutputStream toDownload;
    private FileInputStream toUpload;
    private FileChannel uploadChannel;
    private boolean finished = false;
    private int timeoutsInRow = 0;
    private int lastAcceptedPacket = -1;


    public ServerUDP(boolean random_drops, int windowSize){
        PORT = DEFUALT_PORT;
        RAND_DROPS = random_drops;
        keyToSend = ThreadLocalRandom.current().nextLong();
        this.windowSize = windowSize;

        createBuffers(windowSize);
    }
    public ServerUDP(int port, boolean random_drops, int windowSize){
        PORT = port;
        RAND_DROPS = random_drops;
        keyToSend = ThreadLocalRandom.current().nextLong();
        this.windowSize = windowSize;

        createBuffers(this.windowSize);
    }

    private void createBuffers(int size){
        outgoing = new ByteBuffer[size];
        incoming = new ByteBuffer[size];

        for(int i = 0; i < size; i++){
            //512 body, 4 header
            outgoing[i] = ByteBuffer.allocate(516);
            incoming[i] = ByteBuffer.allocate(516);
        }
    }

    public void open(){
        System.out.println("Setting up UDP connection...");
        try{
            sel = Selector.open();
            dgs = DatagramChannel.open();
            dgs.bind(new InetSocketAddress(PORT));

            System.out.println("Getting remote connection...");

            dgs.connect(dgs.receive(incoming[0]));
            dgs.configureBlocking(false);
            skey = dgs.register(sel, SelectionKey.OP_READ);//OP_ACCEPT);
            System.out.println("Connected.");
            createKey();

            sel.selectedKeys().clear();
            int s = sel.select(10000);
            if(s == 0){
                System.out.println("Timeout occured.");
                shutdown();
                return;
            }
            recieveFileName();
            sendAck(0);

            //reset buffers
            curIncomingBuffer = 0;
            curOutgoingBuffer = 0;

            if (upload) {
                uploadFile();
            }
            else {
                downloadFile();
            }

            shutdown();
        }
        catch (IOException ex){
            ex.printStackTrace();
            System.exit(-1);
        }
    }


    //UPLOAD
    private void uploadFile() throws IOException{

            //start throughput calculator
            long timeStarted = System.nanoTime();
            long bytesSent = 0;
            int s;

            //send the full window
            for (int i = 0; i < windowSize; i++) {
                bytesSent += sendMsg();
            }

            while (!finished && !Thread.currentThread().isInterrupted()) {
                sel.selectedKeys().clear();
                s = sel.select(10000);
                if (s == 0) {
                    timeoutsInRow++;
                    if (timeoutsInRow == 6) {
                        System.out.println("Timeout " + timeoutsInRow + " of 6 occured. Shutting down...");
                        return;
                    }
                    System.out.println("Timeout " + timeoutsInRow + " of 6 occured. Resending window...");
                    resendWindow();
                    continue;
                }
                //else reset timeoutsInRow
                timeoutsInRow = 0;
                //check the recievedAck
                int recievedBlock = recieveAck();
                //if the recievedBlock = the sent first sent block in the window, then progress, else resend the window
                if (recievedBlock == curAwaitingAck) {
                    System.out.println("Recieved ack " + curAwaitingAck + ".");
                    curAwaitingAck++;
                    bytesSent += sendMsg(); //send the next msg
                    minAwaitingBuffer++;//inc the bufferplace as well
                    if (minAwaitingBuffer == outgoing.length)
                        minAwaitingBuffer = 0;
                } else if (recievedBlock == -1) {
                    System.out.println("Error occured.");
                    return;
                } else {
                    System.out.println("Mismatched ack. Expecting " + curAwaitingAck + " but recieved " + recievedBlock + ". Resending window...");
                    resendWindow();
                }
            }

            System.out.println("Finished sending, awaiting last ACKs...");
            //putting this in a try catch in case some of the last acks get dropped
        try {
            //we've finished sending so we're on pure "make sure the last window gets recieved" mode
            while (windowSize > 1 && !Thread.currentThread().isInterrupted()) {
                sel.selectedKeys().clear();
                s = sel.select(10000);
                if (s == 0) {
                    timeoutsInRow++;
                    if (timeoutsInRow == 6) {
                        System.out.println("Timeout occured. Shutting down...");
                        return;
                    }
                    System.out.println("Timeout occured. Resending window...");
                    resendWindow();
                    continue;
                }
                //else reset timeoutsInRow
                timeoutsInRow = 0;
                int recievedBlock = recieveAck();
                //if the recievedBlock = the sent first sent block in the window, then progress, else resend the window
                if (recievedBlock == curAwaitingAck) {
                    System.out.println("Recieved ack " + curAwaitingAck + ".");
                    curAwaitingAck++;
                    windowSize--; //shrink what we're waiting on
                    minAwaitingBuffer++;//inc the bufferplace as well
                    if (minAwaitingBuffer == outgoing.length)
                        minAwaitingBuffer = 0;
                } else if (recievedBlock == -1) {
                    System.out.println("Error occured.");
                    return;
                } else {
                    System.out.println("Mismatched ack. Expecting " + curAwaitingAck + " but recieved " + recievedBlock + ". Resending window...");
                    resendWindow();
                }
            }

            //calculate throughput
            long timeFinished = System.nanoTime();
            double throughput = bytesSent * 8 * Math.pow(10, 6) / (timeFinished - timeStarted);
            System.out.println("Throughput: " + Math.round(throughput * 1000000.0) / 1000000.0 + " bps");
        }
        //if we lose the port at the end, then we might as well get a throughput and shutdown gracefully
        catch(PortUnreachableException pue){
            long timeFinished = System.nanoTime();
            double throughput = bytesSent * 8 * Math.pow(10, 6) / (timeFinished - timeStarted);
            System.out.println("Throughput: " + Math.round(throughput * 1000000.0) / 1000000.0 + " bps");
            shutdown();
        }
    }

    private int recieveAck() throws IOException{
        incoming[curIncomingBuffer].clear();
        dgs.read(incoming[curIncomingBuffer]);

        //check opcode
        incoming[curIncomingBuffer].flip();
        int OPCODE = incoming[curIncomingBuffer].getShort()^shortKey;
        if(OPCODE != OPCODES.ACK.getValue()){
            return -1;
        }
        int packetNumber = incoming[curIncomingBuffer].getShort()^shortKey;

        curIncomingBuffer++; //go to the next buffer
        if(curIncomingBuffer == incoming.length){curIncomingBuffer = 0;}

        return packetNumber;
    }

    private void resendWindow() throws IOException{
        for(int i = 0, curBuffer = minAwaitingBuffer; i < windowSize; i++){
            //IMPORTANT: RESET POSITION
            outgoing[curBuffer].position(0);
            dgs.write(outgoing[curBuffer]);
            curBuffer++;
            if(curBuffer == outgoing.length) curBuffer = 0;

        }
    }

    private int sendMsg() throws IOException{
        outgoing[curOutgoingBuffer].clear();
        outgoing[curOutgoingBuffer].putShort((short)(OPCODES.DATA.getValue()^shortKey));
        outgoing[curOutgoingBuffer].putShort((short)(curPacket^shortKey));
        //use the -1 chk in fileinputstream.read() to determine if we hit everything
        int bytesRead = uploadChannel.read(outgoing[curOutgoingBuffer]);
        if(bytesRead == -1){
            finished = true;
            return 0;
        }
        else{
            //encrypt
            //go past header
            if(bytesRead == 512) {
                for (int i = Short.BYTES * 2; i < bytesRead + Short.BYTES * 2; i += Long.BYTES) {
                    outgoing[curOutgoingBuffer].putLong(i, outgoing[curOutgoingBuffer].getLong(i) ^ key);
                }
            }
            else{
                for(int i = Short.BYTES*2; i <bytesRead + Short.BYTES * 2-1; i+= Short.BYTES){
                    outgoing[curOutgoingBuffer].putShort(i, (short)(outgoing[curOutgoingBuffer].getShort(i) ^ shortKey));
                }
            }
            //send
            outgoing[curOutgoingBuffer].flip();
            dgs.write(outgoing[curOutgoingBuffer]);

            curOutgoingBuffer++;
            if(curOutgoingBuffer == outgoing.length){curOutgoingBuffer = 0;}

            System.out.println("Sending packet " +curPacket +"...");
        }
        curPacket++;
        return bytesRead+Short.BYTES*2;
    }


    //DOWNLOAD
    private void downloadFile() throws IOException{
        int s;
        //according to TFTP, we'll know we've reached the end if the recieved body is less than 512 bytes
        while (!finished && !Thread.currentThread().isInterrupted()) {
            sel.selectedKeys().clear();
            s = sel.select(10000);
            if (s == 0) {

                if(timeoutsInRow == 6) {
                    System.out.println("Timeout " +timeoutsInRow +" of 6 occured. Shutting down...");
                    return;
                }
                System.out.println("Timeout " +timeoutsInRow +" of 6 occured. Resending ack...");
                resendAck();
                continue;
            }
            timeoutsInRow = 0;
            sendAck(recieveMsg());
        }

        for(int i =0; i<6; i++) {
            sel.selectedKeys().clear();
            s = sel.select(10000);
            if (s == 0) {
                System.out.println("Check client for throughput.");
                return;
            } else {
                System.out.println("Timeout " +i +" of 6 occured. Resending ack...");
                resendAck();
            }
        }
        System.out.println("Assuming client shutdown. Check it for throughput.");
    }

    private int recieveMsg() throws IOException{
        //Randomdrop % chance = 1% (tested w/ 50%)
        if(RAND_DROPS && ThreadLocalRandom.current().nextInt(100) == 0){
            System.out.println("Random drop (Shh...)");
            incoming[curIncomingBuffer].clear();
            dgs.read(incoming[curIncomingBuffer]);
            return  -2;
        }
        
        incoming[curIncomingBuffer].clear();
        int bytesRead = dgs.read(incoming[curIncomingBuffer]);

        //check opcode. if opcode != data, then be angry
        incoming[curIncomingBuffer].flip();
        int OPCODE = incoming[curIncomingBuffer].getShort()^shortKey;
        if(OPCODE != OPCODES.DATA.getValue()){ return -1; }

        int packetNum = incoming[curIncomingBuffer].getShort()^shortKey;

        System.out.println("Recieved packet " +packetNum);

        //only download if this is the next packet
        if(packetNum == lastAcceptedPacket +1) {
            lastAcceptedPacket++;
            //decrypt
            if(bytesRead == 516) {
                for (int i = Short.BYTES * 2; i < bytesRead; i += Long.BYTES) {
                    incoming[curIncomingBuffer].putLong(i, incoming[curIncomingBuffer].getLong(i) ^ key);
                }
            }
            else{
                for(int i = Short.BYTES*2; i <bytesRead-1; i+= Short.BYTES){
                    incoming[curIncomingBuffer].putShort(i, (short)(incoming[curIncomingBuffer].getShort(i) ^ shortKey));
                }
            }
            //write the body of the recieved msg to file
            byte[] toWrite = new byte[bytesRead - Short.BYTES * 2];
            incoming[curIncomingBuffer].get(toWrite, 0, bytesRead - Short.BYTES * 2);
            toDownload.write(toWrite);

            if (bytesRead != 516) {
                finished = true;
            }
        }

        return packetNum;
    }

    private void resendAck() throws IOException{
        sendAck(lastAcceptedPacket);
    }

    private void sendAck(int packetAcking) throws IOException{
        if(packetAcking == -2) return; //this is the autodrop value

        System.out.println("Sending ack for packet " +packetAcking+".");

        //^shortKey
        outgoing[curOutgoingBuffer].clear();

        outgoing[curOutgoingBuffer].putShort((short)(OPCODES.ACK.getValue()^shortKey));
        outgoing[curOutgoingBuffer].putShort((short)(packetAcking^shortKey));

        outgoing[curOutgoingBuffer].flip();
        dgs.write(outgoing[curOutgoingBuffer]);
        curOutgoingBuffer++;
        if(curOutgoingBuffer == outgoing.length){curOutgoingBuffer = 0;}
    }


    //BOTH
    //this is not the TFTP way, as I'm not using the ascii processing properly
    private void recieveFileName() throws IOException {
        incoming[curIncomingBuffer].clear();
        dgs.read(incoming[curIncomingBuffer]);

        incoming[curIncomingBuffer].flip();
        short mode = incoming[curIncomingBuffer].getShort();
        if(mode == OPCODES.ReadRQ.getValue()){ //RRQ
            upload = true;
        }
        else if (mode == OPCODES.WriteRQ.getValue()){ //WRQ
            upload = false;
        }
        else{
            throw new IOException("Invalid OPCode.");
        }

        //the getChar has to be second or else it will get the value, then hasRemaining will be updated to false
        StringBuilder builder = new StringBuilder();
        char l;
        while (incoming[curIncomingBuffer].hasRemaining() && (l = incoming[curIncomingBuffer].getChar()) != '\0' ){
            builder.append(l);
        }

        if(upload){
            toUpload = new FileInputStream(builder.toString());
            uploadChannel = toUpload.getChannel();
        }
        else{
            toDownload = new FileOutputStream(builder.toString());
        }
    }

    /**
     * From Project Specs:
     * "Arrange that each session begins with a (random) number exchange to generate a key that is used for encrypting data.
     * You can just use Xor to create key, or anything better."
     */
    private void createKey(){
        //send our part of the key, then recieve the other part
        outgoing[0].clear();
        outgoing[0].putLong(keyToSend);
        outgoing[0].flip();

        try{
            dgs.write(outgoing);
            //now read
            sel.selectedKeys().clear();
            int s = sel.select(10000);
            if(s == 0){
                System.out.println("Timeout occured.");
                shutdown();
            }
            else {
                incoming[0].clear();
                dgs.read(incoming[curIncomingBuffer]);

                incoming[curIncomingBuffer].flip();

                long keyRecieved = incoming[0].getLong();
                key = keyRecieved^keyToSend;
                shortKey = (short)key;

                sendAck(0);
            }
        }
        catch(IOException io){
            System.err.print("Message timeout occured.");
            io.printStackTrace();
            System.exit(-1);
        }
    }

    public void shutdown(){
        try{
            if(uploadChannel != null){
                uploadChannel.close();
            }
            if(toDownload != null){
                toDownload.close();
            }
            if(toUpload != null) {
                toUpload.close();
            }
            if(dgs != null){
                dgs.close();
            }
            System.out.println("Closed connection");
            System.exit(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
