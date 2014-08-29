package oracle.communications.talkbac;

import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public abstract class CallFlowHandler extends CallStateHandler {

	protected SipServletRequest destinationRequest;
	protected SipServletRequest originRequest;
	
	@Override
	public abstract void processEvent(SipServletRequest request, SipServletResponse response) throws Exception;


}
