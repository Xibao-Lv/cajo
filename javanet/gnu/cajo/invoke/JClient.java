package gnu.cajo.invoke;

import java.awt.*;
import javax.swing.*;
import java.rmi.registry.*;
import java.rmi.MarshalledObject;
import java.awt.event.WindowListener;

/*
 * Graphical Proxy Loader JApplet
 * Copyright (C) 2006 John Catherino
 * The cajo project: https://cajo.dev.java.net
 *
 * For issues or suggestions mailto:cajo@dev.java.net
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, at version 2.1 of the license, or any
 * later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You can receive a copy of the GNU Lesser General Public License from their
 * website, http://fsf.org/licenses/lgpl.html; or via snail mail, Free
 * Software Foundation Inc., 51 Franklin Street, Boston MA 02111-1301, USA
 */

/**
 * This class is used to create a hosting VM to receive a Swing graphical proxy
 * JComponent <i>or</i> and AWT Component, from a remote VM.  The client will
 * require one outbound port, on which to commuinicate with its proxy server
 * and one on inbound, on which to receive asynchronous callbacks from the
 * server, if required. It will also use one short-term inbound port on which
 * to receive the proxy class files. If the client is behind a firewall, these
 * will have to be open.<br><br>
 * <i><u>Note</u>:</i> to use this Client, instead of the default one, requires
 * use of the new three argument CodebaseServer {@link gnu.cajo.utils.CodebaseServer#CodebaseServer(java.lang.String, int, java.lang.String) 
 * constructor}. In this case, he third argument would need to be:
 * <tt>"gnu.cajo.invoke.JClient"</tt>.
 *
 * @version 1.0, 20-Feb-06 Initial release
 * @author John Catherino
 */
