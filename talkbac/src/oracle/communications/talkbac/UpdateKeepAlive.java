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
import java.util.logging.Logger;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

import weblogic.kernel.KernelLogManager;

public class UpdateKeepAlive extends CallStateHandler {
	static Logger logger;
	{
		logger = Logger.getLogger(UpdateKeepAlive.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}
	private static final long serialVersionUID = 1L;

	public enum Style {
		UPDATE, OPTIONS, INVITE
	}

	Style style;
	long frequency;

	SipSession originSession;
	SipSession destinationSession;
	SipServletResponse originResponse;

	UpdateKeepAlive(long frequency) {
		this.frequency = frequency;
	}

	public void startTimer(SipApplicationSession appSession) {
		state = 1;
		ServletTimer timer = TalkBACSipServlet.timer.createTimer(appSession, TalkBACSipServlet.keepAlive, false, this);
		this.printTimer(timer);
	}

	@SuppressWarnings("unchecked")
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

					// do not invoke keep alive in the middle of a existing call
					// flow, try again next time
					if (null == sipSession.getAttribute(CALL_STATE_HANDLER)) {
						update = sipSession.createRequest("UPDATE");
						update.send();
						this.printOutboundMessage(update);
						sipSession.setAttribute(CALL_STATE_HANDLER, this);
					}

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
