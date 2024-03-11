package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.ConnectionsImpl;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;


public class TftpProtocol implements BidiMessagingProtocol<byte[]> {

  String filesPath = System.getProperty("user.dir")+"/" + "Files";
  String user = "None";
  private int connectionId;
  private ConnectionsImpl<byte[]> connections;
  boolean shouldTerminate = false;
  boolean loggedIn;
  String lastFileName = "";
  ByteBuffer data;
  short readCounter = 1;
  short writeCounter = 1;
  ConcurrentLinkedQueue<byte[]> readQueue;
  FileOutputStream outputStream;
  byte[] errorCodes = {0,5};

  @Override
  public void start(
    int connectionId_,
    ConnectionsImpl<byte[]> connections_
  ) {
    this.connectionId = connectionId_;
    this.connections = connections_;
    loggedIn = false;

    readQueue = new ConcurrentLinkedQueue<>();
  }

  @Override
  public void process(byte[] message) {
    short opCode = (short) (
      ((short) message[0] & 0xff) << 8 | (short) (message[1] & 0xff)
    );
    if (opCode == 1) { // RRQ client wants to read a file
  if (!loggedIn) {
    sendError((short) 6, "User isn't logged in");
    return;
  }

  String fileName = new String(message, 2, message.length - 2);
  connections.lock.readLock().lock();
  String filePath = filesPath + File.separator + fileName;

  File file = new File(filePath);
  if (!file.exists()) {
    connections.lock.readLock().unlock();
    sendError((short) 1, "File not found");
  } else {
    try (RandomAccessFile raf = new RandomAccessFile(filePath, "r")) {
      long fileSize = raf.length();
      int chunkSize = 512; // Adjust chunk size as needed

      // Use a List for storing data packets
      List<byte[]> dataPackets = new ArrayList<>();

      // Calculate total number of chunks
      long numChunks = (fileSize + chunkSize - 1) / chunkSize;

      for (int blockNumber = 1; blockNumber <= numChunks; blockNumber++) {
        byte[] chunk = new byte[chunkSize];
        int bytesRead = raf.read(chunk);

        // Handle last chunk size if smaller than chunkSize
        if (blockNumber == numChunks) {
          chunk = Arrays.copyOf(chunk, bytesRead);
        }

        // Create data packet structure using a Map or custom class
        Map<String, byte[]> dataPacket = new HashMap<>();
        dataPacket.put("type", new byte[] {0, 3}); // DATA packet type
        dataPacket.put("packetSize", new byte[] {(byte) (bytesRead >> 8), (byte) (bytesRead & 0xff)});
        dataPacket.put("blockNumber", new byte[] {(byte) (blockNumber >> 8), (byte) (blockNumber & 0xff)});
        dataPacket.put("data", chunk);

        // Add data packet to the list
        dataPackets.add(concatenateArrays(dataPacket.values().toArray(new byte[0][])));
      }

      // Reset read counter (assuming for comparison logic)
      readCounter = 1;

      // Send data packets one by one
      for (byte[] dataPacket : dataPackets) {
        connections.send(connectionId, dataPacket);
      }
    } catch (IOException e) {
      e.printStackTrace();
      sendError((short) 0, "Problem reading the file");
      return;
    } finally {
      connections.lock.readLock().unlock();
    }
  }
}
if (opCode == 2) { // WRQ request client wants to upload a file
  if (!loggedIn) {
    sendError((short) 6, "User isn't logged in");
    return;
  }

  String fileName = new String(message, 2, message.length - 2);
  connections.lock.writeLock().lock();

  Path filePath = Paths.get(filesPath, fileName);

  if (Files.exists(filePath)) {
    connections.lock.writeLock().unlock();
    sendError((short) 5, "File already exists");
  } else {
    try {
      // Use Files.createFile to create the file with exception handling
      Files.createFile(filePath);
      lastFileName = fileName;
      System.out.println("File created successfully.");

      // Send ACK packet with 0 block number (no data yet)
      byte[] ack = {0, 4, 0, 0}; // ACK type, block number 0
      connections.send(connectionId, ack);
    } catch (IOException e) {
      connections.lock.writeLock().unlock();
      sendError((short) 0, "Problems creating the file");
      return;
    } finally {
      connections.lock.writeLock().unlock();
    }
  }
}
if (opCode == 3) { // Receiving data from client
  if (!loggedIn) {
      sendError((short) 6, "User isn't logged in");
      return;
  }

  short blockNum = (short) ((message[4] & 0xff) << 8 | (message[5] & 0xff));
  short blockLength = (short) ((message[2] & 0xff) << 8 | (message[3] & 0xff));

  connections.lock.writeLock().lock();
  try {
      if (blockNum != writeCounter) {
          sendError((short) 0, "Got the wrong block");
      } else {
          if (blockLength > 0) {
              byte[] data = Arrays.copyOfRange(message, 6, message.length);
              try {
                  outputStream.write(data);
              } catch (IOException e) {
                  sendError((short) 0, "Problem writing to the file");
              }
          }

          byte[] ack = {0, 4, message[4], message[5]}; // Concatenate block bytes
          connections.send(connectionId, ack);

          if (blockLength < 512) {
              // Use a custom class for broadcast message
              BroadcastMessage bcast = new BroadcastMessage((byte) 9, (byte) 1, lastFileName);
              connections.bCast(connectionId, bcast.getBytes());
              writeCounter = 1; // Reset for next upload
          }
      }
  } finally {
      connections.lock.writeLock().unlock();
  }
}
 if (opCode == 4) { // Ack from Client
      if (!loggedIn) {
        sendError((short) 6, "User isn't logged in");
        return;
      }
      short blockNum = (short) (
        ((short) message[2] & 0x00ff) << 8 | (short) (message[3] & 0x00ff)
      );
      if (blockNum != readCounter) {
        sendError((short) (0), "Got the wrong block");
        return;
      }

      if (readQueue.isEmpty()) {
        readCounter = 1;
        connections.lock.readLock().unlock();
        return;
      }
      connections.send(connectionId, readQueue.remove());
      readCounter++;
    }
    if (opCode == 5) {
      if (!loggedIn) {
        sendError((short) 6, "User isn't logged in");
        return;
      }
      String errorMsg = new String(message, 4, message.length - 4);
      System.err.println("Error: " + errorMsg);
    }
    if (opCode == 6) { // File list request
      if (!loggedIn) {
        sendError((short) 6, "User isn't logged in");
        return;
      }
    
      connections.lock.readLock().lock();
      try {
        // Get file names using your data access logic
        List<String> fileNamesList = getFileNames();
    
        // Create a FileListMessage object to encapsulate the data
        FileListMessage fileListMessage = new FileListMessage(fileNamesList);
    
        // Convert FileListMessage to byte array for sending
        byte[] messageBytes = fileListMessage.getMessageBytes();
    
        // Send the message in a single packet (assuming message size is appropriate)
        connections.send(connectionId, messageBytes);
      } finally {
        connections.lock.readLock().unlock();
      }
    }
    
    if (opCode == 7) { // client wants to logIn
      if (loggedIn) {
        sendError((short) 7, "User is logged in already");
        return;
      } else {
        String userName = new String(message, 2, message.length - 2); // getting the string from the message
        if (connections.logged(userName) != null) {
          sendError((short) 0, "The username u gave is already loggedIn");
          return;
        } else {
          loggedIn = true;
          connections.logIn(userName, connectionId);
          user = userName;
          byte[] ack = { 0, 4, 0, 0 };
          connections.send(connectionId, ack);
        }
      }
    }
    if (opCode == 8) {
      connections.lock.writeLock().lock();
      try {
          if (!loggedIn) {
              sendError((short) 6, "User isn't logged in");
              return;
          }
  
          String fileName = new String(message, 2, message.length - 2);
          File fileToDelete = new File(filesPath, fileName);
  
          // Check if the file exists and is a regular file
          if (!fileToDelete.exists() || !fileToDelete.isFile()) {
              sendError((short) 1, "File not found");
              return;
          }
  
          // Attempt to delete the file
          if (fileToDelete.delete()) {
              byte[] ack = {0, 4, 0, 0};
  
              // Send ACK to the requesting client
              connections.send(connectionId, ack);
  
              // Use BroadcastMessage for broadcasting the file deletion message
              BroadcastMessage bcastMessage = new BroadcastMessage((byte) 9, (byte) 0, fileName);
              connections.bCast(connectionId, bcastMessage.getBytes());
          } else {
              sendError((short) 0, "Error deleting the file");
          }
      } finally {
          connections.lock.writeLock().unlock();
      }
  }
  
    if (opCode == 10) {
      if (!loggedIn) {
        sendError((short) (6), "User isn't logged in");
        return;
      }
      loggedIn = false;
      connections.logOut(user);
      user = "None";
      byte[] ack = { 0, 4, 0, 0 };
      connections.send(connectionId, ack);
      System.out.println(user);
    }
  }

