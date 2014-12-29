package oracle.communications.talkbac;

import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

import weblogic.kernel.KernelLogManager;

public abstract class CallFlowHandler extends CallStateHandler {
	private static final long serialVersionUID = 1L;
	protected boolean update_supported = false;
	protected boolean options_supported = false;
	protected boolean kpml_supported = false;

	static Logger logger;
	{
		logger = Logger.getLogger(CallFlowHandler.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	@Override
	public abstract void processEvent(SipApplicationSession appSession,
			TalkBACMessageUtility msgUtility, SipServletRequest request,
			SipServletResponse response, ServletTimer timer) throws Exception;

	protected void discoverOptions(SipServletResponse response) {
		// Support for Keep-Alive
		String allow;
		ListIterator<String> allows = response.getHeaders("Allow");
		while (allows.hasNext()) {

			allow = allows.next();

			if (logger.isLoggable(Level.FINE)) {
				System.out.println("Allow: " + allow);
			}

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

			if (logger.isLoggable(Level.FINE)) {
				System.out.println("Allow-Events: " + event);
			}

			if (event.equals("kpml")) {
				kpml_supported = true;
			}
		}

		if (logger.isLoggable(Level.FINE)) {
			System.out.println("update_supported: " + update_supported);
			System.out.println("options_supported: " + options_supported);
			System.out.println("kpml_supported: " + kpml_supported);
		}

	}

}
