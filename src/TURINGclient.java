import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.LocalDateTime;
import java.util.Scanner;


public class TURINGclient {
    public static void main(String[] args){
        int port;
        String serverAddress;
        try{
            serverAddress=args[0];
            port=Integer.parseInt(args[1]);
        }
        catch(RuntimeException e){
            System.out.println("Use: java TURINGclient < server_address > < port_number >");
            return;
        }
        SocketChannel server = null;
        SocketChannel uploadinSC = null;
        SocketAddress address;
        try {
            address = new InetSocketAddress(serverAddress, port);
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
        System.out.println(">> Welcome in TURING! (disTribUted collaboRative edItiNG) <<");

        //thread gestore degli inviti
        Thread t = new Thread();
        //thread gestore della chat
        Thread chatThread = new Thread();
        //frame utilizzato per la chat
        JFrame fr = null;

        /**------------------------------------------- variabili di stato ------------------------------------------**/
        boolean working= true;
        boolean logged = false;
        boolean editing = false;
        String username_logged = "none";
        String password_logged= "none";
        String editing_file = "";
        /**---------------------------------------------------------------------------------------------------------**/

        Scanner sc = new Scanner(System.in);
        String cmd;
        while (working){
            while(!sc.hasNextLine()){ ; }
            cmd = sc.nextLine();
            String[] arguments = cmd.split(" ");
            if(arguments.length<=1) arguments = "wrong command".split(" ");
            if(arguments[0].equals("turing")) {
                switch (arguments[1]) {
                    case "register": {
                        if(arguments.length!=4){
                            System.out.println(">>>> Use: turing register username password");
                        }
                        else if(logged){
                            System.out.println(">>>> ATTENTION! You can't register when you are logged-in!");
                        }
                        else if(editing){
                            System.out.println(">>>> ATTENTION! You MUST end-edit before doing anything else!");
                        }
                        else if(arguments[2].length()>20 || arguments[3].length()>20){
                            System.out.println(">>>> ATTENTION! Username and password max length is 20 characters.");
                        }
                        else if(arguments[2].indexOf('/')!=-1 || arguments[2].indexOf('%')!=-1 || arguments[3].indexOf('/')!=-1 || arguments[2].indexOf('%')!=-1){
                            System.out.println(">>>> ATTENTION! '/' and '%' characters not allowed. ");
                        }
                        else {
                            try{
                                register(arguments[2],arguments[3]);
                            }
                            catch (UsernameAlreadyExistsException e){
                                System.out.println(">>>> ATTENTION! Username not available! Use a different username!");
                                break;
                            }
                            catch (IllegalArgumentException e){
                                System.out.println(">>>> ATTENTION! Username length and password length must be >0 ");
                                break;
                            }
                            catch (java.rmi.ConnectException e){
                                System.out.println(">>>> ATTENTION! Server seems to be offline");
                                break;
                            }
                            catch (java.lang.Exception e){
                                e.printStackTrace();
                                break;
                            }
                            System.out.println(">>>> User "+arguments[2]+" correctly registered!");
                        }
                    } break;

                    case "login":{
                        if(arguments.length!=4){
                            System.out.println(">>>> Use: turing login username password");
                        }
                        else if(logged){
                            System.out.println(">>>> ATTENTION! You are already logged!");
                        }
                        else{
                            try {
                                byte[] op = createOp(arguments);
                                ByteBuffer b = ByteBuffer.allocate(op.length);
                                b.put(op);
                                b.flip();
                                server.write(b);
                                b.clear();
                                ByteBuffer bytesToRead = ByteBuffer.allocate(4);
                                //se leggo più di 0 bytes, il server è online e mi ha risposto
                                if(server.read(bytesToRead)>0){
                                    bytesToRead.flip();
                                    int nbytes = bytesToRead.getInt();
                                    bytesToRead.clear();
                                    ByteBuffer res = ByteBuffer.allocate(nbytes);
                                    server.read(res);
                                    res.flip();
                                    String result = new String(res.array());
                                    res.clear();
                                    if (result.equals("logged")) {
                                        uploadinSC = SocketChannel.open(address);
                                        String req= "uploadingSC/%/"+arguments[2]+"/%/";
                                        ByteBuffer bb = ByteBuffer.allocate(req.length());
                                        bb.put(req.getBytes());
                                        bb.flip();
                                        uploadinSC.write(bb);
                                        //creo il thread che gestirà gli inviti
                                        t = new Thread(new InvitationsHandler(arguments[2],serverAddress,port));
                                        logged = true;
                                        username_logged = arguments[2];
                                        password_logged = arguments[3];
                                        System.out.println(">>>> " + result);
                                        t.start();
                                        //se non esiste, creo la cartella per raccogliere i file che riceverò
                                        File file_dir = new File (username_logged+"_file");
                                        if(!file_dir.exists()) file_dir.mkdir();
                                    }
                                    else{System.out.println(">>>> " + result);}
                                }
                                //altrimenti il server è terminato
                                else{
                                    System.out.println("_____ Server offline _____");
                                }
                            }
                            catch(IOException e){
                                System.out.println("_____ An I/O error occurred. Server may be offline. _____");
                            }
                        }
                    }break;

                    case "logout":{
                        if(editing){
                            System.out.println(">>>> ATTENTION! You MUST end-edit before doing anything else!");
                        }
                        else if(logged) {
                            //invio al server il comando per il logout
                            try {
                                String[] argts = new String[4];
                                argts[0]="turing";
                                argts[1] = "logout";
                                argts[2] = username_logged;
                                argts[3] = password_logged;
                                byte[] op = createOp(argts);
                                ByteBuffer b = ByteBuffer.allocate(op.length);
                                b.put(op);
                                b.flip();
                                server.write(b);
                                b.clear();
                                ByteBuffer bytesToRead = ByteBuffer.allocate(4);
                                //se leggo più di 0 bytes, il server è online e mi ha risposto
                                if(server.read(bytesToRead)>0){
                                    bytesToRead.flip();
                                    int nbytes = bytesToRead.getInt();
                                    bytesToRead.clear();
                                    ByteBuffer res = ByteBuffer.allocate(nbytes);
                                    server.read(res);
                                    res.flip();
                                    String result = new String(res.array());
                                    res.clear();
                                    if (result.equals("logged_out")) {
                                        t.interrupt();
                                        uploadinSC.close();
                                        logged = false;
                                        username_logged = "none";
                                        password_logged = "none";
                                    }
                                    System.out.println(">>>> " + result);
                                }
                                else{
                                    System.out.println("_____ Server offline _____");
                                }
                            }
                            catch (IOException e){
                                System.out.println("_____ An I/O error occurred. Server may be offline. _____");
                            }
                        }
                        //se logged=false
                        else {
                            System.out.println(">>>> You are not logged!");
                        }

                    }break;

                    case "create":{
                        if(!logged){
                            System.out.println(">>>> ATTENTION! You must be logged to create a file!");
                        }
                        else if(editing){
                            System.out.println(">>>> ATTENTION! You MUST end-edit before doing anything else!");
                        }
                        else if(arguments.length!=4){
                            System.out.println(">>>> Use: turing create filename n_sections");
                        }
                        else{
                            try{
                                int nsect = Integer.parseInt(arguments[3]);
                                if(nsect<=0){
                                    System.out.println(">>>> ATTENTION! The number of sections must be >0 ");
                                    break;
                                }
                                byte[] op = createOp(arguments);
                                ByteBuffer b = ByteBuffer.allocate(op.length);
                                b.put(op);
                                b.flip();
                                server.write(b);
                                b.clear();
                                ByteBuffer bytesToRead = ByteBuffer.allocate(4);
                                //se leggo più di 0 bytes, il server è online e mi ha risposto
                                if(server.read(bytesToRead)>0){
                                    bytesToRead.flip();
                                    int nbytes = bytesToRead.getInt();
                                    bytesToRead.clear();
                                    ByteBuffer res = ByteBuffer.allocate(nbytes);
                                    server.read(res);
                                    res.flip();
                                    String result = new String(res.array());
                                    System.out.println(">>>> " + result);
                                    res.clear();
                                }
                                //altrimenti il server è terminato
                                else{
                                    System.out.println("_____ Server offline _____");
                                }
                            }
                            catch(NumberFormatException ex){
                                System.out.println(">>>> Use: turing create filename n_sections (n_sections must be an integer >0)");
                            }
                            catch(IOException e){
                                System.out.println("_____ An I/O error occurred. Server may be offline. _____");
                            }
                        }
                    }break;

                    case "share":{
                        if(!logged){
                            System.out.println(">>>> ATTENTION! You must be logged to share a file!");
                        }
                        else if(editing){
                            System.out.println(">>>> ATTENTION! You MUST end-edit before doing anything else!");
                        }
                        else if(arguments.length!=4){
                            System.out.println(">>>> Use: turing share filename user");
                        }
                        else{
                            try{
                                byte[] op = createOp(arguments);
                                ByteBuffer b = ByteBuffer.allocate(op.length);
                                b.put(op);
                                b.flip();
                                server.write(b);
                                b.clear();
                                ByteBuffer bytesToRead = ByteBuffer.allocate(4);
                                //se leggo più di 0 bytes, il server è online e mi ha risposto
                                if(server.read(bytesToRead)>0){
                                    bytesToRead.flip();
                                    int nbytes = bytesToRead.getInt();
                                    bytesToRead.clear();
                                    ByteBuffer res = ByteBuffer.allocate(nbytes);
                                    server.read(res);
                                    res.flip();
                                    String result = new String(res.array());
                                    System.out.println(">>>> " + result);
                                    res.clear();
                                }
                                //altrimenti il server è terminato
                                else{
                                    System.out.println("_____ Server offline _____");
                                }
                            }
                            catch(IOException e){
                                System.out.println("_____ An I/O error occurred. Server may be offline. _____");
                            }
                        }
                    }break;

                    case "list":{
                        if(!logged){
                            System.out.println(">>>> ATTENTION! You must be logged to see your file list!");
                        }
                        else if(editing){
                            System.out.println(">>>> ATTENTION! You MUST end-edit before doing anything else!");
                        }
                        else{
                            try{
                                byte[] op = "list/%/".getBytes();
                                ByteBuffer b = ByteBuffer.allocate(op.length);
                                b.put(op);
                                b.flip();
                                server.write(b);
                                b.clear();
                                ByteBuffer bytesToRead = ByteBuffer.allocate(4);
                                //se leggo più di 0 bytes, il server è online e mi ha risposto
                                if(server.read(bytesToRead)>0){
                                    bytesToRead.flip();
                                    int nbytes = bytesToRead.getInt();
                                    bytesToRead.clear();
                                    ByteBuffer res = ByteBuffer.allocate(nbytes);
                                    server.read(res);
                                    res.flip();
                                    String result = new String(res.array());
                                    System.out.println(">>>> Your files: ");
                                    System.out.println();
                                    System.out.println(result);
                                    System.out.println();
                                    res.clear();
                                }
                                //altrimenti il server è terminato
                                else{
                                    System.out.println("_____ Server offline _____");
                                }
                            }
                            catch(IOException e){
                                System.out.println("_____ An I/O error occurred. Server may be offline. _____");
                            }
                        }
                    }break;

                    case "show":{
                        if(!logged){
                            System.out.println(">>>> ATTENTION! You must be logged to see a file!");
                        }
                        else if(editing){
                            System.out.println(">>>> ATTENTION! You MUST end-edit before doing anything else!");
                        }
                        else if(arguments.length<3 || arguments.length>4){
                            System.out.println(">>>> Use: turing show document [section]");
                        }
                        else{
                            try {
                                byte[] op = null;
                                String[] argts = new String[4];
                                argts[0] = arguments[0];
                                argts[1] = arguments[1];
                                argts[2] = arguments[2];
                                if (arguments.length == 3) {
                                    argts[3] = "0";
                                }
                                else {  //(arguments.length==4)
                                    try {
                                        int sect = Integer.parseInt(arguments[3]);
                                        if (sect < 1) {
                                            throw new RuntimeException();
                                        }
                                    } catch (RuntimeException e) {
                                        System.out.println(">>>> Use: turing show document [section] --> section MUST be an int number >0");
                                        break;
                                    }
                                    argts[3] = arguments[3];
                                }
                                op = createOp(argts);
                                ByteBuffer b = ByteBuffer.allocate(op.length);
                                b.put(op);
                                b.flip();
                                server.write(b);
                                b.clear();
                                ByteBuffer filesToReceive = ByteBuffer.allocate(4);
                                if(server.read(filesToReceive)>0) {
                                    filesToReceive.flip();
                                    int nfiles = filesToReceive.getInt();
                                    filesToReceive.clear();
                                    if(nfiles==0){
                                        System.out.println(">>>> File (or section) not found.");
                                    }
                                    else if(nfiles==-1){
                                        System.out.println(">>>> You are not authorized to see this file. ");
                                    }
                                    //ho almeno una sezione del file da leggere
                                    else{
                                        File f = new File(username_logged+"_file/"+ getFileName(argts[2],argts[3]));
                                        f.createNewFile();
                                        ByteBuffer size = ByteBuffer.allocate(4);
                                        for(int i=0; i<nfiles; i++){
                                            server.read(size);
                                            size.flip();
                                            int fsize = size.getInt();
                                            size.clear();
                                            FileChannel fc = FileChannel.open(Paths.get(f.getAbsolutePath()), StandardOpenOption.APPEND);
                                            fc.transferFrom(server,0,fsize);
                                            String delim = "";
                                            if(argts[3].equals("0")){
                                                 delim = "\n\n ----------------------------------  End of section "+(i+1)+"  ---------------------------------- \n\n";
                                            }
                                            else{
                                                delim = "\n\n ----------------------------------  End of section "+argts[3]+"  ---------------------------------- \n\n";
                                            }
                                            ByteBuffer bb = ByteBuffer.allocate(delim.length());
                                            bb.put(delim.getBytes());
                                            bb.flip();
                                            fc.write(bb);
                                            bb.flip();
                                        }
                                        showDoc(f);
                                        System.out.println(">>>> Success");
                                    }
                                }
                                else{
                                    System.out.println("_____ Server offline _____");
                                }
                            }
                            catch (IOException e){
                                System.out.println("_____ An I/O error occurred. Server may be offline. _____");
                            }
                        }
                    }break;

                    case "edit":{
                        if(!logged){
                            System.out.println(">>>> ATTENTION! You must be logged to edit a file!");
                        }
                        else if(editing){
                            System.out.println(">>>> Already editing.");
                        }
                        else if(arguments.length!=4){
                            System.out.println(">>>> Use: turing edit filename section");
                        }
                        else{
                            try {
                                int sect = Integer.parseInt(arguments[3]);
                                if (sect < 1) {
                                    throw new RuntimeException();
                                }
                            } catch (RuntimeException e) {
                                System.out.println(">>>> Use: turing edit document section --> section MUST be an int number >0");
                                break;
                            }
                            try{
                                byte[] op = createOp(arguments);
                                ByteBuffer b = ByteBuffer.allocate(op.length);
                                b.put(op);
                                b.flip();
                                server.write(b);
                                b.clear();
                                ByteBuffer bytesToRead = ByteBuffer.allocate(4);
                                //se leggo più di 0 bytes, un thread del server mi ha risposto
                                if(server.read(bytesToRead)>0){
                                    bytesToRead.flip();
                                    int ans = bytesToRead.getInt();
                                    bytesToRead.clear();
                                    if(ans==0){
                                        System.out.println(">>>> File (or section) requested not found");
                                    }
                                    else if (ans==-1){
                                        System.out.println(">>>> You are not authorized to edit this file. ");
                                    }
                                    else if(ans ==-2){
                                        System.out.println(">>>> Someone is already editing this section.");
                                    }
                                    //posso ricevre la sezione da editare
                                    else{
                                        File f = new File(username_logged+"_file/"+ arguments[2]+"_section"+arguments[3]);
                                        if(f.exists()) f.delete();
                                        f.createNewFile();
                                        ByteBuffer size = ByteBuffer.allocate(4);
                                        server.read(size);
                                        size.flip();
                                        int fsize= size.getInt();
                                        size.clear();
                                        FileChannel fc = FileChannel.open(Paths.get(f.getAbsolutePath()), StandardOpenOption.APPEND);
                                        fc.transferFrom(server,0,fsize);
                                        editing_file=f.getName();
                                        editing=true;
                                        System.out.println(">>>> Now editing file : '"+arguments[2]+"' section "+arguments[3]);
                                        server.read(bytesToRead);
                                        bytesToRead.flip();
                                        int nbytes = bytesToRead.getInt();
                                        bytesToRead.clear();
                                        ByteBuffer addr = ByteBuffer.allocate(nbytes);
                                        String ipAddr;
                                        server.read(addr);
                                        addr.flip();
                                        ipAddr=new String(addr.array());
                                        ipAddr= ipAddr.replaceAll(":",".");
                                        fr = new JFrame("Chat for document: '" + arguments[2] + "' (you are logged as:'" + username_logged + "')");
                                        chatThread= new Thread(new ChatHandler(ipAddr,arguments[2],username_logged,fr));
                                        chatThread.start();
                                    }

                                }
                                //altrimenti il server è terminato
                                else{
                                    System.out.println("_____ Server offline _____");
                                }
                            }
                            catch(IOException e){
                                System.out.println("_____ An I/O error occurred. Server may be offline. _____");
                            }
                        }
                    }break;

                    case "end-edit":{
                        if(!logged){
                            System.out.println(">>>> ATTENTION! You are not logged!");
                        }
                        else if(!editing){
                            System.out.println(">>>> You are not editing.");
                        }
                        else{
                            try {
                                String filename = editing_file.substring(0,editing_file.lastIndexOf('_'));
                                FileInputStream f = new FileInputStream(username_logged + "_file/" + editing_file);
                                FileChannel fc = f.getChannel();
                                String operation= "end-edit"+"/%/"+username_logged+"/%/"+filename+"/%/"+fc.size()+"/%/";
                                byte[] op = operation.getBytes();
                                ByteBuffer b = ByteBuffer.allocate(op.length);
                                b.put(op);
                                b.flip();
                                //invio al server l'operazione
                                server.write(b);
                                b.clear();
                                ByteBuffer size = ByteBuffer.allocate(4);
                                //trasferisco il file
                                long sz=fc.size();
                                long position = 0;
                                long missing = sz;
                                long part;
                                while(position<sz){
                                    if(missing<1024) part = missing;
                                    else part=1024;
                                    position+= fc.transferTo(position,part,uploadinSC);
                                    missing-=part;
                                }
                                fc.close();
                                f.close();
                                if(server.read(size)>0){
                                    size.flip();
                                    ByteBuffer ans= ByteBuffer.allocate(size.getInt());
                                    server.read(ans);
                                    ans.flip();
                                    String res = new String(ans.array());
                                    if(res.equals("file received")){
                                        File fi = new File(username_logged+"_file/"+editing_file);
                                        if(fi.exists()) fi.delete();
                                        editing=false;
                                        editing_file="";
                                        chatThread.interrupt();
                                        if(fr!=null) fr.dispose();
                                        System.out.println(">>>> section correctly edited");
                                    }
                                    else System.out.println(">>>> "+res);
                                    ans.clear();
                                }
                                else{

                                    System.out.println("_____ Server offline _____");
                                }
                            }
                            catch(FileNotFoundException e){
                                System.out.println(">>>> ATTENTION! File to send not found! File to send MUST be named '"+editing_file+"'!");
                                break;
                            }
                            catch(IOException e){
                                System.out.println("_____ An I/O error occurred. Server may be offline. _____");
                            }
                        }
                    }break;

                    case "quit":{
                        if(editing){
                            System.out.println(">>>> ATTENTION! You MUST end-edit before doing anything else!");
                            break;
                        }
                        //se ero loggato, prima di terminare, eseguo il logout
                        else if(logged){
                            try {
                                String[] argts = new String[4];
                                argts[0]="turing";
                                argts[1] = "logout";
                                argts[2] = username_logged;
                                argts[3] = password_logged;
                                byte[] op = createOp(argts);
                                ByteBuffer b = ByteBuffer.allocate(op.length);
                                b.put(op);
                                b.flip();
                                server.write(b);
                                b.clear();
                                ByteBuffer bytesToRead = ByteBuffer.allocate(4);
                                if(server.read(bytesToRead)>0){
                                    bytesToRead.flip();
                                    int nbytes = bytesToRead.getInt();
                                    bytesToRead.clear();
                                    ByteBuffer res = ByteBuffer.allocate(nbytes);
                                    server.read(res);
                                    res.flip();
                                    String result = new String(res.array());
                                    res.clear();
                                    if (result.equals("logged_out")) {
                                        t.interrupt();
                                        uploadinSC.close();
                                        logged = false;
                                        username_logged = "none";
                                        password_logged = "none";
                                    }
                                }
                                else{
                                    System.out.println("_____ Server offline _____");
                                }
                            }
                            catch (IOException e){
                                System.out.println("_____ An I/O error occurred. Server may be offline. _____");
                            }
                        }
                        System.out.println(">>>> Goodbye!");
                        working=false;
                    } break;

                    case "--help":{
                        System.out.println("\n\nusage : turing COMMAND [ ARGS ...]\n" +
                                "commands :\n" +
                                "register < username > < password >  registra l ’ utente\n" +
                                "login < username > < password >     effettua il login\n" +
                                "logout                              effettua il logout\n" +
                                "create < doc > < numsezioni >       crea un documento\n" +
                                "share < doc > < username >          condivide il documento\n" +
                                "show < doc > <sec >                 mostra una sezione del documento\n" +
                                "show < doc >                        mostra l ’ intero documento\n" +
                                "list                                mostra la lista dei documenti\n" +
                                "edit < doc > < sec >                modifica una sezione del documento\n" +
                                "end-edit                            fine modifica della sezione del doc .\n"
                                );
                    }break;

                    default: {
                        System.out.println(">>>> Invalid command typed.");
                    } break;
                }
            }
            else System.out.println(">>>> use : turing COMMAND [ ARGS ...]");
        }
    }

    //recupera l'oggetto remoto esportato dal server e effettua la registrazione di un utente
    private static void register(String username, String password) throws java.lang.Exception{
        UserSetInterface us;
        Remote robj;
        Registry r = LocateRegistry.getRegistry(4321);
        robj = r.lookup("UserSet");
        us = (UserSetInterface) robj;
        us.Register(username, password);
    }

    //crea un byte array contenente i byes della richiesta da inviare al server
    private static byte[] createOp(String[] arg){
        String op= arg[1];
        op= op.concat("/%/");
        op= op.concat(arg[2]);
        op=op.concat("/%/");
        op=op.concat(arg[3]);
        op=op.concat("/%/");
        return op.getBytes();
    }

    //crea un frame e mostra il contenuto del file
    private static void showDoc(File f){
        JFrame fr = new JFrame(f.getName());
        fr.setSize(1000,700);
        fr.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JTextArea tx = new JTextArea();
        JScrollPane p = new JScrollPane(tx,JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        tx.setBackground(new Color(0,200,170));
        tx.setEditable(false);
        fr.add(p);
        try {
            FileChannel fc = FileChannel.open(Paths.get(f.getAbsolutePath()), StandardOpenOption.READ);
            ByteBuffer bb = ByteBuffer.allocate(1024);
            long size = fc.size();
            while (size > 0) {
                size-=fc.read(bb);
                bb.flip();
                String txt = new String(bb.array());
                if(txt.indexOf('\0')!=-1) txt=txt.substring(0,txt.indexOf('\0'));
                tx.append(txt);
                bb.clear();
            }
        }
        catch(IOException e){
            System.out.println("Error showing file.");
        }
        fr.setVisible(true);
    }

    //restituisce il nome di un file concatenato alla data e ora attuale
    private static String getFileName(String name, String sect){
        if(sect.equals("0")){
            return name+"("+LocalDateTime.now()+")";
        }
        else{
            return name+"_section"+sect+"("+LocalDateTime.now()+")";
        }
    }

}
