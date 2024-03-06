package bgu.spl.net.impl.tftp;

import java.util.concurrent.ConcurrentHashMap;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

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
        // if (!shouldTerminate())

    }

    @Override
    public boolean shouldTerminate() {
        this.connections.disconnect(this.connectionId);
        ids_login.remove(this.connectionId);
        return shouldTerminate;
    } 


    
}
