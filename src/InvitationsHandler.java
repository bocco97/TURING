import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class InvitationsHandler implements Runnable {

    private String username;
    private String serverAddress;
    private int nport;

    public InvitationsHandler(String usrnm, String addr, int n){
        if(usrnm == null || addr == null) throw new NullPointerException();
        username=usrnm;
        serverAddress=addr;
        nport=n;
    }
    @Override
    public void run() {
        SocketChannel server;
        try {
            SocketAddress address = new InetSocketAddress(serverAddress, nport);
            server = SocketChannel.open(address);
        }
        catch(java.net.ConnectException ex){
            System.out.println(">>>> Attention! Connection refused! No server found on that address/port ");
            return;
        }
        catch(IOException e){
            e.printStackTrace();
            return;
        }
        ByteBuffer bb = ByteBuffer.allocate(100);
        bb.put(("InvitationsHandler/%/"+username).getBytes());
        bb.flip();
        try{
            server.write(bb);
            bb.clear();
            ByteBuffer b1 = ByteBuffer.allocate(4);
            server.read(b1);
            b1.flip();
            int bytesToRead = b1.getInt();
            //controllo se è presente qualche invito pendente da ricevere
            if(bytesToRead>0){
                ByteBuffer buff = ByteBuffer.allocate(bytesToRead);
                server.read(buff);
                buff.flip();
                String [] allInvitations = new String(buff.array()).split("/%/");
                for(String s: allInvitations){
                    String[] invitation = s.split(" ");
                    System.out.println("User '"+invitation[0]+"' shared the file '"+invitation[1]+"' with you");
                }
            }
            b1.clear();
            server.read(b1);
            b1.flip();
            int answer_size= b1.getInt();
            ByteBuffer b = ByteBuffer.allocate(answer_size);
            server.read(b);
            b.flip();
            String res = new String(b.array());
            if(res.equals("error!!")){
                System.out.println("ERROR while starting the Invitations Handler.");
            }
            b.clear();
        }
        catch(IOException e){
            System.out.println("Error sending username to server");
        }
        while (!Thread.currentThread().isInterrupted()){
            try {
                ByteBuffer b = ByteBuffer.allocate(4);
                //ricevo gli eventuali inviti dal server
                if(server.read(b)>0) {
                    b.flip();
                    int byteToRead=b.getInt();
                    b.clear();
                    ByteBuffer buf = ByteBuffer.allocate(byteToRead);
                    server.read(buf);
                    buf.flip();
                    String allInvitations = new String(buf.array());
                    buf.clear();
                    String[] invitations = allInvitations.split(" ");
                    System.out.println("User '"+invitations[0]+"' shared the file '"+invitations[1]+"' with you");
                }
                else{
                    return;
                }
            }
            catch(IOException e){
                System.out.print("");
            }
        }

    }
}
