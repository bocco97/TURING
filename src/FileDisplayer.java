import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentLinkedQueue;

public class FileDisplayer implements Runnable {

    private String file;
    private int sect;
    private SocketChannel cl;
    private Hashtable<String,Permissions> ft;
    private String user;
    private ConcurrentLinkedQueue<SocketChannelWithName> qu;

    public FileDisplayer(String filename, int section, Hashtable<String,Permissions> fileTable, String username, SocketChannel client,ConcurrentLinkedQueue<SocketChannelWithName> q){
        file=filename;
        sect=section;
        ft=fileTable;
        user=username;
        cl=client;
        qu=q;
    }

    @Override
    public void run() {
        File f = new File(file);
        try {
            if(!ft.containsKey(file)){
                throw new FileNotFoundException();
            }
            //se il client richiede di vedere un file per cui non ha i permessi, invio -1 come risposta
            if(!ft.get(file).isAuthorized(user)){
                ByteBuffer b = ByteBuffer.allocate(4);
                b.putInt(-1);
                b.flip();
                cl.write(b);
                b.clear();
                qu.add(new SocketChannelWithName(cl,user));
                return;
            }
            File fdir = new File(file);
            if(!fdir.exists()) f.mkdir();
            //se sect == 0, devo inviare tutte le sezoni del file richiesto
            if (sect == 0){
                ByteBuffer b = ByteBuffer.allocate(4);
                //invio al client il numero di sezioni che dovrà ricevere
                int nsect= ft.get(file).getNsections();
                b.putInt(nsect);
                b.flip();
                cl.write(b);
                b.clear();
                for (int i =1; i<=nsect; i++) {
                    //se la sezione del file che devo trasferire è in corso di aggiornamento, attendo che l'aggiornamento sia terminato
                    while(ft.get(file).isUpdating(i)){
                        ;
                    }
                    File fsect = new File(file + "/" + i);
                    if(!fsect.exists()) fsect.createNewFile();
                    FileInputStream fis = new FileInputStream(fsect);
                    FileChannel ch = fis.getChannel();
                    b.putInt((int)ch.size());
                    b.flip();
                    cl.write(b);
                    b.clear();
                    ch.transferTo(0,ch.size(),cl);
                    ch.close();
                    fis.close();
                }
                qu.add(new SocketChannelWithName(cl,user));
            }
            //caso in cui devo inviare una sola sezione di un file
            else{
                if(sect<=ft.get(file).getNsections()) {
                    File fsect = new File(file + "/" + sect);
                    if (!fsect.exists()) fsect.createNewFile();
                    FileInputStream fis = new FileInputStream(fsect);
                    ByteBuffer b = ByteBuffer.allocate(4);
                    //invio al client il numero di sezioni che dovrà ricevere (1)
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
                    qu.add(new SocketChannelWithName(cl, user));
                }
                else throw new FileNotFoundException();
            }
        }
        //se il file richiesto dal client non esiste (o la sezione del file richiesta non esiste), invio 0 come risposta al client
        catch(FileNotFoundException | NullPointerException e){
            System.out.println("File to transfer not found");
            ByteBuffer b = ByteBuffer.allocate(4);
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
