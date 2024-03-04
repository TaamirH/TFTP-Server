package bgu.spl.net.impl.tftp;

import java.nio.charset.StandardCharsets;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    public int opCode = 0; 
    int stage = 0;
    private byte[] bytes = new byte[1 << 10]; //start with 1k
    private int index =0; 

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        if (nextByte == 0 && stage==0) {
            stage = 1;
        }
        else if (stage==1){
            opCode=nextByte;
            stage=2;
            if (nextByte==6){
                return popString();
            }
            if (nextByte==10){
                return popString();
            }
        }
        else if (nextByte==0 && 2==stage){
            return popString();
        }
        return null;
    }
    byte[] popString(){
        opCode=0;
        stage=0;
        index = 0;
        return bytes;
    }

    void pushByte(Byte nextByte){
        bytes [index++]= nextByte;
    }

    @Override
    public byte[] encode(byte[] message) {
        return message;
    }}
    
                    
