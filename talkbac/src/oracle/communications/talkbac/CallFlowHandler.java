package oracle.communications.talkbac;

import java.util.logging.Logger;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

import weblogic.kernel.KernelLogManager;

public abstract class CallFlowHandler extends CallStateHandler {
	private static final long serialVersionUID = 1L;
	protected boolean update_supported = true;
	protected boolean options_supported = true;
	protected boolean kpml_supported = true;

	static Logger logger;
	{
		logger = Logger.getLogger(CallFlowHandler.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	@Override
	public abstract void processEvent(SipApplicationSession appSession, MessageUtility msgUtility, SipServletRequest request,
			SipServletResponse response, ServletTimer timer) throws Exception;

	protected void discoverOptions(SipServletResponse response) {
		// Support for Keep-Alive
		// String allow;
		// ListIterator<String> allows = response.getHeaders("Allow");
		// while (allows.hasNext()) {
		// allow = allows.next();
		// if (allow.equals("UPDATE")) {
		// update_supported = true;
		// } else if (allow.equals("OPTIONS")) {
		// options_supported = true;
		// }
		// }
		//
		// // Support for DTMF
		// String event;
		// ListIterator<String> events = response.getHeaders("Allow-Events");
		// while (events.hasNext()) {
		// event = events.next();
		// if (event.equals("kpml")) {
		// kpml_supported = true;
		// }
		// }

		kpml_supported = true;
		update_supported = true;
		options_supported = true;

	}
	
	static final String blackhole = ""
			+ "v=0\r\n"
			+ "o=- 15474517 1 IN IP4 127.0.0.1\r\n"
			+ "s=cpc_med\r\n"
			+ "c=IN IP4 0.0.0.0\r\n"
			+ "t=0 0\r\n"
			+ "m=audio 23348 RTP/AVP 0\r\n"
			+ "a=rtpmap:0 pcmu/8000\r\n"
			+ "a=rtpmap:101 telephone-event/8000\r\n"
			+ "a=inactive\r\n";

}
