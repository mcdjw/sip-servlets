/*
 *             A                 Controller                  B
 *             |......................|......................|
 *             |                      |(1) INVITE (bh)       |
 *             |                      |--------------------->|
 *             |                      |(2) 200 OK            |
 *             |                      |<---------------------|
 *             |                      |(3) ACK               |
 *             |                      |--------------------->|
 *
 */

package oracle.communications.talkbac;

import javax.servlet.sip.Address;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

public class Hold extends CallStateHandler {
	private static final long serialVersionUID = 1L;
	private Address destination;

	Hold(Address destination) {
		this.destination = destination;
	}

	@Override
	public void processEvent(SipApplicationSession appSession, MessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		switch (state) {
		case 1: // send INVITE
			SipSession destinationSession = findSession(appSession, destination);

			SipServletRequest holdRequest = destinationSession.createRequest("INVITE");
			holdRequest.setContent(blackhole.getBytes(), "application/sdp");
			holdRequest.send();
			this.printOutboundMessage(holdRequest);

			state = 2;
			holdRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			break;

		case 2: // receive 200 OK
		case 3: // send ack

			if (status >= 200) {

				if (status < 300) {
					SipServletRequest ack = response.createAck();
					ack.send();
					this.printOutboundMessage(ack);
					ack.getSession().removeAttribute(CALL_STATE_HANDLER);
				}

				TalkBACMessage msg = new TalkBACMessage(appSession, "call_on_hold");
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
