import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentLinkedQueue;

public class FileReceiver implements Runnable {

    private String user;
    private String filename;
    private Hashtable<String,Permissions> filetable;
    private SocketChannel client;
    private SocketChannel recClient;
    private int size;
    private ConcurrentLinkedQueue<SocketChannelWithName> qu;
    private ChatAddressesGenerator cag;


    public FileReceiver(String us, String f, Hashtable<String,Permissions> ft, SocketChannel cl,SocketChannel rcl,int sz, ConcurrentLinkedQueue<SocketChannelWithName> q,ChatAddressesGenerator CAG){
        user=us;
        if(f.indexOf('\0')!=-1) f=f.substring(0,f.indexOf('\0'));
        filename=f;
        filetable=ft;
        client=cl;
        recClient=rcl;
        size=sz;
        qu=q;
        cag=CAG;
    }

    @Override
    public void run() {
        Permissions p =filetable.get(filename);
        int sectionUpdated = p.getSectionEditedBy(user);
        if(sectionUpdated>0){
            File fnew= new File(filename + "/" + sectionUpdated+"(new)");;
            try {
                // path della vecchia sezione del file
                File fold= new File(filename + "/" + sectionUpdated);
                // path della nuova sezione del file
                FileChannel fc;
                if(fnew.exists()) fnew.delete();
                fnew.createNewFile();
                fc = FileChannel.open(Paths.get(fnew.getAbsolutePath()), StandardOpenOption.APPEND);
                // trasferisco il contenuto della nuova sezione
                long position = 0;
                long missing = size;
                long part;
                while(position<size){
                    if(missing<1024) part = missing;
                    else part=1024;
                    position+= fc.transferFrom(recClient,position,part);
                    missing-=part;
                }
                fc.close();
                //setto la variabile di aggiornamento della sezione per evitare che qualche
                //client legga la sezione mentre viene aggiornata
                while(!p.update_section(sectionUpdated)){
                }
                //cancello la vecchia sezione
                if(fold.exists()) fold.delete();
                //rinomino la nuova sezione come la vecchia sezione
                fnew.renameTo(fold);
                //setto la variabile di aggiornamento a false
                p.end_update_section(sectionUpdated);
                //sblocco la sezione avendo finito di editarla
                p.unlock_section(user);
                String ip= p.revokeIpIfNotNeeded();
                if(ip!=null){
                    cag.returnAddress(ip);
                }
                ByteBuffer b = ByteBuffer.allocate(4);
                b.putInt("file received".length());
                b.flip();
                client.write(b);
                b.clear();
                ByteBuffer ans = ByteBuffer.allocate("file received".length());
                ans.put("file received".getBytes());
                ans.flip();
                client.write(ans);
                ans.clear();
                qu.add(new SocketChannelWithName(client,user));
            }
            catch(IOException e){
                System.out.println("Client "+user+" disconnected while sending the file");
                if(fnew.exists())fnew.delete();
                //inserisco nella coda un SocketChannelWithName con SocketChannel a null,
                //così il main thread capisce che il client associato si è disconnesso e deve
                //disconnettere l'utente
                qu.add(new SocketChannelWithName(null,user));
                return;
            }
        }
        else {
            try{
            ByteBuffer b = ByteBuffer.allocate(4);
            b.putInt("you were not editing".length());
            b.flip();
            client.write(b);
            b.clear();
            ByteBuffer ans = ByteBuffer.allocate("you were not editing".length());
            ans.put("you were not editing".getBytes());
            ans.flip();
            client.write(ans);
            ans.clear();
            qu.add(new SocketChannelWithName(client, user));
            }
            catch(IOException e){
                System.out.println("Si è verificato un errore di I/O");
            }
        }
    }
}
