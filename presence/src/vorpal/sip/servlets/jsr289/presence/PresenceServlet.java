/* 
 * COPYRIGHT: VORPAL.ORG, 2014
 * AUTHOR:    JEFF@MCDONALD.NET
 */

package vorpal.sip.servlets.jsr289.presence;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.sip.Address;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.annotation.SipApplicationKey;

@SuppressWarnings("serial")
public class PresenceServlet extends SipServlet {

	@SipApplicationKey
	public static String sessionKey(SipServletRequest req) {
		String key = null;

		if (0 == req.getMethod().compareTo("SUBSCRIBE") && req.getExpires() > 0) {
			key = generateKey(req.getTo());
		} else if (0 == req.getMethod().compareTo("PUBLISH") && req.getExpires() > 0) {
			key = generateKey(req.getFrom());
		}

		return key;
	}

	public static String generateKey(Address address) {
		SipURI uri = (SipURI) address.getURI();
		String key = uri.getUser().toLowerCase() + "@" + uri.getHost().toLowerCase();
		return key;
	}

	@Override
	protected void doSubscribe(SipServletRequest req) throws ServletException, IOException {

		// Send Ok response back
		SipServletResponse response = req.createResponse(202);
		response.addHeader("Expires", req.getHeader("Expires"));
		response.send();

		SipApplicationSession app = req.getApplicationSession();
		app.setInvalidateWhenReady(false);
		app.setExpires(req.getExpires());

		// Add self to list of subscribers
		HashMap<String, String> subscribers; // URI, SessionId
		subscribers = (HashMap<String, String>) app.getAttribute(req.getHeader("Event") + ".subscribers");
		subscribers = (subscribers != null) ? subscribers : new HashMap<String, String>();
		subscribers.put(req.getFrom().getURI().toString(), req.getSession().getId());
		app.setAttribute(req.getHeader("Event") + ".subscribers", subscribers);

		// Send 'notify' if status known
		SipServletRequest status = (SipServletRequest) app.getAttribute(req.getHeader("Event") + ".status");
		if (null != status) {
			SipServletRequest notify = req.getSession().createRequest("NOTIFY");
			notify.setContent(status.getContent(), status.getContentType());
			notify.setHeader("Event", status.getHeader("Event"));
			notify.setHeader("Subscription-State", "active;expires=" + status.getExpires());
			notify.send();
		}
	}

	@Override
	protected void doPublish(SipServletRequest req) throws ServletException, IOException {

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
		}

	}

}
