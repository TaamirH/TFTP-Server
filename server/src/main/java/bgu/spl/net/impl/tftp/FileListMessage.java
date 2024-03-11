package bgu.spl.net.impl.tftp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FileListMessage {

  private final List<String> fileNames;

  public FileListMessage(List<String> fileNames) {
      // Deep copy to ensure immutability
      this.fileNames = Collections.unmodifiableList(new ArrayList<>(fileNames));
  }

  public byte[] getMessageBytes() {
      // Build the message byte array
      StringBuilder sb = new StringBuilder();
      for (String fileName : fileNames) {
          sb.append(fileName).append("\0"); // Use null byte as separator
      }
      String fileNamesString = sb.toString();

      // Combine total file count and file names string into a single byte array
      int totalFileCount = fileNames.size();
      byte[] totalFileCountBytes = new byte[]{(byte) (totalFileCount >> 8), (byte) totalFileCount};
      byte[] fileNamesBytes = fileNamesString.getBytes();
      return concatenateArrays(totalFileCountBytes, fileNamesBytes);
  }

  private static byte[] concatenateArrays(byte[]... arrays) {
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
}
