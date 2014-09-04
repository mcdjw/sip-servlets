/*
 * http://tools.ietf.org/html/rfc3725
 *
 * 4.1.  Flow I
 *
 *             A              Controller               B
 *             |(1) INVITE no SDP  |                   |
 *             |<------------------|                   |
 *             |(2) 200 offer1     |                   |
 *             |------------------>|                   |
 *             |                   |(3) INVITE offer1  |
 *             |                   |------------------>|
 *             |                   |(4) 200 OK answer1 |
 *             |                   |<------------------|
 *             |                   |(5) ACK            |
 *             |                   |------------------>|
 *             |(6) ACK answer1    |                   |
 *             |<------------------|                   |
 *             |(7) RTP            |                   |
 *             |.......................................|
 *
 */

package oracle.communications.talkbac;

import java.util.logging.Logger;

import javax.servlet.sip.Address;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;

import weblogic.kernel.KernelLogManager;

public class CallFlow1 extends CallStateHandler {
	static Logger logger;
	{
		logger = Logger.getLogger(CallFlow1.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	Address origin;
	Address destination;

	SipServletRequest destinationRequest;
	SipServletRequest originRequest;
	SipServletResponse originResponse;

	CallFlow1(Address origin, Address destination) {
		this.origin = origin;
		this.destination = destination;
	}

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		SipApplicationSession appSession;
		TalkBACMessage msg;

		switch (state) {

		case 1:
			appSession = request.getApplicationSession();

			msg = new TalkBACMessage(appSession, "call_created");
			msg.send();

			originRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", destination, origin);
			destinationRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", origin, destination);
			if (TalkBACSipServlet.callInfo != null) {
				destinationRequest.setHeader("Call-Info", TalkBACSipServlet.callInfo);
				destinationRequest.setHeader("Session-Expires", "3600;refresher=uac");
				destinationRequest.setHeader("Allow", "INVITE, BYE, OPTIONS, CANCEL, ACK, REGISTER, NOTIFY, REFER, SUBSCRIBE, PRACK, UPDATE, MESSAGE, PUBLISH");
			}

			Address identity = request.getAddressHeader("P-Asserted-Identity");
			String originKey = TalkBACSipServlet.generateKey(identity);
			SipApplicationSession originAppSession = TalkBACSipServlet.util
					.getApplicationSessionByKey(originKey, false);
			String pbx = (String) originAppSession.getAttribute("PBX");
			System.out.println("pbx: " + pbx + ", " + originAppSession.getId().hashCode());
			if (pbx != null) {

				String originUser = ((SipURI) origin.getURI()).getUser();
				SipURI originUri = (SipURI) TalkBACSipServlet.factory.createURI("sip:" + originUser + "@" + pbx);
				originRequest.pushRoute(originUri);

				String destinationUser = ((SipURI) destination.getURI()).getUser();
				SipURI destinationURI = (SipURI) TalkBACSipServlet.factory.createURI("sip:" + destinationUser + "@"
						+ pbx);
				destinationRequest.pushRoute(destinationURI);
			}

			originRequest.send();

			state = 2;
			originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			destinationRequest.getSession().setAttribute(PEER_SESSION_ID, originRequest.getSession().getId());
			originRequest.getSession().setAttribute(PEER_SESSION_ID, destinationRequest.getSession().getId());

			appSession.setAttribute(DESTINATION_SESSION_ID, destinationRequest.getSession().getId());
			appSession.setAttribute(ORIGIN_SESSION_ID, originRequest.getSession().getId());
			// appSession.setAttribute(INITIATOR_SESSION_ID,
			// initiator.getSession().getId());

			break;
		case 2:
		case 3: // Response from origin
			if (status == 200) {
				destinationRequest.setContent(response.getContent(), response.getContentType());
				destinationRequest.send();

				state = 4;
				originResponse = response;
				destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

				msg = new TalkBACMessage(response.getApplicationSession(), "source_connected");
				msg.setStatus(183, "Session Progress");
				msg.send();

			}

			if (status >= 300) {
				msg = new TalkBACMessage(response.getApplicationSession(), "call_failed");
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msg.send();
			}
			break;

		case 4:
		case 5:
		case 6: // Response from destination

			if (status >= 200 && status < 300) {
				SipServletRequest destinationAck = response.createAck();
				destinationAck.send();

				SipServletRequest originAck = originResponse.createAck();
				originAck.setContent(response.getContent(), response.getContentType());
				originAck.send();

				destinationAck.getSession().removeAttribute(CALL_STATE_HANDLER);
				originAck.getSession().removeAttribute(CALL_STATE_HANDLER);

				msg = new TalkBACMessage(response.getApplicationSession(), "destination_connected");
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msg.send();

				msg = new TalkBACMessage(response.getApplicationSession(), "call_connected");
				msg.send();

			}
			if (status >= 300) {
				originResponse.getSession().createRequest("BYE").send();

				response.getSession().removeAttribute(CALL_STATE_HANDLER);
				originResponse.getSession().removeAttribute(CALL_STATE_HANDLER);

				msg = new TalkBACMessage(response.getApplicationSession(), "call_failed");
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msg.send();
			}

			break;

		}

	}
}
