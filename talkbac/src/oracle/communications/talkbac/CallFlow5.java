/*
 * Jeff's 'Ringing' Callflow
 *
 *             A                 Controller                  B
 *             |(1) INVITE w/o SDP    |                      |
 *             |<---------------------|                      |
 *             |(2) 200 OK            |                      |
 *             |--------------------->|                      |
 *             |(3) ACK               |                      |
 *             |<---------------------|                      |
 *             |(4) REFER             |                      |
 *             |<---------------------|                      |
 *             |(5) 202 Accepted      |                      |
 *             |--------------------->|                      |
 *             |(6) NOTIFY            |                      |
 *             |--------------------->|                      |
 *             |(7) 200 OK            |                      |
 *             |<---------------------|                      |
 *             |(8) INVITE w/SDP      |                      |
 *             |--------------------->|                      |
 *             |                      |(9) INVITE            |
 *             |                      |--------------------->|
 *             |                      |(10) 200 OK            |
 *             |                      |<---------------------|
 *             |(11) 200 OK            |                      |
 *             |<---------------------|                      |
 *             |(12) ACK              |                      |
 *             |--------------------->|                      |
 *             |                      |(13) ACK              |
 *             |                      |--------------------->|
 *             |.............................................|
 *
 */

package oracle.communications.talkbac;

import javax.servlet.sip.Address;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public class CallFlow5 extends CallStateHandler {
	Address origin;
	Address destination;
	public Address user;

	SipServletRequest destinationRequest;
	SipServletResponse destinationResponse;

	SipServletRequest originRequest;
	SipServletResponse originResponse;
	
	SipServletRequest originInviteRequest;
	SipServletResponse originInviteResponse;
	

	CallFlow5(Address user, Address origin, Address destination) {
		this.user = user;
		this.origin = origin;
		this.destination = destination;
	}

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		SipApplicationSession appSession;
		TalkBACMessage msg;

		if (request != null && request.getMethod().equals("NOTIFY")) {
			request.createResponse(200).send();
			return;
		}

		switch (state) {
		case 1: {
			appSession = request.getApplicationSession();

			msg = new TalkBACMessage(appSession, "call_created");
			msg.send();

			destinationRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", origin, destination);
			if (TalkBACSipServlet.callInfo != null) {
				destinationRequest.setHeader("Call-Info", TalkBACSipServlet.callInfo);
				destinationRequest.setHeader("Session-Expires", "3600;refresher=uac");
				destinationRequest.setHeader("Allow", "INVITE, BYE, OPTIONS, CANCEL, ACK, REGISTER, NOTIFY, REFER, SUBSCRIBE, PRACK, UPDATE, MESSAGE, PUBLISH");
			}

			originRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", destination, origin);

			if (TalkBACSipServlet.outboundProxy != null) {
				destinationRequest.pushRoute(TalkBACSipServlet.outboundProxy);
				originRequest.pushRoute(TalkBACSipServlet.outboundProxy);
			}

			destinationRequest.getSession().setAttribute(PEER_SESSION_ID, originRequest.getSession().getId());
			originRequest.getSession().setAttribute(PEER_SESSION_ID, destinationRequest.getSession().getId());

			originRequest.setContent(blackhole, "application/sdp");
			originRequest.send();

			state = 2;
			originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			appSession.setAttribute(DESTINATION_SESSION_ID, destinationRequest.getSession().getId());
			appSession.setAttribute(ORIGIN_SESSION_ID, originRequest.getSession().getId());
			// appSession.setAttribute(INITIATOR_SESSION_ID,
			// initiator.getSession().getId());

		}
			break;

		case 2:
		case 3:
		case 4: {
			if (status >= 200 && status < 300) {
				SipServletRequest originAck = response.createAck();
				originAck.send();

				SipServletRequest refer = response.getSession().createRequest("REFER");

				Address selfAddress = TalkBACSipServlet.factory.createAddress("sip:" + TalkBACSipServlet.servletName + "@" + TalkBACSipServlet.listenAddress);
				selfAddress.getURI().setParameter("key", TalkBACSipServlet.generateKey(user));

				refer.setAddressHeader("Refer-To", selfAddress);
				refer.send();

				state = 5;
				originResponse = response;
				refer.getSession().setAttribute(CALL_STATE_HANDLER, this);
				refer.getApplicationSession().setAttribute(CALL_STATE_HANDLER, this);

				msg = new TalkBACMessage(response.getApplicationSession(), "source_connected");
				msg.setStatus(183, "Session Progress");
				msg.send();
			}

			if (status >= 300) {
				msg = new TalkBACMessage(response.getApplicationSession(), "call_failed");
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msg.send();
			}

		}
			break;

		case 5:
		case 6:
		case 7:

			if (request != null && request.getMethod().equals("INVITE")) {
				request.getApplicationSession().removeAttribute(CALL_STATE_HANDLER);

				originInviteRequest = request;
				destinationRequest = destinationRequest.getSession().createRequest("INVITE");
				destinationRequest.setContent(request.getContent(), request.getContentType());
				destinationRequest.send();

				state = 8;
				destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
			}

			break;

		case 8:
		case 9:

			if (response != null) {
				originInviteResponse = originInviteRequest.createResponse(response.getStatus());
				originInviteResponse.setContent(response.getContent(), response.getContentType());
				originInviteResponse.send();

				if (status == 200) {
					destinationResponse = response;

					state = 10;
					originInviteResponse.getSession().setAttribute(CALL_STATE_HANDLER, this);

					msg = new TalkBACMessage(response.getApplicationSession(), "destination_connected");
					msg.setStatus(response.getStatus(), response.getReasonPhrase());
					msg.send();
				}

				if (status > 300) {

					originRequest.getSession().createRequest("BYE").send();
					response.getSession().removeAttribute(CALL_STATE_HANDLER);
					originResponse.getSession().removeAttribute(CALL_STATE_HANDLER);

					msg = new TalkBACMessage(response.getApplicationSession(), "call_failed");
					msg.setStatus(response.getStatus(), response.getReasonPhrase());
					msg.send();
				}

			}

			break;

		case 10:
		case 11:

			if (request != null && request.getMethod().equals("ACK")) {
				destinationRequest = destinationResponse.createAck();
				destinationRequest.setContent(request.getContent(), request.getContentType());
				destinationRequest.send();

				msg = new TalkBACMessage(request.getApplicationSession(), "call_connected");
				msg.send();
			}

			break;

		}

	}

	// media line has a range of zero ports "4002/0"
	static final String blackhole = ""
			+ "v=0\n"
			+ "o=- 3614531588 3614531588 IN IP4 192.168.1.202\n"
			+ "s=cpc_med\n"
			+ "c=IN IP4 192.168.1.202\n"
			+ "t=0 0\n"
			+ "m=audio 4002/0 RTP/AVP 111 110 109 9 0 8 101"
			+ "a=sendrecv\n"
			+ "a=rtpmap:111 OPUS/48000\n"
			+ "a=fmtp:111 maxplaybackrate=32000;useinbandfec=1\n"
			+ "a=rtpmap:110 SILK/24000\n"
			+ "a=fmtp:110 useinbandfec=1\n"
			+ "a=rtpmap:109 SILK/16000\n"
			+ "a=fmtp:109 useinbandfec=1\n"
			+ "a=rtpmap:9 G722/8000\n"
			+ "a=rtpmap:0 PCMU/8000\n"
			+ "a=rtpmap:8 PCMA/8000\n"
			+ "a=rtpmap:101 telephone-event/8000\n"
			+ "a=fmtp:101 0-16\n";

}
