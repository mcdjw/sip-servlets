package oracle.communications.talkbac;

import java.util.Iterator;
import java.util.logging.Logger;

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
	public void processEvent(SipServletRequest request, SipServletResponse response) throws Exception {

		if (request != null) { // BYE REQUEST

			SipApplicationSession appSession = request.getApplicationSession();
			SipSession sipSession = request.getSession();

			Iterator<?> sessions = appSession.getSessions("SIP");

			System.out.println("***TERMINATE***");
			System.out.println("Request: "
					+ request.getMethod()
					+ ", State: "
					+ request.getSession().getState().toString()
					+ ", Session: "
					+ request.getSession().getId());

			while (sessions.hasNext()) {
				SipSession ss = (SipSession) sessions.next();
				logger.info(ss.getId() + " " + ss.getState().toString());

				ss.removeAttribute(CALL_STATE_HANDLER);

				try {

					System.out.println("\t " + ss.getState() + ", Session: " + ss.getId() + ", " + ss.getRemoteParty().toString());
					if (ss.isValid() && false==request.getSession().equals(ss.getRemoteParty())) {
						switch (ss.getState()) {

						case INITIAL:
						case EARLY:
						case CONFIRMED:
						default:
							ss.createRequest("BYE").send();
							ss.setAttribute(CALL_STATE_HANDLER, this);
							System.out.println("\t sending BYE");
							break;
						}
					}

				} catch (Exception e) {

					e.printStackTrace();
					// do nothing;
				}

			}
		} else {
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
