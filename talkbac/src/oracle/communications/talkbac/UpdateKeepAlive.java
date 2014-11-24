package oracle.communications.talkbac;

/*
 * Keep Alive via UPDATE
 * on timeout/error, disconnect call leg
 *
 *             A           Controller            B                C
 *             |.................................|................|
 *             | UPDATE         |                |                |
 *             |<---------------|                |                |
 *             |                | UPDATE         |                |
 *             |                |--------------->|                |
 *             |                | UPDATE         |                |
 *             | 200 OK         |-------------------------------->|
 *             |--------------->| 200 OK         |                |
 *             |                |<---------------|                |
 *             |                | 408 Timeout    |                |
 *             |                |<--------------------------------|
 *             |                |                |                |
 *             |           disconnect(C)         |                |
 *             |                |                |                |
 *             |.................................|
 *
 */

import java.util.Iterator;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipSession.State;

public class UpdateKeepAlive extends CallStateHandler {

	public enum Style {
		UPDATE, OPTIONS, INVITE
	}

	Style style;
	long frequency;

	SipSession originSession;
	SipSession destinationSession;
	SipServletResponse originResponse;

	UpdateKeepAlive(long frequency) {
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
	public void processEvent(SipApplicationSession appSession, TalkBACMessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {

		if (timer != null) {
			SipServletRequest update;
			SipSession sipSession;
			Iterator<SipSession> itr = (Iterator<SipSession>) appSession.getSessions();
			while (itr.hasNext()) {
				sipSession = itr.next();
				if (sipSession.isValid()) {
					update = sipSession.createRequest("UPDATE");
					update.send();
					this.printOutboundMessage(update);
					sipSession.setAttribute(CALL_STATE_HANDLER, this);
				}
			}
			startTimer(appSession);
		} else if (response != null) {
			if (response.getStatus() != 200) {
				CallStateHandler handler = new Disconnect(response.getSession());
				handler.processEvent(appSession, msgUtility, request, response, timer);
			}
		}

	}

}
