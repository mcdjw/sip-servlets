package oracle.communications.talkbac;

import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Logger;

import javax.servlet.sip.Address;
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
	public void processEvent(SipApplicationSession appSession, SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {

		SipSession sipSession = null;
		if (request != null) {
			sipSession = request.getSession();
		} else if (response != null) {
			sipSession = response.getSession();
		}

		Collection<ServletTimer> timers = appSession.getTimers();
		if (timers != null) {
			for (ServletTimer t : timers) {
				t.cancel();
			}
		}

		String user = (String) appSession.getAttribute("USER");
		Iterator<?> sessions = appSession.getSessions("SIP");
		while (sessions.hasNext()) {
			SipSession ss = (SipSession) sessions.next();

			if (msgUtility == null) {
				msgUtility = new TalkBACMessageUtility(appSession);
			}

			Address remoteParty = ss.getRemoteParty();
			if (remoteParty != null) {
				msgUtility.addEndpoint(ss.getRemoteParty());
			}

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
		TalkBACMessage msg = new TalkBACMessage(appSession, "call_completed");
		
		Address originAddress = (Address) appSession.getAttribute(TalkBACSipServlet.ORIGIN_ADDRESS);
		Address destinationAddress = (Address) appSession.getAttribute(TalkBACSipServlet.DESTINATION_ADDRESS);
		if(originAddress!=null && destinationAddress!=null){
			msg.setParameter("origin", originAddress.getURI().toString());
			msg.setParameter("destination", destinationAddress.getURI().toString());
		}
		msgUtility.send(msg);
		
		
	}

}
