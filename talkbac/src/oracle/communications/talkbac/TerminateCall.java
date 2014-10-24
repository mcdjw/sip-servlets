package oracle.communications.talkbac;

import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Logger;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

import weblogic.kernel.KernelLogManager;

public class TerminateCall extends CallStateHandler {
	static Logger logger;
	{
		logger = Logger.getLogger(TerminateCall.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {

		SipApplicationSession appSession = null;
		SipSession sipSession = null;
		if (request != null) {
			appSession = request.getApplicationSession();
			sipSession = request.getSession();
		} else if (response != null) {
			appSession = response.getApplicationSession();
			sipSession = response.getSession();
		}

		Collection<ServletTimer> timers = appSession.getTimers();
		for (ServletTimer t : timers) {
			t.cancel();
		}

		Iterator<?> sessions = appSession.getSessions("SIP");

		String user = (String) appSession.getAttribute("USER");

		while (sessions.hasNext()) {
			SipSession ss = (SipSession) sessions.next();
			logger.info(ss.getId() + " " + ss.getState().toString());

			ss.removeAttribute(CALL_STATE_HANDLER);

			try {

				logger.fine("\t " + ss.getState() + ", Session: " + ss.getId() + ", Remote Party: " + ss.getRemoteParty().getURI().toString());
				if (ss.isValid() && false == ss.getId().equals(sipSession.getId())) {
					switch (ss.getState()) {

					case TERMINATED:
						// do nothing;
						break;

					case INITIAL:
					case EARLY:
					case CONFIRMED:
					default:

						if (false == ss.getRemoteParty().getURI().toString().equals(user)) {
							SipServletRequest bye = ss.createRequest("BYE");
							bye.send();
							this.printOutboundMessage(bye);
							ss.setAttribute(CALL_STATE_HANDLER, new InvalidateSession());
						}
						break;
					}
				}

			} catch (Exception e) {

				e.printStackTrace();
				// do nothing;
			}

		}
	}

}
