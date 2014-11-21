/*
 * http://tools.ietf.org/html/rfc3725
 *
 * 4.1.  Flow I
 *
 *  A              Controller               B
 *  |(1) INVITE         |                   |
 *  |------------------>|                   |
 *  |                   |(2) INVITE         |
 *  |                   |------------------>|
 *  |                   |(3) 200 OK         |
 *  |                   |<------------------|
 *  |(4) 200 OK         |                   |
 *  |<------------------|                   |
 *  |(5) ACK            |                   |
 *  |------------------>|                   |
 *  |                   |(6) ACK            |
 *  |                   |------------------>|
 *  | RTP               |                   |
 *  |.......................................|
 *
 */

package oracle.communications.talkbac;

import javax.servlet.sip.Address;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;

public class AcceptCall extends CallStateHandler {

	SipServletRequest originRequest;
	SipServletResponse destinationResponse;

	@Override
	public void processEvent(SipApplicationSession appSession, TalkBACMessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		if (request != null) {
			if (request.isInitial()) {
				Address originAddress = null;
				Address destinationAddress = null;

				originAddress = request.getFrom();

				// in case of INVITE due to REFER
				String key = ((SipURI) originAddress.getURI()).getUser().toString().toLowerCase();
				SipApplicationSession userAppSession = TalkBACSipServlet.util.getApplicationSessionByKey(key, false);
				if (userAppSession != null) {
					destinationAddress = (Address) userAppSession.getAttribute(TalkBACSipServlet.DESTINATION_ADDRESS);
					System.out.println("Getting Destination " + destinationAddress + " from appSession: " + userAppSession.getId());
					if (destinationAddress != null) {
						userAppSession.removeAttribute(TalkBACSipServlet.DESTINATION_ADDRESS);
						msgUtility.removeEndpoint(request.getTo());
						// msgUtility.removeEndpoint(originAddress);
					}
				}

				destinationAddress = (destinationAddress != null) ? destinationAddress : request.getTo();

				msgUtility.addEndpoint(destinationAddress);

				appSession.setAttribute(TalkBACSipServlet.ORIGIN_ADDRESS, originAddress);
				appSession.setAttribute(TalkBACSipServlet.DESTINATION_ADDRESS, destinationAddress);

				originRequest = request;

				TalkBACMessage msg;
				msg = new TalkBACMessage(appSession, "call_created");
				msg.setParameter("origin", originAddress.getURI().toString());
				msg.setParameter("destination", destinationAddress.getURI().toString());
				msgUtility.send(msg);

				msg = new TalkBACMessage(appSession, "origin_connected");
				msg.setParameter("origin", originAddress.getURI().toString());
				msg.setParameter("destination", destinationAddress.getURI().toString());
				msgUtility.send(msg);

				msg = new TalkBACMessage(appSession, "call_incoming");
				msg.setParameter("origin", originAddress.getURI().toString());
				msg.setParameter("destination", destinationAddress.getURI().toString());
				msgUtility.send(msg);

				// SipServletRequest destinationRequest =
				// TalkBACSipServlet.factory.createRequest(request.getApplicationSession(),
				// "INVITE", destinationAddress,
				// originAddress);

				SipServletRequest destinationRequest = TalkBACSipServlet.factory.createRequest(request.getApplicationSession(), "INVITE", originAddress,
						destinationAddress);

				destinationRequest.setContent(request.getContent(), request.getContentType());
				destinationRequest.send();
				destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
				this.printOutboundMessage(destinationRequest);

			} else {

				if (request.getMethod().equals("ACK")) {
					SipServletRequest destinationAck = destinationResponse.createAck();
					destinationAck.setContent(request.getContent(), request.getContentType());
					destinationAck.send();
					this.printOutboundMessage(destinationAck);
					destinationAck.getSession().removeAttribute(CALL_STATE_HANDLER);

					// Add Update timer for KeepAlive

					KeepAlive ka = new KeepAlive(originRequest.getSession(), destinationResponse.getSession(), KeepAlive.Style.UPDATE,
							TalkBACSipServlet.keepAlive);
					ka.startTimer(appSession);

				} else if (request.getMethod().equals("PRACK")) {
					SipServletRequest destinationPrack = destinationResponse.createPrack();
					destinationPrack.setContent(request.getContent(), request.getContentType());
					destinationPrack.send();
					this.printOutboundMessage(destinationPrack);
					destinationPrack.getSession().setAttribute(CALL_STATE_HANDLER, this);
				} else {
					SipServletRequest destRequest = destinationResponse.getSession().createRequest(request.getMethod());
					destRequest.setContent(request.getContent(), request.getContentType());
					destRequest.send();
					this.printOutboundMessage(destRequest);
				}

			}

		} else {
			if (response.getStatus() >= 200 && response.getStatus() < 400) {
				TalkBACMessage msg;
				msg = new TalkBACMessage(appSession, "call_answered");
				msg.setParameter("origin", response.getFrom().getURI().toString());
				msg.setParameter("destination", response.getTo().getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msgUtility.send(msg);

				msg = new TalkBACMessage(appSession, "destination_connected");
				msg.setParameter("origin", response.getFrom().getURI().toString());
				msg.setParameter("destination", response.getTo().getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msgUtility.send(msg);

				msg = new TalkBACMessage(appSession, "call_connected");
				msg.setParameter("origin", response.getFrom().getURI().toString());
				msg.setParameter("destination", response.getTo().getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msgUtility.send(msg);

			} else if (response.getStatus() >= 400) {
				TalkBACMessage msg;
				msg = new TalkBACMessage(appSession, "call_rejected");
				msg.setParameter("origin", response.getFrom().getURI().toString());
				msg.setParameter("destination", response.getTo().getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msgUtility.send(msg);

				msg = new TalkBACMessage(appSession, "destination_connected");
				msg.setParameter("origin", response.getFrom().getURI().toString());
				msg.setParameter("destination", response.getTo().getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msgUtility.send(msg);

				msg = new TalkBACMessage(appSession, "call_connected");
				msg.setParameter("origin", response.getFrom().getURI().toString());
				msg.setParameter("destination", response.getTo().getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msgUtility.send(msg);
			}

			destinationResponse = response;
			SipServletResponse originResponse = originRequest.createResponse(destinationResponse.getStatus());
			originResponse.setContent(destinationResponse.getContent(), destinationResponse.getContentType());
			originResponse.send();
			this.printOutboundMessage(originResponse);
			originResponse.getSession().setAttribute(CALL_STATE_HANDLER, this);
		}

	}
}
