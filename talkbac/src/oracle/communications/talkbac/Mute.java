/*
 * Ringback Tone during Blind Transfer
 * w/DTMF & Keep Alive
 *
 *             A                 Controller                  B
 *             |..RTP.................|......................|
 *             |                      |(1) INVITE            |
 *             |                      |--------------------->|
 *             |                      |(2) 200 OK            |
 *             |                      |<---------------------|
 *             |(3) INVITE (recvonly) |                      |             
 *             |<---------------------|                      |
 *             |(4) 200 OK            |                      |             
 *             |--------------------->|                      |
 *             |                      | (5) ACK              |
 *             |                      |--------------------->|
 *             |..RTP (muted).........|......................|
 *             |                      |                      |
 *
 */

package oracle.communications.talkbac;

import java.util.Iterator;

import javax.servlet.sip.Address;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

public class Mute extends CallStateHandler {
	private static final long serialVersionUID = 1L;
	Address origin;
	Address destination;
	SipSession originSession = null;
	SipSession destinationSession = null;
	SipServletResponse destinationResponse;
	SipServletResponse originResponse;

	Mute(Address origin, Address destination) {
		this.origin = origin;
		this.destination = destination;
	}

	@Override
	public void processEvent(SipApplicationSession appSession, TalkBACMessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		switch (state) {
		case 1: // send INVITE
			this.originSession = findSession(appSession, origin);
			this.destinationSession = findSession(appSession, destination);

			SipServletRequest destinationRequest = destinationSession.createRequest("INVITE");
			destinationRequest.send();
			this.printOutboundMessage(destinationRequest);

			state = 2;
			destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			break;

		case 2: // receive 200 OK
		case 3: // send INVITE

			if (status == 200) {
				this.destinationResponse = response;

				SipServletRequest originRequest = originSession.createRequest("INVITE");

				String content = destinationResponse.getContent().toString();
				content = content.replace("sendrecv", "recvonly");
				originRequest.setContent(content, destinationResponse.getContentType());
				originRequest.send();
				this.printOutboundMessage(originRequest);

				state = 4;
				originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			}

			break;

		case 4: // receive 200 OK
		case 5: // send ACK
		case 6: // send ACK

			if (status >= 200) {
				this.originResponse = response;

				SipServletRequest originAck = originResponse.createAck();
				originAck.send();
				this.printOutboundMessage(originAck);

				SipServletRequest destinationAck = destinationResponse.createAck();
				destinationAck.setContent(originResponse.getContent(), originResponse.getContentType());
				destinationAck.send();
				this.printOutboundMessage(destinationAck);

				destinationAck.getSession().removeAttribute(CALL_STATE_HANDLER);
				originAck.getSession().removeAttribute(CALL_STATE_HANDLER);

				TalkBACMessage msg = new TalkBACMessage(appSession, "call_muted");
				msg.setParameter("destination", destination.getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msgUtility.send(msg);
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
