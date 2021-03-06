
/**
* Class MailAllert
* Widget for tracking emails from given senders
* Creation: September, 3, 2013
* @author Ludovic APVRILLE
* @see
*/

package crocwidget;

import uicrocbar.*;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import javax.imageio.*;
import javax.swing.*;

import javax.mail.*;
import javax.mail.event.*;
import javax.mail.internet.*;
import javax.activation.*;

import org.w3c.dom.*;


//import java.io.*;

//import myutil.*;

public  class MailAlert extends CrocWidget implements Runnable, UseBackgroundImage  {
    //static final private String BROWSER = "thunderbird"; // TODO: configurable ?

    protected String pathToNormalIcon, pathToAlertIcon;
    protected Image imageN0, imageU0; // Image normal and image update ...
    //protected BufferedImage imageN, imageU;
    protected Image backgroundImage;
    protected String username;
    protected String password;
    protected String protocol; // pop, imap
    protected String host;
    protected String alertSender;
    protected String alertSubject;
    protected boolean shake;

    protected int port;
    protected String mailboxName;
    protected String label;
    protected boolean useSSL;
    protected int nbOfEmails = -1;
    protected int nbOfEmailsAlert = -1;
    protected int nbOfUnreadEmailsAlert = -1;
    protected int fontSize;

    protected int widthN, heightN, widthU, heightU;
    protected int border = 2;

    protected JCrocBarTextFrame jcbtf;
    protected int maxWidth = 640;

    protected int updateTime;
    protected boolean update;
    protected boolean checking  = false;
    protected MediaTracker media;
    protected int decX, decY;



    protected int state; //0: image not loaded

    protected boolean paused = false;

    static final String SSL_FACTORY = "javax.net.ssl.SSLSocketFactory";

    private boolean go = true;
    private int threadState = 0;
    private Thread t;


    // Constructor
    public MailAlert(JCrocBarFrame _frame, JCrocBarPanels _panel, int _posx, int _posy, int _width, int _height, Color _bg, Color _fg, NodeList _nl, NodeList _listData, boolean [] _faces) {
        super(_frame, _panel, _posx, _posy, _width, _height, _bg, _fg, _nl, _listData, _faces);

        // To load the images
        update = false;

        //clicks = 0;
        t = new Thread(this);
        t.start();
    }

