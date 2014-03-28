/* 
 * COPYRIGHT: VORPAL.ORG, 2014
 * AUTHOR:    JEFF@MCDONALD.NET
 */

package vorpal.sip.servlets.jsr289.registrar;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.sip.Address;
import javax.servlet.sip.Proxy;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.URI;
import javax.servlet.sip.annotation.SipApplicationKey;

import weblogic.kernel.KernelLogManager;

@SuppressWarnings("serial")
public class ProxyRegistrarServlet extends SipServlet {

	static Logger logger;
	{
		logger = Logger.getLogger(ProxyRegistrarServlet.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	public final static String CONTACTS_MAP = "CONTACTS_MAP";

	@Resource
	SipFactory sipFactory;

	@SipApplicationKey
	public static String sessionKey(SipServletRequest req) {
		return generateKey(req.getTo());
	}

	public static String generateKey(Address address) {
		SipURI uri = (SipURI) address.getURI();
		String key = uri.getUser().toLowerCase() + "@" + uri.getHost().toLowerCase();
		return key;
	}

	@Override
	protected void doRequest(SipServletRequest req) throws ServletException, IOException {
		if (0 == req.getMethod().compareToIgnoreCase("REGISTER")) {
			doRegister(req);
		} else {
			doMethod(req);
		}
	}

	@Override
	protected void doRegister(SipServletRequest req) throws ServletException, IOException {

		SipServletResponse resp = req.createResponse(200);
		SipApplicationSession appSession = req.getApplicationSession();

		ListIterator<Address> contactsIterator = req.getAddressHeaders("Contact");

		HashMap<Address, Long> contacts_map = (HashMap<Address, Long>) appSession.getAttribute(CONTACTS_MAP);
		contacts_map = (contacts_map != null) ? contacts_map : new HashMap<Address, Long>();

		if (contactsIterator.hasNext()) { // add or remove a contact

			while (contactsIterator.hasNext()) {
				Address contact = contactsIterator.next();

				int expires = (contact.getExpires() >= 0) ? contact.getExpires() : req.getExpires();

				if (expires > 0) { // add contact
					contacts_map.put(contact, System.currentTimeMillis() + (expires * 1000));
				} else { // remove contact
					contacts_map.remove(contact);
				}

			}

		} else { // query a contact
			if (0 == contacts_map.size()) { // no contacts found
				resp = req.createResponse(404);
			}
		}

		Iterator<Entry<Address, Long>> itr = contacts_map.entrySet().iterator();
		while (itr.hasNext()) {
			if (0 > (int) Math.ceil((itr.next().getValue() - System.currentTimeMillis()) / 1000.0)) {
				itr.remove();
			}
		}

		int max_expires = 0;
		if (resp.getStatus() == 200) {

			for (Entry<Address, Long> entry : contacts_map.entrySet()) {
				int expires = (int) Math.ceil((entry.getValue() - System.currentTimeMillis()) / 1000.0);
				if (contacts_map.size() == 1) {
					resp.addAddressHeader("Contact", entry.getKey(), false);
					resp.setExpires(expires);
				} else {
					Address contactWithExpiration = sipFactory.createAddress(entry.getKey().toString());
					contactWithExpiration.setExpires(expires);
					resp.addAddressHeader("Contact", contactWithExpiration, false);
				}
				max_expires = Math.max(expires, max_expires);
			}

			appSession.setAttribute(CONTACTS_MAP, contacts_map);

			if (contacts_map.size() > 0) {
				appSession.setInvalidateWhenReady(false);
				appSession.setExpires((int) Math.ceil(max_expires / 60.0));
			} else {
				appSession.setInvalidateWhenReady(true);
			}
		}

		if (logger.getLevel().equals(Level.FINE)) {
			logger.fine(req.getMethod() + " " + req.getTo());
			ListIterator<Address> i = resp.getAddressHeaders("Contact");
			while (i.hasNext()) {
				logger.fine("Contact: " + i.next().toString());
			}
		}

		resp.send();
	}

	protected void doMethod(SipServletRequest request) throws ServletException, IOException {
		SipApplicationSession appSession = request.getApplicationSession();

		HashMap<Address, Long> contacts = (HashMap<Address, Long>) appSession.getAttribute(CONTACTS_MAP);
		if (null == contacts) {
			Proxy proxy = request.getProxy();
			proxy.proxyTo(request.getRequestURI());
		} else {

			LinkedList<URI> aors = new LinkedList<URI>();

			for (Entry<Address, Long> entry : contacts.entrySet()) {
				aors.add(entry.getKey().getURI());
			}

			Proxy proxy = request.getProxy();
			proxy.createProxyBranches(aors);
			proxy.startProxy();
		}

	}

}