  @Override
  public boolean shouldTerminate() {
    return shouldTerminate;
  }
  private byte[] concatenateArrays(byte[][] arrays) {
    int totalLength = 0;
    for (byte[] array : arrays) {
      totalLength += array.length;
    }
  
    byte[] result = new byte[totalLength];
    int offset = 0;
    for (byte[] array : arrays) {
      System.arraycopy(array, 0, result, offset, array.length);
      offset += array.length;
    }
    return result;
  }
  private byte[] concatenateArrays(List<byte[]> arrays) {
    int totalLength = 0;
    for (byte[] array : arrays) {
      totalLength += array.length;
    }
  
    byte[] result = new byte[totalLength];
    int offset = 0;
    for (byte[] array : arrays) {
      System.arraycopy(array, 0, result, offset, array.length);
      offset += array.length;
    }
    return result;
  }
  
  public static byte[] concatenateArrays(byte[] array1, byte[] array2) {
    // Calculate the size of the concatenated array
    int totalLength = array1.length + array2.length;

    // Create a new byte array to hold the concatenated data
    byte[] result = new byte[totalLength];

    // Copy the contents of the first array into the result array
    System.arraycopy(array1, 0, result, 0, array1.length);

    // Copy the contents of the second array into the result array
    System.arraycopy(array2, 0, result, array1.length, array2.length);

    return result;
  }