    public void paintComponent(Graphics g) {
        //System.out.println("paint ---------------------->Label=" + label);

        if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, frame);
        }
        g.setColor(bg);
        g.fillRect(0, 0, width, height);

        if (state == 2) {
            if (nbOfUnreadEmailsAlert < 1) {
                g.drawImage(imageN0, (width-widthN)/2, (height-heightN - 14)/2, frame);
            } else {
                g.drawImage(imageU0, (width-widthU)/2, (height-heightU - 14)/2, frame);
            }
            printEmailInfo(g);
        }
    }

    public void printEmailInfo(Graphics g) {
        String s;
        int w;
        g.setColor(fg);
        Font f = g.getFont();
        g.setFont(f.deriveFont((float)fontSize));
        if (paused) {
            s = "paused";
        }  else if (password.length() ==0) {
            s = "password?";
        } else {
            if (!checking) {
                s = label + " " + nbOfUnreadEmailsAlert + "/" + nbOfEmailsAlert;
            } else {
                s = "checking";
            }
        }
        w = g.getFontMetrics().stringWidth(s);
        g.drawString(s, (width-w)/2, height - 3);
    }

    public void startLoadImage() {
        state = 0;

        media = new MediaTracker(this);
        //System.out.println("pathToNormalIcon=" + pathToNormalIcon);
        imageN0 = getImage(pathToNormalIcon);
        //imageN = imageN0.getScaledInstance(width-2*border, height-2*border, Image.SCALE_SMOOTH);
        media.addImage(imageN0, 0);
        //System.out.println("pathToActiveIcon=" + pathToActiveIcon);
        imageU0 = getImage(pathToAlertIcon);
        //imageU = imageU.getScaledInstance(width-2*border, height-2*border, Image.SCALE_SMOOTH);
        media.addImage(imageU0, 1);

        //newTimer(500);
    }

    public void timerExpired() {
        ttcw = null;
        boolean b;

        //System.out.println("Label in timer expiration=" + label);
        //timer.cancel(this);

        if (state == 0) {
            b = media.checkAll();
            if (b) {
                if (imageN0== null) {
                    System.out.println("Could not load image " + pathToNormalIcon + ": aborting MailAlert");
                    return;
                }

                if (imageU0 == null) {
                    System.out.println("Could not load image " + pathToAlertIcon + ": aborting MailAlert");
                    return;
                }

                //System.out.println("Making scaled instance width=" + (width-2*border) + " height=" + (height-2*border));

                /*imageN = toBufferedImage(imageN0);
                imageU = toBufferedImage(imageU0);
                imageN = getScaledInstance(imageN, width-2*border, height-2*border, RenderingHints.VALUE_INTERPOLATION_BICUBIC, false);
                imageU = getScaledInstance(imageU, width-2*border, height-2*border, RenderingHints.VALUE_INTERPOLATION_BICUBIC, false);*/

                widthU = imageU0.getWidth(null);
                heightU = imageU0.getHeight(null);
                widthN = imageN0.getWidth(null);
                heightN = imageN0.getHeight(null);


                if (bg.getAlpha() < 255) {
                    backgroundImage = loadBackgroundImage();
                }


                state = 2;
                repaint();
                updateMail(true);

            } else {
                //newTimer(500);
            }

        } else {
            updateMail(true);
        }

    }

    public void updateMail(boolean setTimer) {
        //System.out.println("object=" + this.toString());
        //System.out.println("label= " + label + " host=" + host + " port=" + port + " username=" + username + " password=" + password);

        if (paused) {
            if (setTimer) {
                //newTimer(updateTime);
            }
            return;
        }

        if (password.length() == 0) {
            if (setTimer) {
                //newTimer(updateTime);
            }
            return;
        }
        checking = true;
        repaint();
        Properties props = System.getProperties();

        if (useSSL) {
            if (protocol.equals("imap")) {
                props.setProperty( "mail.imap.socketFactory.class", SSL_FACTORY);
                props.setProperty( "mail.imap.socketFactory.fallback", "false");
                props.setProperty( "mail.imap.port", ""+port);
                props.setProperty( "mail.imap.socketFactory.port",  ""+port);
            } else {
                props.setProperty( "mail.pop.socketFactory.class", SSL_FACTORY);
                props.setProperty( "mail.pop.socketFactory.fallback", "false");
                props.setProperty( "mail.pop.port", ""+port);
                props.setProperty( "mail.pop.socketFactory.port", ""+port);


            }
        }

        if (protocol.equals("imap")) {
            props.put("mail.imap.connectiontimeout", "3000");
            props.put("mail.imap.timeout", "2000");
        } else {
            props.put("mail.pop3.connectiontimeout", "3000");
            props.put("mail.pop3.timeout", "2000");
        }





        try {
            Session session = Session.getInstance(props, null);
            session.setDebug(false);

            Store store = session.getStore(protocol);
            //System.out.println("host=" + host + " port=" + port + " username=" + username + " password=" + password);
            store.connect(host, port, username, password);
            Folder folder = store.getDefaultFolder();

            if (folder == null) {
                //System.out.println("No default border");
                nbOfEmails = -1;
                nbOfEmailsAlert = -1;
                nbOfUnreadEmailsAlert = -1;
                checking = false;
                store.close();
                return ;
            }

            //System.out.println("Folder");
            folder = folder.getFolder(mailboxName);
            //System.out.println("Nb Of emails");
            nbOfEmails = folder.getMessageCount();
            //System.out.println("Get email alerts");
            getAlertEmails();

            //System.out.println("No of emails" + nbOfEmails);
            //newTimer(updateTime);
            checking = false;
            store.close();
            repaint();
        } catch (Exception e) {
            System.out.println("Exception when fetching emails:" + e.getMessage());
            //nbOfEmails = -1;
            nbOfEmails = -1;
            nbOfEmailsAlert = -1;
            nbOfUnreadEmailsAlert = -1;
            checking = false;
            repaint();
        }



    }


    private ArrayList<String> getAlertEmails() {
        //System.out.println("Shake=" + shake);

        int old = nbOfUnreadEmailsAlert;
        ArrayList <String> emails = new ArrayList<String>();

        Properties props = System.getProperties();

        if (useSSL) {
            if (protocol.equals("imap")) {
                props.setProperty( "mail.imap.socketFactory.class", SSL_FACTORY);
                props.setProperty( "mail.imap.socketFactory.fallback", "false");
                props.setProperty( "mail.imap.port", ""+port);
                props.setProperty( "mail.imap.socketFactory.port",  ""+port);
            } else {
                props.setProperty( "mail.pop.socketFactory.class", SSL_FACTORY);
                props.setProperty( "mail.pop.socketFactory.fallback", "false");
                props.setProperty( "mail.pop.port", ""+port);
                props.setProperty( "mail.pop.socketFactory.port", ""+port);
            }
        }


        try {
            //System.out.println("Shake1=" + shake);
            Session session = Session.getInstance(props, null);
            session.setDebug(false);

            Store store = session.getStore(protocol);
            //System.out.println("host=" + host + " port=" + port + " username=" + username + " password=" + password);
            store.connect(host, port, username, password);
            Folder folder = store.getDefaultFolder();

            if (folder == null) {
                System.out.println("No default border");
                return null;
            }

            folder = folder.getFolder(mailboxName);
            int lnbOfEmails = folder.getMessageCount();
            String s;
            //System.out.println("Shake2=" + shake);

            if (lnbOfEmails == 0) {
                s = "Empty mail box";
                emails.add(s);
            } else {

                LinkedList<Message> ll = new LinkedList<Message>();

                int i;
                int cpt = 0;
                Message msg0;

                //System.out.println("First Nb of emails:" + lnbOfEmails);


                folder.open(Folder.READ_ONLY);
                try {
                    for(i=1; i<folder.getMessageCount()+1; i++) {
                        msg0 = folder.getMessage(i);
                        //System.out.println("Testing message:" + i +  " = " + msg0);
                        if ((!msg0.isSet(Flags.Flag.DELETED)) &&  (!msg0.isSet(Flags.Flag.DRAFT))) {
                            ll.add(msg0);
                            cpt ++;
                        }
                        if (cpt == 500) {
                            break;
                        }
                    }
                } catch (Exception e) {}

                System.out.println("Nb of emails:" + ll.size());

                nbOfEmailsAlert = 0;
                nbOfUnreadEmailsAlert = 0;

                if (ll.size() > 0) {
                    //System.out.println("Shake3=" + shake);
                    Address adr;
                    Address[] adrs;
                    int j;
                    //DateFormat df = DateFormat.getDateInstance(DateFormat.LONG);
                    DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                    java.util.Date currentDate = new java.util.Date();
                    Date date;
                    //System.out.println("Current Date Time : " + dateFormat.format(date));
                    //System.out.println("Shake31=" + shake);

                    for(Message msg: ll) {
                        s = "";

                        //msg = folder.getMessage(lnbOfEmails-i);

                        //Date
                        //System.out.println("msg: " + msg);
                        date = msg.getSentDate();
                        s += "at: " + dateFormat.format(date);

                        // From
                        adrs =  msg.getFrom();
                        boolean foundSender = false;
                        s+= "     from: ";
                        for(j=0; j<adrs.length; j++) {
                            if (alertSender.length() > 0) {
                                if (adrs[j].toString().indexOf(alertSender) > -1) {
                                    foundSender = true;
                                }
                            }
                            s+= adrs[j] + " ";
                        }
                        //System.out.println("date5" + shake);

                        // Subject
                        boolean foundSubject = false;
                        if (msg.getSubject() != null) {
                            if (alertSubject.length() > 0) {
                                foundSubject = (msg.getSubject().toString().indexOf(alertSubject) > -1);
                            }
                            //System.out.println("date6" + shake);
                            s+= "     sub: " + msg.getSubject();
                        }
                        //System.out.println("date7" + shake);
                        boolean found = false;

                        //System.out.println("Shake4=" + shake);

                        if (alertSender.length() > 0) {
                            if (foundSender) {
                                if (alertSubject.length() > 0) {
                                    found = foundSubject;
                                } else {
                                    found = foundSender;
                                }
                            }
                        } else {
                            found = foundSubject;
                        }


                        if (found) {
                            System.out.println("FOUND with alertSender=" + alertSender + " alertSubject=" + alertSubject + " email=" + s);
                            if (msg.isSet(Flags.Flag.SEEN)) {
                                nbOfEmailsAlert++;
                            } else {
                                System.out.println("FOUND with alertSender=" + alertSender + " alertSubject=" + alertSubject + " email=" + s);
                                nbOfUnreadEmailsAlert++;
                                System.out.println("UN_SEEN");
                            }
                            emails.add(s);
                        }
                        //System.out.println("Shake5=" + shake);
                    }

                    folder.close(false);


                    //return emails;
                } else {
                    s = "Empty mail box";
                    emails.add(s);
                }
            }
            store.close();


            System.out.println("nbOfUnreadEmailsAlert=" + nbOfUnreadEmailsAlert + " old=" + old + " shake=" + shake);
            if ((nbOfUnreadEmailsAlert > old) && (nbOfUnreadEmailsAlert > 0)) {
                if (shake) {
                    //System.out.println("ring ring");
                    frame.ring();
                }
            }


            return emails;
        } catch (Exception e) {
            nbOfEmails = -1;
            nbOfEmailsAlert = -1;
            nbOfUnreadEmailsAlert = -1;
            System.out.println("Exception when fetching emails:" + e.getMessage());
            return null;
        }

    }



    /*private ArrayList<String> getEmails() {
    	ArrayList <String> emails = new ArrayList<String>();

    	Properties props = System.getProperties();

    	if (useSSL) {
    		if (protocol.equals("imap")) {
    			props.setProperty( "mail.imap.socketFactory.class", SSL_FACTORY);
    			props.setProperty( "mail.imap.socketFactory.fallback", "false");
    			props.setProperty( "mail.imap.port", ""+port);
    			props.setProperty( "mail.imap.socketFactory.port",  ""+port);
    		} else {
    			props.setProperty( "mail.pop.socketFactory.class", SSL_FACTORY);
    			props.setProperty( "mail.pop.socketFactory.fallback", "false");
    			props.setProperty( "mail.pop.port", ""+port);
    			props.setProperty( "mail.pop.socketFactory.port", ""+port);
    		}
    	}


    	try {
    		Session session = Session.getInstance(props, null);
    		session.setDebug(false);

    		Store store = session.getStore(protocol);
    		//System.out.println("host=" + host + " port=" + port + " username=" + username + " password=" + password);
    		store.connect(host, port, username, password);
    		Folder folder = store.getDefaultFolder();

    		if (folder == null) {
    			System.out.println("No default border");
    			return null;
    		}

    		folder = folder.getFolder(mailboxName);
    		int lnbOfEmails = folder.getMessageCount();
    		String s;

    		if (lnbOfEmails == 0) {
    			s = "Empty mail box";
    			emails.add(s);
    		} else {

    			LinkedList<Message> ll = new LinkedList<Message>();

    			int i;
    			int cpt = 0;
    			Message msg0;

    			folder.open(Folder.READ_ONLY);
    			for(i=0; i<folder.getMessageCount(); i++) {
    				msg0 = folder.getMessage(lnbOfEmails-i);
    				if (!msg0.isSet(Flags.Flag.DELETED)) {
    					ll.add(msg0);
    					cpt ++;
    				}
    				if (cpt == 30) {
    					break;
    				}
    			}


    			if (ll.size() > 0) {
    				Address adr;
    				Address[] adrs;
    				int j;
    				//DateFormat df = DateFormat.getDateInstance(DateFormat.LONG);
    				DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    				java.util.Date currentDate = new java.util.Date();
    				Date date;
    				//System.out.println("Current Date Time : " + dateFormat.format(date));

    				for(Message msg: ll) {
    					s = "";
    					//msg = folder.getMessage(lnbOfEmails-i);

    					//Date
    					date = msg.getSentDate();
    					s += "at: " + dateFormat.format(date);

    					// From
    					adrs =  msg.getFrom();
    					s+= "     from: ";
    					for(j=0; j<adrs.length; j++) {
    						s+= adrs[j] + " ";
    					}

    					// Subject
    					s+= "     sub: " + msg.getSubject();
    					emails.add(s);
    				}

    				folder.close(false);


    				return emails;
    			} else {
    				s = "Empty mail box";
    				emails.add(s);
    			}
    		}
    		return emails;
    	} catch (Exception e) {
    		System.out.println("Exception when fetching emails:" + e.getMessage());
    		//nbOfEmails = -1;
    		return null;
    	}

    }*/


    public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {

            if (jcbtf != null) {
                if (jcbtf.isVisible()) {
                    jcbtf.setVisible(false);
                    return;
                } else {
                    threadState = 1;
                    t.interrupt();
                    /*ArrayList<String> emails = getEmails();
                    if (emails != null) {
                    jcbtf.make(emails, 12, fg, maxWidth);
                    jcbtf.setVisible(true);
                    }*/
                }
            } else {
                threadState = 2;
                t.interrupt();
                /*ArrayList<String> emails = getEmails();
                if (emails != null) {
                jcbtf = new JCrocBarTextFrame(posx+(width/2), posy+(height/2), frame.getPosx(), frame.getPosy(), frame.getWidth(), frame.getHeight(), bg);
                jcbtf.setLineNumber(true);
                jcbtf.make(emails, 12, fg, maxWidth);
                jcbtf.setVisible(true);
                }*/
            }

        }
    }

    public void loadExtraParam(NodeList nl) {
        pathToNormalIcon = "";
        pathToAlertIcon = "";
        updateTime = 30; /* every 30 seconds */
        password = "";
        username = "";
        protocol = "";
        host = "";
        mailboxName = "";
        label = "";
        fontSize = 12;
        alertSender = "";
        alertSubject = "";
        shake = false;
        System.out.println("*** shake set to false *** ");
        //System.out.println(nl.toString());
        try {

            NodeList nli;
            Node n1, n2;
            Element elt;
            int k;
            String s;
            int wt;
            int fs;

            //System.out.println("Loading Synchronization gates");
            //System.out.println(nl.toString());

            for(int i=0; i<nl.getLength(); i++) {
                //System.out.println("i=" + i);
                n1 = nl.item(i);
                //System.out.println(n1);
                if (n1.getNodeType() == Node.ELEMENT_NODE) {
                    nli = n1.getChildNodes();
                    for(int j=0; j<nli.getLength(); j++) {
                        n2 = nli.item(j);
                        //System.out.println(n2);
                        if (n2.getNodeType() == Node.ELEMENT_NODE) {
                            elt = (Element) n2;
                            //System.out.println(elt);
                            if (elt.getTagName().equals("PathToNormalIcon")) {
                                System.out.println("Path to normal");
                                s = elt.getAttribute("value");
                                //System.out.println("value=" +s );
                                if (s != null) {
                                    pathToNormalIcon = s;
                                }
                            }
                            if (elt.getTagName().equals("PathToAlertIcon")) {
                                System.out.println("Path to mail");
                                s = elt.getAttribute("value");
                                //System.out.println("value=" +s );
                                if (s != null) {
                                    pathToAlertIcon = s;
                                }
                            }
                            if (elt.getTagName().equals("Username")) {
                                System.out.println("username");
                                s = elt.getAttribute("value");
                                System.out.println("value=" +s );
                                if (s != null) {
                                    username = s;
                                }
                            }
                            if (elt.getTagName().equals("Password")) {
                                System.out.println("Password");
                                s = elt.getAttribute("value");
                                System.out.println("value=" +s );
                                if (s != null) {
                                    password = s;
                                }
                            }
                            if (elt.getTagName().equals("Protocol")) {
                                System.out.println("Protocol");
                                s = elt.getAttribute("value");
                                //System.out.println("value=" +s );
                                if (s != null) {
                                    protocol = s;
                                }
                            }
                            if (elt.getTagName().equals("Host")) {
                                System.out.println("Host");
                                s = elt.getAttribute("value");
                                //System.out.println("value=" +s );
                                if (s != null) {
                                    host = s;
                                }
                            }
                            if (elt.getTagName().equals("Port")) {
                                s = elt.getAttribute("value");
                                try {
                                    if (s != null) {
                                        wt = Integer.decode(s).intValue();
                                        if (wt > 0) {
                                            System.out.println("Setting port to " + wt);
                                            port = wt;
                                        }
                                    }
                                } catch (Exception e) {
                                    System.err.println("Could not load the port: " + s);
                                }
                            }
                            if (elt.getTagName().equals("MailboxName")) {
                                System.out.println("Mailbox");
                                s = elt.getAttribute("value");
                                //System.out.println("value=" +s );
                                if (s != null) {
                                    mailboxName = s;
                                }
                            }
                            if (elt.getTagName().equals("Label")) {
                                System.out.println("Label");
                                s = elt.getAttribute("value");
                                //System.out.println("value=" +s );
                                if (s != null) {
                                    label = s;
                                }
                            }
                            if (elt.getTagName().equals("AlertSender")) {
                                System.out.println("Sender");
                                s = elt.getAttribute("value");
                                //System.out.println("value=" +s );
                                if (s != null) {
                                    alertSender = s;
                                }
                            }
                            if (elt.getTagName().equals("AlertSubject")) {
                                System.out.println("Subject");
                                s = elt.getAttribute("value");
                                //System.out.println("value=" +s );
                                if (s != null) {
                                    alertSubject = s;
                                }
                            }
                            if (elt.getTagName().equals("UseSSL")) {
                                System.out.println("UseSSL");
                                s = elt.getAttribute("value");
                                //System.out.println("value=" +s );
                                if (s != null) {
                                    if (s.toUpperCase().equals("TRUE"))
                                        useSSL= true;
                                } else {
                                    useSSL = false;
                                }
                            }
                            if (elt.getTagName().equals("UpdateTime")) {
                                s = elt.getAttribute("value");
                                try {
                                    if (s != null) {
                                        wt = Integer.decode(s).intValue();
                                        if (wt > 0) {
                                            System.out.println("Setting update time to " + updateTime);
                                            updateTime = wt * 1000;
                                        }
                                    }
                                } catch (Exception e) {
                                    System.err.println("Could not load the waiting time: " + s);
                                }
                            }
                            if (elt.getTagName().equals("FontSize")) {
                                s = elt.getAttribute("value");
                                try {
                                    if (s != null) {
                                        fs = Integer.decode(s).intValue();
                                        if (fs > 0) {
                                            //System.out.println("Setting update time to " + updateTime);
                                            fontSize =  fs;
                                        }
                                    }
                                } catch (Exception e) {
                                    System.err.println("Could not load the font size: " + s);
                                }
                            }

                            if (elt.getTagName().equals("Shake")) {

                                s = elt.getAttribute("value");
                                System.out.println("\n\n**************** Found shake s=>" + s + "<\n\n");
                                try {
                                    if (s != null) {
                                        if (s.compareTo("true") == 0) {
                                            shake = true;
                                            System.out.println("shake Set to true");
                                        } else {
                                            shake = false;
                                            System.out.println("shake Set to false");
                                        }
                                    }
                                } catch (Exception e) {
                                    System.err.println("Could not load the shake value: " + s);
                                }
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            //System.out.println("Error");
            System.err.println("Error when loading extra parameters of mailalter: " + e.getMessage());
        }
        //System.out.println("label=" + label + " host=" + host + " port=" + port + " username=" + username + " password=" + password);
        //System.out.println("object=" + this.toString());

        System.out.println("End of load shake = " + shake);
    }

    protected String getAboutString() {
        String s = "MailAlert CrocWidget\nProgrammed by L. Apvrille";
        return s;
    }

    protected String getHelpString() {
        String s = "MailAlert Widget options:\n";
        s += "* PathToNormalIcon <String> (default = none)\n";
        s += "* PathToAlertIcon <String> (default = none)\n";
        s += "* Username <String> (default = none)\n";
        s += "* Protocol: imap / pop3 (default = imap)\n";
        s += "* Host <String> (default = none)\n";
        s += "* Port <int> (default = 993)\n";
        s += "* Mailboxname <String> (default = none)\n";
        s += "* UseSSL <boolean> (default = false)\n";
        s += "* AlertSender <string> (default = none)\n";
        s += "* AlertSubject <string> (default = none)\n";
        s += "* UpdateTime <int in s> (default = 300s)\n";
        s += "* Shake <true, false> (default = false)\n";
        s += "* FontSize <int>\n";
        return s;
    }


    // Menu
    protected void makeExtraMenu() {
        //System.out.println("Making menu");
        JMenuItem jmi;

        // View alarms
        jmi = new JMenuItem("Set password");
        jmi.setBackground(bg);
        jmi.setForeground(fg);
        jmi.addActionListener(menuAL);
        menu.add(jmi);

        jmi = new JMenuItem("Check for alert now!");
        jmi.setBackground(bg);
        jmi.setForeground(fg);
        jmi.addActionListener(menuAL);
        menu.add(jmi);

        jmi = new JMenuItem("Pause / unpause");
        jmi.setBackground(bg);
        jmi.setForeground(fg);
        jmi.addActionListener(menuAL);
        menu.add(jmi);

        menu.addSeparator();



    }

    protected void extraPopupAction(ActionEvent e) {
        String s = e.getActionCommand();

        if (s.startsWith("Set password")) {
            setPassword();
            return;
        }  else if (s.startsWith("Check for alert now!")) {
            //System.out.println("update email");
            if ((t!= null) && (go == true)) {
                t.interrupt();
            }
            //updateMail(false);
        } else if (s.startsWith("Pause / unpause")) {
            //System.out.println("pausing / unpausing");
            paused = !paused;
            repaint();
            //updateMail(false);
        }
    }

    protected void setPassword() {

        JDialogPassword dialog = new JDialogPassword(frame, "Password for " + label);
        dialog.setSize(250, 150);
        //GraphicLib.centerOnParent(dialog);
        showDialog(dialog, 250, 150); // blocked until dialog has been closed

        if (!dialog.isRegularClose()) {
            return;
        }

        if (dialog.getPassword().length() != 0) {
            password = dialog.getPassword().trim();
            updateMail(false);
        }
    }

    public void run() {
        startLoadImage();

        while(go) {
            myWait();
            //if(threadState ==1) {
            //	ArrayList<String> emails = getEmails();
            //	if (emails != null) {
            //		jcbtf.make(emails, 12, fg, maxWidth);
            //		jcbtf.setVisible(true);
            //	}
            //} else if (threadState == 2) {
            if ((threadState == 1) || (threadState == 2)) {
                ArrayList<String> emails = getAlertEmails();
                if (emails != null) {
                    jcbtf = new JCrocBarTextFrame(posx+(width/2), posy+(height/2), frame.getPosx(), frame.getPosy(), frame.getWidth(), frame.getHeight(), bg);
                    jcbtf.setLineNumber(true);
                    jcbtf.make(emails, 12, fg, maxWidth);
                    jcbtf.setVisible(true);
                }
            }
            timerExpired();

            threadState = 0;
        }
    }



    public void myWait() {
        int duration = updateTime;
        if (state == 0) {
            duration = 500;
        }
        try {
            Thread.currentThread().sleep(duration);
        } catch (InterruptedException ie) {
        }
    }

    public void setBackgroundImage(Image _backgroundImage) {
        backgroundImage = _backgroundImage;
    }


} // End of class

