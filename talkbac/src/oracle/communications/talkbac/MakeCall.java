/*
 * Use REFER to create ringback-tone.
 * Use AcceptCall state machine to handle incoming INVITE.
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
 *             | AcceptCall()         |                      |
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

public class MakeCall extends CallStateHandler {
	private Address origin;
	private Address destination;
	private String requestId;
	private String destinationUser;
	private String originUser;

	// SipServletRequest destinationRequest;
	// SipServletResponse destinationResponse;

	SipServletRequest originRequest;
	SipServletResponse originResponse;

	boolean update_supported = false;
	boolean options_supported = false;
	boolean kpml_supported = false;

	TalkBACMessageUtility msgUtility;

	MakeCall(String requestId, Address origin, Address destination) {
		this.requestId = requestId;
		this.origin = origin;
		this.destination = destination;
	}

	MakeCall(MakeCall that) {
		this.origin = that.origin;
		this.destination = that.destination;
		this.requestId = that.requestId;
		// this.destinationRequest = that.destinationRequest;
		// this.destinationResponse = that.destinationResponse;
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

		switch (state) {
		case 1: // send INVITE

			msg = new TalkBACMessage(appSession, "call_created");
			msgUtility.send(msg);

			String pbx = (String) request.getApplicationSession().getAttribute("PBX");
			this.originUser = ((SipURI) origin.getURI()).getUser();
			Address proxyAddress = TalkBACSipServlet.factory.createAddress("<sip:" + originUser + "@" + pbx + ">");

			originRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", destination, origin);
			originRequest.pushRoute(proxyAddress);
			originRequest.setContent(blackhole_sdp, "application/sdp");
			originRequest.send();
			this.printOutboundMessage(originRequest);

			state = 2;
			originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
			appSession.setAttribute(ORIGIN_SESSION_ID, originRequest.getSession().getId());
			break;

		case 2: // receive 200 OK
		case 3: // send ack

			if (status >= 200 && status < 300) {

				// Support for Keep-Alive
				// String allow;
				// ListIterator<String> allows = response.getHeaders("Allow");
				// while (allows.hasNext() && (update_supported == false ||
				// options_supported == false)) {
				// allow = allows.next();
				// if (allow.equals("UPDATE")) {
				// update_supported = true;
				// } else if (allow.equals("OPTIONS")) {
				// options_supported = true;
				// }
				// }

				// Support for DTMF
				// String event;
				// ListIterator<String> events =
				// response.getHeaders("Allow-Events");
				// while (events.hasNext()) {
				// event = events.next();
				// if (event.equals("kpml")) {
				// kpml_supported = true;
				// }
				// }

				originResponse = response;

				SipServletRequest originAck = response.createAck();
				originAck.send();
				this.printOutboundMessage(originAck);

				// set timer
				state = 4;
				ServletTimer t = TalkBACSipServlet.timer.createTimer(appSession, 250, false, this);

				msg = new TalkBACMessage(appSession, "source_connected");
				msg.setStatus(183, "Session Progress");
				msgUtility.send(msg);

			}

			if (status >= 300) {
				msg = new TalkBACMessage(appSession, "call_failed");
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msgUtility.send(msg);
			}
			break;

		case 4: // receive timeout
		case 5: // send REFER

			SipServletRequest refer = originRequest.getSession().createRequest("REFER");
			Address refer_to = TalkBACSipServlet.factory.createAddress("<sip:" + destinationUser + "@" + TalkBACSipServlet.listenAddress + ">");
			refer.setAddressHeader("Refer-To", refer_to);
			refer.setAddressHeader("Referred-By", TalkBACSipServlet.talkBACAddress);
			refer.send();
			this.printOutboundMessage(refer);

			state = 6;
			refer.getSession().setAttribute(CALL_STATE_HANDLER, this);

			// // Prepare for that INVITE
			// CallStateHandler csh = new MakeCall(this);
			// csh.state = 7;
			// appSession.setAttribute(CALL_STATE_HANDLER, csh);

			break;

		case 6: // receive 202 Accepted
		case 7: // receive NOTIFY

			if (request != null && request.getMethod().equals("NOTIFY")) {
				SipServletResponse notifyResponse = request.createResponse(200);
				notifyResponse.send();
				this.printOutboundMessage(notifyResponse);
			}

			break;

		}
	}

	static final String blackhole_sdp = ""
			+ "v=0\r\n"
			+ "o=- 15474517 1 IN IP4 127.0.0.1\r\n"
			+ "s=cpc_med\r\n"
			+ "c=IN IP4 0.0.0.0\r\n"
			+ "t=0 0\r\n"
			+ "m=audio 23348 RTP/AVP 0\r\n"
			+ "a=rtpmap:0 pcmu/8000\r\n"
			+ "a=inactive\r\n";

}
