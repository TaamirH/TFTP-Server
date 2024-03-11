package bgu.spl.net.impl.tftp;

public class BroadcastMessage {
    private final byte opcode;
  private final byte data1;
  private final byte[] data2; // Can be a byte array for flexible data

  public BroadcastMessage(byte opcode, byte data1, String data2) {
    this.opcode = opcode;
    this.data1 = data1;
    this.data2 = data2.getBytes(); // Convert String to byte array
  }

  public byte[] getBytes() {
    // Combine opcode, data1, and data2 into a single byte array
    byte[] message = new byte[data2.length + 2];
    message[0] = opcode;
    message[1] = data1;
    System.arraycopy(data2, 0, message, 2, data2.length);
    return message;
  }
}

