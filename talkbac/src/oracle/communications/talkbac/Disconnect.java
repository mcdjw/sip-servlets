package oracle.communications.talkbac;

import java.util.Iterator;
import java.util.logging.Logger;

import javax.servlet.sip.Address;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

import weblogic.kernel.KernelLogManager;

public class Disconnect extends CallStateHandler {
	private static final long serialVersionUID = 1L;
	static Logger logger;
	{
		logger = Logger.getLogger(Disconnect.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	private SipSession sipSession = null;
	private Address address = null;

	Disconnect(SipSession sipSession) {
		this.sipSession = sipSession;
	}

	Disconnect(Address address) {
		this.address = address;
	}

	@Override
	public void processEvent(SipApplicationSession appSession, TalkBACMessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {
		SipServletRequest bye;

		switch (state) {
		case 1:
			sipSession = (sipSession != null) ? sipSession : findSession(appSession, address);

			int count = 0;

			Iterator<SipSession> itr = (Iterator<SipSession>) appSession.getSessions();
			while (itr.hasNext()) {
				if (itr.next().isValid()) {
					count++;
				}
			}

			if (count >= 3) {
				bye = sipSession.createRequest("BYE");
				bye.send();
				this.printOutboundMessage(bye);

				state = 2;
				sipSession.setAttribute(CALL_STATE_HANDLER, this);
			} else {

				SipSession tmpSession;
				Iterator<SipSession> itr2 = (Iterator<SipSession>) appSession.getSessions();
				while (itr2.hasNext()) {
					tmpSession = itr2.next();
					if (tmpSession.isValid()) {
						bye = sipSession.createRequest("BYE");
						bye.send();
						this.printOutboundMessage(bye);
					}
				}
			}
			break;
		case 2:
			response.getSession().invalidate();
			break;
		}

	}

}