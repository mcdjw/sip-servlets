/*
 * DTMF Relay w/Unsolicited Notify
 * RFC 2833 encoded payload
 *
 *             A                 Controller                  B
 *             | Established RTP      |                      |
 *             |.............................................|
 *             | SUBSCRIBE            |                      |
 *             |<---------------------|                      |
 *             | 200 OK               |                      |
 *             |--------------------->|                      |
 *             | NOTIFY               |                      |
 *             |--------------------->|                      |
 *             |                      | NOTIFY               |
 *             |                      |--------------------->|
 *             |                      | 200 OK               |
 *             |                      |<---------------------|
 *             | 200 OK               |                      |
 *             |<---------------------|                      |
 *             |           (repeat until finished)           |
 *             |.............................................|
 *
 */

package oracle.communications.talkbac;

import java.nio.ByteBuffer;

import javax.servlet.sip.Address;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

public class DtmfRelay extends CallStateHandler {
	private static final long serialVersionUID = 1L;
	private Address destination;
	private String digits = null;
	private char digit;

	DtmfRelay(Address destination, String digits) {
		this.destination = destination;
		this.digits = digits;
	}

	@Override
	public void processEvent(SipApplicationSession appSession, MessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {
		SipSession sipSession = null;
		SipServletRequest digitRequest = null;

		TalkBACMessage msg;

		switch (state) {
		case 1:

			msg = new TalkBACMessage(appSession, "dial_created");
			msg.setParameter("destination", destination.getURI().toString());
			this.printOutboundMessage(msgUtility.send(msg));

			sipSession = this.findSession(appSession, destination);

			digitRequest = sipSession.createRequest("NOTIFY");

			digit = digits.charAt(0);
			digits = digits.substring(1);

			// digitRequest.setHeader("Subscription-State", "active");
			digitRequest.setHeader("Event", "telephone-event;rate=1000");
			// digitRequest.setHeader("Call-Info", TalkBACSipServlet.callInfo);
			// digitRequest.setHeader("Session-Expires", "3600;refresher=uac");
			digitRequest.setContent(encodeRFC2833(digit, false, 500), "audio/telephone-event");

			digitRequest.send();
			this.printOutboundMessage(digitRequest);

			state = 2;
			digitRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
			break;

		case 2: // send 'end' bit
			// msg = new TalkBACMessage(response.getApplicationSession(),
			// "digit_dialed");
			// msg.setStatus(response.getStatus(), response.getReasonPhrase());
			// msg.setParameter("digit", digit);
			// msg.send();

			sipSession = response.getSession();

			digitRequest = sipSession.createRequest("NOTIFY");
			// digitRequest.setHeader("Subscription-State", "active");
			digitRequest.setHeader("Event", "telephone-event;rate=1000");
			// digitRequest.setHeader("Call-Info", TalkBACSipServlet.callInfo);
			digitRequest.setContent(encodeRFC2833(digit, true, 500), "audio/telephone-event");
			digitRequest.send();
			this.printOutboundMessage(digitRequest);

			if (digits.length() > 0) {
				state = 3;
				digitRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
			} else {
				state = 4;
				digitRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
			}
			break;

		case 3:
			// send next digit

			digit = digits.charAt(0);
			digits = digits.substring(1);

			sipSession = response.getSession();

			digitRequest = sipSession.createRequest("NOTIFY");
			digitRequest.setHeader("Subscription-State", "active");
			digitRequest.setHeader("Event", "telephone-event;rate=1000");
			digitRequest.setHeader("Call-Info", TalkBACSipServlet.callInfo);
			digitRequest.setContent(encodeRFC2833(digit, false, 500), "audio/telephone-event");
			digitRequest.send();
			this.printOutboundMessage(digitRequest);

			state = 2;
			digitRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
			break;

		case 4:
			msg = new TalkBACMessage(appSession, "dial_complete");
			msg.setParameter("destination", destination.getURI().toString());
			this.printOutboundMessage(msgUtility.send(msg));

			sipSession = response.getSession();
			sipSession.removeAttribute(CALL_STATE_HANDLER);

		}

	}

	private byte[] encodeRFC2833(char digit, boolean end, int duration) {
		byte tone = 0;

		switch (digit) {
		case '0':
			tone = 0x00;
			break;
		case '1':
			tone = 0x01;
			break;
		case '2':
			tone = 0x02;
			break;
		case '3':
			tone = 0x03;
			break;
		case '4':
			tone = 0x04;
			break;
		case '5':
			tone = 0x05;
			break;
		case '6':
			tone = 0x06;
			break;
		case '7':
			tone = 0x07;
			break;
		case '8':
			tone = 0x08;
			break;
		case '9':
			tone = 0x09;
			break;
		case '*': // 10
			tone = 0x0A;
			break;
		case '#': // 11
			tone = 0x0B;
			break;
		case 'A': // 12
			tone = 0x0C;
			break;
		case 'B': // 13
			tone = 0x0D;
			break;
		case 'C': // 14
			tone = 0x0E;
			break;
		case 'D': // 15
			tone = 0x0F;
			break;
		case 'F': // 16 for 'flash'
			tone = 0x10;
			break;
		}

		ByteBuffer buf = ByteBuffer.allocate(4);
		buf.put(tone);

		if (end) {
			buf.put((byte) 0x80);
		} else {
			buf.put((byte) 0x00);
		}

		buf.putShort((short) duration);

		return buf.array();
	}

}
