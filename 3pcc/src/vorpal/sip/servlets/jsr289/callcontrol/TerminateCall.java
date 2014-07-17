package vorpal.sip.servlets.jsr289.callcontrol;

import java.util.Iterator;
import java.util.logging.Logger;

import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

import weblogic.kernel.KernelLogManager;

public class TerminateCall extends CallStateHandler{
	static Logger logger;
	{
		logger = Logger.getLogger(TerminateCall.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}
	
	public void invoke(SipServletRequest request) throws Exception{
		logger.fine("---TERMINATING CALL---");
		
		SipApplicationSession appSession = request.getApplicationSession();
		SipSession sipSession = request.getSession();

		request.createResponse(200).send();
		
		Iterator<?> sessions = appSession.getSessions("SIP");
		while (sessions.hasNext()) {
			SipSession ss = (SipSession) sessions.next();
			
			logger.info(ss.getId() + " "+ss.getState().toString());

			if (ss.getId() != sipSession.getId()) {
				switch (ss.getState()) {
				case CONFIRMED:
					ss.createRequest("BYE").send();
					ss.setAttribute(CALL_STATE_HANDLER, this);
					break;
				case INITIAL:
				case EARLY:
					ss.createRequest("CANCEL").send();
					ss.setAttribute(CALL_STATE_HANDLER, this);
					break;
				}
			}
		}
		
	}

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response) throws Exception {
		// TODO Auto-generated method stub
		
	}

}
