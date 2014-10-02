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

		if (request != null) { // BYE REQUEST
			SipApplicationSession appSession = request.getApplicationSession();
			SipSession sipSession = request.getSession();

			Collection<ServletTimer> timers = appSession.getTimers();
			for (ServletTimer t : timers) {
				t.cancel();
			}

			Iterator<?> sessions = appSession.getSessions("SIP");

			String user = (String) request.getApplicationSession().getAttribute("USER");

			logger.fine("***TERMINATE***");
			logger.fine("Request: " + request.getMethod() + ", State: " + sipSession.getState().toString() + ", Session: " + sipSession.getId()
					+ ", Remote Party: " + sipSession.getRemoteParty().toString() + ", User: " + user);

			while (sessions.hasNext()) {
				SipSession ss = (SipSession) sessions.next();
				logger.info(ss.getId() + " " + ss.getState().toString());

				ss.removeAttribute(CALL_STATE_HANDLER);

				try {

					logger.fine("\t " + ss.getState() + ", Session: " + ss.getId() + ", Remote Party: " + ss.getRemoteParty().getURI().toString());
					if (ss.isValid() && false == ss.getId().equals(request.getSession().getId())) {
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
								ss.setAttribute(CALL_STATE_HANDLER, this);
								logger.fine("\t sending BYE");
							}
							break;
						}
					}

				} catch (Exception e) {

					e.printStackTrace();
					// do nothing;
				}

			}
		} else {

			// Does this code even work?
			// I guess it does.

			response.getSession().invalidate();
			boolean invalidate = true;
			SipSession ss;

			Iterator<?> sessions = response.getApplicationSession().getSessions("SIP");
			while (sessions.hasNext()) {
				ss = (SipSession) sessions.next();
				if (ss.isValid()) {
					invalidate = false;
				}
			}

			if (invalidate == true) {
				response.getApplicationSession().invalidate();
			}
		}
	}

}
