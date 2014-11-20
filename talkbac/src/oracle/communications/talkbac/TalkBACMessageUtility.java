package oracle.communications.talkbac;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.servlet.sip.Address;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipURI;

public class TalkBACMessageUtility implements Serializable {
	private static final long serialVersionUID = 1L;
	private SipApplicationSession appSession;
	private HashMap<String, Address> hashmap = new HashMap<String, Address>();

	TalkBACMessageUtility(SipApplicationSession appSession) {
		this.appSession = appSession;
	}

	public void addClient(Address address) {
		String user = ((SipURI) address.getURI()).getUser().toLowerCase();
		Address addressWithOutTags = TalkBACSipServlet.factory.createAddress(address.getURI());
		hashmap.put(user, addressWithOutTags);
	}

	public void addEndpoint(Address address) {
		String user = ((SipURI) address.getURI()).getUser().toLowerCase();
		SipApplicationSession appSession = TalkBACSipServlet.util.getApplicationSessionByKey(user, false);
		if (appSession != null) {
			Address clientAddress = (Address) appSession.getAttribute(TalkBACSipServlet.CLIENT_ADDRESS);
			String userName = (String) appSession.getAttribute(TalkBACSipServlet.USER);
			if (clientAddress != null && userName != null) {
				System.out.println("Add Endpoint: " + userName + ", " + clientAddress);
				hashmap.put(userName, clientAddress);
			}
		}
	}

	public void removeEndpoint(Address address) {
		String user = ((SipURI) address.getURI()).getUser().toLowerCase();
		hashmap.remove(user);
	}

	public void send(TalkBACMessage m) {

		for (Entry<String, Address> entry : hashmap.entrySet()) {
			System.out.println("Sending message to: " + entry.getKey() + " : " + entry.getValue());
		}

		SipServletRequest msg;

		// TalkBACSipServlet.cdr.info(m.toString().replaceAll("[\n\r]", ""));
		TalkBACSipServlet.cdr.info(m.toString());

		try {
			for (Address clientAddress : hashmap.values()) {
				msg = TalkBACSipServlet.factory.createRequest(appSession, "MESSAGE", TalkBACSipServlet.talkBACAddress, clientAddress);
				msg.setContent(m.toString(), "text/plain");
				msg.send();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
