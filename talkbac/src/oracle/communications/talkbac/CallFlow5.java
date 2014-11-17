/*
 * Ringback Tone during Blind Transfer
 * w/DTMF & Keep Alive
 *
 *             A                 Controller                  B
 *             |(1) INVITE w/SDP      |                      |
 *             |<---------------------|                      |
 *             |(2) 200 OK            |                      |
 *             |--------------------->|                      |
 *             |(3) ACK               |                      |
 *             |<---------------------|                      |
 *             |              (4) 250ms delay                |             
 *             |(5) REFER             |                      |
 *             |<---------------------|                      |
 *             |(6) 202 Accepted      |                      |
 *             |--------------------->|                      |
 *             | NOTIFY               |                      |
 *             |--------------------->|                      |
 *             | 200 OK               |                      |
 *             |<---------------------|                      |
 *             |(7) INVITE w/SDP      |                      |
 *             |--------------------->|                      |
 *             |                      |(8) INVITE            |
 *             |                      |--------------------->|
 *             |                      |(9a) 180 Ringing      |
 *             |                      |<---------------------|
 *             |(10a) 180 Ringing     |                      |
 *             |<---------------------|                      |
 *             |                      |(9b) 200 OK           |
 *             |                      |<---------------------|
 *             |(10b) 200 OK          |                      |
 *             |<---------------------|                      |
 *             |(11) ACK              |                      |
 *             |--------------------->|                      |
 *             |                      |(12) ACK              |
 *             |                      |--------------------->|
 *             |.............................................|
 *
 */

package oracle.communications.talkbac;

import java.util.ListIterator;

import javax.servlet.sip.Address;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;

public class CallFlow5 extends CallStateHandler {
	private Address origin;
	private Address destination;
	private String requestId;
	private String destinationUser;
	private String originUser;

	SipServletRequest destinationRequest;
	SipServletResponse destinationResponse;

	SipServletRequest originRequest;
	SipServletResponse originResponse;

	boolean update_supported = false;
	boolean options_supported = false;
	boolean kpml_supported = false;

	TalkBACMessageUtility msgUtility;

	CallFlow5(String requestId, Address origin, Address destination) {
		this.requestId = requestId;
		this.origin = origin;
		this.destination = destination;
		this.msgUtility = new TalkBACMessageUtility();
	}

	CallFlow5(CallFlow5 that) {
		this.origin = that.origin;
		this.destination = that.destination;
		this.requestId = that.requestId;
		this.destinationRequest = that.destinationRequest;
		this.destinationResponse = that.destinationResponse;
		this.originRequest = that.originRequest;
		this.originResponse = that.originResponse;

		this.update_supported = that.update_supported;
		this.options_supported = that.options_supported;
		this.kpml_supported = that.kpml_supported;

		this.msgUtility = that.msgUtility;

	}

