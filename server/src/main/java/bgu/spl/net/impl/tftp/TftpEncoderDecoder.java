package bgu.spl.net.impl.tftp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    private static final int MAX_BUFFER_SIZE = 1024;
    private static final String CHARSET = StandardCharsets.UTF_8.name();

    private int opCode = 0;
    private final byte[] buffer = new byte[MAX_BUFFER_SIZE];
    private int nextIndex = 0;
    private byte case3 ;
    private short countdown=-1;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        write(nextByte);
         if (nextIndex == 2) {
            opCode = nextByte;
            if (opCode==6 || opCode==10){
                return popString();
            }
        } else if (opCode==4 && nextIndex==4){
            return popString();
        } else if (opCode==3 && nextIndex==3){
            case3 = nextByte;
        } else if (opCode==3 && nextIndex==4){
            countdown = (short) (((short) case3) << 8 | (short) (nextByte));
        }
        if (nextByte==0 && nextIndex>1){
        if (opCode==7 ||opCode==8 ||opCode==1 ||opCode==2 ||opCode==6)
            return popString();
        if(opCode==5 && nextIndex>4)    
            return popString();
        if(opCode==9 && nextIndex>3)    
            return popString();
        }
        if (opCode==3 && countdown==0){
            return popString();
        }
        return null;
    }
    private void write(byte nextByte){
        buffer[nextIndex]= nextByte;
        if (opCode==3 && countdown>0){
            countdown--;
        }
        nextIndex++;
    }

 private byte[] popString() {
        opCode = 0;
        nextIndex = 0;
        countdown=-1;
        return Arrays.copyOfRange(buffer, 0, nextIndex);
    }

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