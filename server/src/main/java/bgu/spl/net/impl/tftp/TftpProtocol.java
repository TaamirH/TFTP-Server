package bgu.spl.net.impl.tftp;

import java.util.concurrent.ConcurrentHashMap;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;


public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {

    private boolean shouldTerminate = false;
    private int connectionId;
    private Connections<byte[]> connections;
    static final ConcurrentHashMap<Integer,Boolean> ids_login = new ConcurrentHashMap<>();


    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public void process(byte[] message) {
        int opCode = message[1];
        switch (opCode) {
        case 1: // Read request (RRQ)
            String filename = extractFilename(message);
            try {
                writeFile(filename, message); // Write received data
                connections.send(connectionId, createAckPacket(0)); // Send ack packet
            } catch (IOException e) {
                connections.send(connectionId, createErrorPacket(1, "File operation failed"));
            }
            break;
        }
        case 2: // Write request (WRQ)
            String filename = extractFilename(message);
            try {
                byte[] fileData = readFile(filename); // Read file data
                connections.send(connectionId, fileData); // Send file data
            } catch (IOException e) {
                connections.send(connectionId, createErrorPacket(1, "File operation failed"));
            }
            break;
        case 3: // Data
            int blockNumber = (message[2] << 8) + message[3];
            byte[] data = new byte[message.length - 4];
            System.arraycopy(message, 4, data, 0, data.length);
            try {
                writeFile("file.txt", data); // Write received data
                connections.send(connectionId, createAckPacket(blockNumber)); // Send ack packet
            } catch (IOException e) {
                connections.send(connectionId, createErrorPacket(1, "File operation failed"));
            }
            break;
        case 4: // Ack
            int blockNumber = (message[2] << 8) + message[3];
            // Implement logic to handle ACK packet
            // This would involve checking the block number and sending the next data packet
            // ... (similar to processWriteRequest)
            break;
        case 5: // Error
            int errorCode = (message[2] << 8) + message[3];
            String errorMessage = new String(message, 4, message.length - 5);
            // Implement logic to handle ERROR packet
            // This would involve logging the error and possibly retrying the operation
            // ... (similar to processWriteRequest)
            break;
        case 6: // Directory listing request
            // Implement logic to handle directory listing request
            // This would involve listing files in the server directory and sending the list
            // ... (similar to processWriteRequest)
            break;
        case 7: //LOGRQ , login request
            String username = extractFilename(message);
            if(ids_login.containsKey(this.connectionId)){
                connections.send(connectionId, createErrorPacket(7, "User already logged in"));
            }
            else{
                ids_login.put(this.connectionId, true);
                connections.send(connectionId, createAckPacket(0));
            }
            break;
        case 8: //DELRQ , delete request
            String filename = extractFilename(message);
            File file = new File("server/Files/" + filename);
            if(file.exists()){
                file.delete();
                connections.send(connectionId, createAckPacket(0));
            }
            else{
                connections.send(connectionId, createErrorPacket(1, "File not found"));
            }
            break;
        case 9: //BCAST , broadcast
            String filename = extractFilename(message);
            File file = new File("server/Files/" + filename);
            if(file.exists()){
                connections.send(connectionId, createErrorPacket(5, "File already exists"));
            }
            else{
                connections.send(connectionId, createAckPacket(0));
            }
            break;
        case 10: //DISC , disconnect
            ids_login.remove(this.connectionId);
            connections.disconnect(this.connectionId);
            break;
        default:
            connections.send(connectionId, createErrorPacket(4, "Illegal TFTP operation"));
            break;
        }
     

    @Override
    public boolean shouldTerminate() {
        this.connections.disconnect(this.connectionId);
        ids_login.remove(this.connectionId);
        return shouldTerminate;
    } 
    private String extractFilename(byte[] data) {
        // Implement logic to extract filename from data based on your TFTP packet format
        // This might involve skipping opcode bytes and parsing the remaining data
        // You might need additional helper methods depending on your specific format
        return new String(data).trim(); // Placeholder assuming filename is at the beginning
    }
    private byte[] createErrorPacket(int errorCode, String errorMessage) {
        return new byte[0]; // Placeholder
        // Implement logic to create a byte array representing an ERROR packet
        // This would involve setting the opcode to 5 (ERROR), error code, and error message
        // ... (similar to createAckPacket)
        // todo Implement logic to create a byte array representing an ERROR packet
    }


    private byte[] createAckPacket(int blockNumber) {
        // Implement logic to create a byte array representing an ACK packet
        // This would involve setting the opcode to 4 (ACK) and block number
        byte[] ackPacket = new byte[4];
        ackPacket[0] = (byte) (4 >> 8); // Opcode (ACK) - higher byte
        ackPacket[1] = (byte) (4 & 0xFF); // Opcode (ACK) - lower byte
        ackPacket[2] = (byte) (blockNumber >> 8); // Block number - higher byte
        ackPacket[3] = (byte) (blockNumber & 0xFF); // Block number - lower byte
        return ackPacket;
    }

    private void writeFile(String filename, byte[] data) throws IOException {
        String filePath = "server/Files/" + filename; // Adjust path as needed
        try (FileOutputStream outputStream = new FileOutputStream(filePath)) {
            outputStream.write(data);
        }
    }
    private byte[] readFile(String filename) throws IOException {
        String filePath = "server/Files/" + filename; // Adjust path as needed
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + filePath);
        }
        try (FileInputStream inputStream = new FileInputStream(file)) {
            byte[] fileContent = new byte[(int) file.length()]; // Pre-allocate based on file size (optional)
            int bytesRead = inputStream.read(fileContent);
            if (bytesRead != fileContent.length) {
                throw new IOException("Failed to read entire file");
            }
            return fileContent;
        }
    }    

}
