package oracle.communications.talkbac;

import java.util.ListIterator;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public abstract class CallFlowHandler extends CallStateHandler {
	private static final long serialVersionUID = 1L;
	protected boolean update_supported = false;
	protected boolean options_supported = false;
	protected boolean kpml_supported = false;

	@Override
	public abstract void processEvent(SipApplicationSession appSession, TalkBACMessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception;

	protected void discoverOptions(SipServletResponse response){
		// Support for Keep-Alive
		String allow;
		ListIterator<String> allows = response.getHeaders("Allow");
		while (allows.hasNext() && (update_supported == false || options_supported == false)) {
			allow = allows.next();
			if (allow.equals("UPDATE")) {
				update_supported = true;
			} else if (allow.equals("OPTIONS")) {
				options_supported = true;
			}
		}

		// Support for DTMF
		String event;
		ListIterator<String> events = response.getHeaders("Allow-Events");
		while (events.hasNext()) {
			event = events.next();
			if (event.equals("kpml")) {
				kpml_supported = true;
			}
		}

		
	}
	
	
}
