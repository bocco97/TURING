import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;

public class TURINGserver {
    public static void main(String[] args) throws java.rmi.RemoteException {
        int port;
        try{
            port=Integer.parseInt(args[0]);
        }
        catch(RuntimeException e){
            System.out.println("Use: java TURINGserver < port_number >");
            return;
        }

        /**------------------------------------------ strutture dati ------------------------------------------**/

        // usermap : contiene tutti gli utenti registrati
        HashMap<String,String> usermap = new HashMap<>();

        // onlineSet : contiene gli utenti attualmente online, e per ognuno il socketChannel su cui inviare gli inviti
        HashMap<String,SocketChannel> onlineSet = new HashMap<>();

        // receivingSet: contiene per ogni utente, il socketChannel dal quale ricevere i file nelle operazioni di end-edit
        HashMap<String,SocketChannel> receivingSet = new HashMap<>();

        // fileTable : contiene i nomi dei file creati con i rispettivi dati associati (Permissions)
        Hashtable<String,Permissions> fileTable = new Hashtable<>();

        // pendingInvitations : contiene tutti gli inviti pendenti di ogni utente
        Hashtable<String,LinkedList<Invitation>> pendingInvitations = new Hashtable<>();

        // keyQueue: coda usata dai thread per inserire le chiavi dopo il trasferimento di un file
        ConcurrentLinkedQueue<SocketChannelWithName> keyQueue = new ConcurrentLinkedQueue<>();

        // generator: generatore di indirizzi ip per le chat associate ai documenti
        ChatAddressesGenerator generator = new ChatAddressesGenerator();

        ThreadPoolExecutor tpe = new ThreadPoolExecutor(5,10,1000, TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>());

        /**----------------------------------------------------------------------------------------------------**/

        UserSet us = new UserSet(usermap);
        UserSetInterface stub= (UserSetInterface) UnicastRemoteObject.exportObject(us,0);
        LocateRegistry.createRegistry(4321);
        Registry r = LocateRegistry.getRegistry(4321);
        r.rebind("UserSet",stub);
        System.out.println("Remote UserSet created.");

        ServerSocketChannel serverChannel;
        Selector selector;
        try{
            serverChannel= ServerSocketChannel.open();
            ServerSocket ss = serverChannel.socket();
            InetSocketAddress address= new InetSocketAddress(port);
            ss.bind(address);
            serverChannel.configureBlocking(false);
            selector= Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        }
        catch(IOException e){
            e.printStackTrace();
            return;
        }
        while(true) {
            try {
                selector.select(500);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            Set<SelectionKey> readyKeys = selector.selectedKeys();
            Iterator<SelectionKey> it = readyKeys.iterator();
            while(it.hasNext()){
                SelectionKey key = it.next();
                it.remove();
                try{
                    if(key.isValid()&&key.isAcceptable()){
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel client = server.accept();
                        System.out.println("Connection accepted");
                        client.configureBlocking(false);
                        SelectionKey key2 = client.register(selector,SelectionKey.OP_READ);
                        key2.attach(new myAttachment("","empty"));
                    }
                    else if(key.isValid()&&key.isReadable()){
                        String owner;
                        if(key.attachment()!=null){
                            myAttachment tmp = (myAttachment) key.attachment();
                            owner=tmp.getUsername();
                        }
                        else { owner=""; }
                        SocketChannel client =(SocketChannel) key.channel();
                        ByteBuffer b= ByteBuffer.allocate(1024);
                        if(client.read(b)>0) {
                            b.flip();
                            //esamino la richiesta eseguita dal client e la elaboro
                            String res = processRequest(new String(b.array()), usermap, onlineSet,receivingSet, fileTable, owner,pendingInvitations,client,keyQueue,generator,tpe);
                            b.clear();
                            // se res!="transfer", l'operazione non era un'operazione di trasferimento di un file e posso quindi registrare la chiave in scrittura
                            if(!res.startsWith("transfer")) {
                                SelectionKey key2 = client.register(selector, SelectionKey.OP_WRITE);
                            /*  se l'operazione che mi era stata richiesta era un login, associo alla chiave un attachment contenente lo username
                                dell'utente che ha effettuato il login e il risultato dell'operazione che invierò come risposta al client */
                                if (res.startsWith("logged-")) {
                                    String username = res.substring(res.indexOf('-') + 1);
                                    System.out.println("A client has connected as : " + username);
                                    System.out.println("Users online: " + onlineSet.keySet());
                                    key2.attach(new myAttachment(username, "logged"));
                                }
                                else if(res.equals("logged_out")){
                                    key2.attach(new myAttachment("", res));
                                }
                                //creo un nuovo attachment con lo username associato precedentemente alla chiave e l'esito dell'ultima operazione eseguita
                                else {
                                    key2.attach(new myAttachment(owner, res));
                                }
                            }
                            // altrimenti, se re=="transfer", è in corso un trasferimento da o verso il client
                            // il thread che si sta occupando del trasferiento metterà il socketChannel del client nella keyQueue
                            // una volta terminato il trasferimento
                            else{
                                //azzero l'interestsSet della chiave
                                key.interestOps(0);
                            }
                            //se la richiesta era una richiesta di registrazione per un uploadingSocketChannel,
                            //cancello la chiave dal set in quanto noon voglio ricevere dati da questo socket
                            // e configuro il socket a bloccante per trasferimenti dei file futuri (end-edit)
                            if(res.equals("transfer-in")){
                                key.cancel();
                                client.configureBlocking(true);
                            }
                        }
                        //caso in cui il client è terminato senza effettuare il logout
                        //utilizzo l'attachment associato alla chiave per disconnettere l'utente,
                        //chiudere i SocketChannel associati e liberare un'eventuale sezione che stava editando
                        else{
                            myAttachment att = (myAttachment) key.attachment();
                            String user= att.getUsername();
                            //rimuovo l'utente dagli utenti online
                            if(!user.equals("")) {
                                onlineSet.remove(user).close();
                                receivingSet.remove(user).close();
                                for(String s : fileTable.keySet()){
                                    Permissions p = fileTable.get(s);
                                    p.unlock_section(user);
                                    //se era l'unico utente che stava editando un file, revoco l'indirizzo IP al file
                                    //e lo restituisco al ChatAddressGenerator
                                    String ip= p.revokeIpIfNotNeeded();
                                    if(ip!=null){
                                        generator.returnAddress(ip);
                                    }
                                }
                                System.out.println("Client logged as " + user + " got disconnected.. bye bye");
                                System.out.println("Users online: " + onlineSet.keySet());
                                key.cancel();
                            }
                            b.clear();
                        }
                    }
                    else if(key.isValid()&&key.isWritable()){
                        //prendo l'attachment dalla chiave
                        myAttachment att = (myAttachment)key.attachment();
                        SocketChannel client = (SocketChannel) key.channel();
                        //prendo la risposta da inviare al client dall'attachment
                        String ans = att.getValue();
                        ByteBuffer b = ByteBuffer.allocate(ans.length());
                        ByteBuffer bytesToSend = ByteBuffer.allocate(4);
                        //invio la dimensione della risposta
                        bytesToSend.putInt(ans.length());
                        bytesToSend.flip();
                        client.write(bytesToSend);
                        bytesToSend.clear();
                        b.put(ans.getBytes());
                        b.flip();
                        //invio la risposta
                        client.write(b);
                        b.clear();
                        SelectionKey key2 = client.register(selector,SelectionKey.OP_READ);
                        key2.attach(new myAttachment(att.getUsername(),"nop"));
                    }
                }
                catch(IOException e){
                    try{
                        key.channel().close();
                    }
                    catch (IOException ex){
                        ex.printStackTrace();
                    }
                }
            }
            //controllo se nella keyQueue è stato inserito qualche SocketChannel da ri-registrare
            while (!keyQueue.isEmpty()) {
                try {
                    SocketChannelWithName c= keyQueue.remove();
                    SelectionKey kn = null;
                    //se il SocketChannel del SocketChannelWithName è != null, lo registro in lettura
                    //per ricevere una nuova richiesta
                    if(c.ch!=null) {
                        try {
                            kn = c.ch.register(selector, SelectionKey.OP_READ);
                            kn.attach(new myAttachment(c.username, "nop"));
                        } catch (CancelledKeyException e) {
                            System.out.println("Found a non-valid key");
                        }
                    }
                    //altrimenti il client si era disconnesso mentre inviava il file
                    //eseguo le operazioni di disconnessione
                    else{
                        if(onlineSet.containsKey(c.username)) {
                            onlineSet.remove(c.username).close();
                            receivingSet.remove(c.username).close();
                            for(String s : fileTable.keySet()){
                                Permissions p = fileTable.get(s);
                                p.unlock_section(c.username);
                                //se era l'unico utente che stava editando un file, revoco l'indirizzo IP al file
                                //e lo restituisco al ChatAddressGenerator
                                String ip= p.revokeIpIfNotNeeded();
                                if(ip!=null){
                                    generator.returnAddress(ip);
                                }
                            }
                            System.out.println("Client logged as " + c.username + " got disconnected while sending a file.. bye bye");
                            System.out.println("Users online: " + onlineSet.keySet());
                        }
                    }
                }
                catch (IOException e){
                    System.out.println("Si è verificato un errore di I/O.");
                }
            }
        }
    }

    //effettua il parsing della richiesta, la elabora e restituisce il risultato
    private static String processRequest(String op, HashMap<String,String> usermap, HashMap<String,SocketChannel> onlineSet, HashMap<String,SocketChannel> receivingSet, Hashtable<String,Permissions> fileTable, String user, Hashtable<String,LinkedList<Invitation>> pendingInvitations,SocketChannel cl, ConcurrentLinkedQueue<SocketChannelWithName> q, ChatAddressesGenerator generator, ThreadPoolExecutor tpe){
        String [] elts = op.split("/%/");
        switch (elts[0]){
            case "login":{
                if(onlineSet.containsKey(elts[1])){ return "user already logged"; }
                else if(!usermap.containsKey(elts[1])){ return "username not found"; }
                else if(!usermap.get(elts[1]).equals(elts[2])){ return "wrong password"; }
                else{
                    onlineSet.put(elts[1],null);
                    return "logged".concat("-").concat(elts[1]);
                }
            }
            case "InvitationsHandler":{
                if(elts[1].indexOf('\0')!=-1) elts[1]= elts[1].substring(0,elts[1].indexOf('\0'));
                if(onlineSet.containsKey(elts[1])){
                    onlineSet.put(elts[1],cl);
                    //prendo la lista degli inviti pendenti per l'utente che si è loggato
                    List<Invitation> l = pendingInvitations.get(elts[1]);
                    String allInvitations="";
                    int bytesToSend=0;
                    //concateno gli inviti pendenti in un'unica stringa
                    if(l!=null) {
                        for (Invitation i : l) {
                            allInvitations = allInvitations.concat( i.getFrom() + " " + i.getFile() + "/%/");
                        }
                        //svuoto la lista per evitare di rinviare gli stessi inviti al prossimo login
                        l.clear();
                    }
                    bytesToSend=allInvitations.length();
                    ByteBuffer bb = ByteBuffer.allocate(4);
                    bb.putInt(bytesToSend);
                    bb.flip();
                    try {
                        //inivio gli inviti sul SocketChannel riservato
                        onlineSet.get(elts[1]).write(bb);
                        bb.clear();
                        ByteBuffer buf = ByteBuffer.allocate(bytesToSend);
                        buf.put(allInvitations.getBytes());
                        buf.flip();
                        onlineSet.get(elts[1]).write(buf);
                        buf.clear();
                    }
                    catch (IOException e){
                        System.out.println("Erorre di I/O.");
                    }
                    return "success";
                }
                else return "error!!";
            }
            case "uploadingSC":{
                //controllo che l'utente associato alla richiesta sia online
                if(onlineSet.containsKey(elts[1])){
                    //registro nel receivingSet il socketChannel per l'upload dei file
                    receivingSet.put(elts[1],cl);
                    return "transfer-in";
                }
                return "error";
            }
            case "create":{
                if(fileTable.containsKey(elts[1])){ return "file already exists"; }
                //numero delle sezioni del file da creare
                int nsections = Integer.parseInt(elts[2]);
                // creo la directory e i vari file che costituiscono le sezioni del file
                File filename = new File(elts[1]);
                if(filename.mkdir()) {
                    for (int i = 1; i <= nsections; i++) {
                        File f = new File(elts[1] + "/" + i );
                        try {
                            if (!f.createNewFile()) {
                                System.out.println("Error creating file.");
                                return "error creating file";
                            }
                        } catch (IOException e) {
                            System.out.println("I/O error while creating file.");
                        }
                    }
                    //aggiungo il file alla fileTable con il rispettivo creatore
                    fileTable.put(elts[1],new Permissions(user,nsections));
                    System.out.println("New file created. File list: "+fileTable);
                    return "file created";
                }
                else {
                    System.out.println("Error creating file!");
                    return "error creating file";
                }
            }
            case "share":{
                if(!fileTable.containsKey(elts[1])){ return "file not found"; }
                else if(!usermap.containsKey(elts[2])){ return "receiver user not found"; }
                else if(elts[2].equals(user)){return "can't share with yourself";}
                else if(!fileTable.get(elts[1]).getOwner().equals(user)){ return "you can't share this file";}
                else{
                    //controllo che l'utente da autorizzare non sia già tra gli utenti autorizzati,
                    //in tal caso non lo riaggiungo alla lista degli autorizzati per evitare duplicati
                    if(!fileTable.get(elts[1]).getAuthorized().contains(elts[2])){
                        fileTable.get(elts[1]).add_authorization(elts[2]);
                    }
                    //se il destinatario della share è online, gli notifico l'invito immediatamente
                    if(onlineSet.containsKey(elts[2])){
                        SocketChannel d = onlineSet.get(elts[2]);
                        if(d!=null){
                            try {
                                String invitations = user + " " + elts[1];
                                int bytesToSend = invitations.length();
                                ByteBuffer size = ByteBuffer.allocate(4);
                                ByteBuffer tmp = ByteBuffer.allocate(bytesToSend);
                                size.putInt(bytesToSend);
                                size.flip();
                                d.write(size);
                                size.clear();
                                tmp.put(invitations.getBytes());
                                tmp.flip();
                                d.write(tmp);
                                tmp.clear();
                            }
                            catch (IOException e){
                                System.out.println("Error sending invitations to client "+elts[2]);
                                return "error!!";
                            }
                        }
                        else{
                            pendingInvitations.get(elts[2]).add(new Invitation(user,elts[1]));
                        }
                    }
                    //se non è online, aggiungo l'invito nella lista degli inviti pendenti
                    else if(pendingInvitations.containsKey(elts[2])){
                        pendingInvitations.get(elts[2]).add(new Invitation(user,elts[1]));
                    }
                    else{
                        LinkedList<Invitation> l = new LinkedList<>();
                        l.add(new Invitation(user,elts[1]));
                        pendingInvitations.put(elts[2],l);
                    }
                    return "shared";
                }
            }
            case "list":{
                String allFiles = "";
                //recupero tutti i file per cui l'utente è autorizzato
                for(String f: fileTable.keySet()){
                    if(fileTable.get(f).isAuthorized(user)){
                        allFiles= allFiles+f+": \n"+(fileTable.get(f).toString())+"\n\n";
                    }
                }
                return allFiles+"\n";
            }
            case "show":{
                //affido al thread-pool la richiesta di show
                tpe.execute(new FileDisplayer(elts[1],Integer.parseInt(elts[2]),fileTable,user,cl,q));
                //restituisco "transfer" così da settare l'interestsSet della chiave a 0
                return "transfer";
            }
            case "edit":{
                //affido al thread-pool la richiesta di edit
                tpe.execute(new FileSender(elts[1],Integer.parseInt(elts[2]),fileTable,user,cl,q,generator));
                //restituisco "transfer" così da settare l'interestsSet della chiave a 0
                return "transfer";
            }
            case "end-edit":{
                //affido al thread-pool la richiesta di end-edit
                tpe.execute(new FileReceiver(elts[1],elts[2],fileTable,cl,receivingSet.get(user),Integer.parseInt(elts[3]),q,generator));
                //restituisco "transfer" così da settare l'interestsSet della chiave a 0
                return "transfer";
            }
            case "logout":{
                if(!onlineSet.containsKey(elts[1])){ return "user not logged"; }
                else if(!usermap.get(elts[1]).equals(elts[2])){ return "wrong password"; }
                else{
                    try {
                        //disconnetto l'utente e chiudo il SocketChannel degli iniviti
                        onlineSet.remove(elts[1]).close();
                        //disconnetto l'utente e chiudo il SocketChannel per l'upload
                        receivingSet.remove(elts[1]).close();
                    }
                    catch (IOException e){
                        System.out.println("I/O exception closing a SocketChannel");
                    }
                    System.out.println("Client logged as " + elts[1] + " got disconnected.. bye bye");
                    System.out.println("Users online: " + onlineSet.keySet());
                    return "logged_out";
                }
            }
            default:{ return "wrong operation"; }
        }
    }
}