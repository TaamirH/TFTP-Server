package bgu.spl.net.srv;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConnectionsImpl<T> implements Connections<T> {

  public ConcurrentHashMap<Integer, BlockingConnectionHandler<T>> connections;
  public ConcurrentHashMap<String, Integer> userSessions;
  public ReentrantReadWriteLock lock;


  public ConnectionsImpl() {
    connections = new ConcurrentHashMap<>();
    userSessions = new ConcurrentHashMap<>();
    lock = new ReentrantReadWriteLock();
  }

  @Override
  public void connect(int connectionId, BlockingConnectionHandler<T> handler) {
    if (connections.get(connectionId) != null) return;
    connections.put(connectionId, handler);
  }

  @Override
  public boolean send(int connectionId, T msg) {
    if (connections.get(connectionId) == null) return false;
    connections.get(connectionId).send(msg);
    return true;
  }

  @Override
  public void disconnect(int connectionId) {
    if (connections.get(connectionId) != null) connections.remove(connectionId);
  }

  public Integer logged(String userName) {
    return userSessions.get(userName);
  }

  public void logIn(String userName, int connectionId) {
    userSessions.put(userName, connectionId);
  }

  public void logOut(String userName) {
    userSessions.remove(userName);
  }

  public BlockingConnectionHandler<T> getConnectionHandler(int connectionId) {
    return connections.get(connectionId);
  }

public void broadcast(int connectionId, T msg) {
    for (Map.Entry<Integer, BlockingConnectionHandler<T>> entry : connections.entrySet()) {
        int newId = entry.getKey();
        if (newId != connectionId) {
            BlockingConnectionHandler<T> handler = entry.getValue();
            handler.send(msg);
        }
    }
}
}
