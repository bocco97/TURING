import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.*;


public class ChatHandler implements Runnable {

    private String ipAddr;
    private String filename;
    private String username;
    private JFrame fr;

    public ChatHandler(String addr,String f,String usr, JFrame frame){
        ipAddr=addr;
        filename=f;
        username=usr;
        fr=frame;
    }


    @Override
    public void run() {
        InetAddress ia;
        try {
             ia= InetAddress.getByName(ipAddr);
        }
        catch (UnknownHostException e) {
            System.out.println("Error getting hostname.");
            return;
        }
        int port= 6543;
        DatagramSocket ds;
        try {
             ds = new DatagramSocket();
        }
        catch(SocketException e){
            System.out.println("Error while creating DatagramSocket.");
            fr.dispose();
            return;
        }
        /********************************************* creo il frame per la chat ****************************************************/
        fr.setSize(500, 700);
        fr.setLocationRelativeTo(null);
        JTextArea receiving = new JTextArea();
        receiving.setEditable(false);
        JScrollPane jsp = new JScrollPane(receiving, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        jsp.setPreferredSize(new Dimension(fr.getWidth() - 20, fr.getHeight() / 4 * 3));
        fr.setLayout(new FlowLayout());
        fr.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        JButton jb = new JButton("Send");
        JButton jbclear = new JButton("Clear");
        MyTextArea sending = new MyTextArea();
        JScrollPane jsps = new JScrollPane(sending, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        jsps.setPreferredSize(new Dimension(fr.getWidth() / 4 * 3 - 80, 80));
        jb.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                String msg = sending.getText();
                if (!msg.equals("")) {
                    msg = username+" : "+msg+"\n";
                    //se la lunghezza del messaggio da spedire è > di 1024, invio più messaggi da al più 1024 bytes l'uno
                    if(msg.length()>1024){
                        byte[] b ;
                        while(msg.length()>0){
                            String part;
                            if(msg.length()>1024)  part = msg.substring(0,1023);
                            else part = msg;
                            b= part.getBytes();
                            if(msg.length()>1024)msg=msg.substring(1024);
                            else msg ="";
                            DatagramPacket dp =  new DatagramPacket(b,b.length,ia,port);
                            try{
                                ds.send(dp);
                            }
                            catch (IOException e){
                                System.out.println("Error sending message.");
                            }
                        }
                    }
                    //altrimenti invio un unico pacchetto
                    else {
                        byte[] buf = msg.getBytes();
                        DatagramPacket dp = new DatagramPacket(buf, buf.length, ia, port);
                        try {
                            ds.send(dp);
                        } catch (IOException e) {
                            System.out.println("Error sending message.");
                        }
                    }
                    sending.setText("");
                }
            }
        });
        jbclear.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                sending.setText("");
            }
        });
        fr.add(jsp);
        fr.add(jsps);
        fr.add(jb);
        fr.add(jbclear);
        fr.setVisible(true);
        /*******************************************************************************************************************************/

        MulticastSocket ms= null;
        try {
            try {
                ms = new MulticastSocket(port);
            } catch (BindException e) {
                System.out.println("Port not available.");
                return;
            }
            ms.joinGroup(ia);
            //setto il timeout per la receive in modo che il thread non resti bloccato sulla receive
            //se nessuno invia messaggi
            ms.setSoTimeout(1000);
        }
        catch (IOException e){
            System.out.println("Si è verificato un errore di I/O.");
            fr.dispose();
            return;
        }

        while (!Thread.currentThread().isInterrupted()) {
            byte [] buf = new byte [1024];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            try{
                //leggo il pacchetto
                ms.receive(dp);
                String msg =new String(dp.getData(), 0, dp.getLength(), "US-ASCII");
                receiving.append(msg);
            }
            catch (SocketTimeoutException e){
            }
            catch(IOException e){
                System.out.println("Si è verificato un errore di I/O.");
            }
        }
        return;
    }
}
