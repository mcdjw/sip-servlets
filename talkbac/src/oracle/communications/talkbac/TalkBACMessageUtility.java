package oracle.communications.talkbac;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.sip.Address;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipURI;

import weblogic.kernel.KernelLogManager;

public class TalkBACMessageUtility implements Serializable {
	private static final long serialVersionUID = 1L;
	static Logger logger;
	{
		logger = Logger.getLogger(CallStateHandler.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	private HashMap<String, SipApplicationSession> hashmap = new HashMap<String, SipApplicationSession>();

	public void addClient(Address address) {
		String user = ((SipURI) address.getURI()).getUser().toLowerCase();
		SipApplicationSession appSession = TalkBACSipServlet.util.getApplicationSessionByKey(user, false);
		if (appSession != null) {
			hashmap.put(user, appSession);
		}
	}

	public void removeClient(Address address) {
		String user = ((SipURI) address.getURI()).getUser().toLowerCase();
		hashmap.remove(user);
	}

	public void send(TalkBACMessage m) {
		TalkBACSipServlet.cdr.info(m.toString().replaceAll("\r\n", ""));

		try {
			SipServletRequest msg;

			for (SipApplicationSession appSession : hashmap.values()) {
				if (appSession.isValid()) {
					Address address = (Address) appSession.getAttribute(TalkBACSipServlet.CLIENT_ADDRESS);
					if (address != null) {
						msg = TalkBACSipServlet.factory.createRequest(appSession, "MESSAGE", TalkBACSipServlet.talkBACAddress, address);
						msg.setContent(m.toString(), "text/plain");
						msg.send();

						if (logger.isLoggable(Level.FINE)) {
							int state = 1;

							System.out.println(this.getClass().getSimpleName()
									+ " "
									+ state
									+ " "
									+ ((SipURI) msg.getTo().getURI()).getUser()
									+ " <-- "
									+ msg.getMethod()
									+ " "
									+ m.getParameter("event")
									+ ", ["
									+ msg.getApplicationSession().hashCode()
									+ ":"
									+ msg.getSession().hashCode()
									+ "] "
									+ msg.getSession().getState().toString());
						}

					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
