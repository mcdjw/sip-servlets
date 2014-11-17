package oracle.communications.talkbac;

import java.io.IOException;
import java.io.Serializable;
import java.util.LinkedList;

import javax.servlet.sip.Address;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipURI;

public class TalkBACMessageUtility implements Serializable{

	private LinkedList<SipApplicationSession> list = new LinkedList<SipApplicationSession>();

	public boolean addClient(Address address) {
		boolean added = false;
		String user = ((SipURI) address.getURI()).getUser();

		SipApplicationSession appSession = TalkBACSipServlet.util.getApplicationSessionByKey(user, false);
		if (appSession != null) {
			added = list.add(appSession);
		}

		return added;
	}

//	public boolean removeClient(Address address) {
//		return list.remove(address);
//	}

	public void send(TalkBACMessage m) {
		SipServletRequest msg;
		Address clientAddress;

		TalkBACSipServlet.cdr.info(m.toString().replaceAll("[\n\r]", ""));

		for (SipApplicationSession appSession : list) {
			try {
				clientAddress = (Address) appSession.getAttribute(TalkBACSipServlet.CLIENT_ADDRESS);
				if (clientAddress != null) {
					msg = TalkBACSipServlet.factory.createRequest(appSession, "MESSAGE", TalkBACSipServlet.talkBACAddress, clientAddress);
					msg.setContent(m.toString(), "text/plain");
					msg.send();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

}
