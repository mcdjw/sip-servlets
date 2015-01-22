package oracle.communications.talkbac;

import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.sip.Address;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

import weblogic.kernel.KernelLogManager;

public class TerminateCall extends CallStateHandler {
	private static final long serialVersionUID = 1L;
	static Logger logger;
	{
		logger = Logger.getLogger(TerminateCall.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	@Override
	public void processEvent(SipApplicationSession appSession, TalkBACMessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {

		// Send the message now, not later
		TalkBACMessage msg = new TalkBACMessage(appSession, "call_completed");
		Address originAddress = (Address) appSession.getAttribute(TalkBACSipServlet.ORIGIN_ADDRESS);
		Address destinationAddress = (Address) appSession.getAttribute(TalkBACSipServlet.DESTINATION_ADDRESS);
		if (originAddress != null && destinationAddress != null) {
			msg.setParameter("origin", originAddress.getURI().toString());
			msg.setParameter("destination", destinationAddress.getURI().toString());
		}
		msgUtility.send(msg);

		appSession.removeAttribute(CALL_STATE_HANDLER);

		Collection<ServletTimer> timers = appSession.getTimers();
		if (timers != null) {
			for (ServletTimer t : timers) {
				t.cancel();
			}
		}

		Iterator<?> sessions = appSession.getSessions("SIP");
		while (sessions.hasNext()) {
			SipSession ss = (SipSession) sessions.next();

			// if (logger.isLoggable(Level.FINE)) {
			// System.out.println(this.getClass().getSimpleName()
			// + " ["
			// + appSession.getId().hashCode()
			// + ":"
			// + ss.getId().hashCode()
			// + "] "
			// + ss.getState()
			// + " "
			// + ss.isValid()
			// + " "
			// + ss.getAttribute(REQUEST_DIRECTION));
			// }

			if (ss.isValid()) {
				ss.removeAttribute(CALL_STATE_HANDLER);

				try {

					switch (ss.getState()) {

					case INITIAL:
					case EARLY:
						SipServletRequest initialInvite = (SipServletRequest) ss.getAttribute(CallStateHandler.INITIAL_INVITE_REQUEST);
						if (initialInvite != null) {

							String direction = (String) ss.getAttribute(CallStateHandler.REQUEST_DIRECTION);
							if (direction != null && direction.equals("OUTBOUND")) {
								SipServletRequest cancel = initialInvite.createCancel();
								cancel.send();
								this.printOutboundMessage(cancel);
								ss.setAttribute(CALL_STATE_HANDLER, new InvalidateSession());
							} else {
								SipServletResponse errorResponse = initialInvite.createResponse(487);
								this.printOutboundMessage(errorResponse);
								errorResponse.send();
							}

						} else {

						}
						break;

					case CONFIRMED:
						if (request != null && request.getMethod().equals("BYE")) {
							if (false == ss.getId().equals(request.getSession().getId())) {
								SipServletRequest bye = ss.createRequest("BYE");
								bye.send();
								this.printOutboundMessage(bye);
								ss.setAttribute(CALL_STATE_HANDLER, new InvalidateSession());
							}
						} else {
							SipServletRequest bye = ss.createRequest("BYE");
							bye.send();
							this.printOutboundMessage(bye);
							ss.setAttribute(CALL_STATE_HANDLER, new InvalidateSession());
						}

						break;

					case TERMINATED:
					default:
						// do nothing;
						break;

					}

				} catch (Exception e) {
					if (logger.isLoggable(Level.FINE)) {
						System.out.println(this.getClass().getSimpleName() + " " + e.getMessage());
					}
				}

			}

		}

//		if (appSession.isValid()) {
//			appSession.invalidate();
//		}

	}

}
