package bgu.spl.net.impl.tftp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    private static final int MAX_BUFFER_SIZE = 1024;
    private static final String CHARSET = StandardCharsets.UTF_8.name();

    private int currentOpcode = 0;
    private int currentStage = 0;
    private final byte[] buffer = new byte[MAX_BUFFER_SIZE];
    private int currentIndex = 0;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        if (nextByte == 0 && currentStage == 0) {
            currentStage = 1;
        } else if (currentStage == 1) {
            currentOpcode = nextByte;
            currentStage = 2;

        } else if (nextByte == 0 && currentStage == 2) {
            if (isClientToServerOpcode(currentOpcode)) {
                return popString();
            }
        }
        return null;
    }
    private boolean isClientToServerOpcode(int opcode) {
        return opcode >= 1 && opcode <= 10;
    }

 private byte[] popString() {
        currentOpcode = 0;
        currentStage = 0;
        currentIndex = 0;
        return Arrays.copyOfRange(buffer, 0, currentIndex);
    }


    // private void pushByte(Byte nextByte) {
    //     if (currentIndex >= buffer.length) {
    //         throw new RuntimeException("Buffer overflow during decoding");
    //     }
    //     buffer[currentIndex++] = nextByte;
    // }


    @Override
    public byte[] encode(byte[] message) {
        return message;
    }
                    
    private String getOpcodeName(int opcode) {
        switch (opcode) {
            case 1:
                return "RRQ (Read Request)";
            case 2:
                return "WRQ (Write Request)";
            case 3:
                return "DATA (Data)";
            case 4:
                return "ACK (Acknowledgement)";
            case 5:
                return "ERROR (Error)";
            case 6:
                return "DIRQ (Directory Listing Request)";
            case 7:
                return "LOGRQ (Login Request)";
            case 8:
                return "DELRQ (Delete File Request)";
            case 9:
                return "BCAST (Broadcast File)";
            case 10:
                return "DISC (Disconnect)";
            default:
                return "Unknown Opcode: " + opcode;
        }
    }
    
}