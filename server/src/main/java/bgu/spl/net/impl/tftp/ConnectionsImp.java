package bgu.spl.net.impl.tftp;

import java.util.HashMap;
import java.util.Map;

import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;

public class ConnectionsImp<T> implements Connections<T> {
    private final Map<Integer, BlockingConnectionHandler<T>> handlers = new HashMap<>();

    @Override
    public void connect(int connectionId, BlockingConnectionHandler<T> handler) {
        // Check for existing connection and validate handler type
        if (handlers.containsKey(connectionId)) {
            throw new IllegalArgumentException("Connection with ID " + connectionId + " already exists");
        }
        if (!ConnectionHandler.class.isAssignableFrom(handler.getClass())) {
            throw new IllegalArgumentException("handler must be a subclass of ConnectionHandler");
        }

        // Store the handler
        handlers.put(connectionId, handler);
        // Start the handler
        new Thread(handler).start();
    }

    @Override
    public boolean send(int connectionId, T msg) {
        // Check for existing connection
        if (handlers.containsKey(connectionId)) {
            // Send the message
            try{
            handlers.get(connectionId).send(msg);
            return true;
            }catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    @Override
    public void disconnect(int connectionId) {
        // Check for existing connection
        if (handlers.containsKey(connectionId)) {
            // Close the connection
            try {
                handlers.get(connectionId).close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            // Remove the handler
            handlers.remove(connectionId);
        }
    }
}

