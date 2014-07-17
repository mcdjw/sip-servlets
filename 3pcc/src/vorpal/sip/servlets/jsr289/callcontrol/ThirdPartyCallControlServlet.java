package vorpal.sip.servlets.jsr289.callcontrol;

import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletContextEvent;
import javax.servlet.sip.SipServletListener;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSessionsUtil;
import javax.servlet.sip.annotation.SipListener;

import weblogic.kernel.KernelLogManager;

@SipListener
public class ThirdPartyCallControlServlet extends SipServlet implements SipServletListener {
	final static String INITIATOR = "INITIATOR";
	final static String ORIGIN_SESSION_ID = "ORIGIN_SESSION_ID";	
	final static String DESTINATION_SESSION_ID = "DESTINATION_SESSION_ID";
	static Logger logger;
	{
		logger = Logger.getLogger(ThirdPartyCallControlServlet.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	@Resource
	public static SipFactory factory;

	@Resource
	public static SipSessionsUtil util;

	@Override
	public void servletInitialized(SipServletContextEvent event) {
	}

	@Override
	protected void doRequest(SipServletRequest request) throws ServletException, IOException {
		try {

			CallStateHandler handler = (CallStateHandler) request.getSession().getAttribute(CallStateHandler.CALL_STATE_HANDLER);
			if (handler == null) {

				if(request.getMethod().equals("INVITE")){
				
				if (request.isInitial()) {
					CallFlow1 callflow = new CallFlow1();
					callflow.initiator = request;
					callflow.makeCall(request.getFrom(), request.getTo());
				}else{
					Reinvite reinvite = new Reinvite();
					reinvite.processEvent(request, null);
				}
					
					
				} else if (request.getMethod().equals("BYE")) {
					TerminateCall tc = new TerminateCall();
					tc.invoke(request);
				}

			} else {
				if (request.getMethod().equals("BYE")) {

				}

				logger.fine(handler.getClass() + " " + request.getMethod() + " " + " State: " + handler.state);
				handler.processEvent(request, null);

			}
		} catch (Exception e) {
			throw new ServletException(e);
		}

	}

	@Override
	protected void doResponse(SipServletResponse response) throws ServletException, IOException {
		SipServletRequest initiatorRequest = (SipServletRequest) response.getSession().getAttribute(INITIATOR);
		CallStateHandler handler = (CallStateHandler) response.getSession().getAttribute(CallStateHandler.CALL_STATE_HANDLER);

		try {
			logger.fine(handler.getClass() + " " + response.getMethod() + " " + response.getReasonPhrase() + " State: " + handler.state);

			if (handler != null) {
				handler.processEvent(null, response);
			}
		} catch (Exception e) {
			throw new ServletException(e);
		}

	}

}
