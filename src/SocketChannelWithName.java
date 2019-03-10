import java.nio.channels.SocketChannel;

//OVERVIEW: classe utilizzata per mantenere l'associazione tra un SocketChannel e lo username dell'utente associato

public class SocketChannelWithName {
    public SocketChannel ch;
    public String username;

    public SocketChannelWithName(SocketChannel c, String usrnm){
        ch=c;
        username=usrnm;
    }
}
