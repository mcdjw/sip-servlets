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
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

import weblogic.kernel.KernelLogManager;

public class CallFlow1 extends CallFlowHandler {
	private static final long serialVersionUID = 1L;
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
	public void processEvent(SipApplicationSession appSession, MessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		// SipApplicationSession appSession;
		TalkBACMessage msg;

		switch (state) {

		case 1:

			msg = new TalkBACMessage(appSession, "call_created");
			msg.setParameter("origin", origin.getURI().toString());
			msg.setParameter("destination", destination.getURI().toString());
			this.printOutboundMessage(msgUtility.send(msg));

			destinationRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", origin, destination);

			if (TalkBACSipServlet.callInfo != null) {
				destinationRequest.setHeader("Call-Info", TalkBACSipServlet.callInfo);
				// destinationRequest.setHeader("Session-Expires", "3600;refresher=uac");
				destinationRequest.setHeader("Allow", "INVITE, BYE, OPTIONS, CANCEL, ACK, REGISTER, NOTIFY, REFER, MESSAGE");
				destinationRequest.setHeader("Allow-Events", "telephone-event");
			}

			originRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", destination, origin);
			originRequest.setHeader("Call-Info", TalkBACSipServlet.callInfo);
			originRequest.setHeader("Session-Expires", "3600;refresher=uac");
			originRequest.setHeader("Allow", "INVITE, BYE, OPTIONS, CANCEL, ACK, REGISTER, NOTIFY, REFER, MESSAGE");
			originRequest.setHeader("Allow-Events", "telephone-event");
			originRequest.send();
			this.printOutboundMessage(originRequest);

			state = 2;
			originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			destinationRequest.getSession().setAttribute(PEER_SESSION_ID, originRequest.getSession().getId());
			originRequest.getSession().setAttribute(PEER_SESSION_ID, destinationRequest.getSession().getId());

			appSession.setAttribute(DESTINATION_SESSION_ID, destinationRequest.getSession().getId());
			appSession.setAttribute(ORIGIN_SESSION_ID, originRequest.getSession().getId());

			destinationRequest.getSession().setAttribute(REQUEST_DIRECTION, "OUTBOUND");
			destinationRequest.getSession().setAttribute(INITIAL_INVITE_REQUEST, destinationRequest);
			originRequest.getSession().setAttribute(REQUEST_DIRECTION, "OUTBOUND");
			originRequest.getSession().setAttribute(INITIAL_INVITE_REQUEST, originRequest);

			break;
		case 2:
		case 3: // Response from origin
			if (status == 200) {

				discoverOptions(response);

				destinationRequest.setContent(response.getContent(), response.getContentType());
				destinationRequest.send();
				this.printOutboundMessage(destinationRequest);

				state = 4;
				originResponse = response;
				destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

				msg = new TalkBACMessage(appSession, "source_connected");
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				msg.setStatus(183, "Session Progress");
				this.printOutboundMessage(msgUtility.send(msg));

			}

			if (status >= 300) {
				msg = new TalkBACMessage(appSession, "call_failed");
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				this.printOutboundMessage(msgUtility.send(msg));
			}
			break;

		case 4:
		case 5:
		case 6: // Response from destination

			if (status >= 200 && status < 300) {
				SipServletRequest destinationAck = response.createAck();
				destinationAck.send();
				this.printOutboundMessage(destinationAck);

				SipServletRequest originAck = originResponse.createAck();
				originAck.setContent(response.getContent(), response.getContentType());
				originAck.send();
				this.printOutboundMessage(originAck);

				destinationAck.getSession().removeAttribute(CALL_STATE_HANDLER);
				originAck.getSession().removeAttribute(CALL_STATE_HANDLER);

				msg = new TalkBACMessage(appSession, "destination_connected");
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				this.printOutboundMessage(msgUtility.send(msg));

				msg = new TalkBACMessage(appSession, "call_connected");
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				this.printOutboundMessage(msgUtility.send(msg));

				if (kpml_supported) {
					KpmlRelay kpmlRelay = new KpmlRelay(3600);
					kpmlRelay.delayedSubscribe(appSession, 500);
				}

				// Launch Keep Alive Timer
				if (update_supported) {
					UpdateKeepAlive ka = new UpdateKeepAlive(60 * 1000);
					ka.startTimer(appSession);
				}

			}
			if (status >= 300) {
				SipServletRequest bye = originResponse.getSession().createRequest("BYE");
				bye.send();
				this.printOutboundMessage(bye);

				response.getSession().removeAttribute(CALL_STATE_HANDLER);
				originResponse.getSession().removeAttribute(CALL_STATE_HANDLER);

				msg = new TalkBACMessage(appSession, "call_failed");
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				this.printOutboundMessage(msgUtility.send(msg));
			}

			break;

		}

	}
}
