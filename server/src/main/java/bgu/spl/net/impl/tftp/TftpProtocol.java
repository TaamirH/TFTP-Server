package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.ConnectionsImpl;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {

  String filesPath = System.getProperty("user.dir")+"/" + "Flies";
  String usrName = "None";
  private int connectionId;
  private ConnectionsImpl<byte[]> connections;
  boolean shouldTerminate = false;
  byte[] errorCode = { 0, 5 };
  String lastFileName = "";
  ByteBuffer data;
  short readCounter = 1;
  short writeCounter = 1;
  ConcurrentLinkedQueue<byte[]> readQueue;
  FileOutputStream outputStream;
  public static final int MAX_PACKET_SIZE = 512;
  boolean isLoggedIn;

  
  @Override
  public void start(
    int connectionId_,
    ConnectionsImpl<byte[]> connections_
  ) {
    this.connectionId = connectionId_;
    this.connections = connections_;
    isLoggedIn = false;

    readQueue = new ConcurrentLinkedQueue<>();
  }

  @Override
  public void process(byte[] message) {
    short opCode = (short) (
      ((short) message[0] & 0xff) << 8 | (short) (message[1] & 0xff)
    );
    switch(opCode){
      case 1: 
        processReadRequest(message);
        break;

      case 2:  
        processWriteRequest(message);
        break;
    
      case 3:  
        processReceivedData(message);
        break;
    
      case 4:  
        processAcknowledgment(message);
        break;
    
      case 5:{
        if (!isLoggedIn) {
          sendError((short) 6, "User not logged in , please log in");
          return;
        }
        String errorMsg = new String(message, 4, message.length - 4);
        System.err.println("Error: " + errorMsg);
        break;
      }

      case 6:{
        proccessDirq(message);
        break;
      }
  
    
      case 7:{
        if (isLoggedIn) {
          sendError((short) 7, "User is already logged in");
          return;
        } else {
          String userName = new String(message, 2, message.length - 2); // getting the string from the message
          if (connections.logged(userName) != null) {
            sendError((short) 0, "User is already logged in");
            return;
          } else {
            isLoggedIn = true;
            connections.logIn(userName, connectionId);
            usrName = userName;
            byte[] ack = { 0, 4, 0, 0 };
            connections.send(connectionId, ack);
            break;
          }
        }
      }
      case 8:
        processDeleteFile(message);
        break;
    
      case 10:{
      if (!isLoggedIn) {
        sendError((short) (6), "User not logged in , please log in");
        return;
      }
      isLoggedIn = false;
      connections.logOut(usrName);
      usrName = "None";
      byte[] ack = { 0, 4, 0, 0 };
      connections.send(connectionId, ack);
      System.out.println(usrName);
      break;
    }
    
    }
  }
  @Override
  public boolean shouldTerminate() {
    return shouldTerminate;
  }

  public static byte[] mixArrays(byte[] array1, byte[] array2) {
    int len = array1.length + array2.length;
    byte[] result = new byte[len];
    System.arraycopy(array1, 0, result, 0, array1.length);
    System.arraycopy(array2, 0, result, array1.length, array2.length);

    return result;
  }

  // functions that sends Errors to users
  public void sendError(short opCode, String message) {
    byte[] opCodeByteArray = new byte[] {
      (byte) (opCode >> 8),
      (byte) (opCode & 0xff),
    };
    byte[] errorStart = mixArrays(errorCode, opCodeByteArray);
    byte[] errorMsg = new String(message + new String(new byte[] { 0 }))
      .getBytes();
    connections.send(connectionId, mixArrays(errorStart, errorMsg));
  }

  public List<String> getFileNames() {
    List<String> fileNamesList = new ArrayList<>();
    File folder = new File(filesPath);
    System.out.println(filesPath);

    // Check if the folder exists and is a directory
    if (folder.exists() && folder.isDirectory()) {
      // Get all files in the folder
      File[] files = folder.listFiles();
      if (files != null) {
        for (File file : files) {
          // Add file names to the list
          fileNamesList.add(file.getName());
        }
      }
    } else {
      System.out.println("Folder does not exist");
    }

    return fileNamesList;
  }

  public static List<byte[]> splitByteArray(byte[] byteArray) {
    int chunkSize = 512;
    int numChunks = (byteArray.length + chunkSize - 1) / chunkSize;
    List<byte[]> chunks = new ArrayList<>();

    for (int i = 0; i < numChunks - 1; i++) {
      int startIndex = i * chunkSize;
      int endIndex = startIndex + chunkSize;
      byte[] chunk = new byte[chunkSize];
      System.arraycopy(byteArray, startIndex, chunk, 0, chunkSize);
      chunks.add(chunk);
    }

    // Last chunk
    int lastChunkStart = (numChunks - 1) * chunkSize;
    int lastChunkSize = byteArray.length - lastChunkStart;
    byte[] lastChunk = new byte[lastChunkSize];
    System.arraycopy(byteArray, lastChunkStart, lastChunk, 0, lastChunkSize);
    chunks.add(lastChunk);

    return chunks;
  }

  private void processReadRequest(byte[] message) {
    if (!isLoggedIn) {
      sendError((short) 6, "User not logged in , please log in");
      return;
    }

    String fileName = new String(message, 2, message.length - 2);
    String filePath = filesPath + File.separator + fileName;

    if (!fileExists(filePath)) {
      sendError((short) 1, "File not found");
    } else {
      readFileAndSendChunks(filePath);
    }
  }

  private boolean fileExists(String filePath) {
    connections.lock.readLock().lock();
    File file = new File(filePath);
    boolean exists = file.exists();
    connections.lock.readLock().unlock();
    return exists;
  }

  private void readFileAndSendChunks(String filePath) {
    try (FileInputStream fis = new FileInputStream(filePath)) {
      FileChannel channel = fis.getChannel();
      ByteBuffer byteBuffer = ByteBuffer.allocate(512);

      int bytesRead;
      while ((bytesRead = channel.read(byteBuffer)) != -1) {
        byteBuffer.rewind();

        byte[] chunk = new byte[bytesRead];
        byteBuffer.get(chunk);

        byte[] blockNum = getBlockNumberBytes(readCounter);
        byte[] packetSize = getPacketSizeBytes(bytesRead);

        byte[] start = mixArrays(new byte[]{0, 3}, packetSize, blockNum);

        readCounter++;
        readQueue.add(mixArrays(start, chunk));
        byteBuffer.clear();
      }

      readCounter = 1;
    } catch (IOException e) {
      e.printStackTrace();
      sendError((short) 0, "Could not read the file");
      return;
    }

    connections.send(connectionId, readQueue.remove());
  }

  private short getShortValue(byte[] array, int startIndex) {
    return (short) (((short) array[startIndex] & 0xff) << 8 | (short) (array[startIndex + 1] & 0xff));
  }

  private byte[] getBlockNumberBytes(short blockNumber) {
    return new byte[]{
        (byte) (blockNumber >> 8),
        (byte) (blockNumber & 0xff),
    };
  }

  private byte[] getPacketSizeBytes(int packetSize) {
    return new byte[]{
        (byte) (packetSize >> 8),
        (byte) (packetSize & 0xff),
    };
  }

  private byte[] mixArrays(byte[]... arrays) {
    int totalLength = Arrays.stream(arrays).mapToInt(arr -> arr.length).sum();
    byte[] result = new byte[totalLength];
    int currentIndex = 0;

    for (byte[] array : arrays) {
      System.arraycopy(array, 0, result, currentIndex, array.length);
      currentIndex += array.length;
    }

    return result;
  }

  private void processWriteRequest(byte[] message) {
    if (!isLoggedIn) {
      sendError((short) 6, "User not logged in , please log in");
      return;
    }

    String fileName = new String(message, 2, message.length - 2);
    String filePath = filesPath + File.separator + fileName;

    if (fileExists(filePath)) {
      sendError((short) 5, "File already exists");
    } else {
      createAndSendAck(fileName);
    }
  }

  private void createAndSendAck(String fileName) {
    connections.lock.writeLock().lock();
    File file = new File(filesPath, fileName);

    try {
      boolean created = file.createNewFile();

      if (created) {
        System.out.println("File created successfully.");
      } else {
        connections.lock.writeLock().unlock();
        sendError((short) 0, "Could not create the file");
        return;
      }
    } catch (IOException e) {
      connections.lock.writeLock().unlock();
      sendError((short) 0, "Could not create the file");
      return;
    }

    byte[] ack = {0, 4, 0, 0};
    try {
      outputStream = new FileOutputStream(file);
    } catch (IOException e) {
      // Handle exception
    }

    connections.send(connectionId, ack);
  }
  private void processReceivedData(byte[] message) {
    if (!isLoggedIn) {
        sendError((short) 6, "User not logged in , please log in");
        return;
    }

    short blockNum = getShortValue(message, 4);
    short blockLength = getShortValue(message, 2);

    connections.lock.writeLock().lock();

    if (blockNum != writeCounter) {
        connections.lock.writeLock().unlock();
        sendError((short) 0, "wrong block");
        return;
    }

    if (blockLength > 0) {
        byte[] data = Arrays.copyOfRange(message, 6, message.length);

        try {
            outputStream.write(data);
        } catch (IOException e) {
            connections.lock.writeLock().unlock();
            sendError((short) 0, "Could not write to the file");
            return;
        }
    }

    if (blockLength == 512) {
        // Continue receiving next block
        writeCounter++;
        sendAcknowledgment(message);
    } else {
        // Last block received, finalize transfer
        finalizeFileTransfer();
    }

    connections.lock.writeLock().unlock();
}

private void finalizeFileTransfer() {
    writeCounter = 1;
    byte[] broadcastStart = {0, 9, 1};
    String fileNameWithNullByte = lastFileName + "\0";
    connections.broadcast(connectionId, mixArrays(broadcastStart, fileNameWithNullByte.getBytes()));
    // Optionally close the file output stream or perform any other finalization tasks
}

private void sendAcknowledgment(byte[] message) {
    byte[] ack = {0, 4, message[4], message[5]};
    connections.send(connectionId, ack);
}
private void processAcknowledgment(byte[] message) {
  if (!isLoggedIn) {
      sendError((short) 6, "User not logged in , please log in");
      return;
  }

  connections.lock.readLock().lock(); // Acquire the read lock

  try {
      short blockNum = getShortValue(message, 2);

      if (blockNum != readCounter) {
          sendError((short) 0, "Wrong block");
          return;
      }

      if (readQueue.isEmpty()) {
          // No more data to send, reset counter
          readCounter = 1;
      } else {
          // Send the next data packet and increment the counter
          connections.send(connectionId, readQueue.remove());
          readCounter++;
      }
  } finally {
      connections.lock.readLock().unlock(); // Release the read lock in a finally block
  }
}

private void processListFiles(byte[] message) {
    if (!isLoggedIn) {
        sendError((short) 6, "User not logged in , please log in");
        return;
    }

    connections.lock.readLock().lock();

    List<String> fileNamesList = getFileNames();
    Map<Integer, byte[]> chunks = createFileListChunks(fileNamesList);

    sendFileListChunks(chunks);

    connections.lock.readLock().unlock();
}

private Map<Integer, byte[]> createFileListChunks(List<String> fileNamesList) {
    Map<Integer, byte[]> chunks = new HashMap<>();

    for (int i = 0; i < fileNamesList.size(); i++) {
        String fileName = fileNamesList.get(i);
        byte[] fileNameBytes = (fileName + "\0").getBytes();

        short packetSizeShort = (short) fileNameBytes.length;
        byte[] packetSizeBytes = new byte[]{
                (byte) (packetSizeShort >> 8),
                (byte) (packetSizeShort & 0xff),
        };
        byte[] indexByte = new byte[]{
                (byte) ((short) i + 1 >> 8),
                (byte) (i + 1 & 0xff),
        };
        byte[] start = {
                0,
                3,
                packetSizeBytes[0],
                packetSizeBytes[1],
                indexByte[0],
                indexByte[1],
        };
        byte[] msg = mixArrays(start, fileNameBytes);
        chunks.put(i + 1, msg);
    }

    return chunks;
}

private void sendFileListChunks(Map<Integer, byte[]> chunks) {
  int sentChunkIndex = 1;
  for (int index : chunks.keySet()) {
    if (index == sentChunkIndex) {
      connections.send(connectionId, chunks.get(index));
      sentChunkIndex++;
    }
  }
}

private void processDeleteFile(byte[] message) {
  if (!isLoggedIn) {
      sendError((short) 6, "User not logged in , please log in");
      return;
  }

  String fileName = extractFileName(message);

  deleteFileAndNotify(fileName);
}

private String extractFileName(byte[] message) {
  return new String(message, 2, message.length - 2);
}

private void deleteFileAndNotify(String fileName) {
  connections.lock.writeLock().lock();

  File file = new File(filesPath, fileName);

  if (!file.exists()) {
      connections.lock.writeLock().unlock();
      sendError((short) 1, "File not found");
      return;
  }

  if (tryDeleteFile(file)) {
      notifyFileDeletion(fileName);
  } else {
      connections.lock.writeLock().unlock();
      sendError((short) 0, "Could not delete the file");
  }
}

private boolean tryDeleteFile(File file) {
  try {
      boolean deletionResult = file.delete();

      if (deletionResult) {
          connections.lock.writeLock().unlock(); // Unlock here to handle the case where deletion was successful
      }

      return deletionResult;
  } catch (SecurityException e) {
      return false;
  }
}

private void notifyFileDeletion(String fileName) {
  byte[] broadcastStart = {0, 9, 0};
  String fileNameWithNullByte = fileName + "\0";
  byte[] ack = {0, 4, 0, 0};
  
  connections.send(connectionId, ack);
  connections.broadcast(connectionId, mixArrays(broadcastStart, fileNameWithNullByte.getBytes()));
}

private void proccessDirq(byte[] message) {
  if (!isLoggedIn) {
    sendError((short) 6, "User not logged in , please log in");
    return;
  }
  connections.lock.readLock().lock();
  List<String> fileNamesList = getFileNames();
  byte[] msg = createFileListMessage(fileNamesList);
  connections.send(connectionId, msg);
  connections.lock.readLock().unlock();


  }
private byte[] createFileListMessage(List<String> fileNamesList) {
  StringBuilder fileListBuilder = new StringBuilder();
  for (String fileName : fileNamesList) {
      fileListBuilder.append(fileName).append('\0');
  }

  byte[] data = fileListBuilder.toString().getBytes();
  return prepareFileListPacket(data);
}

private byte[] prepareFileListPacket(byte[] data) {
  int numPackets = (int) Math.ceil((double) data.length / MAX_PACKET_SIZE);
  List<byte[]> packets = new ArrayList<>();

  for (int i = 0; i < numPackets; i++) {
      int chunkSize = Math.min(data.length - i * MAX_PACKET_SIZE, MAX_PACKET_SIZE);
      byte[] chunk = Arrays.copyOfRange(data, i * MAX_PACKET_SIZE, i * MAX_PACKET_SIZE + chunkSize);

      byte[] packetSizeBytes = getPacketSizeBytes(chunkSize);
      byte[] indexByte = getIndexByte(i + 1);
      byte[] header = {0, 3, packetSizeBytes[0], packetSizeBytes[1], indexByte[0], indexByte[1]};
      byte[] packet = mixArrays(header, chunk);

      packets.add(packet);
  }

  return packets.get(0); 
}


private byte[] getIndexByte(int index) {
  return new byte[] {(byte) (index >> 8), (byte) (index & 0xff)};
}


}