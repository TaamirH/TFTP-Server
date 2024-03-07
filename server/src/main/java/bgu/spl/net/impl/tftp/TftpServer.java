package bgu.spl.net.impl.tftp;
import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.srv.Server;
import bgu.spl.net.srv.Connections;

public class TftpServer {

    public static void main(String[] args) {
        Server.threadPerClient(7777,
         () -> new TftpProtocol(),
          TftpEncoderDecoder::new,
            new ConnectionsImp<>()
          ).serve();


    }
    }   