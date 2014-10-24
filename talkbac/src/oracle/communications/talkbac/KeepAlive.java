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

	SipSession originSession;
	SipSession destinationSession;
	SipServletResponse originResponse;

	KeepAlive(SipSession originSession, SipSession destinationSession, Style style) {
		this.originSession = originSession;
		this.destinationSession = destinationSession;
		this.style = style;
	}

	public void startTimer(SipApplicationSession appSession) {
		state = 1;
		ServletTimer t = TalkBACSipServlet.timer.createTimer(appSession, TalkBACSipServlet.keepAlive, false, this);
	}

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {

		if (request != null && request.getMethod().equals("NOTIFY")) {
			SipServletResponse okNotify = request.createResponse(200);
			okNotify.send();
			this.printOutboundMessage(okNotify);
			return;
		}

		switch (style) {
		case UPDATE:
			processEventUpdate(request, response, timer);
			break;
		case OPTIONS:
			processEventOptions(request, response, timer);
			break;
		case INVITE:
			processEventInvite(request, response, timer);
			break;
		}
	}

	public void processEventUpdate(SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		SipApplicationSession appSession = null;
		if (request != null) {
			appSession = request.getApplicationSession();
		} else if (response != null) {
			appSession = response.getApplicationSession();
		} else if (timer != null) {
			appSession = timer.getApplicationSession();
		}

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
			}
			break;
		case 3:
			if (status == 200) {

				// Cleanup call handlers.
				originSession.getApplicationSession().removeAttribute(CALL_STATE_HANDLER);
				originSession.removeAttribute(CALL_STATE_HANDLER);
				destinationSession.removeAttribute(CALL_STATE_HANDLER);

				state = 1;
				ServletTimer t = TalkBACSipServlet.timer.createTimer(appSession, TalkBACSipServlet.keepAlive, false, this);

			}
			break;
		}

	}

	public void processEventOptions(SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		SipApplicationSession appSession = null;
		if (request != null) {
			appSession = request.getApplicationSession();
		} else if (response != null) {
			appSession = response.getApplicationSession();
		} else if (timer != null) {
			appSession = timer.getApplicationSession();
		}

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

				state = 1;
				ServletTimer t = TalkBACSipServlet.timer.createTimer(appSession, TalkBACSipServlet.keepAlive, false, this);

			}
			break;
		}

	}

	public void processEventInvite(SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		SipApplicationSession appSession = null;
		if (request != null) {
			appSession = request.getApplicationSession();

			if (request.getMethod().equals("NOTIFY")) {
				SipServletResponse okNotify = request.createResponse(200);
				okNotify.send();
				this.printOutboundMessage(okNotify);
				return;
			}

		} else if (response != null) {
			appSession = response.getApplicationSession();
		} else if (timer != null) {
			appSession = timer.getApplicationSession();
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

				state = 1;
				ServletTimer t = TalkBACSipServlet.timer.createTimer(appSession, TalkBACSipServlet.keepAlive, false, this);

			}
			break;
		}

	}

}
