/* 
 * COPYRIGHT: VORPAL.ORG, 2014
 * AUTHOR:    JEFF@MCDONALD.NET
 */

package vorpal.sip.servlets.jsr289.presence;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletContextEvent;
import javax.servlet.sip.SipServletListener;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipSessionsUtil;
import javax.servlet.sip.annotation.SipApplicationKey;
import javax.servlet.sip.annotation.SipListener;

import weblogic.kernel.KernelLogManager;

@SuppressWarnings("serial")
@SipListener
public class PresenceServlet extends SipServlet implements SipServletListener {

	static Logger logger;
	{
		logger = Logger.getLogger(PresenceServlet.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	@Resource
	public static SipFactory factory;

	@Resource
	public static SipSessionsUtil util;

	@SipApplicationKey
	public static String sessionKey(SipServletRequest req) {
		return generateKey(req.getTo().getURI().toString());
	}

	private static String generateKey(String uri) {
		return uri.substring(uri.indexOf(':') + 1).toLowerCase();
	}

	@Override
	public void doSubscribe(SipServletRequest req) throws ServletException, IOException {
		if (logger.isLoggable(Level.FINE)) {
			logger.fine("SUBSCRIBE, " + req.getTo() + ", Event: " + req.getHeader("Event") + ", Contact: "
					+ req.getHeader("Contact"));
		}
		
		

		SipApplicationSession appSession = req.getApplicationSession();
		String identity = generateKey( req.getTo().getURI().toString() );
		// String event = req.getHeader("Event");
		// String contact = req.getAddressHeader("Contact").toString();
		// Integer expires = req.getExpires();
		//
		ListIterator<String> acceptsItr = req.getHeaders("Accepts");
		HashSet<String> accepts = new HashSet<String>();
		while (acceptsItr.hasNext()) {
			accepts.add(acceptsItr.next());
		}
		
		 // Send Ok response back
		 SipServletResponse response = req.createResponse(202);
		 response.setHeader("Expires", req.getHeader("Expires"));
		 response.send();

		HashMap<String, Subscriber> subscribers;
		subscribers = doSubscribe(appSession, identity, event, contact, expires, acceptsItr);
		//
		// String eventStatus = (String) appSession.getAttribute(event +
		// ".status");
		// String eventType = (String) appSession.getAttribute(event + ".type");
		// Long eventExpiration = (Long) appSession.getAttribute(event +
		// ".expiration");
		//
		// if (eventExpiration != null) { // if status is known
		// if (eventExpiration > System.currentTimeMillis()) { // and not
		// // expired
		// if (accepts.contains(eventType)) { // and of the right type
		// SipServletRequest notify = factory.createRequest(appSession,
		// "NOTIFY", req.getTo(),
		// req.getAddressHeader("Contact"));
		// notify.setHeader("Event", event);
		// notify.setHeader("Subscription-State", "active;expires=" + expires);
		// notify.setContent(eventStatus, eventType);
		// notify.send();
		// }
		// } else {
		// appSession.removeAttribute(event + ".status");
		// appSession.removeAttribute(event + ".type");
		// appSession.removeAttribute(event + ".expiration");
		// }
		// }

	}

	public void doSubscribe(SipApplicationSession appSession, String identity, String event, String contact,
			Integer expires, ListIterator<String> acceptsItr) throws ServletException, IOException {

		appSession.setInvalidateWhenReady(false);
		appSession.setExpires(expires);

		Subscriber subscriber = new Subscriber(contact, expires, acceptsItr);

		HashMap<String, Subscriber> subscribers; // Contact, Subscriber
		subscribers = (HashMap<String, Subscriber>) appSession.getAttribute(event + ".subscribers");
		subscribers = (subscribers != null) ? subscribers : new HashMap<String, Subscriber>();

		subscribers.put(contact, subscriber);
		appSession.setAttribute(event + ".subscribers", subscribers);

	}

	@Override
	 protected void doPublish(SipServletRequest req) throws ServletException,
	 IOException {
//	 req.createResponse(200).send();
//	
//	 SipApplicationSession appSession = req.getApplicationSession();
//	 String event = req.getHeader("Event");
//	 Object eventStatus = req.getContent();
//	 String eventType = req.getContentType();
//	 int expires = req.getExpires();
//	 String identity = req.getTo().getURI().toString();
//	
//	 List<Subscriber> subscribers;
//	 subscribers = doPublish(appSession, eventStatus, eventType, expires);
//	
//	 for(Subscriber subscriber : subscribers){ //This assumes SIP for
//	 everything
//	 SipServletRequest notify = factory.createRequest(appSession, "NOTIFY",
//	 identity, subscriber.getContact());
//	
//	 notify.setHeader("Event", event);
//	 notify.setHeader("Subscription-State", "active;expires=" + expires);
//	 notify.setContent(eventStatus, eventType);
//	 notify.send();
		//
		// }
	
	 }

	protected void doPublishOld(SipServletRequest req) throws ServletException, IOException {

		// send OK response back
		req.createResponse(200).send();

		SipApplicationSession app = req.getApplicationSession();
		app.setAttribute(req.getHeader("Event") + ".status", req);

		// send notifications to all subscribers
		HashMap<String, String> subscribers; // URI, SessionId
		subscribers = (HashMap<String, String>) app.getAttribute(req.getHeader("Event") + ".subscribers");
		if (null != subscribers) {

			String subscriber;
			String session_id;
			SipSession session;

			if (logger.isLoggable(Level.FINE)) {
				logger.fine("PUBLISH " + req.getFrom() + ", Event: " + req.getHeader("Event") + ", Subscribers: "
						+ Arrays.toString(subscribers.keySet().toArray()));
			}

			for (Entry<String, String> entry : subscribers.entrySet()) {
				subscriber = entry.getKey();
				session_id = entry.getValue();
				session = app.getSipSession(session_id);

				if (session != null && session.isValid()) {
					SipServletRequest notify = session.createRequest("NOTIFY");
					notify.setContent(req.getContent(), req.getContentType());
					notify.setHeader("Event", req.getHeader("Event"));
					notify.setHeader("Subscription-State", "active;expires=" + req.getExpires());

					notify.send();
				} else {
					subscribers.remove(subscriber);
					app.setAttribute(req.getHeader("Event") + ".subscribers", subscribers);
				}

			}
		} else {
			if (logger.isLoggable(Level.FINE)) {
				logger.fine("PUBLISH " + req.getFrom() + ", Event: " + req.getHeader("Event") + ", Subscribers: none");
			}
		}

	}

	@Override
	public void servletInitialized(SipServletContextEvent arg0) {
		logger.fine(this.getServletName() + " initialized");
	}

}
