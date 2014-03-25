// COPYRIGHT: VORPAL.ORG, 2014
// AUTHOR:    JEFF@MCDONALD.NET

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
		String _key = uri.getUser().toString().toLowerCase() + "@" + uri.getHost().toString().toLowerCase();
		return _key;
	}

	@Override
	protected void doSubscribe(SipServletRequest arg0) throws ServletException, IOException {
		System.out.println("\ndoSubscribe... ");

		// Send Ok response back
		SipServletResponse response = arg0.createResponse(202);
		response.addHeader("Expires", arg0.getHeader("Expires"));
		response.send();

		SipApplicationSession app = arg0.getApplicationSession();
		app.setInvalidateWhenReady(false);
		app.setExpires(arg0.getExpires());

		// Add self to list of subscribers
		HashMap<String, String> subscribers; // URI, SessionId
		subscribers = (HashMap<String, String>) app.getAttribute(arg0.getHeader("Event") + ".subscribers");
		subscribers = (subscribers != null) ? subscribers : new HashMap<String, String>();
		subscribers.put(arg0.getFrom().getURI().toString(), arg0.getSession().getId());
		app.setAttribute(arg0.getHeader("Event") + ".subscribers", subscribers);

		// Send 'notify' if status known
		SipServletRequest status = (SipServletRequest) app.getAttribute(arg0.getHeader("Event") + ".status");
		if (null != status) {
			System.out.println("\tsending notification...");
			SipServletRequest notify = arg0.getSession().createRequest("NOTIFY");
			notify.setContent(status.getContent(), status.getContentType());
			notify.setHeader("Event", status.getHeader("Event"));
			notify.setHeader("Subscription-State", "active;expires=" + status.getExpires());
			notify.send();
		}
	}

	@Override
	protected void doPublish(SipServletRequest arg0) throws ServletException, IOException {
		System.out.println("\ndoPublish... " + arg0.getRequestURI());
		System.out.println("\tAppSession: " + arg0.getApplicationSession().getId());
		System.out.println("\tEvent: " + arg0.getHeader("Event").toString());

		// send OK response back
		arg0.createResponse(200).send();

		SipApplicationSession app = arg0.getApplicationSession();
		app.setAttribute(arg0.getHeader("Event") + ".status", arg0);

		// send notifications to all subscribers
		HashMap<String, String> subscribers; // URI, SessionId
		subscribers = (HashMap<String, String>) app.getAttribute(arg0.getHeader("Event") + ".subscribers");
		if (null != subscribers) {
			System.out.println("\tsubscribers found: " + subscribers.size());
			String subscriber;
			String session_id;
			SipSession session;

			for (Entry<String, String> entry : subscribers.entrySet()) {
				subscriber = entry.getKey();
				session_id = entry.getValue();
				session = app.getSipSession(session_id);

				System.out.println("\t\t" + subscriber + ", " + session_id);

				if (session != null && session.isValid()) {
					SipServletRequest notify = session.createRequest("NOTIFY");
					notify.setContent(arg0.getContent(), arg0.getContentType());
					notify.setHeader("Event", arg0.getHeader("Event"));
					notify.setHeader("Subscription-State", "active;expires=" + arg0.getExpires());

					System.out.println("\tsending notify to " + notify.getTo());
					notify.send();
				} else {
					System.out.println("\tremoving invalid session");
					subscribers.remove(subscriber);
					app.setAttribute(arg0.getHeader("Event") + ".subscribers", subscribers);
				}

			}
		}

	}

}