public final class JClient extends JApplet {
   private static final long serialVersionUID = 1L;
   private static Object proxy;
   /**
    * The default constructor does nothing.  Initialization is done when
    * the JApplet is loaded into the browser.
    */
   public JClient() {}
   /**
    * This method provides the standard mechanism to identify this JApplet.
    * @return The identification string for this JApplet.
    */
   public String getAppletInfo() {
      return "cajo Proxy JApplet, Copyright \u00A9 2006 John Catherino";
   }
   /**
    * This method describes the optional client parameters. There are five
    * such parameters which can be specified:
    * <ul>
    * <li>The <code>proxyName</code> parameter is the name of the proxy server
    * item registered in the server's rmiregistry.  Unspecified it will be
    * "main".
    * <li>The <code>proxyPort</code> parameter is the outbound port number on
    * which to contact the proxy server.  Unspecified it will be 1099.  If the
    * client is operating behind a firewall, the must be a permitted outbound
    * port.
    * <li>The <code>clientHost</code> parameter is the external domain name or
    * IP address the server must use to callback its proxy.  It may need to
    * be specified if the client is operating behind a NAT router. Unspecified
    * it will be the client's default host address.
    * <li>The <code>clientPort</code> parameter is the external inbound port
    * number on which the server can contact its proxy. It may need to be
    * specified if the client is behind NAT, to map to the correct local port.
    * If a firewall is being used, it must be a permitted inbound port.
    * Unspecified, it will be the same as the local port value below.
    * <li>The <code>localPort</code> parameter is the internal inbound port
    * number on which the server can contact its proxy. It may need to be
    * specified if the client is behind NAT, to map to the correct remote port.
    * Unspecified, it will be anonymous.
    * </ul>
    * @return The parameter / information array.
    */
   public String[][] getParameterInfo() {
      return new String[][] {
         { "proxyName",  "String",  "Server's proxy's registry name" },
         { "proxyPort",  "Integer", "Server's proxy's port number"   },
         { "clientHost", "String",  "Client's external host name"    },
         { "clientPort", "Integer", "Client's external port number"  },
         { "localPort",  "Integer", "Client's internal port number"  },
      };
   }
   /**
    * This method connects back to its hosting server and requests the item
    * from the server's rmiregistry. Next it will invoke a getProxy(null) on
    * the remote reference to request its proxy item.  If the item returns the
    * proxy in a MarshalledObject, it will be extracted automatically. If the
    * returned object is a proxy, the client will invoke its init method,
    * passing it a remote reference itself, and to obtain its primary
    * graphical Component representation, which will then be added into the
    * JApplet via the Swing event dispatch thread. The proxy can pass this
    * remote reference back to its hosting item, or to other remote items, on
    * which they can asynchronously call it back.
    */
    public void init() {
      try {
         String proxyName  = getParameter("proxyName");
         String proxyPort  = getParameter("proxyPort");
         String clientHost = getParameter("clientHost");
         String clientPort = getParameter("clientPort");
         String localPort  = getParameter("localPort");
         int pPort = proxyPort  != null ? Integer.parseInt(proxyPort)  : 1099;
         int cPort = clientPort != null ? Integer.parseInt(clientPort) : 0;
         int lPort = localPort  != null ? Integer.parseInt(localPort)  : 0;
         if (proxyName == null) proxyName = "main";
         Remote.config(null, lPort, clientHost, cPort);
         proxy = LocateRegistry.getRegistry(getCodeBase().getHost(), pPort);
         proxy = ((Registry)proxy).lookup(proxyName);
         proxy = Remote.invoke(proxy, "getProxy", null);
         if (proxy instanceof MarshalledObject)
            proxy = ((MarshalledObject)proxy).get();
         if (!(proxy instanceof RemoteInvoke)) {
            SwingUtilities.invokeAndWait(new Runnable() {
               public void run() {
                  try {
                     proxy = Remote.invoke(proxy, "init", new Remote(proxy));
                     if (proxy instanceof Component) {
                        getContentPane().add((Component)proxy);
                        validate();
                     }
                  } catch(Exception x) { x.printStackTrace(); }
               }
            });
         }
      } catch(Exception x) { x.printStackTrace(); }
   }
   /**
    * The application creates a graphical Component proxy hosting VM.
    * With the URL argument provided, it will use the static
    * {@link Remote#getItem getItem} method of the {@link Remote Remote} class
    * to contact the server. It will then invoke a null-argument getProxy on
    * the resulting reference to request the primary proxy object of the item.<br><br>
    * <i><u>Note</u>:</i> When running as an application (<i><u>except</u> via
    * WebStart</i>) it will load a NoSecurityManager, therefore, if no external
    * SecurityManager is specified in the startup command line; the arriving
    * proxies will have <i><u><b>full permissions</b></u></i> on this machine!<br><br>
    * To restrict client proxies permissions, use a startup invocation
    * similar to the following:<br><br>
    * <tt>java -jar -Djava.security.manager -Djava.security.policy=client.policy</tt>
    * <br><br>
    * See the project client <a href=https://cajo.dev.java.net/client.html>
    * documentation</a>, for more details.<br><br>
    * The startup requires one mandatory, and up to four optional
    * configuration parameters, in this order:<ul>
    * <li><tt>args[0] - </tt>The URL where to get the graphical proxy item:<br>
    * file:// http:// ftp:// ..., //host:port/name (rmiregistry), /path/name
    * (serialized), or path/name (class).
    * <li><tt>args[1] - </tt>The optional external client port number,
    * if using NAT.
    * <li><tt>args[2] - </tt>The optional external client host name,
    * if using NAT.
    * <li><tt>args[3] - </tt>The optional internal client port number,
    * if using NAT.
    * <li><tt>args[4] - </tt>The optional internal client host name,
    * if multi home/NIC.</ul>
    */
   public static void main(final String args[]) {
      try {
         if (System.getSecurityManager() == null)
            System.setSecurityManager(new NoSecurityManager());
         if (args.length > 0) {
            int clientPort    = args.length > 1 ? Integer.parseInt(args[1]) : 0;
            String clientHost = args.length > 2 ? args[2] : null;
            int localPort     = args.length > 3 ? Integer.parseInt(args[3]) : 0;
            String localHost  = args.length > 4 ? args[4] : null;
            Remote.config(localHost, localPort, clientHost, clientPort);
            proxy = Remote.getItem(args[0]);
            proxy = Remote.invoke(proxy, "getProxy", null);
            if (proxy instanceof MarshalledObject)
               proxy = ((MarshalledObject)proxy).get();
            if (!(proxy instanceof RemoteInvoke)) {
               SwingUtilities.invokeLater(new Runnable() {
                  public void run() {
                     try {
                        proxy = Remote.invoke(proxy, "init", new Remote(proxy));
                        if (proxy instanceof Component) {
                           JFrame frame =
                              new JFrame("cajo Proxy Viewer - " + args[0]);
                           if (proxy instanceof WindowListener)
                              frame.addWindowListener((WindowListener)proxy);
                           frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                           frame.getContentPane().add((Component)proxy);
                           frame.pack();
                           frame.setVisible(true);
                        } // otherwise non-graphical proxy
                     } catch(Exception x) { x.printStackTrace(); }
                  }
               });
            }
         } else System.err.println("No source URL provided");
      } catch(Exception x) { x.printStackTrace(); }
   }
}