  public void sendError(short opCode, String message) {
      // Convert opCode to byte array
      byte[] opCodeByteArray = ByteBuffer.allocate(2).putShort(opCode).array();
  
      // Concatenate error code and opCodeByteArray
      byte[] errorStart = concatenateArrays(errorCodes, opCodeByteArray);
  
      // Convert the error message to bytes using UTF-8 encoding
      byte[] errorMsg = (message + '\0').getBytes(StandardCharsets.UTF_8);
  
      // Concatenate errorStart and errorMsg
      byte[] errorData = concatenateArrays(errorStart, errorMsg);
  
      // Send the error data using the connections object (assuming it's properly initialized)
      connections.send(connectionId, errorData);
  }
  
  
  public List<String> getFileNames() {
    try {
        return Files.list(Paths.get(filesPath))
                .filter(Files::isRegularFile)
                .map(Path::getFileName)
                .map(Path::toString)
                .collect(Collectors.toList());
    } catch (IOException e) {
        System.out.println("Error reading files from the folder: " + e.getMessage());
        return Collections.emptyList(); // or handle the exception in a way that suits your requirements
    }
}
  public static List<byte[]> splitByteArray(byte[] byteArray) {
    return splitByteArray(byteArray, 512);
}

private static List<byte[]> splitByteArray(byte[] byteArray, int splitSize) {
    List<byte[]> splits = new ArrayList<>();
    int startIndex = 0;

    while (startIndex < byteArray.length) {
        int endIndex = Math.min(startIndex + splitSize, byteArray.length);
        byte[] chunk = Arrays.copyOfRange(byteArray, startIndex, endIndex);
        splits.add(chunk);
        startIndex += splitSize;
    }

    return splits;
}
}