	@Override
	public void processEvent(SipApplicationSession appSession, SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		TalkBACMessage msg;

		if (request != null) {
			if (request.getMethod().equals("NOTIFY")) {
				// I don't want to deal with this in my state machine
				SipServletResponse rsp = request.createResponse(200);
				rsp.send();
				this.printOutboundMessage(rsp);
			}
		}

		switch (state) {
		case 1: // send INVITE

			msgUtility.addClient(origin);
			msgUtility.addClient(destination);

			msg = new TalkBACMessage(appSession, "call_created");
			msg.setParameter("origin", origin.getURI().toString());
			msg.setParameter("destination", destination.getURI().toString());
			msgUtility.send(msg);

			originRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", destination, origin);
			destinationRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", origin, destination);

			this.destinationUser = ((SipURI) destination.getURI()).getUser().toLowerCase();
			this.originUser = ((SipURI) origin.getURI()).getUser().toLowerCase();
			SipApplicationSession originAppSession = TalkBACSipServlet.util.getApplicationSessionByKey(originUser, true);
			originAppSession.setAttribute("DESTINATION", destination);

			String gateway = (String) request.getApplicationSession().getAttribute(TalkBACSipServlet.GATEWAY);
			if (gateway != null) {
				Address originAddress = TalkBACSipServlet.factory.createAddress("<sip:" + originUser + "@" + gateway + ">");
				((SipURI) originAddress.getURI()).setLrParam(true);
				originRequest.pushRoute(originAddress);

				Address destinationAddress = TalkBACSipServlet.factory.createAddress("<sip:" + destinationUser + "@" + gateway + ">");
				((SipURI) destinationAddress.getURI()).setLrParam(true);
				destinationRequest.pushRoute(destinationAddress);
			}

			if (TalkBACSipServlet.callInfo != null) {
				destinationRequest.setHeader("Call-Info", TalkBACSipServlet.callInfo);
				destinationRequest.setHeader("Session-Expires", "3600;refresher=uac");
				destinationRequest.setHeader("Allow", "INVITE, BYE, OPTIONS, CANCEL, ACK, REGISTER, NOTIFY, REFER, SUBSCRIBE, PRACK, MESSAGE, PUBLISH");
			}

			destinationRequest.getSession().setAttribute(PEER_SESSION_ID, originRequest.getSession().getId());
			originRequest.getSession().setAttribute(PEER_SESSION_ID, destinationRequest.getSession().getId());

			originRequest.setContent(blackhole, "application/sdp");
			originRequest.send();
			this.printOutboundMessage(originRequest);

			state = 2;
			originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			appSession.setAttribute(DESTINATION_SESSION_ID, destinationRequest.getSession().getId());
			appSession.setAttribute(ORIGIN_SESSION_ID, originRequest.getSession().getId());

			break;

		case 2: // receive 200 OK
		case 3: // send ack

			if (status >= 200 && status < 300) {

				// Support for Keep-Alive
				String allow;
				ListIterator<String> allows = response.getHeaders("Allow");
				while (allows.hasNext() && (update_supported == false || options_supported == false)) {
					allow = allows.next();
					if (allow.equals("UPDATE")) {
						update_supported = true;
					} else if (allow.equals("OPTIONS")) {
						options_supported = true;
					}
				}

				// Support for DTMF
				String event;
				ListIterator<String> events = response.getHeaders("Allow-Events");
				while (events.hasNext()) {
					event = events.next();
					if (event.equals("kpml")) {
						kpml_supported = true;
					}
				}

				originResponse = response;

				SipServletRequest originAck = response.createAck();
				originAck.send();
				this.printOutboundMessage(originAck);

				// set timer
				state = 4;
				ServletTimer t = TalkBACSipServlet.timer.createTimer(appSession, 250, false, this);

				msg = new TalkBACMessage(appSession, "source_connected");
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				msg.setStatus(183, "Session Progress");
				msgUtility.send(msg);

			}

			if (status >= 300) {
				msg = new TalkBACMessage(appSession, "call_failed");
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msgUtility.send(msg);
			}
			break;

		case 4: // receive timeout
		case 5: // send REFER

			SipServletRequest refer = originRequest.getSession().createRequest("REFER");

			Address refer_to = TalkBACSipServlet.factory.createAddress("<sip:" + destinationUser + "@" + TalkBACSipServlet.listenAddress + ">");
			appSession.encodeURI(refer_to.getURI());
			refer.setAddressHeader("Refer-To", refer_to);
			refer.setAddressHeader("Referred-By", TalkBACSipServlet.talkBACAddress);
			refer.send();
			this.printOutboundMessage(refer);

			state = 6;
			refer.getSession().setAttribute(CALL_STATE_HANDLER, this);

			// Prepare for that INVITE
			CallStateHandler csh = new CallFlow5(this);
			csh.state = 7;
			appSession.setAttribute(CALL_STATE_HANDLER, csh);

			break;

		case 6: // receive 202 Accepted
		case 7: // receive INVITE
		case 8: // send INVITE

			if (request != null && request.getMethod().equals("INVITE")) {
				request.getApplicationSession().removeAttribute(CALL_STATE_HANDLER);

				if (false == request.getCallId().equals(originResponse.getCallId())) {
					appSession.setAttribute("IGNORE_BYE", originResponse.getCallId());
				}

				originRequest = request;

				appSession.setAttribute(ORIGIN_SESSION_ID, request.getSession().getId());
				request.getSession().setAttribute(PEER_SESSION_ID, destinationRequest.getSession().getId());
				destinationRequest.getSession().setAttribute(PEER_SESSION_ID, request.getSession().getId());

				destinationRequest.setContent(request.getContent(), request.getContentType());
				destinationRequest.send();
				printOutboundMessage(destinationRequest);

				state = 9;
				destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
			}

			break;

		case 9: // receive 180 / 183 / 200
		case 10: // send 180 / 183 / 200

			if (response != null) {

				if (status < 300) {
					originResponse = originRequest.createResponse(response.getStatus());
					originResponse.setContent(response.getContent(), response.getContentType());
					originResponse.send();
					this.printOutboundMessage(originResponse);

					if (status == 200) {
						destinationResponse = response;

						state = 11;
						originResponse.getSession().setAttribute(CALL_STATE_HANDLER, this);

						msg = new TalkBACMessage(appSession, "destination_connected");
						msg.setParameter("origin", origin.getURI().toString());
						msg.setParameter("destination", destination.getURI().toString());						
						msg.setStatus(response.getStatus(), response.getReasonPhrase());
						msgUtility.send(msg);
					}

				} else {

					response.getSession().removeAttribute(CALL_STATE_HANDLER);
					originResponse.getSession().removeAttribute(CALL_STATE_HANDLER);

					msg = new TalkBACMessage(appSession, "call_failed");
					msg.setParameter("origin", origin.getURI().toString());
					msg.setParameter("destination", destination.getURI().toString());
					msg.setStatus(response.getStatus(), response.getReasonPhrase());
					msgUtility.send(msg);

					TerminateCall terminate = new TerminateCall();
					terminate.processEvent(appSession, request, response, timer);
				}

			}

			break;

		case 11: // receive ACK
		case 12: // send ACK

			if (request != null && request.getMethod().equals("ACK")) {
				destinationRequest = destinationResponse.createAck();
				destinationRequest.setContent(request.getContent(), request.getContentType());
				destinationRequest.send();
				this.printOutboundMessage(destinationRequest);

				msg = new TalkBACMessage(appSession, "call_connected");
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				msgUtility.send(msg);

				destinationRequest.getSession().removeAttribute(CALL_STATE_HANDLER);

				if (kpml_supported) {
					KpmlRelay kpmlRelay = new KpmlRelay(origin, destination);
					kpmlRelay.subscribe(originRequest.getSession());
				}

				// Launch Keep Alive Timer
				KeepAlive ka;
				if (update_supported) {
					ka = new KeepAlive(originRequest.getSession(), destinationRequest.getSession(), KeepAlive.Style.UPDATE, TalkBACSipServlet.keepAlive);
				} else if (options_supported) {
					ka = new KeepAlive(originRequest.getSession(), destinationRequest.getSession(), KeepAlive.Style.OPTIONS, TalkBACSipServlet.keepAlive);
				} else {
					ka = new KeepAlive(originRequest.getSession(), destinationRequest.getSession(), KeepAlive.Style.INVITE, TalkBACSipServlet.keepAlive);
				}
				// ka.processEvent(request, response, timer);
				ka.startTimer(appSession);

			}

			break;

		}

	}

	static final String blackhole = ""
			+ "v=0\r\n"
			+ "o=- 15474517 1 IN IP4 127.0.0.1\r\n"
			+ "s=cpc_med\r\n"
			+ "c=IN IP4 0.0.0.0\r\n"
			+ "t=0 0\r\n"
			+ "m=audio 23348 RTP/AVP 0\r\n"
			+ "a=rtpmap:0 pcmu/8000\r\n"
			+ "a=inactive\r\n";

}
