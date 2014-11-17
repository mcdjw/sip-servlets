package oracle.communications.talkbac;

/*
 * Keep Alive via SIP reINVITE
 *
 *             A                 Controller                  B
 *             | Established RTP      |                      |
 *             |.............................................|
 *             | INVITE               |                      |
 *             |<---------------------|                      |
 *             | 200 OK               |                      |
 *             |--------------------->|                      |
 *             |                      | INVITE               |
 *             |                      |--------------------->|
 *             |                      | 200 OK               |
 *             |                      |<---------------------|
 *             | 200 OK               |                      |
 *             |<---------------------|                      |
 *             | ACK                  |                      |
 *             |--------------------->|                      |
 *             |                      | ACK                  |
 *             |                      |--------------------->|
 *             |.............................................|
 *
 */

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

public class KeepAlive extends CallStateHandler {

	public enum Style {
		UPDATE, OPTIONS, INVITE
	}

	Style style;
	long frequency;

	SipSession originSession;
	SipSession destinationSession;
	SipServletResponse originResponse;

	KeepAlive(SipSession originSession, SipSession destinationSession, Style style, long frequency) {
		this.originSession = originSession;
		this.destinationSession = destinationSession;
		this.style = style;
		this.frequency = frequency;
	}

	public void startTimer(SipApplicationSession appSession) {
		state = 1;
		ServletTimer t = TalkBACSipServlet.timer.createTimer(appSession, TalkBACSipServlet.keepAlive, false, this);
	}

	@Override
	public void processEvent(SipApplicationSession appSession, SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {

		if (request != null && request.getMethod().equals("NOTIFY")) {
			SipServletResponse okNotify = request.createResponse(200);
			okNotify.send();
			this.printOutboundMessage(okNotify);
			return;
		}

		switch (style) {
		case UPDATE:
			processEventUpdate(appSession, request, response, timer);
			break;
		case OPTIONS:
			processEventOptions(appSession, request, response, timer);
			break;
		case INVITE:
			processEventInvite(appSession, request, response, timer);
			break;
		}
	}

	public void processEventUpdate(SipApplicationSession appSession, SipServletRequest request, SipServletResponse response, ServletTimer timer)
			throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		switch (state) {
		case 1: // receive timeout
			SipServletRequest invite = originSession.createRequest("UPDATE");
			invite.send();
			this.printOutboundMessage(invite);

			state = 2;
			originSession.setAttribute(CALL_STATE_HANDLER, this);
			break;
		case 2:
			if (status == 200) {
				originResponse = response;

				SipServletRequest dstRqst = destinationSession.createRequest("UPDATE");
				dstRqst.send();
				this.printOutboundMessage(dstRqst);

				state = 3;
				destinationSession.setAttribute(CALL_STATE_HANDLER, this);
			} else {
				TerminateCall terminate = new TerminateCall();
				terminate.processEvent(appSession, request, response, timer);
			}

			break;
		case 3:
			if (status == 200) {
				// Cleanup call handlers.
				originSession.getApplicationSession().removeAttribute(CALL_STATE_HANDLER);
				originSession.removeAttribute(CALL_STATE_HANDLER);
				destinationSession.removeAttribute(CALL_STATE_HANDLER);

				if (frequency > 0) {
					state = 1;
					ServletTimer t = TalkBACSipServlet.timer.createTimer(appSession, frequency, false, this);
				}
			} else {
				TerminateCall terminate = new TerminateCall();
				terminate.processEvent(appSession, request, response, timer);
			}
			break;
		}

	}

	public void processEventOptions(SipApplicationSession appSession, SipServletRequest request, SipServletResponse response, ServletTimer timer)
			throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		switch (state) {
		case 1: // receive timeout
			SipServletRequest invite = originSession.createRequest("OPTIONS");
			invite.send();
			this.printOutboundMessage(invite);

			state = 2;
			originSession.setAttribute(CALL_STATE_HANDLER, this);
			break;
		case 2:
			if (status == 200) {
				originResponse = response;

				SipServletRequest dstRqst = destinationSession.createRequest("OPTIONS");
				dstRqst.send();
				this.printOutboundMessage(dstRqst);

				state = 3;
				destinationSession.setAttribute(CALL_STATE_HANDLER, this);
			}
			break;
		case 3:
			if (status == 200) {

				// Cleanup call handlers.
				originSession.getApplicationSession().removeAttribute(CALL_STATE_HANDLER);
				originSession.removeAttribute(CALL_STATE_HANDLER);
				destinationSession.removeAttribute(CALL_STATE_HANDLER);

				if (frequency > 0) {
					state = 1;
					ServletTimer t = TalkBACSipServlet.timer.createTimer(appSession, TalkBACSipServlet.keepAlive, false, this);
				}
			}
			break;
		}

	}

	public void processEventInvite(SipApplicationSession appSession, SipServletRequest request, SipServletResponse response, ServletTimer timer)
			throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		if (request != null && request.getMethod().equals("NOTIFY")) {
			SipServletResponse okNotify = request.createResponse(200);
			okNotify.send();
			this.printOutboundMessage(okNotify);
			return;
		}

		switch (state) {
		case 1: // receive timeout
			SipServletRequest invite = originSession.createRequest("INVITE");
			invite.send();
			this.printOutboundMessage(invite);

			state = 2;
			originSession.setAttribute(CALL_STATE_HANDLER, this);
			break;
		case 2:
			if (status == 200) {
				originResponse = response;

				SipServletRequest dstRqst = destinationSession.createRequest("INVITE");
				dstRqst.setContent(response.getContent(), response.getContentType());
				dstRqst.send();
				this.printOutboundMessage(dstRqst);

				state = 3;
				destinationSession.setAttribute(CALL_STATE_HANDLER, this);
			}
			break;
		case 3:
			if (status == 200) {
				SipServletRequest ack = response.createAck();
				ack.send();
				this.printOutboundMessage(ack);
				SipServletRequest ackRqst = originResponse.createAck();
				ackRqst.setContent(response.getContent(), response.getContentType());
				ackRqst.send();
				this.printOutboundMessage(ackRqst);

				// Cleanup call handlers.
				ack.getApplicationSession().removeAttribute(CALL_STATE_HANDLER);
				ack.getSession().removeAttribute(CALL_STATE_HANDLER);
				ackRqst.getSession().removeAttribute(CALL_STATE_HANDLER);

				if (frequency > 0) {
					state = 1;
					ServletTimer t = TalkBACSipServlet.timer.createTimer(appSession, TalkBACSipServlet.keepAlive, false, this);
				}
			}
			break;
		}

	}

}
