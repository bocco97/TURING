import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentLinkedQueue;

public class FileSender implements Runnable {

    private String file;
    private int sect;
    private SocketChannel cl;
    private Hashtable<String,Permissions> ft;
    private String user;
    private ConcurrentLinkedQueue<SocketChannelWithName> qu;
    private ChatAddressesGenerator cag;

    public FileSender(String filename, int section, Hashtable<String,Permissions> fileTable, String username, SocketChannel client,ConcurrentLinkedQueue<SocketChannelWithName> q,ChatAddressesGenerator CAG){
        file=filename;
        sect=section;
        ft=fileTable;
        user=username;
        cl=client;
        qu=q;
        cag= CAG;
    }

    public void run() {
        File f = new File(file);
        ByteBuffer b = ByteBuffer.allocate(4);
        try{
            if(!ft.containsKey(file)){
                System.out.println("File to transfer not found");
                b.putInt(0);
                b.flip();
                cl.write(b);
                b.clear();
                qu.add(new SocketChannelWithName(cl,user));
                return;
            }
            if(!ft.get(file).isAuthorized(user)){
                b.putInt(-1);
                b.flip();
                cl.write(b);
                b.clear();
                qu.add(new SocketChannelWithName(cl,user));
                return;
            }
            //se la sezione che voglio editare è libera, la blocco e invio il file al client
            try {
                if(!f.exists()) f.mkdir();
                Permissions p = ft.get(file);
                if (p.lock_section(sect, user)) {
                    File fi = new File(file + "/" + sect);
                    if (!fi.exists()) fi.createNewFile();
                    FileInputStream fis = new FileInputStream(fi);
                    b.putInt(1);
                    b.flip();
                    cl.write(b);
                    b.clear();
                    FileChannel ch = fis.getChannel();
                    b.putInt((int) ch.size());
                    b.flip();
                    cl.write(b);
                    b.clear();
                    ch.transferTo(0, ch.size(), cl);
                    ch.close();
                    fis.close();
                    //genero un indirizzo
                    String addr= cag.getNext();
                    //se il file non aveva ancora un indirizzo associato, associo l'indirizzo appena generato
                    if(p.setIpAddr(addr)){
                        b.putInt(addr.length());
                        b.flip();
                        cl.write(b);
                        b.clear();
                        ByteBuffer address = ByteBuffer.allocate(addr.length());
                        address.put(addr.getBytes());
                        address.flip();
                        cl.write(address);
                        address.clear();
                    }
                    //altrimenti restituisco l'indirizzo generato
                    else{
                        cag.returnAddress(addr);
                        addr=p.getIpAddr();
                        b.putInt(addr.length());
                        b.flip();
                        cl.write(b);
                        b.clear();
                        ByteBuffer address = ByteBuffer.allocate(addr.length());
                        address.put(addr.getBytes());
                        address.flip();
                        cl.write(address);
                        address.clear();
                    }
                    qu.add(new SocketChannelWithName(cl,user));
                }
                //la sezione è già bloccata da qualcun altro che sta facendo la edit
                else {
                    b.putInt(-2);
                    b.flip();
                    cl.write(b);
                    b.clear();
                    qu.add(new SocketChannelWithName(cl,user));
                }
            }
            catch (IllegalArgumentException e){
                System.out.println("Section to transfer not found");
                b.putInt(0);
                b.flip();
                cl.write(b);
                b.clear();
                qu.add(new SocketChannelWithName(cl,user));
            }
        }
        catch(FileNotFoundException e){
            System.out.println("File to transfer not found");
            b.putInt(0);
            b.flip();
            try {
                cl.write(b);
                b.clear();
                qu.add(new SocketChannelWithName(cl,user));
            }
            catch (IOException ex){
                System.out.println("Si è verificato un errore di I/O .");
            }
        }
        catch (IOException e){
            System.out.println("Si è verificato un errore di I/O nel trasferimento del file.");
        }
    }
}
